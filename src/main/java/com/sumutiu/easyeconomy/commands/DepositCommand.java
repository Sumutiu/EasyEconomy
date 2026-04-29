package com.sumutiu.easyeconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sumutiu.easyeconomy.storage.BankStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static com.sumutiu.easyeconomy.EasyEconomy.EasyEconomyInitialized;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class DepositCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("deposit")
                .then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> {

                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                Logger(1, PLAYER_ONLY_COMMAND);
                                return 0;
                            }

                            if (EasyEconomyInitialized) {
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                return execute(ctx.getSource(), amount);
                            } else {
                                PrivateMessage(player, MOD_INIT_NOT_READY);
                                return 0;
                            }
                        })));
    }

    private static int execute(CommandSourceStack source, int amount) {

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            Logger(1, PLAYER_ONLY_COMMAND);
            return 0;
        }

        int removed = 0;

        for (int i = 0; i < player.getInventory().getContainerSize() && removed < amount; i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.getItem() == Items.DIAMOND) {
                int take = Math.min(stack.getCount(), amount - removed);
                stack.shrink(take);
                removed += take;
            }
        }

        if (removed <= 0) {
            PrivateMessage(player, INVENTORY_EMPTY);
            return 0;
        }

        try {
            BankStorage.addBalance(player.getUUID(), removed);
            PrivateMessage(player, String.format(BANK_DEPOSIT_QTY, removed));
        } catch (Exception e) {
            Logger(2, String.format(BANK_DEPOSIT_FAILED, player.getUUID(), e.getMessage()));
            PrivateMessage(player, BANK_DEPOSIT_FAILED_PRIVATE);
            return 0;
        }

        return removed;
    }
}