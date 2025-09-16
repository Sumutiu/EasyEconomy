package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHStorage {
    private static final File AH_FOLDER = new File("mods/EasyEconomy/AH");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class AHListing {
        public String itemId;
        public int quantity;
        public long price; // in diamonds
        public long timestamp; // epoch millis
        public UUID seller;
        public String sellerName;

        public AHListing(String itemId, int quantity, long price, UUID seller, String sellerName) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = System.currentTimeMillis();
            this.seller = seller;
            this.sellerName = sellerName;
        }
    }

    private static void ensureFolder() {
        if (!AH_FOLDER.exists()) {
            if (AH_FOLDER.mkdirs()) {
                Logger(0, AH_FOLDER_SUCCESS);
            } else {
                Logger(2, AH_FOLDER_FAIL);
            }
        }
    }

    public static File getFile(UUID uuid) {
        ensureFolder();
        return new File(AH_FOLDER, uuid.toString() + ".json");
    }

    public static List<AHListing> loadListings(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) {
            Logger(0, String.format(AH_NO_FILE, uuid));
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            AHListing[] arr = GSON.fromJson(reader, AHListing[].class);
            return arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
        } catch (IOException e) {
            Logger(2, String.format(AH_FILE_LOAD_ERROR, uuid, e.getMessage()));
            return new ArrayList<>();
        }
    }

    public static void saveListings(UUID uuid, List<AHListing> listings) {
        try (Writer writer = new FileWriter(getFile(uuid))) {
            GSON.toJson(listings, writer);
        } catch (IOException e) {
            Logger(2, String.format(AH_FILE_SAVE_ERROR, uuid, e.getMessage()));
        }
    }

    public static List<AHListing> getActiveListings(List<AHListing> all) {
        long now = System.currentTimeMillis();
        return all.stream()
                .filter(l -> now - l.timestamp < 24*60*60*1000L)
                .collect(Collectors.toList());
    }

    public static List<AHListing> getExpiredListings(List<AHListing> all) {
        long now = System.currentTimeMillis();
        return all.stream()
                .filter(l -> now - l.timestamp >= 24*60*60*1000L)
                .collect(Collectors.toList());
    }
}
