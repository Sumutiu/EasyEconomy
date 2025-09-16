package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorage;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.inventory.SimpleInventory;

import java.util.List;

public class AHExpiredScreenFactory {

    public static void open(ServerPlayerEntity player) {
        List<AHStorage.AHListing> all = AHStorage.loadListings(player.getUuid());
        List<AHStorage.AHListing> expired = AHStorage.getExpiredListings(all);

        // Wrap in NamedScreenHandlerFactory
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Expired AH Listings");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity playerEntity) {
                return new AHExpiredScreenHandler(syncId, new SimpleInventory(AHScreenHandler.SIZE), expired);
            }
        };

        player.openHandledScreen(factory);
    }
}
