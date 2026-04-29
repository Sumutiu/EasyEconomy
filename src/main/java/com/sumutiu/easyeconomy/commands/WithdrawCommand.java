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

public class WithdrawCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("withdraw")
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

        // Get player balance
        long balance;
        try {
            balance = BankStorage.getBalance(player.getUUID());
        } catch (Exception e) {
            Logger(2, String.format(BANK_READ_FAILED, player.getUUID(), e.getMessage()));
            PrivateMessage(player, BANK_READ_FAILED_PRIVATE);
            return 0;
        }

        if (balance < amount) {
            PrivateMessage(player, String.format(BANK_BALANCE_INSUFFICIENT, balance));
            return 0;
        }

        // Compute available inventory space
        int capacity = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);

            if (s.isEmpty()) {
                capacity += new ItemStack(Items.DIAMOND).getMaxStackSize();
            } else if (s.getItem() == Items.DIAMOND) {
                capacity += (s.getMaxStackSize() - s.getCount());
            }
        }

        if (capacity < amount) {
            PrivateMessage(player, String.format(BANK_BALANCE_NO_SPACE, amount));
            return 0;
        }

        // Withdraw from bank
        try {
            boolean ok = BankStorage.removeBalance(player.getUUID(), amount);
            if (!ok) {
                PrivateMessage(player, BANK_BALANCE_ERROR);
                return 0;
            }
        } catch (Exception e) {
            Logger(2, String.format(BANK_WITHDRAW_FAILED, player.getUUID(), e.getMessage()));
            PrivateMessage(player, BANK_WITHDRAW_FAILED_PRIVATE);
            return 0;
        }

        // Give diamonds
        int remaining = amount;

        while (remaining > 0) {
            int maxStack = new ItemStack(Items.DIAMOND).getMaxStackSize();
            int take = Math.min(remaining, maxStack);

            ItemStack stack = new ItemStack(Items.DIAMOND, take);

            boolean added = player.getInventory().add(stack);

            if (!added) {
                player.drop(stack, false);
            }

            remaining -= take;
        }

        PrivateMessage(player, String.format(BANK_BALANCE_CONFIRM, amount));
        return amount;
    }
}