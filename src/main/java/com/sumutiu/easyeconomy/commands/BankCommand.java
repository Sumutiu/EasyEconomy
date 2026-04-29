package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static com.sumutiu.easyeconomy.EasyEconomy.EasyEconomyInitialized;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.commands.Commands.literal;

public class BankCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("balance")
                .executes(ctx -> {

                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        Logger(1, PLAYER_ONLY_COMMAND);
                        return 0;
                    }

                    if (EasyEconomyInitialized) {
                        long bal = BankStorage.getBalance(player.getUUID());
                        EasyEconomyMessages.PrivateMessage(player, String.format(BANK_BALANCE, bal));
                        return 1;
                    } else {
                        PrivateMessage(player, MOD_INIT_NOT_READY);
                        return 0;
                    }
                })
        );
    }
}