package com.sumutiu.easyeconomy.storage;

import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AHStorageHelper {

    /**
     * Converts an AHListing into a proper ItemStack.
     * Returns ItemStack.EMPTY if anything is invalid.
     */
    public static ItemStack fromListing(AHStorage.AHListing listing) {
        try {
            if (listing == null || listing.itemId == null) return ItemStack.EMPTY;

            Identifier id = Identifier.tryParse(listing.itemId);
            if (id == null) {
                EasyEconomyMessages.Logger(1, "AH: invalid item id in listing: " + listing.itemId);
                return ItemStack.EMPTY;
            }

            Item item = Registries.ITEM.get(id);
            if (item == net.minecraft.item.Items.AIR) {
                EasyEconomyMessages.Logger(1, "AH: item not found for id: " + listing.itemId);
                return ItemStack.EMPTY;
            }

            int qty = Math.max(1, listing.quantity);
            return new ItemStack(item, qty);
        } catch (Exception e) {
            EasyEconomyMessages.Logger(2, "AH: error converting listing to ItemStack: " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * Gathers all active listings from all player AH files.
     */
    public static List<AHStorage.AHListing> getAllActiveListings() {
        List<AHStorage.AHListing> all = new ArrayList<>();
        File folder = new File("mods/EasyEconomy/AH");
        if (!folder.exists()) {
            EasyEconomyMessages.Logger(0, "AH: folder not found: " + folder.getPath());
            return all;
        }

        File[] files = folder.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".json"));
        if (files == null) {
            EasyEconomyMessages.Logger(1, "AH: could not list files in folder " + folder.getPath());
            return all;
        }

        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                EasyEconomyMessages.Logger(1, "AH: skipping file with unexpected name: " + name);
                continue;
            }
            String base = name.substring(0, dot);

            UUID uuid;
            try {
                uuid = UUID.fromString(base);
            } catch (IllegalArgumentException ex) {
                EasyEconomyMessages.Logger(1, "AH: skipping non-UUID file: " + name);
                continue;
            }

            // load player's listings and keep only active
            List<AHStorage.AHListing> playerAll = AHStorage.loadListings(uuid);
            List<AHStorage.AHListing> active = AHStorage.getActiveListings(playerAll);
            if (!active.isEmpty()) {
                all.addAll(active);
                EasyEconomyMessages.Logger(0, "AH: found " + active.size() + " active listings in " + name);
            } else {
                EasyEconomyMessages.Logger(0, "AH: no active listings in " + name);
            }
        }

        EasyEconomyMessages.Logger(0, "AH: total active listings found: " + all.size());
        return all;
    }
}
