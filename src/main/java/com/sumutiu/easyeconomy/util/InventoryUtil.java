package com.sumutiu.easyeconomy.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

public class InventoryUtil {

    // Main inventory + hotbar. Armor and offhand slots are not used by Inventory.add()
    public static final int MAIN_INVENTORY_SIZE = 36;

    // How many items like the given stack still fit in the player's main inventory
    public static int getFreeSpaceFor(ServerPlayer player, ItemStack stackToInsert) {
        int totalSpace = 0;

        for (int i = 0; i < MAIN_INVENTORY_SIZE; ++i) {
            ItemStack slotStack = player.getInventory().getItem(i);

            if (slotStack.isEmpty()) {
                totalSpace += stackToInsert.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slotStack, stackToInsert)) {
                totalSpace += Math.max(0, slotStack.getMaxStackSize() - slotStack.getCount());
            }
        }

        return totalSpace;
    }

    public static boolean noInventorySpace(ServerPlayer player, ItemStack stackToInsert) {
        return getFreeSpaceFor(player, stackToInsert) < stackToInsert.getCount();
    }

    // Puts the stack in the player's inventory and drops whatever doesn't fit at their feet.
    // This is the only code that differs between the Minecraft 26.2 and 26.3 builds.
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            // 26.3: SERVER_ONLY = a drop started by the server, not by the player (26.2: player.drop(stack, false))
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }
}
