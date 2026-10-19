package com.sumutiu.easyeconomy.fabric;

import com.sumutiu.easyeconomy.EasyEconomy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint (see fabric.mod.json): passes the Fabric API events to the common code.
 * Only Fabric creates it, from the name in fabric.mod.json, so IntelliJ thinks it is never used.
 */
@SuppressWarnings("unused")
public class EasyEconomyFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        EasyEconomy.init(loader.getModContainer(EasyEconomy.MOD_ID)
                        .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            EasyEconomy.onServerStarted(server);

            // Register player balance placeholder
            if (EasyEconomy.EasyEconomyInitialized) {
                BalancePlaceholder.register();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> EasyEconomy.registerCommands(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> EasyEconomy.onPlayerJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) -> EasyEconomy.onPlayerLeave(handler.getPlayer()));

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> EasyEconomy.onServerStopped());
    }
}
