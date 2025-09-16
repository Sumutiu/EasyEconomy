package com.sumutiu.easyeconomy.util;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class InventoryUtil {
    public static boolean hasInventorySpace(ServerPlayerEntity player, ItemStack stackToInsert) {
        int totalSpace = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack slotStack = player.getInventory().getStack(i);
            if (slotStack.isEmpty()) {
                totalSpace += stackToInsert.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(slotStack, stackToInsert)) {
                totalSpace += slotStack.getMaxCount() - slotStack.getCount();
            }
            if (totalSpace >= stackToInsert.getCount()) {
                return true;
            }
        }
        return false;
    }
}
