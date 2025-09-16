package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.component.type.LoreComponent;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AHExpiredScreenHandler extends ScreenHandler {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    private final Inventory inventory;
    private final List<AHStorage.AHListing> expiredListings;

    public AHExpiredScreenHandler(int syncId, Inventory inventory, List<AHStorage.AHListing> expiredListings) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.expiredListings = expiredListings;

        for (int i = 0; i < SIZE; i++) {
            this.addSlot(new Slot(inventory, i, 8 + (i % COLUMNS) * 18, 18 + (i / COLUMNS) * 18) {
                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    return false;
                }

                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }
            });
        }
        drawListings();
    }

    private void drawListings() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (int i = 0; i < SIZE; i++) {
            ItemStack stack;
            if (i < expiredListings.size()) {
                AHStorage.AHListing listing = expiredListings.get(i);
                stack = AHStorageHelper.fromListing(listing);
                if (stack == null) stack = ItemStack.EMPTY;

                String sellerName = listing.sellerName != null ? listing.sellerName : "Unknown";
                String date = sdf.format(new Date(listing.timestamp));

                Text itemName = Text.literal(stack.getCount() + " x " + stack.getItem().getName(stack).getString());
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, itemName);

                List<Text> loreLines = new ArrayList<>();
                loreLines.add(Text.literal("Seller: " + sellerName));
                loreLines.add(Text.literal("Expired: " + date));

                stack.set(net.minecraft.component.DataComponentTypes.LORE, new LoreComponent(loreLines));
            } else {
                stack = ItemStack.EMPTY;
            }
            inventory.setStack(i, stack);
        }
        sendContentUpdates();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.QUICK_MOVE) {
            return;
        }

        if (!(player instanceof ServerPlayerEntity buyer)) {
            return;
        }

        if (slotIndex < 0 || slotIndex >= expiredListings.size()) return;

        AHStorage.AHListing listing = expiredListings.get(slotIndex);
        ItemStack stack = AHStorageHelper.fromListing(listing);

        if (stack == null || stack.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(buyer, "Error: Could not retrieve item from listing.");
            return;
        }

        if (!InventoryUtil.hasInventorySpace(buyer, stack)) {
            EasyEconomyMessages.PrivateMessage(buyer, "Not enough inventory space to claim this item.");
            return;
        }

        if (!buyer.getInventory().insertStack(stack.copy())) {
            buyer.dropItem(stack.copy(), false);
        }
        buyer.playerScreenHandler.sendContentUpdates();

        expiredListings.remove(slotIndex);

        List<AHStorage.AHListing> allListings = AHStorage.loadListings(player.getUuid());
        allListings.removeIf(l -> l.timestamp == listing.timestamp && l.seller.equals(listing.seller));
        AHStorage.saveListings(player.getUuid(), allListings);

        String itemName = stack.getItem().getName().getString();
        EasyEconomyMessages.PrivateMessage(
                buyer,
                "Claimed expired listing: " + stack.getCount() + " x " + itemName
        );

        drawListings();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }
}
