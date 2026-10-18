package com.sumutiu.easyeconomy.neoforge;

import com.sumutiu.easyeconomy.EasyEconomy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * NeoForge entrypoint (see neoforge.mods.toml): passes the NeoForge events to the common code.
 * Only NeoForge creates it, by finding @Mod, so IntelliJ thinks it is never used.
 */
@SuppressWarnings("unused")
@Mod(EasyEconomy.MOD_ID)
public class EasyEconomyNeoForge {

    public EasyEconomyNeoForge(ModContainer container) {
        EasyEconomy.init(container.getModInfo().getVersion().toString(), FMLPaths.CONFIGDIR.get());

        IEventBus events = NeoForge.EVENT_BUS;

        events.addListener(ServerStartedEvent.class, event -> {
            EasyEconomy.onServerStarted(event.getServer());

            // Register player balance placeholder
            if (EasyEconomy.EasyEconomyInitialized) {
                BalancePlaceholder.register();
            }
        });

        events.addListener(RegisterCommandsEvent.class, event -> EasyEconomy.registerCommands(event.getDispatcher()));

        events.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EasyEconomy.onPlayerJoin(player);
            }
        });

        events.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EasyEconomy.onPlayerLeave(player);
            }
        });

        events.addListener(ServerStoppedEvent.class, _ -> EasyEconomy.onServerStopped());
    }
}
