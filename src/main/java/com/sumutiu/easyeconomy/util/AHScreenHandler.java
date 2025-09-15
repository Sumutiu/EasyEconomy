package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
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

    public AHScreenHandler(int syncId, Inventory inventory, List<AHStorage.AHListing> listings) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.listings = listings;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        // ensure inventory has SIZE slots filled (SimpleInventory passed in factory should be size 54)
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

                // set custom name via DataComponentTypes
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, customName);
            } else {
                stack = ItemStack.EMPTY;
            }

            // store stack in the provided inventory so client receives it
            inventory.setStack(i, stack);

            // add a read-only slot for the AH inventory
            final int slotIndex = i;
            this.addSlot(new Slot(inventory, i, 8 + (i % COLUMNS) * 18, 18 + (i / COLUMNS) * 18) {
                @Override
                public boolean canTakeItems(PlayerEntity playerEntity) {
                    // normal drag/take is disabled; purchases handled in onSlotClick
                    return false;
                }

                @Override
                public boolean canInsert(ItemStack stack) {
                    return false;
                }
            });
        }

        // note: do not add player inventory slots here; we want the GUI to show only AH items
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /**
     * Intercept clicks on the screen handler. We only handle clicks that target AH slots (0..SIZE-1).
     * Any click on a listed slot will attempt a purchase.
     */
    @Override
    public ItemStack onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // ensure server-side player
        if (!(player instanceof ServerPlayerEntity buyer)) {
            return ItemStack.EMPTY;
        }

        // Only handle clicks in our AH region
        if (slotIndex < 0 || slotIndex >= SIZE) {
            return ItemStack.EMPTY;
        }

        // If slot doesn't correspond to an active listing, ignore (let default behavior do nothing)
        if (slotIndex >= listings.size()) {
            return ItemStack.EMPTY;
        }

        AHStorage.AHListing listing = listings.get(slotIndex);

        // Check buyer balance
        long balance = BankStorage.getBalance(buyer.getUuid());
        if (balance < listing.price) {
            EasyEconomyMessages.PrivateMessage(buyer, "Not enough diamonds to buy this item.");
            return ItemStack.EMPTY;
        }

        // Attempt to withdraw from buyer
        boolean withdrawn = BankStorage.removeBalance(buyer.getUuid(), listing.price);
        if (!withdrawn) {
            EasyEconomyMessages.PrivateMessage(buyer, "Failed to withdraw balance. Try again.");
            return ItemStack.EMPTY;
        }

        // Deposit to seller
        BankStorage.addBalance(listing.seller, listing.price);

        // Give item to buyer (or drop if inventory full)
        ItemStack purchased = AHStorageHelper.fromListing(listing);
        if (purchased == null) purchased = ItemStack.EMPTY;
        if (!purchased.isEmpty()) {
            if (!buyer.getInventory().insertStack(purchased)) {
                buyer.dropItem(purchased, false);
            }
        }

        // Remove listing from seller file and in-memory list
        List<AHStorage.AHListing> sellerListings = AHStorage.loadListings(listing.seller);
        sellerListings.remove(listing);
        AHStorage.saveListings(listing.seller, sellerListings);

        // remove listing from in-memory list so GUI no longer shows it
        listings.remove(slotIndex);

        // remove stack from inventory and update viewers
        inventory.setStack(slotIndex, ItemStack.EMPTY);
        this.sendContentUpdates(); // push update to client(s)

        EasyEconomyMessages.PrivateMessage(
                buyer,
                "Bought " + purchased.getCount() + " x " + purchased.getItem().getName(purchased).getString()
                        + " for " + listing.price + " diamonds from " + listing.seller
        );

        // Return the purchased stack (server-side reference) — prevents vanilla moving behavior
        return ItemStack.EMPTY;
    }

    /**
     * Prevent shift-quick-move into player inventory.
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }
}
