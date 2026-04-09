package com.sumutiu.easyeconomy.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHStorageHelper {

    /**
     * Converts an AHListing into a proper ItemStack.
     * Returns ItemStack.EMPTY if anything is invalid.
     */
    public static ItemStack fromListing(AHStorage.AHListing listing, HolderLookup.Provider registries) {
        try {
            if (listing == null) return ItemStack.EMPTY;

            ItemStack stack = ItemStack.EMPTY;

            // Try loading from NBT first to preserve components/enchantments
            if (listing.nbt != null && !listing.nbt.isEmpty() && registries != null) {
                try {
                    CompoundTag tag = TagParser.parseCompoundFully(listing.nbt);

                    RegistryOps<Tag> ops =
                            registries.createSerializationContext(NbtOps.INSTANCE);

                    stack = ItemStack.CODEC
                            .parse(ops, tag)
                            .result()
                            .orElse(ItemStack.EMPTY);

                    // Ensure correct quantity from listing
                    if (!stack.isEmpty()) {
                        stack.setCount(Math.max(1, listing.quantity));
                    }

                } catch (Exception e) {
                    Logger(2, String.format(AH_ID_NBT_ERROR, e.getMessage()));
                }
            }

            // Fallback for older listings or if NBT loading failed
            if (stack.isEmpty()) {
                if (listing.itemId == null) return ItemStack.EMPTY;

                Identifier id = Identifier.tryParse(listing.itemId);
                if (id == null) {
                    Logger(1, String.format(AH_INVALID_ID, listing.itemId));
                    return ItemStack.EMPTY;
                }

                Item item = BuiltInRegistries.ITEM.get(id).orElseThrow().value();

                if (item == Items.AIR) {
                    Logger(1, String.format(AH_ID_NOT_FOUND, listing.itemId));
                    return ItemStack.EMPTY;
                }

                int qty = Math.max(1, listing.quantity);
                stack = new ItemStack(item, qty);
            }

            return stack;

        } catch (Exception e) {
            Logger(2, String.format(AH_ID_ITEMSTACK_ERROR, e.getMessage()));
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
            Logger(1, String.format(AH_FOLDER_NOT_FOUND, folder.getPath()));
            return all;
        }

        File[] files = folder.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".json"));

        if (files == null) {
            Logger(2, String.format(AH_FILE_ERROR, folder.getPath()));
            return all;
        }

        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                Logger(1, String.format(AH_FILE_NAME_ERROR, name));
                continue;
            }

            String base = name.substring(0, dot);

            UUID uuid;
            try {
                uuid = UUID.fromString(base);
            } catch (IllegalArgumentException ex) {
                Logger(1, String.format(AH_FILE_NAME_NO_UUID, name));
                continue;
            }

            List<AHStorage.AHListing> playerAll = AHStorage.loadListings(uuid);
            List<AHStorage.AHListing> active = AHStorage.getActiveListings(playerAll);

            if (!active.isEmpty()) {
                all.addAll(active);
                Logger(0, String.format(AH_LISTING_INFO, active.size(), name));
            } else {
                Logger(0, String.format(AH_LISTING_EMPTY, name));
            }
        }

        Logger(0, String.format(AH_LISTING_ALL, all.size()));
        return all;
    }
}