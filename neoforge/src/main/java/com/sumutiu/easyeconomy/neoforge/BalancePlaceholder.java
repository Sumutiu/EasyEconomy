package com.sumutiu.easyeconomy.neoforge;

import com.sumutiu.easyeconomy.storage.BankStorage;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The %easyeconomy:balance% placeholder, for Placeholder API NeoForge (bundled in the NeoForge jar).
 * Same code as the Fabric one: the port has the same API, but it is a different library,
 * so each loader compiles its own copy.
 */
public class BalancePlaceholder {

    // Registers player balance placeholder
    public static void register() {
        Placeholders.registerCommon(
                Identifier.fromNamespaceAndPath("easyeconomy", "balance"),
                (ctx, _) -> {

                    if (!ctx.hasPlayer()) {
                        return PlaceholderResult.invalid("No player");
                    }

                    Long balance = BankStorage.tryGetBalance(Objects.requireNonNull(ctx.player()).getUUID());

                    if (balance == null) {
                        return PlaceholderResult.invalid("Bank unavailable");
                    }

                    return PlaceholderResult.value(
                            Component.literal(String.valueOf(balance))
                    );
                }
        );
    }
}
