package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Shows the player's own listings (/shop cancel = still for sale, /shop expired = expired).
 * Clicking a listing takes the item back.
 */
public class AHOwnListingsScreenHandler extends AbstractContainerMenu {

    public enum Mode {
        ACTIVE,
        EXPIRED
    }

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;
    public static final int ITEMS_PER_PAGE = 45;

    private final Container inventory;
    private final List<AHStorage.AHListing> ownListings;
    private final Player player;
    private final Mode mode;
    private int currentPage = 0;

    public AHOwnListingsScreenHandler(int syncId, Container inventory, List<AHStorage.AHListing> ownListings, Player player, Mode mode) {
        super(MenuType.GENERIC_9x6, syncId);
        this.inventory = inventory;
        this.ownListings = ownListings;
        this.player = player;
        this.mode = mode;

        // Listing slots with click handling
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
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // ---- Pagination ----
        if (slotIndex == 45 && currentPage > 0) {
            currentPage--;
            drawListings();
            return;
        }
        if (slotIndex == 53 && (currentPage + 1) * ITEMS_PER_PAGE < ownListings.size()) {
            currentPage++;
            drawListings();
            return;
        }

        // ---- Take the item back ----
        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < ownListings.size()) {
            AHStorage.AHListing listing = ownListings.get(listingIndex);
            ItemStack stack = AHStorageHelper.fromListing(listing, serverPlayer.registryAccess());

            if (stack == null || stack.isEmpty()) {
                PrivateMessage(serverPlayer, SHOP_ITEM_UNREADABLE);
                drawListings();
                return;
            }

            if (InventoryUtil.noInventorySpace(serverPlayer, stack)) {
                PrivateMessage(serverPlayer, SHOP_CLAIM_NO_SPACE);
                drawListings();
                return;
            }

            ownListings.remove(listingIndex);

            // Remove the listing before handing out the item.
            // If it is already gone (e.g. someone bought it a moment ago), stop here.
            if (!AHStorage.removeListing(listing.seller, listing.timestamp)) {
                PrivateMessage(serverPlayer, SHOP_CLAIM_NOT_AVAILABLE);
                drawListings();
                return;
            }

            InventoryUtil.giveOrDrop(serverPlayer, stack.copy());
            this.broadcastChanges();

            String message = mode == Mode.EXPIRED ? SHOP_CLAIM_EXPIRED : SHOP_TAKEN_BACK;
            PrivateMessage(serverPlayer, String.format(message, stack.getCount(), stack.getHoverName().getString()));

            drawListings();
        }
    }

    private void drawListings() {
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);

        int maxPage = ownListings.isEmpty() ? 0 : (ownListings.size() - 1) / ITEMS_PER_PAGE;

        // Stay on a valid page after the last listing of the last page was taken back
        if (currentPage > maxPage) currentPage = maxPage;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int startIndex = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int listingIndex = startIndex + i;

            if (listingIndex < ownListings.size()) {
                AHStorage.AHListing listing = ownListings.get(listingIndex);
                ItemStack stack = AHStorageHelper.fromListing(listing, player.registryAccess());

                List<Component> loreLines = new ArrayList<>();
                boolean readable = stack != null && !stack.isEmpty();

                // Never put a name/lore on the shared ItemStack.EMPTY; show a placeholder instead
                if (!readable) {
                    stack = new ItemStack(Items.BARRIER);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Unavailable item"));
                    loreLines.add(Component.literal("This item's data could not be loaded"));
                } else {
                    stack.set(DataComponents.CUSTOM_NAME,
                            Component.literal(stack.getCount() + " x " + stack.getHoverName().getString()));
                }

                loreLines.add(Component.literal("Price: " + listing.price + " diamonds"));
                loreLines.add(Component.literal("Listed: " + sdf.format(new Date(listing.timestamp))));
                if (mode == Mode.EXPIRED) {
                    loreLines.add(Component.literal("Expired: " + sdf.format(new Date(listing.getExpiresAt()))));
                } else {
                    loreLines.add(Component.literal("Expires: " + sdf.format(new Date(listing.getExpiresAt()))));
                }
                loreLines.add(Component.literal(readable ? "Click to take back" : "Can't be taken back - please tell an admin"));
                stack.set(DataComponents.LORE, new ItemLore(loreLines));

                inventory.setItem(i, stack);
            }
        }

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
