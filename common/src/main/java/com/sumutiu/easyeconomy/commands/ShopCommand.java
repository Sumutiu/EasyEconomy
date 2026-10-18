package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.AHStorageHelper;
import com.sumutiu.easyeconomy.config.EasyEconomyConfig;
import com.sumutiu.easyeconomy.util.AHOwnListingsScreenFactory;
import com.sumutiu.easyeconomy.util.AHScreenFactory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.sumutiu.easyeconomy.EasyEconomy.EasyEconomyInitialized;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("shop")
                .executes(ctx -> {

                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        Logger(1, PLAYER_ONLY_COMMAND);
                        return 0;
                    }

                    if (EasyEconomyInitialized) {
                        AHScreenFactory.open(player);
                        return SINGLE_SUCCESS;
                    } else {
                        PrivateMessage(player, MOD_INIT_NOT_READY);
                        return 0;
                    }
                })
                .then(literal("sell")
                        .then(argument("price", IntegerArgumentType.integer(1))
                                .executes(ctx -> {

                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        Logger(1, PLAYER_ONLY_COMMAND);
                                        return 0;
                                    }

                                    if (EasyEconomyInitialized) {
                                        long price = IntegerArgumentType.getInteger(ctx, "price");
                                        return sellItem(player, price);
                                    } else {
                                        PrivateMessage(player, MOD_INIT_NOT_READY);
                                        return 0;
                                    }
                                })
                        )
                )
                .then(literal("list")
                        .executes(ctx -> {

                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                Logger(1, PLAYER_ONLY_COMMAND);
                                return 0;
                            }

                            if (EasyEconomyInitialized) {
                                return listItem(player);
                            } else {
                                PrivateMessage(player, MOD_INIT_NOT_READY);
                                return 0;
                            }
                        })
                )
                .then(literal("expired")
                        .executes(ctx -> {

                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                Logger(1, PLAYER_ONLY_COMMAND);
                                return 0;
                            }

                            if (EasyEconomyInitialized) {
                                AHOwnListingsScreenFactory.openExpired(player);
                                return SINGLE_SUCCESS;
                            } else {
                                PrivateMessage(player, MOD_INIT_NOT_READY);
                                return 0;
                            }
                        })
                )
                .then(literal("cancel")
                        .executes(ctx -> {

                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                Logger(1, PLAYER_ONLY_COMMAND);
                                return 0;
                            }

                            if (EasyEconomyInitialized) {
                                AHOwnListingsScreenFactory.openActive(player);
                                return SINGLE_SUCCESS;
                            } else {
                                PrivateMessage(player, MOD_INIT_NOT_READY);
                                return 0;
                            }
                        })
                )
        );
    }

    private static int sellItem(ServerPlayer player, long price) {

        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            PrivateMessage(player, SHOP_SELL_EMPTY);
            return 0;
        }

        if (price <= 0) {
            PrivateMessage(player, SHOP_SELL_NO_PRICE);
            return 0;
        }

        int qty = held.getCount();

        // Item info BEFORE modifying stack
        String itemName = held.getHoverName().getString();
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        RegistryOps<Tag> ops =
                player.registryAccess().createSerializationContext(NbtOps.INSTANCE);

        Tag tag = ItemStack.CODEC.encodeStart(ops, held).getOrThrow();

        String itemNbt = tag.toString();

        AHStorage.AHListing listing = new AHStorage.AHListing(
                itemId,
                qty,
                price,
                player.getUUID(),
                player.getName().getString(),
                itemNbt
        );

        // Parse the seller's listings once, so ones with unreadable item data don't count toward the limit
        for (AHStorage.AHListing own : AHStorage.getListings(player.getUUID())) {
            AHStorageHelper.fromListing(own, player.registryAccess());
        }

        AHStorage.AddResult result = AHStorage.addListing(player.getUUID(), listing);

        if (result == AHStorage.AddResult.LIMIT_REACHED) {
            int count = AHStorage.countListingsForLimit(player.getUUID());
            PrivateMessage(player, String.format(SHOP_SELL_LIMIT, count, EasyEconomyConfig.getMaxListingsPerPlayer()));
            return 0;
        }

        if (result != AHStorage.AddResult.ADDED) {
            PrivateMessage(player, SHOP_SELL_FAILED);
            return 0;
        }

        held.shrink(qty); // remove all items from hand

        PrivateMessage(player, String.format(SHOP_SELL_CONFIRMATION, qty, itemName, price));

        return SINGLE_SUCCESS;
    }

    // Opens the Shop filtered to the item the player is holding
    private static int listItem(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            PrivateMessage(player, SHOP_LIST_EMPTY_HAND);
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        AHScreenFactory.open(player, itemId);
        return SINGLE_SUCCESS;
    }
}
