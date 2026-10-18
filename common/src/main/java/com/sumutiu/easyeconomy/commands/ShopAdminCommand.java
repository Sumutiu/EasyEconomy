package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.storage.NameCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.*;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.sumutiu.easyeconomy.EasyEconomy.EasyEconomyInitialized;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ShopAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("shopadmin")
                // Only operators (and the server console) can see or run this command
                .requires(ShopAdminCommand::isAdmin)
                .then(literal("set")
                        .then(argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var server = ctx.getSource().getServer();
                                    Set<String> names = new HashSet<>();
                                    names.addAll(Arrays.asList(server.getPlayerNames()));
                                    names.addAll(Arrays.asList(NameCache.getAutocompleteSuggestions()));
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
                                .then(argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();

                                            if (!EasyEconomyInitialized) {
                                                source.sendFailure(Component.literal(MOD_INIT_NOT_READY));
                                                return 0;
                                            }

                                            String playerName = StringArgumentType.getString(ctx, "player");
                                            long amount = LongArgumentType.getLong(ctx, "amount");

                                            var server = source.getServer();
                                            var onlinePlayer = server.getPlayerList().getPlayerByName(playerName);

                                            UUID uuid;
                                            if (onlinePlayer != null) {
                                                uuid = onlinePlayer.getUUID();
                                            } else {
                                                uuid = NameCache.getUUID(playerName);
                                                if (uuid == null) {
                                                    try {
                                                        uuid = UUID.fromString(playerName);
                                                    } catch (IllegalArgumentException ignored) {
                                                        // Not a known name and not a UUID either
                                                    }
                                                }
                                            }

                                            if (uuid == null) {
                                                source.sendFailure(Component.literal(String.format(SHOPADMIN_PLAYER_NOT_FOUND, playerName)));
                                                return 0;
                                            }

                                            BankStorage.setBalance(uuid, amount);
                                            String resolvedName = NameCache.getName(uuid);
                                            source.sendSuccess(() -> Component.literal(
                                                    String.format(SHOPADMIN_SET_SUCCESS, resolvedName, amount)
                                            ), false);
                                            return SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(literal("list")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();

                            if (!EasyEconomyInitialized) {
                                source.sendFailure(Component.literal(MOD_INIT_NOT_READY));
                                return 0;
                            }

                            List<Map.Entry<UUID, Long>> all = BankStorage.getTopBalances(Integer.MAX_VALUE);

                            if (all.isEmpty()) {
                                source.sendSuccess(() -> Component.literal(SHOPADMIN_LIST_EMPTY), false);
                                return 0;
                            }

                            source.sendSuccess(() -> Component.literal(
                                    String.format(SHOPADMIN_LIST_HEADER, all.size())
                            ), false);

                            for (Map.Entry<UUID, Long> entry : all) {
                                String name = NameCache.getName(entry.getKey());
                                source.sendSuccess(() -> Component.literal(
                                        String.format(SHOPADMIN_LIST_ENTRY, name, entry.getValue())
                                ), false);
                            }

                            return SINGLE_SUCCESS;
                        })
                )
        );
    }

    private static boolean isAdmin(CommandSourceStack source) {
        try {
            // No entity = server console, RCON or a command block, which are already trusted
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return source.getEntity() == null;
            }

            return source.getServer().getPlayerList().isOp(
                    new NameAndId(player.getUUID(), player.getName().getString())
            );
        } catch (Exception ignored) {
            return false;
        }
    }
}
