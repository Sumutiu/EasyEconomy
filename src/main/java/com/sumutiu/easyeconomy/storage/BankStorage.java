package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sumutiu.easyeconomy.EasyEconomy.STORAGE_FOLDER;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class BankStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // In-memory cache for balances
    private static final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();

    // Players whose latest balance could not be written to disk yet
    private static final Set<UUID> unsavedBalances = ConcurrentHashMap.newKeySet();

    // Bank files that could not be read, and when; they are not retried for a short while
    // so placeholders/scoreboards can't hammer the disk and flood the log
    private static final Map<UUID, Long> failedReadAt = new ConcurrentHashMap<>();
    private static final long READ_RETRY_DELAY_MS = 10_000;

    public static File getPlayerFile(UUID uuid) {
        return new File(STORAGE_FOLDER, uuid.toString() + ".json");
    }

    // ----------------------------
    // Read / Write (per player)
    // ----------------------------
    // Returns null if the file exists but can't be read right now. Callers must then
    // not cache anything or write the file, or the real balance would be overwritten.
    // A corrupt file is moved aside (so its data is kept) and counts as a new, empty bank.
    private static Long loadFromFile(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) return 0L;

        try (Reader reader = new FileReader(file)) {
            PlayerBankData data = GSON.fromJson(reader, PlayerBankData.class);
            return (data != null) ? data.balance : 0L;
        } catch (JsonParseException e) {
            Logger(2, String.format(BANK_FILE_READ_FAILED_PLAYER, uuid, e.getMessage()));
            if (StorageUtil.isCorruptFileError(e)) return null;

            // If the corrupt file can't be moved aside, leave it alone instead of overwriting it
            return StorageUtil.quarantineCorruptFile(file) ? 0L : null;
        } catch (IOException e) {
            Logger(2, String.format(BANK_FILE_READ_FAILED_PLAYER, uuid, e.getMessage()));
            return null;
        }
    }

    // Returns false if the balance could not be written to disk
    public static boolean saveToFile(UUID uuid, long balance) {
        File file = getPlayerFile(uuid);
        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");

        PlayerBankData data = new PlayerBankData(balance);

        // Serialize to a String first: Gson wraps write errors in an unchecked JsonIOException
        try {
            Files.writeString(tmpFile.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger(2, String.format(BANK_FILE_WRITE_FAILED_PLAYER, uuid, e.getMessage()));
            unsavedBalances.add(uuid);
            return false;
        }

        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Logger(1, String.format(BANK_TEMP_RENAME_FAILED, uuid, e.getMessage()));
            unsavedBalances.add(uuid);
            return false;
        }

        unsavedBalances.remove(uuid);
        return true;
    }

    // ----------------------------
    // Public API
    // ----------------------------
    /**
     * Returns the player's balance, or null if their bank file can't be read right now.
     * Nothing is cached on failure, so the next call simply tries again.
     */
    public static synchronized Long tryGetBalance(UUID uuid) {
        Long cached = balanceCache.get(uuid);
        if (cached != null) return cached;

        Long loaded = loadWithRetryDelay(uuid);
        if (loaded != null) balanceCache.put(uuid, loaded);
        return loaded;
    }

    // Reads from disk, but doesn't retry a file that failed to read only moments ago
    private static Long loadWithRetryDelay(UUID uuid) {
        Long failedAt = failedReadAt.get(uuid);
        if (failedAt != null && System.currentTimeMillis() - failedAt < READ_RETRY_DELAY_MS) return null;

        Long loaded = loadFromFile(uuid);
        if (loaded != null) {
            failedReadAt.remove(uuid);
        } else {
            failedReadAt.put(uuid, System.currentTimeMillis());
        }
        return loaded;
    }

    // Returns false (and changes nothing) if the current balance can't be read
    public static synchronized boolean addBalance(UUID uuid, long amount) {
        if (amount <= 0) return true; // nothing to add
        Long current = tryGetBalance(uuid);
        if (current == null) return false;
        long newBalance = current + amount;
        balanceCache.put(uuid, newBalance);
        saveToFile(uuid, newBalance);
        Logger(0, String.format(BANK_ADDED, amount, uuid, newBalance));
        return true;
    }

    // Returns false (and changes nothing) if the balance is too low or can't be read
    public static synchronized boolean removeBalance(UUID uuid, long amount) {
        if (amount <= 0) return false;
        Long current = tryGetBalance(uuid);
        if (current == null || current < amount) return false;
        long newBalance = current - amount;
        balanceCache.put(uuid, newBalance);
        saveToFile(uuid, newBalance);
        Logger(0, String.format(BANK_REMOVED, amount, uuid, newBalance));
        return true;
    }

    public static synchronized void setBalance(UUID uuid, long amount) {
        if (amount < 0) amount = 0;
        balanceCache.put(uuid, amount);
        saveToFile(uuid, amount);
        Logger(0, String.format(BANK_SET, uuid, amount));
    }

    // Optionally clear cache when player leaves
    public static synchronized void unloadPlayer(UUID uuid) {
        // Keep the balance in memory if it never made it to disk, otherwise it would be lost
        if (!saveIfUnsaved(uuid)) return;
        balanceCache.remove(uuid);
    }

    // Clear everything when the server stops, so balances from one world
    // never leak into another world opened in the same game session
    public static synchronized void clearCache() {
        for (UUID uuid : new ArrayList<>(unsavedBalances)) {
            saveIfUnsaved(uuid);
        }
        balanceCache.clear();
        unsavedBalances.clear();
        failedReadAt.clear();
    }

    // Retries a failed save. Returns true if nothing is left unsaved for this player.
    private static boolean saveIfUnsaved(UUID uuid) {
        if (!unsavedBalances.contains(uuid)) return true;

        Long balance = balanceCache.get(uuid);
        if (balance == null) {
            unsavedBalances.remove(uuid);
            return true;
        }
        return saveToFile(uuid, balance);
    }

    /**
     * Returns the richest players, highest balance first.
     * Offline players are read straight from their files and are not added to the cache.
     */
    public static synchronized List<Map.Entry<UUID, Long>> getTopBalances(int count) {
        File folder = STORAGE_FOLDER;
        if (folder == null || !folder.exists()) return Collections.emptyList();

        File[] files = folder.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".json"));
        if (files == null) return Collections.emptyList();

        Map<UUID, Long> allBalances = new HashMap<>();
        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(name.substring(0, dot));
            } catch (IllegalArgumentException ignored) {
                continue; // Not a player bank file (e.g. name_cache.json)
            }

            Long cached = balanceCache.get(uuid);
            Long balance = cached != null ? cached : loadWithRetryDelay(uuid);
            if (balance == null) {
                Logger(1, String.format(BANK_UNREADABLE_SKIPPED, uuid));
                continue;
            }
            allBalances.put(uuid, balance);
        }

        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(allBalances.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        if (sorted.size() <= count) return sorted;
        return sorted.subList(0, count);
    }

    // ----------------------------
    // Data Model
    // ----------------------------
    private static class PlayerBankData {
        long balance;

        PlayerBankData(long balance) {
            this.balance = balance;
        }
    }
}
