package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class AHExpiredScreenHandler extends ScreenHandler {

    private final List<AHStorage.AHListing> expiredListings;

    public AHExpiredScreenHandler(List<AHStorage.AHListing> expiredListings) {
        super(ScreenHandlerType.GENERIC_9X6, 0);
        this.expiredListings = expiredListings;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /**
     * Call this when a player clicks a slot in the expired items GUI
     */
    public void onClick(ServerPlayerEntity player, int slot) {
        if (slot < 0 || slot >= expiredListings.size()) return;

        AHStorage.AHListing listing = expiredListings.get(slot);
        ItemStack stack = AHStorageHelper.fromListing(listing);

        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }

        // Remove listing from memory
        expiredListings.remove(listing);

        // Remove listing from player's file
        List<AHStorage.AHListing> allListings = AHStorage.loadListings(player.getUuid());
        allListings.remove(listing);
        AHStorage.saveListings(player.getUuid(), allListings);

        // Null-safe item name retrieval
        String itemName = stack.getItem().getName().getString();
        EasyEconomyMessages.PrivateMessage(
                player,
                "Claimed expired listing: " + stack.getCount() + " x " + itemName
        );
    }

    /**
     * Required override for shift-clicking items (quick move)
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }
}
