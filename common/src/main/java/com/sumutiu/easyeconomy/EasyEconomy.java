package com.sumutiu.easyeconomy;

import com.mojang.brigadier.CommandDispatcher;
import com.sumutiu.easyeconomy.commands.*;
import com.sumutiu.easyeconomy.config.EasyEconomyConfig;
import com.sumutiu.easyeconomy.storage.AHStorage;
import com.sumutiu.easyeconomy.storage.BankStorage;
import com.sumutiu.easyeconomy.storage.NameCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

/**
 * The mod itself, shared by Fabric and NeoForge.
 * Each loader's entrypoint (EasyEconomyFabric, EasyEconomyNeoForge) calls init() once
 * and forwards its events to the on...() methods below.
 */
public class EasyEconomy {

    public static final String MOD_ID = "easyeconomy";

    public static File STORAGE_FOLDER;
    public static File AH_FOLDER;
    public static Path CONFIG_FOLDER;
    public static Path CONFIG_FILE;

    public static volatile boolean EasyEconomyInitialized = false;

    private static String modVersion = "unknown";

    // Called by the loader entrypoint when the mod loads, with the mod version and the loader's config folder
    public static void init(String version) {
        modVersion = version;
    }

    public static String getModVersion() {
        return modVersion;
    }

    // -----------------------------
    // SERVER START (WORLD EXISTS)
    // -----------------------------
    public static void onServerStarted(MinecraftServer server) {

        long seed = server.getWorldGenSettings().options().seed();

        STORAGE_FOLDER = new File("mods/EasyEconomy_Seed_" + Long.toUnsignedString(seed) + "/Banks");
        AH_FOLDER = new File("mods/EasyEconomy_Seed_" + Long.toUnsignedString(seed) + "/AH");
        CONFIG_FOLDER = Path.of("config", "EasyEconomy_Seed_" + Long.toUnsignedString(seed));
        CONFIG_FILE = CONFIG_FOLDER.resolve("EasyEconomy.json");

        EasyEconomyConfig.load(CONFIG_FILE);

        if (initPlugin()) {
            NameCache.load();
            AHStorage.loadAll();
            EasyEconomyInitialized = true;
        } else {
            Logger(2, MOD_INIT_FAILED);
        }
    }

    // Called by the loader when the server registers its commands
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        DepositCommand.register(dispatcher);
        WithdrawCommand.register(dispatcher);
        BankCommand.register(dispatcher);
        PayCommand.register(dispatcher);
        ShopCommand.register(dispatcher);
        ShopAdminCommand.register(dispatcher);
        LeaderboardCommand.register(dispatcher);
    }

    // Player join
    public static void onPlayerJoin(ServerPlayer player) {
        if (!EasyEconomyInitialized) {
            player.connection.disconnect(
                    Component.literal(AH_NOT_INITIALIZED)
            );
            return;
        }

        UUID uuid = player.getUUID();

        // Remember the player's name so offline players show up by name
        NameCache.put(uuid, player.getName().getString());

        try {
            File playerFile = BankStorage.getPlayerFile(uuid);
            boolean isNewPlayer = !playerFile.exists();

            Long balance = BankStorage.tryGetBalance(uuid);

            if (balance == null) {
                // Existing bank file that can't be read right now; it is left untouched
                Logger(2, String.format(BANK_INIT_FAILED, uuid, "bank file could not be read"));
                PrivateMessage(player, BANK_INIT_FAILED_PRIVATE);
            } else if (isNewPlayer) {
                BankStorage.saveToFile(uuid, balance);
                Logger(0, String.format(BANK_FILE_CREATED_FOR_PLAYER, uuid));
                PrivateMessage(player, BANK_WELCOME_NEW_PLAYER);
            }

        } catch (Exception e) {
            Logger(2, String.format(BANK_INIT_FAILED, uuid, e.getMessage()));
            PrivateMessage(player, BANK_INIT_FAILED_PRIVATE);
        }
    }

    // Player quit
    public static void onPlayerLeave(ServerPlayer player) {
        if (EasyEconomyInitialized) {
            BankStorage.unloadPlayer(player.getUUID());
        }
    }

    // Server stop: drop all cached data, so a different world opened
    // in the same game session (singleplayer) starts with a clean slate
    public static void onServerStopped() {
        EasyEconomyInitialized = false;
        BankStorage.clearCache();
        AHStorage.clear();
        NameCache.clear();
    }

    // Initialize storage
    private static boolean initPlugin() {
        logAsciiBanner(
                MOD_ASCII_BANNER,
                Mod_ID + ": V" + getModVersion() + " - Because emeralds are overrated!"
        );

        boolean banksMissing = !STORAGE_FOLDER.isDirectory();
        boolean ahMissing = !AH_FOLDER.isDirectory();

        // Create each folder only if it is missing (one may exist without the other)
        if ((!banksMissing || STORAGE_FOLDER.mkdirs()) && (!ahMissing || AH_FOLDER.mkdirs())) {
            if (banksMissing || ahMissing) {
                Logger(0, MAIN_FOLDER_CREATED);
            }
            return true;
        } else {
            Logger(2, MAIN_FOLDER_CREATION_FAILED);
            return false;
        }
    }
}
