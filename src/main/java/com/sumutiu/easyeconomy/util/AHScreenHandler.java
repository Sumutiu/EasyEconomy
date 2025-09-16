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
import net.minecraft.component.type.LoreComponent;
import java.util.ArrayList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AHScreenHandler extends ScreenHandler {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS; // 54 slots
    public static final int ITEMS_PER_PAGE = 45;

    private final Inventory inventory;
    private final List<AHStorage.AHListing> listings;
    private boolean inConfirmation = false;
    private int confirmSlot = -1;
    private int currentPage = 0;

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
        // Clear entire inventory
        for (int i = 0; i < SIZE; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        // Draw item listings for the current page
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int listingIndex = startIndex + i;
            if (listingIndex < listings.size()) {
                AHStorage.AHListing listing = listings.get(listingIndex);
                ItemStack stack = AHStorageHelper.fromListing(listing);
                if (stack == null) stack = ItemStack.EMPTY;

                String sellerName = listing.sellerName != null ? listing.sellerName : "Unknown";
                String date = sdf.format(new Date(listing.timestamp));

                Text itemName = Text.literal(stack.getCount() + " x " + stack.getItem().getName(stack).getString());
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, itemName);

                List<Text> loreLines = new ArrayList<>();
                loreLines.add(Text.literal("Seller: " + sellerName));
                loreLines.add(Text.literal("Listed: " + date));
                loreLines.add(Text.literal("Price: " + listing.price + " diamonds"));

                stack.set(net.minecraft.component.DataComponentTypes.LORE, new LoreComponent(loreLines));
                inventory.setStack(i, stack);
            }
        }

        // Draw navigation controls
        int maxPage = (listings.size() - 1) / ITEMS_PER_PAGE;

        if (currentPage > 0) {
            ItemStack prevStack = new ItemStack(Items.ARROW);
            prevStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Previous Page"));
            inventory.setStack(45, prevStack);
        }

        if (currentPage < maxPage) {
            ItemStack nextStack = new ItemStack(Items.ARROW);
            nextStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Next Page"));
            inventory.setStack(53, nextStack);
        }

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        pageInfo.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (currentPage + 1) + " of " + (maxPage + 1)));
        inventory.setStack(49, pageInfo);

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
        // Black out the background
        ItemStack blackPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        blackPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < SIZE; i++) {
            inventory.setStack(i, blackPane);
        }

        // Green "Confirm" 3x3 grid on the left
        ItemStack greenPane = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
        greenPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Confirm Purchase"));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inventory.setStack(9 + i * 9 + j, greenPane);
            }
        }

        // Red "Cancel" 3x3 grid on the right
        ItemStack redPane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        redPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Cancel Purchase"));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inventory.setStack(15 + i * 9 + j, redPane);
            }
        }

        // Middle 3x3 grid with item in the center
        ItemStack grayPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayPane.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inventory.setStack(12 + i * 9 + j, grayPane);
            }
        }

        // Place the actual item in the center
        AHStorage.AHListing listing = listings.get(confirmSlot);
        ItemStack itemToPurchase = AHStorageHelper.fromListing(listing);
        inventory.setStack(22, itemToPurchase); // Center slot of the middle 3x3 grid

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
            boolean isConfirm = false;
            boolean isCancel = false;

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (slotIndex == 9 + i * 9 + j) {
                        isConfirm = true;
                        break;
                    }
                    if (slotIndex == 15 + i * 9 + j) {
                        isCancel = true;
                        break;
                    }
                }
                if (isConfirm || isCancel) break;
            }

            if (isConfirm) {
                inConfirmation = false;
                AHStorage.AHListing listing = listings.get(confirmSlot);

                ItemStack purchased = AHStorageHelper.fromListing(listing);
                if (purchased == null || purchased.isEmpty()) {
                    EasyEconomyMessages.PrivateMessage(buyer, "Error: Could not retrieve item from listing.");
                    drawListings();
                    return;
                }

                if (!InventoryUtil.hasInventorySpace(buyer, purchased)) {
                    EasyEconomyMessages.PrivateMessage(buyer, "Not enough inventory space to purchase this item.");
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
                                + " for " + listing.price + " diamonds from " + listing.sellerName
                );

                drawListings();
            } else if (isCancel) {
                inConfirmation = false;
                drawListings();
            }
        } else {
            // Handle navigation clicks
            if (slotIndex == 45 && currentPage > 0) { // Previous Page
                currentPage--;
                drawListings();
                return;
            }
            if (slotIndex == 53 && (currentPage + 1) * ITEMS_PER_PAGE < listings.size()) { // Next Page
                currentPage++;
                drawListings();
                return;
            }

            // Handle item clicks
            int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
            if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < listings.size()) {
                inConfirmation = true;
                confirmSlot = listingIndex;
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
