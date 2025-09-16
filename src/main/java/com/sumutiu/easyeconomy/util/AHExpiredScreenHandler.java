package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
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

public class AHExpiredScreenHandler extends ScreenHandler {

    public static final int ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;
    public static final int ITEMS_PER_PAGE = 45;

    private final Inventory inventory;
    private final List<AHStorage.AHListing> expiredListings;
    private int currentPage = 0;

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
        for (int i = 0; i < SIZE; i++) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

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

                Text itemName = Text.literal(stack.getCount() + " x " + stack.getItem().getName(stack).getString());
                stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, itemName);

                List<Text> loreLines = new ArrayList<>();
                loreLines.add(Text.literal("Seller: " + sellerName));
                loreLines.add(Text.literal("Expired: " + date));

                stack.set(net.minecraft.component.DataComponentTypes.LORE, new LoreComponent(loreLines));
                inventory.setStack(i, stack);
            }
        }

        int maxPage = (expiredListings.size() - 1) / ITEMS_PER_PAGE;

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

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.QUICK_MOVE) {
            return;
        }

        if (!(player instanceof ServerPlayerEntity buyer)) {
            return;
        }

        if (slotIndex == 45 && currentPage > 0) { // Previous Page
            currentPage--;
            drawListings();
            return;
        }
        if (slotIndex == 53 && (currentPage + 1) * ITEMS_PER_PAGE < expiredListings.size()) { // Next Page
            currentPage++;
            drawListings();
            return;
        }

        int listingIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
        if (listingIndex < 0 || listingIndex >= expiredListings.size()) return;

        if (slotIndex >= 0 && slotIndex < ITEMS_PER_PAGE) {
            AHStorage.AHListing listing = expiredListings.get(listingIndex);
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

            expiredListings.remove(listingIndex);

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
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }
}
