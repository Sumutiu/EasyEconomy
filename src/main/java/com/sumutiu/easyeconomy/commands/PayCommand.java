package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.sumutiu.easyeconomy.EasyEconomy.EasyEconomyInitialized;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("pay")
                .then(argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {

                            var server = context.getSource().getServer();
                            return SharedSuggestionProvider.suggest(
                                    server.getPlayerNames(),
                                    builder
                            );

                        })
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {

                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        EasyEconomyMessages.Logger(1, EasyEconomyMessages.PLAYER_ONLY_COMMAND);
                                        return 0;
                                    }

                                    if (EasyEconomyInitialized) {
                                        String targetName = StringArgumentType.getString(ctx, "target");
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        return execute(ctx.getSource(), targetName, amount);
                                    } else {
                                        PrivateMessage(player, MOD_INIT_NOT_READY);
                                        return 0;
                                    }
                                }))));
    }

    private static int execute(CommandSourceStack source, String targetName, int amount) {

        if (!(source.getEntity() instanceof ServerPlayer sender)) {
            Logger(1, PLAYER_ONLY_COMMAND);
            return 0;
        }

        var server = source.getServer();

        if (amount <= 0) {
            PrivateMessage(sender, BANK_PAY_NEGATIVE);
            return 0;
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            PrivateMessage(sender, String.format(BANK_PAY_FAILED_PLAYER_NOT_FOUND, targetName));
            return 0;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            PrivateMessage(sender, BANK_PAY_FAILED_SELF);
            return 0;
        }

        long senderBalance = BankStorage.getBalance(sender.getUUID());

        if (senderBalance < amount) {
            PrivateMessage(sender, String.format(BANK_PAY_FAILED_INSUFFICIENT, targetName, senderBalance));
            return 0;
        }

        try {
            // Withdraw from sender
            boolean removed = BankStorage.removeBalance(sender.getUUID(), amount);

            if (!removed) {
                PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
                return 0;
            }

            // Deposit to target
            BankStorage.addBalance(target.getUUID(), amount);

            // Notifications
            PrivateMessage(sender, String.format(BANK_PAY_SUCCESS_SENT, amount, targetName));
            PrivateMessage(target, String.format(BANK_PAY_SUCCESS_RECEIVED, amount, sender.getName().getString()));

        } catch (Exception e) {
            Logger(2, String.format(PAY_FAILED_ERROR,
                    sender.getName().getString(),
                    targetName,
                    e.getMessage()));

            PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
            return 0;
        }

        return SINGLE_SUCCESS;
    }
}