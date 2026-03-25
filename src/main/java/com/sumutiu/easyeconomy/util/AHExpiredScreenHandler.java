package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHExpiredScreenHandler extends AbstractContainerMenu {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;
    public static final int ITEMS_PER_PAGE = 45;

    private final Container inventory;
    private final List<AHStorage.AHListing> expiredListings;
    private int currentPage = 0;

    public AHExpiredScreenHandler(int syncId, Container inventory, List<AHStorage.AHListing> expiredListings, Player player) {
        super(MenuType.GENERIC_9x6, syncId);
        this.inventory = inventory;
        this.expiredListings = expiredListings;

        // Auction house slots with click handling
        for (int i = 0; i < SIZE; i++) {
            this.addSlot(new ClickableSlot(inventory, i, 8 + (i % COLUMNS) * 18, 18 + (i / COLUMNS) * 18) {
                @Override
                protected void onClick(Player player) {
                    handleSlotClick(player, this.index);
                }
            });
        }

        // Player inventory
        int playerInvY = 140;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(player.getInventory(), col + row * 9 + 9,
                        8 + col * 18, playerInvY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(player.getInventory(), col,
                    8 + col * 18, playerInvY + 58));
        }

        drawListings();
    }

    private void handleSlotClick(Player player, int slotIndex) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        // ---- Pagination ----
        if (slotIndex == 45 && currentPage > 0) {
            currentPage--;
            drawListings();
            return;
        }
        if (slotIndex == 53 && (currentPage + 1) * ITEMS_PER_PAGE < expiredListings.size()) {
            currentPage++;
            drawListings();
            return;
        }

        // ---- Claim expired item ----
        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < expiredListings.size()) {
            AHStorage.AHListing listing = expiredListings.get(listingIndex);
            ItemStack stack = AHStorageHelper.fromListing(listing);

            if (stack == null || stack.isEmpty()) {
                PrivateMessage(serverPlayer, AH_BUY_ERROR);
                drawListings();
                return;
            }

            if (InventoryUtil.noInventorySpace(serverPlayer, stack)) {
                PrivateMessage(serverPlayer, AH_CLAIM_NO_SPACE);
                drawListings();
                return;
            }

            ItemStack stackToInsert = stack.copy();
            if (!serverPlayer.getInventory().add(stackToInsert)) {
                serverPlayer.drop(stackToInsert, false);
            }
            this.broadcastChanges();

            expiredListings.remove(listingIndex);

            List<AHStorage.AHListing> allListings = AHStorage.loadListings(serverPlayer.getUUID());
            allListings.removeIf(l -> l.timestamp == listing.timestamp && l.seller.equals(listing.seller));
            AHStorage.saveListings(serverPlayer.getUUID(), allListings);

            PrivateMessage(serverPlayer, String.format(AH_CLAIM_EXPIRED, stack.getCount(), stack.getHoverName().getString()));

            drawListings();
        }
    }

    private void drawListings() {
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int startIndex = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int listingIndex = startIndex + i;

            if (listingIndex < expiredListings.size()) {
                AHStorage.AHListing listing = expiredListings.get(listingIndex);
                ItemStack stack = AHStorageHelper.fromListing(listing);
                if (stack == null) stack = ItemStack.EMPTY;

                String sellerName = listing.sellerName != null ? listing.sellerName : "Unknown";
                String date = sdf.format(new Date(listing.timestamp));

                stack.set(DataComponents.CUSTOM_NAME,
                        Component.literal(stack.getCount() + " x " + stack.getHoverName().getString()));

                List<Component> loreLines = new ArrayList<>();
                loreLines.add(Component.literal("Seller: " + sellerName));
                loreLines.add(Component.literal("Expired: " + date));
                stack.set(DataComponents.LORE, new ItemLore(loreLines));

                inventory.setItem(i, stack);
            }
        }

        int maxPage = (expiredListings.size() - 1) / ITEMS_PER_PAGE;

        if (currentPage > 0) {
            ItemStack prevStack = new ItemStack(Items.ARROW);
            prevStack.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page"));
            inventory.setItem(45, prevStack);
        }
        if (currentPage < maxPage) {
            ItemStack nextStack = new ItemStack(Items.ARROW);
            nextStack.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page"));
            inventory.setItem(53, nextStack);
        }

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        pageInfo.set(DataComponents.CUSTOM_NAME,
                Component.literal("Page " + (currentPage + 1) + " of " + (maxPage + 1)));
        inventory.setItem(49, pageInfo);

        broadcastChanges();
    }

    @Override
    public boolean stillValid(@NonNull Player player) { return true; }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) { return ItemStack.EMPTY; }

    // ---------------- CUSTOM CLICKABLE SLOT ----------------
    private abstract static class ClickableSlot extends Slot {
        protected final int index;

        public ClickableSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.index = index;
        }

        protected abstract void onClick(Player player);

        @Override
        public boolean mayPickup(@NonNull Player player) {
            onClick(player);
            return false;
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) { return false; }
    }
}