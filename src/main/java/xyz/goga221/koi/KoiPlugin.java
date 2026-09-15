package xyz.goga221.koi;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import xyz.goga221.koi.command.KoiCommand;
import xyz.goga221.koi.config.ConfigManager;
import xyz.goga221.koi.config.FishPoolConfig;
import xyz.goga221.koi.data.DatabaseManager;
import xyz.goga221.koi.fishing.FishManager;
import xyz.goga221.koi.item.BaitItems;
import xyz.goga221.koi.item.RodItems;
import xyz.goga221.koi.listener.FishingListener;
import xyz.goga221.koi.listener.RodInteractListener;
import xyz.goga221.koi.menu.ChatInputPrompt;
import xyz.goga221.koi.tournament.TournamentManager;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class KoiPlugin extends JavaPlugin {

    private TaskScheduler scheduler;
    private ConfigManager configManager;
    private FishPoolConfig fishPoolConfig;
    private DatabaseManager databaseManager;
    private FishManager fishManager;
    private TournamentManager tournamentManager;
    private ChatInputPrompt chatInputPrompt;

    @Override
    public void onEnable() {
        scheduler = UniversalScheduler.getScheduler(this);

        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        fishPoolConfig = new FishPoolConfig(this);
        fishManager = new FishManager(this);
        fishManager.reload();
        tournamentManager = new TournamentManager(this);

        chatInputPrompt = new ChatInputPrompt(this);

        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        getServer().getPluginManager().registerEvents(new RodInteractListener(this), this);

        RodItems.registerRecipes(this);
        BaitItems.registerRecipes(this);

        new KoiCommand(this).register();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
