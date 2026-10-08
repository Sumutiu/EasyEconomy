package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHScreenFactory {

    public static final String SHOP_TITLE = "Shop";

    // Opens the Shop with every active listing
    public static void open(ServerPlayer player) {
        open(player, null);
    }

    // Opens the Shop showing only listings of the given item ID (null = no filter)
    public static void open(ServerPlayer player, String filterItemId) {
        List<AHStorage.AHListing> allActive = AHStorageHelper.getAllActiveListings();

        if (allActive.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(player, SHOP_NO_ACTIVE_LISTING);
            return;
        }

        if (filterItemId != null && allActive.stream().noneMatch(l -> filterItemId.equals(l.itemId))) {
            EasyEconomyMessages.PrivateMessage(player, SHOP_LIST_NONE);
            return;
        }

        MenuProvider factory = new MenuProvider() {

            @Override
            public @NonNull Component getDisplayName() {
                return Component.literal(SHOP_TITLE);
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, @NonNull Inventory playerInventory, @NonNull Player playerEntity) {
                return new AHScreenHandler(
                        syncId,
                        new SimpleContainer(AHScreenHandler.SIZE),
                        allActive,
                        playerEntity,
                        filterItemId
                );
            }
        };

        player.openMenu(factory);
    }
}
