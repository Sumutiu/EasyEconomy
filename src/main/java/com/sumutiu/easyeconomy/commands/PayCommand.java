package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.storage.NameCache;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

                            // Online players plus everyone who has ever joined (they can be paid while offline)
                            var server = context.getSource().getServer();
                            Set<String> names = new HashSet<>();
                            names.addAll(Arrays.asList(server.getPlayerNames()));
                            names.addAll(Arrays.asList(NameCache.getAllNames()));
                            return SharedSuggestionProvider.suggest(
                                    names,
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

        // Online player first, otherwise any player who has joined before (paid while offline)
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUuid;
        String targetDisplayName;

        if (target != null) {
            targetUuid = target.getUUID();
            targetDisplayName = target.getName().getString();
        } else {
            targetUuid = NameCache.getUUID(targetName);
            if (targetUuid == null) {
                PrivateMessage(sender, String.format(BANK_PAY_FAILED_PLAYER_NOT_FOUND, targetName));
                return 0;
            }
            targetDisplayName = NameCache.getName(targetUuid);
        }

        if (sender.getUUID().equals(targetUuid)) {
            PrivateMessage(sender, BANK_PAY_FAILED_SELF);
            return 0;
        }

        Long senderBalance = BankStorage.tryGetBalance(sender.getUUID());

        if (senderBalance == null) {
            PrivateMessage(sender, BANK_READ_FAILED_PRIVATE);
            return 0;
        }

        if (senderBalance < amount) {
            PrivateMessage(sender, String.format(BANK_PAY_FAILED_INSUFFICIENT, targetDisplayName, senderBalance));
            return 0;
        }

        // Make sure the target's bank can be read before taking anything from the sender
        if (BankStorage.tryGetBalance(targetUuid) == null) {
            PrivateMessage(sender, String.format(BANK_PAY_TARGET_UNAVAILABLE, targetDisplayName));
            return 0;
        }

        try {
            // Withdraw from sender
            boolean removed = BankStorage.removeBalance(sender.getUUID(), amount);

            if (!removed) {
                PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
                return 0;
            }

            // Deposit to target (refund the sender if that fails)
            if (!BankStorage.addBalance(targetUuid, amount)) {
                BankStorage.addBalance(sender.getUUID(), amount);
                PrivateMessage(sender, String.format(BANK_PAY_TARGET_UNAVAILABLE, targetDisplayName));
                return 0;
            }

            // Notifications
            PrivateMessage(sender, String.format(BANK_PAY_SUCCESS_SENT, amount, targetDisplayName));
            if (target != null) {
                PrivateMessage(target, String.format(BANK_PAY_SUCCESS_RECEIVED, amount, sender.getName().getString()));
            } else {
                // Don't keep offline players' balances in memory
                BankStorage.unloadPlayer(targetUuid);
            }

        } catch (Exception e) {
            Logger(2, String.format(PAY_FAILED_ERROR,
                    sender.getName().getString(),
                    targetDisplayName,
                    e.getMessage()));

            PrivateMessage(sender, BANK_PAY_FAILED_ERROR);
            return 0;
        }

        return SINGLE_SUCCESS;
    }
}