package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class BankCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bank")
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();

                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                        EasyEconomyMessages.Logger(1, EasyEconomyMessages.PLAYER_ONLY_COMMAND);
                        return 0;
                    }

                    long bal = BankStorage.getBalance(player.getUuid());
                    EasyEconomyMessages.PrivateMessage(player, String.format(EasyEconomyMessages.BANK_BALANCE, bal));
                    return 1;
                })
        );
    }
}
