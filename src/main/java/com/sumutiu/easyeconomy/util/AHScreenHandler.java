package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import org.jspecify.annotations.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHScreenHandler extends AbstractContainerMenu {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;
    public static final int ITEMS_PER_PAGE = 45;

    // Bottom row buttons
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_REFRESH = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 53;

    // Clicks on the confirmation screen are ignored for this long after it opens,
    // so a fast double-click on a listing can't buy it by accident
    private static final long CONFIRM_CLICK_DELAY_MS = 300;

    // Don't let Refresh be spammed (e.g. by holding a key over it)
    private static final long REFRESH_COOLDOWN_MS = 1000;

    private final Container inventory;
    private List<AHStorage.AHListing> listings;
    private final Player player;
    private final String filterItemId; // null = show every listing

    private AHStorage.AHListing selectedListing = null; // listing shown on the confirmation screen
    private int confirmationOriginSlot = -1; // slot that was clicked to open the confirmation screen
    private long confirmationOpenedAt = 0;
    private long lastRefreshAt = 0;
    private int currentPage = 0;

    public AHScreenHandler(int syncId, Container inventory, List<AHStorage.AHListing> listings, Player player, String filterItemId) {
        super(MenuType.GENERIC_9x6, syncId);
        this.inventory = inventory;
        this.listings = listings;
        this.player = player;
        this.filterItemId = filterItemId;

        // ---------------- Shop Slots ----------------
        for (int i = 0; i < SIZE; i++) {
            this.addSlot(new ClickableSlot(inventory, i, 8 + (i % COLUMNS) * 18, 18 + (i / COLUMNS) * 18) {
                @Override
                protected void onClick(Player player) {
                    handleListingClick(player, index);
                }

                @Override
                public boolean mayPickup(@NonNull Player player) {
                    onClick(player); // Trigger click server-side
                    return false; // Prevent pickup
                }

                @Override
                public boolean mayPlace(@NonNull ItemStack stack) {
                    return false; // Prevent placing items
                }
            });
        }

        // ---------------- Player Inventory ----------------
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

    // ---------------- FILTER ----------------
    private List<AHStorage.AHListing> getVisibleListings() {
        if (filterItemId == null) return listings;

        List<AHStorage.AHListing> filtered = new ArrayList<>();
        for (AHStorage.AHListing l : listings) {
            if (filterItemId.equals(l.itemId)) {
                filtered.add(l);
            }
        }
        return filtered;
    }

    private int getMaxPage(List<AHStorage.AHListing> visible) {
        return visible.isEmpty() ? 0 : (visible.size() - 1) / ITEMS_PER_PAGE;
    }

    // ---------------- CLICK HANDLER ----------------
    private void handleListingClick(Player player, int slotIndex) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // ---- Confirmation Screen ----
        if (selectedListing != null) {
            if (System.currentTimeMillis() - confirmationOpenedAt < CONFIRM_CLICK_DELAY_MS) return;

            // The slot under the cursor when the screen opened is not a button, so a
            // double-click or a held key (Q, number keys) on a listing can't buy it
            if (slotIndex == confirmationOriginSlot) return;

            if (isConfirmSlot(slotIndex)) {
                if (isOwnListing(selectedListing)) {
                    takeBackListing(serverPlayer);
                } else {
                    buyListing(serverPlayer);
                }
                return;
            }
            if (isCancelSlot(slotIndex)) {
                selectedListing = null;
                drawListings();
                return;
            }
            return;
        }

        List<AHStorage.AHListing> visible = getVisibleListings();

        // ---- Navigation ----
        if (slotIndex == SLOT_PREVIOUS && currentPage > 0) {
            currentPage--;
            drawListings();
            return;
        }

        if (slotIndex == SLOT_NEXT && currentPage < getMaxPage(visible)) {
            currentPage++;
            drawListings();
            return;
        }

        if (slotIndex == SLOT_REFRESH) {
            refreshListings();
            return;
        }

        // ---- Listing Click ----
        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < visible.size()) {
            AHStorage.AHListing clicked = visible.get(listingIndex);

            // Listings whose item data can't be read are blocked from sale
            if (AHStorageHelper.fromListing(clicked, serverPlayer.registryAccess()).isEmpty()) {
                PrivateMessage(serverPlayer, SHOP_ITEM_UNREADABLE);
                return;
            }

            selectedListing = clicked;
            confirmationOriginSlot = slotIndex;
            confirmationOpenedAt = System.currentTimeMillis();
            drawConfirmationScreen();
        }
    }

    private void refreshListings() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAt < REFRESH_COOLDOWN_MS) return;
        lastRefreshAt = now;

        this.listings = AHStorageHelper.getAllActiveListings();
        currentPage = 0;
        drawListings();
    }

    private void buyListing(ServerPlayer serverPlayer) {
        AHStorage.AHListing listing = selectedListing;
        selectedListing = null;

        synchronized (AHStorage.getBuyLock()) {

            // The menu may have been open for a while; expired listings belong to the seller again
            if (listing.isExpired(System.currentTimeMillis())) {
                PrivateMessage(serverPlayer, SHOP_BUY_EXPIRED);
                listings.remove(listing);
                drawListings();
                return;
            }

            ItemStack purchased = AHStorageHelper.fromListing(listing, serverPlayer.registryAccess());

            if (purchased == null || purchased.isEmpty()) {
                PrivateMessage(serverPlayer, SHOP_ITEM_UNREADABLE);
                listings.remove(listing);
                drawListings();
                return;
            }

            if (InventoryUtil.noInventorySpace(serverPlayer, purchased)) {
                PrivateMessage(serverPlayer, SHOP_BUY_NO_SPACE);
                drawListings();
                return;
            }

            Long balance = BankStorage.tryGetBalance(serverPlayer.getUUID());
            if (balance == null) {
                PrivateMessage(serverPlayer, BANK_READ_FAILED_PRIVATE);
                drawListings();
                return;
            }

            if (balance < listing.price) {
                PrivateMessage(serverPlayer, SHOP_BUY_NO_MONEY);
                drawListings();
                return;
            }

            // Make sure the seller can be paid before anything changes
            if (BankStorage.tryGetBalance(listing.seller) == null) {
                PrivateMessage(serverPlayer, SHOP_SELLER_BANK_UNAVAILABLE);
                drawListings();
                return;
            }

            if (!BankStorage.removeBalance(serverPlayer.getUUID(), listing.price)) {
                PrivateMessage(serverPlayer, SHOP_WITHDRAW_ERROR);
                drawListings();
                return;
            }

            // Remove the listing from the seller's file before handing out the item.
            // If it is already gone (bought by someone else or reclaimed), refund and stop.
            if (AHStorage.removeListing(listing.seller, listing.timestamp)) {
                BankStorage.addBalance(serverPlayer.getUUID(), listing.price);
                PrivateMessage(serverPlayer, SHOP_BUY_NOT_AVAILABLE);
                listings.remove(listing);
                drawListings();
                return;
            }

            if (!BankStorage.addBalance(listing.seller, listing.price)) {
                // Should not happen (checked above), but never lose the payout silently
                Logger(2, String.format(SHOP_PAYOUT_FAILED, listing.price, listing.seller));
            }

            InventoryUtil.giveOrDrop(serverPlayer, purchased.copy());

            listings.remove(listing);

            PrivateMessage(serverPlayer, String.format(SHOP_BUY_CONFIRMATION,
                    purchased.getCount(),
                    purchased.getHoverName().getString(),
                    listing.price,
                    listing.sellerName));

            drawListings();
        }
    }

    // The player's own listing: take the item back instead of buying it
    private void takeBackListing(ServerPlayer serverPlayer) {
        AHStorage.AHListing listing = selectedListing;
        selectedListing = null;

        synchronized (AHStorage.getBuyLock()) {

            ItemStack item = AHStorageHelper.fromListing(listing, serverPlayer.registryAccess());

            if (item.isEmpty()) {
                PrivateMessage(serverPlayer, SHOP_ITEM_UNREADABLE);
                drawListings();
                return;
            }

            if (InventoryUtil.noInventorySpace(serverPlayer, item)) {
                PrivateMessage(serverPlayer, SHOP_CLAIM_NO_SPACE);
                drawListings();
                return;
            }

            listings.remove(listing);

            // Remove the listing before handing out the item; stop if someone just bought it
            if (AHStorage.removeListing(listing.seller, listing.timestamp)) {
                PrivateMessage(serverPlayer, SHOP_CLAIM_NOT_AVAILABLE);
                drawListings();
                return;
            }

            InventoryUtil.giveOrDrop(serverPlayer, item.copy());

            PrivateMessage(serverPlayer, String.format(SHOP_TAKEN_BACK, item.getCount(), item.getHoverName().getString()));

            drawListings();
        }
    }

    private boolean isOwnListing(AHStorage.AHListing listing) {
        return listing != null && player.getUUID().equals(listing.seller);
    }

    private boolean isConfirmSlot(int slotIndex) {
        // 3x3 block starting at slot 9 (top-left)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (slotIndex == 9 + i * 9 + j) return true;
            }
        }
        return false;
    }

    private boolean isCancelSlot(int slotIndex) {
        // 3x3 block starting at slot 15 (top-right)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (slotIndex == 15 + i * 9 + j) return true;
            }
        }
        return false;
    }

    // ---------------- DRAW GUI ----------------
    private void drawListings() {
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, ItemStack.EMPTY);

        List<AHStorage.AHListing> visible = getVisibleListings();
        int maxPage = getMaxPage(visible);

        // Stay on a valid page after the last listing of the last page was bought
        if (currentPage > maxPage) currentPage = maxPage;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int startIndex = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int listingIndex = startIndex + i;
            if (listingIndex >= visible.size()) continue;

            AHStorage.AHListing listing = visible.get(listingIndex);
            ItemStack stack = AHStorageHelper.fromListing(listing, player.registryAccess());

            String sellerName = listing.sellerName != null ? listing.sellerName : "Unknown";
            String date = sdf.format(new Date(listing.timestamp));

            List<Component> lore = new ArrayList<>();

            // Never put a name/lore on the shared ItemStack.EMPTY; show a placeholder instead
            if (stack == null || stack.isEmpty()) {
                stack = new ItemStack(Items.BARRIER);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Unavailable item"));
                lore.add(Component.literal("This item's data could not be loaded"));
            } else {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                        stack.getCount() + " x " + stack.getHoverName().getString()
                ));
            }

            lore.add(Component.literal("Seller: " + sellerName));
            lore.add(Component.literal("Listed: " + date));
            lore.add(Component.literal("Price: " + listing.price + " diamonds"));
            if (isOwnListing(listing)) {
                lore.add(Component.literal("Your listing - click to take it back"));
            }

            stack.set(DataComponents.LORE, new ItemLore(lore));
            inventory.setItem(i, stack);
        }

        // Navigation buttons
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page"));
            inventory.setItem(SLOT_PREVIOUS, prev);
        }
        if (currentPage < maxPage) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page"));
            inventory.setItem(SLOT_NEXT, next);
        }

        ItemStack refresh = new ItemStack(Items.EMERALD);
        refresh.set(DataComponents.CUSTOM_NAME, Component.literal("Refresh"));
        inventory.setItem(SLOT_REFRESH, refresh);

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        pageInfo.set(DataComponents.CUSTOM_NAME,
                Component.literal("Page " + (currentPage + 1) + " of " + (maxPage + 1)));
        inventory.setItem(SLOT_PAGE_INFO, pageInfo);

        broadcastChanges();
    }

    private void drawConfirmationScreen() {
        ItemStack blackPane = new ItemStack(Items.STAINED_GLASS_PANE.black());
        blackPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int i = 0; i < SIZE; i++) inventory.setItem(i, blackPane);

        boolean ownListing = isOwnListing(selectedListing);

        ItemStack greenPane = new ItemStack(Items.STAINED_GLASS_PANE.green());
        greenPane.set(DataComponents.CUSTOM_NAME, Component.literal(ownListing ? "Take Back Item" : "Confirm Purchase"));

        ItemStack redPane = new ItemStack(Items.STAINED_GLASS_PANE.red());
        redPane.set(DataComponents.CUSTOM_NAME, Component.literal(ownListing ? "Keep It Listed" : "Cancel Purchase"));

        ItemStack grayPane = new ItemStack(Items.STAINED_GLASS_PANE.gray());
        grayPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inventory.setItem(9 + i * 9 + j, greenPane);
                inventory.setItem(15 + i * 9 + j, redPane);
                inventory.setItem(12 + i * 9 + j, grayPane);
            }
        }

        inventory.setItem(22, AHStorageHelper.fromListing(selectedListing, player.registryAccess()));

        // Make the slot that was just clicked a plain filler (see handleListingClick)
        if (confirmationOriginSlot != 22) inventory.setItem(confirmationOriginSlot, blackPane);

        broadcastChanges();
    }

    // ---------------- SUPPORT ----------------
    @Override
    public boolean stillValid(@NonNull Player player) {
        return true;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    // ---------------- CUSTOM SLOT ----------------
    private abstract static class ClickableSlot extends Slot {

        public ClickableSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        protected abstract void onClick(Player player);

        @Override
        public boolean mayPickup(@NonNull Player player) {
            onClick(player);
            return false;
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return false;
        }
    }
}
