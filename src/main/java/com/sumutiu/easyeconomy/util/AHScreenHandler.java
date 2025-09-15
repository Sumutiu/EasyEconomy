package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AHScreenHandler extends ScreenHandler {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS; // 54 slots

    private final Inventory inventory;
    private final List<AHStorage.AHListing> listings;
    private boolean inConfirmation = false;
    private int confirmSlot = -1;

    public AHScreenHandler(int syncId, Inventory inventory, List<AHStorage.AHListing> listings) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.listings = listings;

        // Add slots once
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
            if (i < listings.size()) {
                AHStorage.AHListing listing = listings.get(i);
                stack = AHStorageHelper.fromListing(listing);
                if (stack == null) stack = ItemStack.EMPTY;

                String sellerName = listing.seller != null ? listing.seller.toString() : "Unknown";
                String date = sdf.format(new Date(listing.timestamp));

                Text customName = Text.literal(
                        stack.getCount() + " x " +
                                stack.getItem().getName(stack).getString() +
                                " | Seller: " + sellerName +
                                " | Listed: " + date +
                                " | Price: " + listing.price + " diamonds"
                );

                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, customName);
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

    /**
     * Intercept clicks on the screen handler. We only handle clicks that target AH slots (0..SIZE-1).
     * Any click on a listed slot will attempt a purchase.
     */
    private void drawConfirmationScreen() {
        for (int i = 0; i < SIZE; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        ItemStack confirmStack = new ItemStack(Items.GREEN_WOOL);
        confirmStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Confirm Purchase"));
        inventory.setStack(22, confirmStack); // Middle-ish

        ItemStack cancelStack = new ItemStack(Items.RED_WOOL);
        cancelStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel"));
        inventory.setStack(40, cancelStack); // Middle-ish

        sendContentUpdates();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.QUICK_MOVE) {
            return;
        }

        if (!(player instanceof ServerPlayerEntity buyer)) {
            return;
        }

        if (inConfirmation) {
            if (slotIndex == 22) { // Confirm
                inConfirmation = false;
                AHStorage.AHListing listing = listings.get(confirmSlot);

                ItemStack purchased = AHStorageHelper.fromListing(listing);
                if (purchased == null || purchased.isEmpty()) {
                    EasyEconomyMessages.PrivateMessage(buyer, "Error: Could not retrieve item from listing.");
                    drawListings();
                    return;
                }

                long balance = BankStorage.getBalance(buyer.getUuid());
                if (balance < listing.price) {
                    EasyEconomyMessages.PrivateMessage(buyer, "Not enough diamonds to buy this item.");
                    drawListings();
                    return;
                }

                if (!BankStorage.removeBalance(buyer.getUuid(), listing.price)) {
                    EasyEconomyMessages.PrivateMessage(buyer, "Failed to withdraw balance. Try again.");
                    drawListings();
                    return;
                }

                BankStorage.addBalance(listing.seller, listing.price);

                if (!buyer.getInventory().insertStack(purchased.copy())) {
                    buyer.dropItem(purchased.copy(), false);
                }
                buyer.playerScreenHandler.sendContentUpdates();

                List<AHStorage.AHListing> sellerListings = AHStorage.loadListings(listing.seller);
                sellerListings.removeIf(l -> l.timestamp == listing.timestamp && l.seller.equals(listing.seller));
                AHStorage.saveListings(listing.seller, sellerListings);
                listings.remove(confirmSlot);

                EasyEconomyMessages.PrivateMessage(
                        buyer,
                        "Bought " + purchased.getCount() + " x " + purchased.getItem().getName(purchased).getString()
                                + " for " + listing.price + " diamonds from " + listing.seller
                );

                drawListings();
            } else if (slotIndex == 40) { // Cancel
                inConfirmation = false;
                drawListings();
            }
        } else {
            if (slotIndex >= 0 && slotIndex < listings.size()) {
                inConfirmation = true;
                confirmSlot = slotIndex;
                drawConfirmationScreen();
            }
        }
    }

    /**
     * Prevent shift-quick-move into player inventory.
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }
}
