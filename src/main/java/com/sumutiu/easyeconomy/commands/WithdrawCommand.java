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

public class WithdrawCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("withdraw")
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

        // Get player balance
        long balance;
        try {
            balance = BankStorage.getBalance(player.getUuid());
        } catch (Exception e) {
            Logger(2, String.format(BANK_READ_FAILED, player.getUuid(), e.getMessage()));
            PrivateMessage(player, BANK_READ_FAILED_PRIVATE);
            return 0;
        }

        if (balance < amount) {
            PrivateMessage(player, String.format(BANK_BALANCE_INSUFFICIENT, balance));
            return 0;
        }

        // Compute available space for diamonds in inventory
        int capacity = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty()) capacity += Items.DIAMOND.getMaxCount();
            else if (s.getItem() == Items.DIAMOND) capacity += (Items.DIAMOND.getMaxCount() - s.getCount());
        }

        if (capacity < amount) {
            PrivateMessage(player, String.format(BANK_BALANCE_NO_SPACE, amount));
            return 0;
        }

        // Remove balance from bank
        try {
            boolean ok = BankStorage.removeBalance(player.getUuid(), amount);
            if (!ok) {
                PrivateMessage(player, BANK_BALANCE_ERROR);
                return 0;
            }
        } catch (Exception e) {
            Logger(2, String.format(BANK_WITHDRAW_FAILED, player.getUuid(), e.getMessage()));
            PrivateMessage(player, BANK_WITHDRAW_FAILED_PRIVATE);
            return 0;
        }

        // Give diamonds (split into stacks if needed)
        int remaining = amount;
        while (remaining > 0) {
            int take = Math.min(remaining, Items.DIAMOND.getMaxCount());
            ItemStack stack = new ItemStack(Items.DIAMOND, take);
            boolean added = player.getInventory().insertStack(stack);
            if (!added) {
                player.dropItem(stack, false);
            }
            remaining -= take;
        }

        PrivateMessage(player, String.format(BANK_BALANCE_CONFIRM, amount));
        return amount;
    }
}
