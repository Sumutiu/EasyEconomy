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

public class AHExpiredScreenFactory {

    public static void open(ServerPlayer player) {
        List<AHStorage.AHListing> all = AHStorage.loadListings(player.getUUID());
        List<AHStorage.AHListing> expired = AHStorage.getExpiredListings(all);

        MenuProvider factory = new MenuProvider() {

            @Override
            public @NonNull Component getDisplayName() {
                return Component.literal("Expired AH Listings");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, @NonNull Inventory playerInventory, @NonNull Player playerEntity) {
                return new AHExpiredScreenHandler(
                        syncId,
                        new SimpleContainer(AHExpiredScreenHandler.SIZE),
                        expired,
                        playerEntity
                );
            }
        };

        player.openMenu(factory);
    }
}