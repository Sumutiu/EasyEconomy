package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sumutiu.easyeconomy.config.EasyEconomyConfig;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.sumutiu.easyeconomy.EasyEconomy.AH_FOLDER;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

/**
 * Shop listings, one JSON file per seller in the AH folder.
 * All files are read once when the server starts and kept in memory; every change is
 * written to the seller's file straight away, so the Shop never has to read the disk.
 */
public class AHStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Object BUY_LOCK = new Object();

    // Seller -> their listings. The lists are never changed in place: every change
    // builds a new list, saves it, and only then replaces the old one (copy-on-write)
    private static final Map<UUID, List<AHListing>> listingsBySeller = new ConcurrentHashMap<>();

    public static class AHListing {
        public String itemId;
        public int quantity;
        public long price; // in diamonds
        public long timestamp; // epoch millis
        public UUID seller;
        public String sellerName;
        public String nbt; // Serialized ItemStack SNBT

        // Parsed item, so the SNBT is only parsed once (transient = not saved to the file)
        public transient ItemStack cachedStack;

        public AHListing(String itemId, int quantity, long price, UUID seller, String sellerName, String nbt) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = System.currentTimeMillis();
            this.seller = seller;
            this.sellerName = sellerName;
            this.nbt = nbt;
        }

        public long getExpiresAt() {
            return timestamp + EasyEconomyConfig.getListingDurationMs();
        }

        public boolean isExpired(long now) {
            return now >= getExpiresAt();
        }
    }

    public enum AddResult {
        ADDED,
        LIMIT_REACHED,
        FAILED
    }

    public static File getFile(UUID uuid) {
        return new File(AH_FOLDER, uuid.toString() + ".json");
    }

    // ----------------------------
    // Loading
    // ----------------------------

    // Reads every Shop file into memory. Called once when the server starts.
    public static synchronized void loadAll() {
        listingsBySeller.clear();

        if (!AH_FOLDER.exists()) {
            Logger(1, String.format(AH_FOLDER_NOT_FOUND, AH_FOLDER.getPath()));
            return;
        }

        File[] files = AH_FOLDER.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".json"));

        if (files == null) {
            Logger(2, String.format(AH_FILE_ERROR, AH_FOLDER.getPath()));
            return;
        }

        int listingCount = 0;
        int fileCount = 0;

        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                Logger(1, String.format(AH_FILE_NAME_ERROR, name));
                continue;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(name.substring(0, dot));
            } catch (IllegalArgumentException ex) {
                Logger(1, String.format(AH_FILE_NAME_NO_UUID, name));
                continue;
            }

            // Only exact "<uuid>.json" names; anything else would be saved under a second file name
            if (!name.equals(uuid + ".json")) {
                Logger(1, String.format(AH_FILE_NAME_ERROR, name));
                continue;
            }

            List<AHListing> listings = tryLoadFromFile(uuid);
            if (listings == null) {
                Logger(1, String.format(AH_FILE_UNREADABLE_SKIPPED, uuid));
                continue;
            }

            listingsBySeller.put(uuid, listings);
            listingCount += listings.size();
            fileCount++;
        }

        Logger(0, String.format(AH_LISTINGS_LOADED, listingCount, fileCount));
    }

    // Forgets everything (server stopped)
    public static synchronized void clear() {
        listingsBySeller.clear();
    }

    // Returns null if the file exists but could not be read, so callers that
    // write the file back don't overwrite listings they never saw
    private static List<AHListing> tryLoadFromFile(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) return new ArrayList<>();

        try (Reader reader = new FileReader(file)) {
            AHListing[] arr = GSON.fromJson(reader, AHListing[].class);
            List<AHListing> listings = new ArrayList<>();
            if (arr == null) return listings;

            for (AHListing l : arr) {
                if (l == null) continue;
                // The file belongs to this seller, so that is who gets paid and can take items back
                l.seller = uuid;
                listings.add(l);
            }
            return listings;
        } catch (JsonParseException e) {
            Logger(2, String.format(AH_FILE_LOAD_ERROR, uuid, e.getMessage()));
            if (StorageUtil.isTemporaryReadError(e)) return null;

            // Corrupt file: move it aside (keeps the data) and start fresh.
            // If it can't be moved, leave it alone instead of overwriting it.
            return StorageUtil.quarantineCorruptFile(file) ? new ArrayList<>() : null;
        } catch (IOException e) {
            Logger(2, String.format(AH_FILE_LOAD_ERROR, uuid, e.getMessage()));
            return null;
        }
    }

    // The seller's listings from memory; if their file could not be read at startup, try again now.
    // Returns null if the file still can't be read.
    private static List<AHListing> getOrLoad(UUID seller) {
        List<AHListing> listings = listingsBySeller.get(seller);
        if (listings != null) return listings;

        listings = tryLoadFromFile(seller);
        if (listings != null) listingsBySeller.put(seller, listings);
        return listings;
    }

    // Returns false if the file could not be written
    private static boolean saveListings(UUID uuid, List<AHListing> listings) {
        try {
            StorageUtil.writeJsonAtomically(getFile(uuid), listings, GSON);
            return true;
        } catch (IOException e) {
            Logger(2, String.format(AH_FILE_SAVE_ERROR, uuid, e.getMessage()));
            return false;
        }
    }

    // ----------------------------
    // Public API
    // ----------------------------

    // All listings of one seller (active and expired). Empty if their file can't be read.
    public static synchronized List<AHListing> getListings(UUID seller) {
        List<AHListing> listings = tryGetListings(seller);
        return listings != null ? listings : new ArrayList<>();
    }

    // Same as getListings, but null if the seller's file can't be read right now
    public static synchronized List<AHListing> tryGetListings(UUID seller) {
        List<AHListing> listings = getOrLoad(seller);
        return listings != null ? new ArrayList<>(listings) : null;
    }

    // Listings that count toward the per-player limit. Listings whose item data is known
    // to be unreadable are left out, since the player can't take those back
    public static synchronized int countListingsForLimit(UUID seller) {
        List<AHListing> listings = getOrLoad(seller);
        if (listings == null) return 0;

        int count = 0;
        for (AHListing l : listings) {
            if (!isKnownUnreadable(l)) count++;
        }
        return count;
    }

    public static boolean isKnownUnreadable(AHListing listing) {
        return listing.cachedStack != null && listing.cachedStack.isEmpty();
    }

    // Every active listing in the Shop, newest first
    public static synchronized List<AHListing> getAllActiveListings() {
        long now = System.currentTimeMillis();
        List<AHListing> all = new ArrayList<>();

        for (List<AHListing> listings : listingsBySeller.values()) {
            for (AHListing l : listings) {
                if (!l.isExpired(now)) all.add(l);
            }
        }

        sortNewestFirst(all);
        return all;
    }

    /**
     * Adds a listing and saves the seller's file.
     * The caller must only take the item from the player when this returns ADDED.
     */
    public static synchronized AddResult addListing(UUID seller, AHListing listing) {
        List<AHListing> current = getOrLoad(seller);
        if (current == null) return AddResult.FAILED;

        // Expired listings that were not reclaimed yet count too
        int limit = EasyEconomyConfig.getMaxListingsPerPlayer();
        if (limit > 0 && countListingsForLimit(seller) >= limit) return AddResult.LIMIT_REACHED;

        // (seller, timestamp) identifies a listing, so keep timestamps unique per seller
        while (hasListingAt(current, listing.timestamp)) {
            listing.timestamp++;
        }

        List<AHListing> updated = new ArrayList<>(current);
        updated.add(listing);

        if (saveListings(seller, updated)) {
            listingsBySeller.put(seller, updated);
            return AddResult.ADDED;
        }
        return AddResult.FAILED;
    }

    /**
     * Removes a listing and saves the seller's file.
     * Returns false if the listing was no longer there (already bought or taken back)
     * or the file could not be saved, so callers can stop before handing out the item.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted") // callers use it as a guard: if (!removeListing(...)) stop
    public static synchronized boolean removeListing(UUID seller, long timestamp) {
        List<AHListing> current = getOrLoad(seller);
        if (current == null) return false;

        // Remove only the first match, never more than one listing
        for (int i = 0; i < current.size(); i++) {
            AHListing l = current.get(i);
            if (l.timestamp == timestamp && seller.equals(l.seller)) {
                List<AHListing> updated = new ArrayList<>(current);
                updated.remove(i);

                if (saveListings(seller, updated)) {
                    listingsBySeller.put(seller, updated);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static Object getBuyLock() {
        return BUY_LOCK;
    }

    // ----------------------------
    // Helpers
    // ----------------------------
    private static boolean hasListingAt(List<AHListing> listings, long timestamp) {
        for (AHListing l : listings) {
            if (l.timestamp == timestamp) return true;
        }
        return false;
    }

    public static void sortNewestFirst(List<AHListing> listings) {
        listings.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
    }

    public static List<AHListing> getActiveListings(List<AHListing> all) {
        long now = System.currentTimeMillis();
        return all.stream()
                .filter(l -> !l.isExpired(now))
                .collect(Collectors.toList());
    }

    public static List<AHListing> getExpiredListings(List<AHListing> all) {
        long now = System.currentTimeMillis();
        return all.stream()
                .filter(l -> l.isExpired(now))
                .collect(Collectors.toList());
    }
}
