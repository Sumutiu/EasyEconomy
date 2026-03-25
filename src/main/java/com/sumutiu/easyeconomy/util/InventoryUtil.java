package com.sumutiu.easyeconomy.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class InventoryUtil {

    public static boolean noInventorySpace(ServerPlayer player, ItemStack stackToInsert) {
        int totalSpace = 0;

        for (int i = 0; i < 36; ++i) {
            ItemStack slotStack = player.getInventory().getItem(i);

            if (slotStack.isEmpty()) {
                totalSpace += stackToInsert.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slotStack, stackToInsert)) {
                totalSpace += slotStack.getMaxStackSize() - slotStack.getCount();
            }

            if (totalSpace >= stackToInsert.getCount()) {
                return false;
            }
        }

        return true;
    }
}