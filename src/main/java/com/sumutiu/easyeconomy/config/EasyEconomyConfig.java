package com.sumutiu.easyeconomy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sumutiu.easyeconomy.storage.StorageUtil;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

/**
 * Server settings, stored in config/easyeconomy.json.
 * The file is created with the defaults on first start; missing keys are added with their defaults.
 * Invalid values are replaced in memory only (with a warning), so the admin's file is never changed.
 */
public class EasyEconomyConfig {

    public static final String FILE_NAME = "EasyEconomy.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Field names are the keys in the JSON file
    private static final class Values {
        // Most listings (active + expired, not yet reclaimed) one player can have. 0 = no limit
        int maxListingsPerPlayer = 20;

        // How long a listing stays in the Shop before it expires
        int listingDurationHours = 24;
    }

    private static volatile Values values = new Values();

    private static final Set<String> KEYS = Set.of("maxListingsPerPlayer", "listingDurationHours");

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Values loaded = new Values();
        boolean writeFile = true;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new JsonParseException("not a JSON object");

                JsonObject json = root.getAsJsonObject();
                for (String key : json.keySet()) {
                    if (!KEYS.contains(key)) Logger(1, String.format(CONFIG_UNKNOWN_KEY, key, path));
                }

                loaded = GSON.fromJson(json, Values.class);
                if (loaded == null) loaded = new Values();

                // Only rewrite an existing file to add keys it is missing
                writeFile = !json.keySet().containsAll(KEYS);
            } catch (IOException | RuntimeException e) {
                // Use the defaults for now, but never overwrite a file the admin may be editing
                Logger(2, String.format(CONFIG_LOAD_FAILED, path, e.getMessage()));
                values = new Values();
                return;
            }
        }

        if (writeFile) {
            // Creates the file on first start, or adds missing keys with their defaults
            try {
                StorageUtil.writeJsonAtomically(path.toFile(), loaded, GSON);
            } catch (IOException e) {
                Logger(1, String.format(CONFIG_SAVE_FAILED, path, e.getMessage()));
            }
        }

        if (loaded.maxListingsPerPlayer < 0) {
            Logger(1, String.format(CONFIG_INVALID_VALUE, "maxListingsPerPlayer", loaded.maxListingsPerPlayer, 0));
            loaded.maxListingsPerPlayer = 0;
        }
        if (loaded.listingDurationHours < 1) {
            Logger(1, String.format(CONFIG_INVALID_VALUE, "listingDurationHours", loaded.listingDurationHours, 24));
            loaded.listingDurationHours = 24;
        }

        values = loaded;
    }

    // 0 = no limit
    public static int getMaxListingsPerPlayer() {
        return values.maxListingsPerPlayer;
    }

    public static long getListingDurationMs() {
        return values.listingDurationHours * 60L * 60L * 1000L;
    }
}
