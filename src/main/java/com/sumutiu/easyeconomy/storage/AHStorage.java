package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.sumutiu.easyeconomy.util.EasyEconomyMessages;

public class AHStorage {
    private static final File AH_FOLDER = new File("mods/EasyEconomy/AH");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class AHListing {
        public String itemId;
        public int quantity;
        public long price; // in diamonds
        public long timestamp; // epoch millis
        public UUID seller;

        public AHListing() {}
        public AHListing(String itemId, int quantity, long price, UUID seller) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = System.currentTimeMillis();
            this.seller = seller;
        }
    }

    private static void ensureFolder() {
        if (!AH_FOLDER.exists()) AH_FOLDER.mkdirs();
    }

    public static File getFile(UUID uuid) {
        ensureFolder();
        return new File(AH_FOLDER, uuid.toString() + ".json");
    }

    public static List<AHListing> loadListings(UUID uuid) {
        File file = getFile(uuid);
        if (!file.exists()) {
            EasyEconomyMessages.Logger(0, "AH: no file for player " + uuid);
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            AHListing[] arr = GSON.fromJson(reader, AHListing[].class);
            List<AHListing> list = arr != null ? new ArrayList<>(Arrays.asList(arr)) : new ArrayList<>();
            EasyEconomyMessages.Logger(0, "AH: loaded " + list.size() + " listings from " + file.getName());
            return list;
        } catch (IOException e) {
            EasyEconomyMessages.Logger(2, "Failed to load AH for " + uuid + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void saveListings(UUID uuid, List<AHListing> listings) {
        try (Writer writer = new FileWriter(getFile(uuid))) {
            GSON.toJson(listings, writer);
        } catch (IOException e) {
            EasyEconomyMessages.Logger(2, "Failed to save AH for " + uuid + ": " + e.getMessage());
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
