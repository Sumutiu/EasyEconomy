package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.util.AHExpiredScreenFactory;
import com.sumutiu.easyeconomy.util.AHScreenFactory;
import com.sumutiu.easyeconomy.util.EasyEconomyMessages;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AHCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("ah")
                .executes(ctx -> { // open main AH GUI
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    AHScreenFactory.open(player);
                    return SINGLE_SUCCESS;
                })
                .then(literal("sell")
                        .then(argument("price", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    long price = IntegerArgumentType.getInteger(ctx, "price");
                                    return sellItem(player, price);
                                })
                        )
                )
                .then(literal("expired")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            AHExpiredScreenFactory.open(player);
                            return SINGLE_SUCCESS;
                        })
                )
        );
    }

    private static int sellItem(ServerPlayerEntity player, long price) {
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            EasyEconomyMessages.PrivateMessage(player, "You are not holding any item.");
            return 0;
        }

        if (price <= 0) {
            EasyEconomyMessages.PrivateMessage(player, "Price must be greater than 0.");
            return 0;
        }

        int qty = held.getCount(); // sell the entire stack

        // Save both item name and item ID BEFORE decrementing
        String itemName = held.getName().getString();
        String itemId = net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString();

        AHStorage.AHListing listing = new AHStorage.AHListing(
                itemId, // store proper identifier like "minecraft:white_wool"
                qty,
                price,
                player.getUuid(),
                player.getName().getString()
        );

        var listings = AHStorage.loadListings(player.getUuid());
        listings.add(listing);
        AHStorage.saveListings(player.getUuid(), listings);

        held.decrement(qty); // remove all from hand

        EasyEconomyMessages.PrivateMessage(
                player,
                "Listed " + qty + " of " + itemName + " on AH for " + price + " diamonds."
        );

        return SINGLE_SUCCESS;
    }
}
