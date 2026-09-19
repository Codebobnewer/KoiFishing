package xyz.goga221.koi;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import xyz.goga221.koi.command.KoiCommand;
import xyz.goga221.koi.config.BaitTypeConfig;
import xyz.goga221.koi.config.ConfigManager;
import xyz.goga221.koi.config.FishPoolConfig;
import xyz.goga221.koi.config.RodTierConfig;
import xyz.goga221.koi.config.SeaCreatureConfig;
import xyz.goga221.koi.data.DatabaseManager;
import xyz.goga221.koi.fishing.FishManager;
import xyz.goga221.koi.fishing.SeaCreaturePool;
import xyz.goga221.koi.fishing.TycheIntegration;
import xyz.goga221.koi.item.BaitItems;
import xyz.goga221.koi.item.BaitTypePool;
import xyz.goga221.koi.item.RodItems;
import xyz.goga221.koi.item.RodTierPool;
import xyz.goga221.koi.listener.FishingListener;
import xyz.goga221.koi.listener.RodInteractListener;
import xyz.goga221.koi.listener.SeaCreatureListener;
import xyz.goga221.koi.menu.ChatInputPrompt;
import xyz.goga221.koi.tournament.ChronosIntegration;
import xyz.goga221.koi.tournament.TournamentManager;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.tyro.vulcan.Vulcan;
import xyz.tyro.vulcan.api.VulcanAPI;

/**
 * Main plugin class and static service locator - every shared subsystem is wired up once in
 * {@link #init(KoiPlugin)} and reachable afterward through the static accessors below, per the
 * network's static-locator convention (mirrors e.g. Convo.getScheduler()). The static entry point
 * here is wiring/lifecycle only - business logic still lives in the subsystem classes themselves.
 */
public final class KoiPlugin extends JavaPlugin {

    private static KoiPlugin instance;
    private static VulcanAPI vulcanApi;
    private static TaskScheduler scheduler;
    private static ConfigManager configManager;
    private static FishPoolConfig fishPoolConfig;
    private static RodTierConfig rodTierConfig;
    private static RodTierPool rodTierPool;
    private static BaitTypeConfig baitTypeConfig;
    private static BaitTypePool baitTypePool;
    private static SeaCreatureConfig seaCreatureConfig;
    private static SeaCreaturePool seaCreaturePool;
    private static DatabaseManager databaseManager;
    private static FishManager fishManager;
    private static TournamentManager tournamentManager;
    private static ChatInputPrompt chatInputPrompt;

    @Override
    public void onEnable() {
        init(this);

        getServer().getPluginManager().registerEvents(new FishingListener(), this);
        getServer().getPluginManager().registerEvents(new RodInteractListener(), this);
        getServer().getPluginManager().registerEvents(new SeaCreatureListener(), this);

        RodItems.registerRecipes(this);
        BaitItems.registerRecipes(this);

        // Must happen synchronously, inline here - Folia's CommandAPI integration refuses to
        // register commands once the server is done starting, which includes anything scheduled
        // even one tick later (e.g. via the global region scheduler). CommandAPI runs as its own
        // separate plugin (see pom.xml/plugin.yml - it's `provided` scope here, not shaded, so we
        // never call CommandAPI.onLoad/onEnable ourselves); `depend: [CommandAPI]` guarantees its
        // onEnable has already finished by the time ours runs.
        new KoiCommand().register();
    }

    private static void init(KoiPlugin plugin) {
        instance = plugin;
        // depend: [CommandAPI, Vulcan] in plugin.yml guarantees Vulcan has already finished its
        // own onEnable (and thus populated its api field) by the time ours runs - unlike
        // Chronos, this is a hard requirement: Koi won't even load if Vulcan is missing.
        vulcanApi = ((Vulcan) plugin.getServer().getPluginManager().getPlugin("Vulcan")).getApi();
        scheduler = UniversalScheduler.getScheduler(plugin);

        configManager = new ConfigManager();
        rodTierConfig = new RodTierConfig();
        rodTierPool = new RodTierPool();
        rodTierPool.reload();
        baitTypeConfig = new BaitTypeConfig();
        baitTypePool = new BaitTypePool();
        baitTypePool.reload();
        seaCreatureConfig = new SeaCreatureConfig();
        seaCreaturePool = new SeaCreaturePool();
        seaCreaturePool.reload();
        databaseManager = new DatabaseManager();
        fishPoolConfig = new FishPoolConfig();
        fishManager = new FishManager();
        fishManager.reload();
        // softdepend: [Chronos, Tyche] in plugin.yml guarantees Tyche has already finished its own
        // onEnable (and thus initialized its mob-file storage) by now, if it's installed at all -
        // this is a no-op that leaves sea creatures on their vanilla entityType fallback if not.
        TycheIntegration.tryEnable().ifPresent(fishManager::attachTycheIntegration);
        tournamentManager = new TournamentManager();
        // softdepend: [Chronos] in plugin.yml guarantees Chronos has already finished its own
        // onEnable (and thus registered its service) by now, if it's installed at all - this is
        // a no-op that leaves tournament scheduling disabled if it isn't.
        ChronosIntegration.tryEnable().ifPresent(tournamentManager::attachChronosIntegration);

        chatInputPrompt = new ChatInputPrompt();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        instance = null;
        vulcanApi = null;
        scheduler = null;
        configManager = null;
        rodTierConfig = null;
        rodTierPool = null;
        baitTypeConfig = null;
        baitTypePool = null;
        seaCreatureConfig = null;
        seaCreaturePool = null;
        fishPoolConfig = null;
        databaseManager = null;
        fishManager = null;
        tournamentManager = null;
        chatInputPrompt = null;
    }

    public static KoiPlugin getInstance() {
        return instance;
    }

    public static VulcanAPI getVulcanApi() {
        return vulcanApi;
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static FishPoolConfig getFishPoolConfig() {
        return fishPoolConfig;
    }

    public static RodTierConfig getRodTierConfig() {
        return rodTierConfig;
    }

    public static RodTierPool getRodTierPool() {
        return rodTierPool;
    }

    public static BaitTypeConfig getBaitTypeConfig() {
        return baitTypeConfig;
    }

    public static BaitTypePool getBaitTypePool() {
        return baitTypePool;
    }

    public static SeaCreatureConfig getSeaCreatureConfig() {
        return seaCreatureConfig;
    }

    public static SeaCreaturePool getSeaCreaturePool() {
        return seaCreaturePool;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static FishManager getFishManager() {
        return fishManager;
    }

    public static TournamentManager getTournamentManager() {
        return tournamentManager;
    }

    public static ChatInputPrompt getChatInputPrompt() {
        return chatInputPrompt;
    }
}
