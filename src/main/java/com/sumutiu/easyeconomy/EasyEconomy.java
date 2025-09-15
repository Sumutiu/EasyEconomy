package com.sumutiu.easyeconomy;

import com.sumutiu.easyeconomy.commands.*;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.util.UUID;

public class EasyEconomy implements ModInitializer {

	@Override
	public void onInitialize() {

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DepositCommand.register(dispatcher);
			WithdrawCommand.register(dispatcher);
			BankCommand.register(dispatcher);
			PayCommand.register(dispatcher);
			AHCommand.register(dispatcher);
		});

		// Player join: initialize bank and send welcome if new
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler != null && handler.getPlayer() != null) {
				ServerPlayerEntity player = handler.getPlayer();
				UUID uuid = player.getUuid();

				try {
					// Get player bank file
					File playerFile = BankStorage.getPlayerFile(uuid);
					boolean isNewPlayer = !playerFile.exists();

					// Load balance into memory (0 if file doesn't exist)
					long balance = BankStorage.getBalance(uuid);

					// Force file creation if missing
					if (isNewPlayer) {
						BankStorage.saveToFile(uuid, balance);
						EasyEconomyMessages.Logger(0, String.format(EasyEconomyMessages.BANK_FILE_CREATED_FOR_PLAYER, uuid));
						EasyEconomyMessages.PrivateMessage(player, EasyEconomyMessages.BANK_WELCOME_NEW_PLAYER);
					}

				} catch (Exception e) {
					EasyEconomyMessages.Logger(2,
							String.format(EasyEconomyMessages.BANK_INIT_FAILED, uuid, e.getMessage()));
					EasyEconomyMessages.PrivateMessage(player, EasyEconomyMessages.BANK_INIT_FAILED_PRIVATE);
				}
			} else {
				EasyEconomyMessages.Logger(2, EasyEconomyMessages.INVALID_CONNECTION_HANDLER);
			}
		});

		// Player quit: unload bank from memory
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler != null && handler.getPlayer() != null) {
				BankStorage.unloadPlayer(handler.getPlayer().getUuid());
			}
		});
	}
}
