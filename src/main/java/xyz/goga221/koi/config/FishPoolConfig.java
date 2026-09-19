package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishPool;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.fishing.LootCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Loads/saves {@code fish-pool.yml} into a {@link FishPool}. Backs the {@code /koi config}
 * fish editor menu - every admin edit there is persisted back through {@link #save}. Each
 * entry's actual ItemStack is a Vulcan item sharing its id (see
 * {@link xyz.goga221.koi.fishing.FishItems}); this file only stores the data that drives the
 * catch roll and the reel minigame, never appearance.
 */
public class FishPoolConfig {

    private final File file;

    public FishPoolConfig() {
        this.file = new File(KoiPlugin.getInstance().getDataFolder(), "fish-pool.yml");
        if (!file.exists()) {
            KoiPlugin.getInstance().saveResource("fish-pool.yml", false);
        }
    }

    public void load(FishPool pool) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection speciesSection = yaml.getConfigurationSection("species");
        if (speciesSection == null) {
            return;
        }

        for (String id : speciesSection.getKeys(false)) {
            ConfigurationSection section = speciesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            boolean vanillaLoot = section.getBoolean("vanilla-loot", false);
            if (vanillaLoot && !KoiPlugin.getConfigManager().isVanillaLootEnabled()) {
                continue;
            }

            ConfigLoadSupport.warnIfMissingVulcanItem(id, "Fish species");

            try {
                FishSpecies species = new FishSpecies(id, section.getDouble("drop-weight", 1.0));
                species.setVanillaLoot(vanillaLoot);
                species.setCategory(LootCategory.valueOf(section.getString("category", "FISH").toUpperCase(Locale.ROOT)));
                species.setCatchSound(ConfigLoadSupport.parseCatchSound(section, id, "fish"));
                species.getFavoredBaitIds().addAll(section.getStringList("favored-baits"));
                species.getFavoredBiomes().addAll(section.getStringList("favored-biomes"));
                species.getWhitelistedBiomes().addAll(section.getStringList("whitelisted-biomes"));
                pool.addSpecies(species);
            } catch (IllegalArgumentException e) {
                KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Skipping invalid fish species '" + id + "' in fish-pool.yml", e);
            }
        }
    }

    public void save(FishPool pool) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (FishSpecies species : pool.getSpecies()) {
            String path = "species." + species.getId();
            yaml.set(path + ".drop-weight", species.getDropWeight());
            yaml.set(path + ".category", species.getCategory().name());
            yaml.set(path + ".vanilla-loot", species.isVanillaLoot());
            if (species.getCatchSound() != null) {
                yaml.set(path + ".catch-sound", ConfigLoadSupport.soundToConfigName(species.getCatchSound()));
            }
            yaml.set(path + ".favored-baits", new ArrayList<>(species.getFavoredBaitIds()));
            yaml.set(path + ".favored-biomes", new ArrayList<>(species.getFavoredBiomes()));
            yaml.set(path + ".whitelisted-biomes", new ArrayList<>(species.getWhitelistedBiomes()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to save fish-pool.yml", e);
        }
    }
}
