package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Loads and exposes {@code config.yml} values: per-world enable state and reel-in minigame
 * tuning knobs.
 */
public class ConfigManager {

    public ConfigManager() {
        KoiPlugin.getInstance().saveDefaultConfig();
    }

    public void reload() {
        KoiPlugin.getInstance().reloadConfig();
    }

    private FileConfiguration config() {
        return KoiPlugin.getInstance().getConfig();
    }

    public boolean isWorldEnabled(String worldName) {
        return !disabledWorlds().contains(worldName.toLowerCase(Locale.ROOT));
    }

    public void setWorldEnabled(String worldName, boolean enabled) {
        List<String> disabled = disabledWorlds();
        String key = worldName.toLowerCase(Locale.ROOT);
        if (enabled) {
            disabled.remove(key);
        } else if (!disabled.contains(key)) {
            disabled.add(key);
        }
        config().set("fishing.disabled-worlds", disabled);
        KoiPlugin.getInstance().saveConfig();
    }

    private List<String> disabledWorlds() {
        return config().getStringList("fishing.disabled-worlds").stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public long getTickPeriodTicks() {
        return config().getLong("fishing.minigame.tick-period-ticks", 2L);
    }

    public String getDatabaseFileName() {
        return config().getString("database.file", "koi.db");
    }

    public Sound getDefaultCatchSound() {
        String name = config().getString("fishing.default-catch-sound", "ENTITY_PLAYER_LEVELUP");
        Sound sound = ConfigLoadSupport.resolveSound(name);
        return sound != null ? sound : Sound.ENTITY_PLAYER_LEVELUP;
    }

    /**
     * Whether vanilla treasure/junk entries ({@link xyz.goga221.koi.fishing.FishSpecies#isVanillaLoot()})
     * are rolled at all. Off by default - flip it and run {@code /koi reload} to pick it up.
     */
    public boolean isVanillaLootEnabled() {
        return config().getBoolean("fishing.vanilla-loot-enabled", false);
    }

    /**
     * Odds (0.0-1.0) that a bite is a {@link xyz.goga221.koi.fishing.SeaCreature} encounter
     * instead of a normal fish - see {@link xyz.goga221.koi.fishing.FishManager#handleBite}.
     */
    public double getSeaCreatureChance() {
        return config().getDouble("fishing.sea-creature-chance", 0.0);
    }
}
