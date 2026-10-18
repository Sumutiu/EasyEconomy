package com.sumutiu.easyeconomy.storage;

import net.minecraft.core.Holder;
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

import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHStorageHelper {

    /**
     * Converts an AHListing into a proper ItemStack (a fresh copy every call).
     * Returns ItemStack.EMPTY if anything is invalid. Such listings can't be bought or taken back.
     */
    public static ItemStack fromListing(AHStorage.AHListing listing, HolderLookup.Provider registries) {
        if (listing == null) return ItemStack.EMPTY;

        // Parse once per listing; the result (even a failure) is kept in memory
        if (listing.cachedStack == null) {
            if (registries == null) return ItemStack.EMPTY;
            listing.cachedStack = parseListing(listing, registries);
        }

        return listing.cachedStack.isEmpty() ? ItemStack.EMPTY : listing.cachedStack.copy();
    }

    private static ItemStack parseListing(AHStorage.AHListing listing, HolderLookup.Provider registries) {
        try {
            // Load from NBT to preserve components/enchantments/contents
            if (listing.nbt != null && !listing.nbt.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseCompoundFully(listing.nbt);

                    RegistryOps<Tag> ops =
                            registries.createSerializationContext(NbtOps.INSTANCE);

                    ItemStack stack = ItemStack.CODEC
                            .parse(ops, tag)
                            .result()
                            .orElse(ItemStack.EMPTY);

                    if (stack.isEmpty()) {
                        Logger(2, String.format(AH_LISTING_UNREADABLE, listing.timestamp, listing.seller, "invalid item data"));
                        return ItemStack.EMPTY;
                    }

                    // Ensure correct quantity from listing
                    stack.setCount(Math.max(1, listing.quantity));
                    return stack;

                } catch (Exception e) {
                    // Never fall back to a plain item here: that would drop enchantments,
                    // names and contents, so the listing is blocked instead
                    Logger(2, String.format(AH_LISTING_UNREADABLE, listing.timestamp, listing.seller, e.getMessage()));
                    return ItemStack.EMPTY;
                }
            }

            // Old listings without NBT: build the item from its ID
            if (listing.itemId == null) return ItemStack.EMPTY;

            Identifier id = Identifier.tryParse(listing.itemId);
            if (id == null) {
                Logger(1, String.format(AH_INVALID_ID, listing.itemId));
                return ItemStack.EMPTY;
            }

            // Unknown IDs (e.g. an item from a mod that was removed) fall back to AIR
            Item item = BuiltInRegistries.ITEM.get(id).map(Holder::value).orElse(Items.AIR);

            if (item == Items.AIR) {
                Logger(1, String.format(AH_ID_NOT_FOUND, listing.itemId));
                return ItemStack.EMPTY;
            }

            int qty = Math.max(1, listing.quantity);
            return new ItemStack(item, qty);

        } catch (Exception e) {
            Logger(2, String.format(AH_ID_ITEMSTACK_ERROR, e.getMessage()));
            return ItemStack.EMPTY;
        }
    }

    /**
     * All active listings from all sellers, newest first (served from memory).
     */
    public static List<AHStorage.AHListing> getAllActiveListings() {
        return AHStorage.getAllActiveListings();
    }
}
