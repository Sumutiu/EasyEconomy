package com.sumutiu.easyeconomy.util;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class InventoryUtil {
    public static boolean hasInventorySpace(ServerPlayerEntity player, ItemStack stack) {
        if (player.getInventory().getEmptySlot() != -1) {
            return true;
        }
        for (int i = 0; i < 36; ++i) { // Main inventory size
            ItemStack slotStack = player.getInventory().getStack(i);
            if (ItemStack.areItemsEqual(slotStack, stack) && slotStack.isStackable() && slotStack.getCount() < slotStack.getMaxCount()) {
                return true;
            }
        }
        return false;
    }
}
