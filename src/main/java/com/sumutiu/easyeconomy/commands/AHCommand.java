package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.util.AHExpiredScreenFactory;
import com.sumutiu.easyeconomy.util.AHScreenFactory;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class AHCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("ah")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    AHScreenFactory.open(player);
                    return SINGLE_SUCCESS;
                })
                .then(literal("sell")
                        .then(argument("price", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    long price = IntegerArgumentType.getInteger(ctx, "price");
                                    return sellItem(player, price);
                                })
                        )
                )
                .then(literal("expired")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            AHExpiredScreenFactory.open(player);
                            return SINGLE_SUCCESS;
                        })
                )
        );
    }

    private static int sellItem(ServerPlayer player, long price) {

        ItemStack held = player.getMainHandItem();

        if (held.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(player, AH_SELL_EMPTY);
            return 0;
        }

        if (price <= 0) {
            EasyEconomyMessages.PrivateMessage(player, AH_SELL_NO_PRICE);
            return 0;
        }

        int qty = held.getCount();

        // Item info BEFORE modifying stack
        String itemName = held.getHoverName().getString();
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        String itemNbt = held.save(player.registryAccess()).toString();

        AHStorage.AHListing listing = new AHStorage.AHListing(
                itemId,
                qty,
                price,
                player.getUUID(),
                player.getName().getString(),
                itemNbt
        );

        var listings = AHStorage.loadListings(player.getUUID());
        listings.add(listing);
        AHStorage.saveListings(player.getUUID(), listings);

        held.shrink(qty); // remove all items from hand

        EasyEconomyMessages.PrivateMessage(
                player,
                String.format(AH_SELL_CONFIRMATION, qty, itemName, price)
        );

        return SINGLE_SUCCESS;
    }
}