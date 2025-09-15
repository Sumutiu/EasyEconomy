package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public class AHScreenFactory {

    public static void open(ServerPlayerEntity player) {
        // Gather all active listings
        List<AHStorage.AHListing> allActive = AHStorageHelper.getAllActiveListings();

        // If nothing found, inform player and return (no empty GUI)
        if (allActive.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(player, "There are currently no active AH listings.");
            return;
        }

        // Always create a 9x6 inventory (54 slots) for GENERIC_9X6
        Inventory inventory = new SimpleInventory(54);

        // Place all active listings into the inventory
        int placed = 0;
        for (int i = 0; i < allActive.size() && placed < 54; i++) {
            AHStorage.AHListing listing = allActive.get(i);
            ItemStack stack = AHStorageHelper.fromListing(listing);

            if (stack.isEmpty()) {
                EasyEconomyMessages.Logger(1, "AH: skipping empty ItemStack for listing " + listing.itemId + " seller=" + listing.seller);
                continue;
            }

            inventory.setStack(placed, stack);
            placed++;
        }

        // Fill remaining slots with EMPTY to prevent IndexOutOfBounds
        for (int i = placed; i < 54; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        EasyEconomyMessages.Logger(0, "AH: opening GUI for player " + player.getUuid() + " with " + placed + " items.");

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Auction House");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity playerEntity) {
                // Pass syncId here to fix the constructor error
                return new AHScreenHandler(syncId, inventory, allActive);
            }
        };

        player.openHandledScreen(factory);
    }
}
