package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHOwnListingsScreenFactory {

    // /shop cancel: the player's listings that are still for sale
    public static void openActive(ServerPlayer player) {
        List<AHStorage.AHListing> own = AHStorage.tryGetListings(player.getUUID());
        if (own == null) {
            PrivateMessage(player, SHOP_DATA_UNAVAILABLE);
            return;
        }

        List<AHStorage.AHListing> active = AHStorage.getActiveListings(own);

        if (active.isEmpty()) {
            PrivateMessage(player, SHOP_NO_OWN_LISTING);
            return;
        }

        open(player, active, AHOwnListingsScreenHandler.Mode.ACTIVE, "Your Shop Listings");
    }

    // /shop expired: the player's listings that expired and can be reclaimed
    public static void openExpired(ServerPlayer player) {
        List<AHStorage.AHListing> own = AHStorage.tryGetListings(player.getUUID());
        if (own == null) {
            PrivateMessage(player, SHOP_DATA_UNAVAILABLE);
            return;
        }

        List<AHStorage.AHListing> expired = AHStorage.getExpiredListings(own);

        if (expired.isEmpty()) {
            PrivateMessage(player, SHOP_NO_EXPIRED_LISTING);
            return;
        }

        open(player, expired, AHOwnListingsScreenHandler.Mode.EXPIRED, "Expired Shop Listings");
    }

    private static void open(ServerPlayer player, List<AHStorage.AHListing> listings, AHOwnListingsScreenHandler.Mode mode, String title) {
        AHStorage.sortNewestFirst(listings);

        MenuProvider factory = new MenuProvider() {

            @Override
            public @NonNull Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, @NonNull Inventory playerInventory, @NonNull Player playerEntity) {
                return new AHOwnListingsScreenHandler(
                        syncId,
                        new SimpleContainer(AHOwnListingsScreenHandler.SIZE),
                        listings,
                        playerEntity,
                        mode
                );
            }
        };

        player.openMenu(factory);
    }
}
