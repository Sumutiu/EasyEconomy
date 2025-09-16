package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class PayCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pay")
                .then(argument("target", StringArgumentType.word())
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    return execute(ctx.getSource(), targetName, amount);
                                }))));
    }

    private static int execute(ServerCommandSource source, String targetName, int amount) {
        if (!(source.getEntity() instanceof ServerPlayerEntity sender)) {
            Logger(1, PLAYER_ONLY_COMMAND);
            return 0;
        }

        if (amount <= 0) {
            PrivateMessage(sender, BANK_PAY_NEGATIVE);
            return 0;
        }

        // Null-safe check for server and player manager
        if (sender.getServer() == null || sender.getServer().getPlayerManager() == null) {
            PrivateMessage(sender, PLAYER_ONLY_COMMAND);
            return 0;
        }

        ServerPlayerEntity target = sender.getServer().getPlayerManager().getPlayer(targetName);

        if (target == null) {
            PrivateMessage(sender, String.format(BANK_PAY_FAILED_PLAYER_NOT_FOUND, targetName));
            return 0;
        }

        if (sender.getUuid().equals(target.getUuid())) {
            PrivateMessage(sender, BANK_PAY_FAILED_SELF);
            return 0;
        }

        long senderBalance = BankStorage.getBalance(sender.getUuid());
        if (senderBalance < amount) {
            PrivateMessage(sender, String.format(BANK_PAY_FAILED_INSUFFICIENT, targetName, senderBalance));
            return 0;
        }

        try {
            // Withdraw from sender
            boolean removed = BankStorage.removeBalance(sender.getUuid(), amount);
            if (!removed) {
                PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
                return 0;
            }

            // Deposit to target
            BankStorage.addBalance(target.getUuid(), amount);

            // Notify both players
            PrivateMessage(sender, String.format(BANK_PAY_SUCCESS_SENT, amount, targetName));
            PrivateMessage(target, String.format(BANK_PAY_SUCCESS_RECEIVED, amount, sender.getName().getString()));

        } catch (Exception e) {
            Logger(2, String.format(PAY_FAILED_ERROR, sender.getName().getString(), targetName, e.getMessage()));
            PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
            return 0;
        }

        return SINGLE_SUCCESS;
    }
}
