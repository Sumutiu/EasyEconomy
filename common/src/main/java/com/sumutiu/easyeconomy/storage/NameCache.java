package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sumutiu.easyeconomy.EasyEconomy.STORAGE_FOLDER;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

/**
 * Remembers the last known name of every player (UUID -> name), so offline players
 * can be shown by name in the leaderboard and found by name in admin commands.
 */
public class NameCache {

    public static final String FILE_NAME = "name_cache.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public static File getFile() {
        return new File(STORAGE_FOLDER, FILE_NAME);
    }

    public static synchronized void load() {
        cache.clear();

        File file = getFile();
        if (!file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            Map<String, String> raw = GSON.fromJson(reader, MAP_TYPE);
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    try {
                        if (entry.getValue() != null) {
                            cache.put(UUID.fromString(entry.getKey()), entry.getValue());
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Skip entries that are not valid UUIDs
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            Logger(2, String.format(NAME_CACHE_LOAD_FAILED, e.getMessage()));
        }
    }

    public static synchronized void save() {
        Map<String, String> raw = new HashMap<>();
        for (Map.Entry<UUID, String> entry : cache.entrySet()) {
            raw.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            StorageUtil.writeJsonAtomically(getFile(), raw, GSON);
        } catch (IOException e) {
            Logger(2, String.format(NAME_CACHE_SAVE_FAILED, e.getMessage()));
        }
    }

    public static synchronized void clear() {
        cache.clear();
    }

    public static synchronized void put(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;

        // Names are unique at any moment: if another account used this name before
        // (name change), forget that old entry so lookups by name find the right player
        boolean changed = cache.entrySet().removeIf(e -> !e.getKey().equals(uuid) && e.getValue().equalsIgnoreCase(name));

        String previous = cache.put(uuid, name);
        if (changed || !name.equals(previous)) {
            save();
        }
    }

    public static String getName(UUID uuid) {
        return cache.getOrDefault(uuid, uuid.toString().substring(0, 8) + "...");
    }

    public static UUID getUUID(String name) {
        if (name == null || name.isBlank()) return null;
        for (Map.Entry<UUID, String> entry : cache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String[] getAllNames() {
        return cache.values().toArray(new String[0]);
    }

    public static String[] getAutocompleteSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : cache.entrySet()) {
            String name = entry.getValue();
            if (name != null && !name.isBlank()) {
                suggestions.add(name);
            }
        }

        if (STORAGE_FOLDER != null && STORAGE_FOLDER.exists()) {
            File[] files = STORAGE_FOLDER.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".json") && !f.getName().equals(FILE_NAME));
            if (files != null) {
                for (File f : files) {
                    int dot = f.getName().lastIndexOf('.');
                    if (dot <= 0) continue;
                    try {
                        UUID uuid = UUID.fromString(f.getName().substring(0, dot));
                        if (!cache.containsKey(uuid)) {
                            suggestions.add(uuid.toString());
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Not a player bank file
                    }
                }
            }
        }

        return suggestions.toArray(new String[0]);
    }
}
