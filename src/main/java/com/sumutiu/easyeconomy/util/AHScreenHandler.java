package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import com.sumutiu.easyeconomy.storage.BankStorage;
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

    private final Container inventory;
    private final List<AHStorage.AHListing> listings;
    private final Player player;

    private boolean inConfirmation = false;
    private int confirmSlot = -1;
    private int currentPage = 0;

    public AHScreenHandler(int syncId, Container inventory, List<AHStorage.AHListing> listings, Player player) {
        super(MenuType.GENERIC_9x6, syncId);
        this.inventory = inventory;
        this.listings = listings;
        this.player = player;

        // ---------------- Auction House Slots ----------------
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

    // ---------------- CLICK HANDLER ----------------
    private void handleListingClick(Player player, int slotIndex) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        // ---- Confirmation Screen ----
        if (inConfirmation) {
            if (isConfirmSlot(slotIndex)) {
                buyListing(serverPlayer);
                return;
            }
            if (isCancelSlot(slotIndex)) {
                inConfirmation = false;
                confirmSlot = -1;
                drawListings();
                return;
            }
            return;
        }

        // ---- Navigation ----
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

        // ---- Listing Click ----
        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE && listingIndex < listings.size()) {
            inConfirmation = true;
            confirmSlot = listingIndex;
            drawConfirmationScreen();
        }
    }

    private void buyListing(net.minecraft.server.level.ServerPlayer serverPlayer) {
        inConfirmation = false;

        AHStorage.AHListing listing = listings.get(confirmSlot);
        ItemStack purchased = AHStorageHelper.fromListing(listing, serverPlayer.registryAccess());

        if (purchased == null || purchased.isEmpty()) {
            PrivateMessage(serverPlayer, AH_BUY_ERROR);
            drawListings();
            confirmSlot = -1;
            return;
        }

        if (InventoryUtil.noInventorySpace(serverPlayer, purchased)) {
            PrivateMessage(serverPlayer, AH_BUY_NO_SPACE);
            drawListings();
            confirmSlot = -1;
            return;
        }

        long balance = BankStorage.getBalance(serverPlayer.getUUID());
        if (balance < listing.price) {
            PrivateMessage(serverPlayer, AH_BUY_NO_MONEY);
            drawListings();
            confirmSlot = -1;
            return;
        }

        if (!BankStorage.removeBalance(serverPlayer.getUUID(), listing.price)) {
            PrivateMessage(serverPlayer, AH_WITHDRAW_ERROR);
            drawListings();
            confirmSlot = -1;
            return;
        }

        BankStorage.addBalance(listing.seller, listing.price);

        ItemStack purchasedCopy = purchased.copy();
        if (!serverPlayer.getInventory().add(purchasedCopy)) {
            serverPlayer.drop(purchasedCopy, false);
        }

        List<AHStorage.AHListing> sellerListings = AHStorage.loadListings(listing.seller);
        sellerListings.removeIf(l -> l.timestamp == listing.timestamp && l.seller.equals(listing.seller));
        AHStorage.saveListings(listing.seller, sellerListings);

        listings.remove(confirmSlot);

        PrivateMessage(serverPlayer, String.format(AH_BUY_CONFIRMATION,
                purchased.getCount(),
                purchased.getHoverName().getString(),
                listing.price,
                listing.sellerName));

        confirmSlot = -1;
        drawListings();
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int startIndex = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int listingIndex = startIndex + i;
            if (listingIndex >= listings.size()) continue;

            AHStorage.AHListing listing = listings.get(listingIndex);
            ItemStack stack = AHStorageHelper.fromListing(listing, player.registryAccess());
            if (stack == null) stack = ItemStack.EMPTY;

            String sellerName = listing.sellerName != null ? listing.sellerName : "Unknown";
            String date = sdf.format(new Date(listing.timestamp));

            stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                    stack.getCount() + " x " + stack.getHoverName().getString()
            ));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Seller: " + sellerName));
            lore.add(Component.literal("Listed: " + date));
            lore.add(Component.literal("Price: " + listing.price + " diamonds"));

            stack.set(DataComponents.LORE, new ItemLore(lore));
            inventory.setItem(i, stack);
        }

        // Navigation buttons
        int maxPage = (listings.size() - 1) / ITEMS_PER_PAGE;
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponents.CUSTOM_NAME, Component.literal("Previous Page"));
            inventory.setItem(45, prev);
        }
        if (currentPage < maxPage) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponents.CUSTOM_NAME, Component.literal("Next Page"));
            inventory.setItem(53, next);
        }

        ItemStack pageInfo = new ItemStack(Items.PAPER);
        pageInfo.set(DataComponents.CUSTOM_NAME,
                Component.literal("Page " + (currentPage + 1) + " of " + (maxPage + 1)));
        inventory.setItem(49, pageInfo);

        broadcastChanges();
    }

    private void drawConfirmationScreen() {
        ItemStack blackPane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        blackPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int i = 0; i < SIZE; i++) inventory.setItem(i, blackPane);

        ItemStack greenPane = new ItemStack(Items.GREEN_STAINED_GLASS_PANE);
        greenPane.set(DataComponents.CUSTOM_NAME, Component.literal("Confirm Purchase"));

        ItemStack redPane = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        redPane.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel Purchase"));

        ItemStack grayPane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                inventory.setItem(9 + i * 9 + j, greenPane);
                inventory.setItem(15 + i * 9 + j, redPane);
                inventory.setItem(12 + i * 9 + j, grayPane);
            }
        }

        AHStorage.AHListing listing = listings.get(confirmSlot);
        inventory.setItem(22, AHStorageHelper.fromListing(listing, player.registryAccess()));

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