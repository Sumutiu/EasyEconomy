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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

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

    public AHScreenHandler(int syncId, Inventory inventory, List<AHStorage.AHListing> listings, PlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.listings = listings;

        // Auction House slots
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

        // --- Add player inventory slots (so quickMove is captured) ---
        int playerInvY = 140; // adjust so it’s below your AH GUI

        // Main player inventory (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(player.getInventory(), col + row * 9 + 9,
                        8 + col * 18, playerInvY + row * 18));
            }
        }

        // Hotbar (1 row of 9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(player.getInventory(), col,
                    8 + col * 18, playerInvY + 58));
        }

        drawListings();
    }

    private void drawListings() {
        // Clear entire inventory
        for (int i = 0; i < SIZE; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        // Draw item listings
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

        // Navigation buttons
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
        AHStorage.AHListing listing = listings.get(this.confirmSlot);
        ItemStack itemToPurchase = AHStorageHelper.fromListing(listing);
        inventory.setStack(22, itemToPurchase);

        sendContentUpdates();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
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
                AHStorage.AHListing listing = listings.get(this.confirmSlot);

                ItemStack purchased = AHStorageHelper.fromListing(listing);
                if (purchased == null || purchased.isEmpty()) {
                    PrivateMessage(buyer, AH_BUY_ERROR);
                    drawListings();
                    this.confirmSlot = -1;
                    return;
                }

                if (InventoryUtil.noInventorySpace(buyer, purchased)) {
                    PrivateMessage(buyer, AH_BUY_NO_SPACE);
                    drawListings();
                    this.confirmSlot = -1;
                    return;
                }

                long balance = BankStorage.getBalance(buyer.getUuid());
                if (balance < listing.price) {
                    PrivateMessage(buyer, AH_BUY_NO_MONEY);
                    drawListings();
                    this.confirmSlot = -1;
                    return;
                }

                if (!BankStorage.removeBalance(buyer.getUuid(), listing.price)) {
                    PrivateMessage(buyer, AH_WITHDRAW_ERROR);
                    drawListings();
                    this.confirmSlot = -1;
                    return;
                }

                BankStorage.addBalance(listing.seller, listing.price);

                ItemStack purchasedCopy = purchased.copy();
                if (!buyer.getInventory().insertStack(purchasedCopy)) {
                    buyer.dropItem(purchasedCopy, false);
                }
                buyer.playerScreenHandler.sendContentUpdates();

                List<AHStorage.AHListing> sellerListings = AHStorage.loadListings(listing.seller);
                sellerListings.removeIf(l -> l.timestamp == listing.timestamp && l.seller.equals(listing.seller));
                AHStorage.saveListings(listing.seller, sellerListings);
                listings.remove(this.confirmSlot);

                EasyEconomyMessages.PrivateMessage( buyer, String.format(AH_BUY_CONFIRMATION, purchased.getCount(), purchased.getItem().getName(purchased).getString(), listing.price, listing.sellerName));

                drawListings();
                this.confirmSlot = -1;

            } else if (isCancel) {
                inConfirmation = false;
                drawListings();
                this.confirmSlot = -1;
            }
            return;
        }

        // Navigation handling
        if (slotIndex == 45 && currentPage > 0) {
            currentPage--;
            drawListings();
            return;
        }
        if (slotIndex == 53 && (currentPage + 1) * ITEMS_PER_PAGE < listings.size()) {
            currentPage++;
            drawListings();
            return;
        }

        // Item clicked
        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < listings.size()) {
            inConfirmation = true;
            this.confirmSlot = listingIndex;
            drawConfirmationScreen();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < SIZE) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            ItemStack newStack = originalStack.copy();
            if (!this.insertItem(newStack, 0, SIZE, false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return ItemStack.EMPTY;
    }
}
