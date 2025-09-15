package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class DepositCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("deposit")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                            return execute(ctx.getSource(), amount);
                        })));
    }

    private static int execute(ServerCommandSource source, int amount) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            Logger(1, PLAYER_ONLY_COMMAND);
            return 0;
        }

        // Count diamonds in inventory
        int removed = 0;
        for (int i = 0; i < player.getInventory().size() && removed < amount; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.DIAMOND) {
                int take = Math.min(stack.getCount(), amount - removed);
                stack.decrement(take);
                removed += take;
            }
        }

        if (removed <= 0) {
            PrivateMessage(player, INVENTORY_EMPTY);
            return 0;
        }

        try {
            BankStorage.addBalance(player.getUuid(), removed);
            PrivateMessage(player, String.format(BANK_DEPOSIT_QTY, removed));
        } catch (Exception e) {
            // Log with consistent message format
            Logger(2, String.format(BANK_DEPOSIT_FAILED, player.getUuid(), e.getMessage()));
            PrivateMessage(player, BANK_DEPOSIT_FAILED_PRIVATE);
            return 0;
        }

        return removed;
    }
}
