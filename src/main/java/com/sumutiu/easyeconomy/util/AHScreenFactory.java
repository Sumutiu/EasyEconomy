package com.sumutiu.easyeconomy.util;

import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class AHScreenFactory {

    public static void open(ServerPlayerEntity player, MinecraftServer server) {
        List<AHStorage.AHListing> allActive = AHStorageHelper.getAllActiveListings();

        if (allActive.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(player, AH_NO_ACTIVE_LISTING, server);
            return;
        }

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Auction House");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity playerEntity) {
                return new AHScreenHandler(syncId, new SimpleInventory(AHScreenHandler.SIZE), allActive, playerEntity, server);
            }
        };

        player.openHandledScreen(factory);
    }
}
