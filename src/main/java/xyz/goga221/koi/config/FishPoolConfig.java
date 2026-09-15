package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishPool;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.fishing.LootCategory;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Loads/saves {@code fish-pool.yml} into a {@link FishPool}. Backs the {@code /koi config}
 * fish editor menu - every admin edit there is persisted back through {@link #save}.
 */
public class FishPoolConfig {

    private final KoiPlugin plugin;
    private final File file;

    public FishPoolConfig(KoiPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fish-pool.yml");
        if (!file.exists()) {
            plugin.saveResource("fish-pool.yml", false);
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
            if (vanillaLoot && !plugin.getConfigManager().isVanillaLootEnabled()) {
                continue;
            }

            try {
                FishSpecies species = new FishSpecies(
                        id,
                        section.getString("display-name", id),
                        FishRarity.valueOf(section.getString("rarity", "COMMON").toUpperCase(Locale.ROOT)),
                        Material.valueOf(section.getString("material", "COD").toUpperCase(Locale.ROOT)),
                        section.getInt("custom-model-data", 0),
                        section.getDouble("drop-weight", 1.0)
                );
                species.setVanillaLoot(vanillaLoot);
                species.setCategory(LootCategory.valueOf(section.getString("category", "FISH").toUpperCase(Locale.ROOT)));
                String soundName = section.getString("catch-sound");
                if (soundName != null) {
                    try {
                        species.setCatchSound(Sound.valueOf(soundName.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().log(Level.WARNING, "Unknown catch-sound '" + soundName + "' for fish '" + id + "'");
                    }
                }
                species.getFavoredBaitIds().addAll(section.getStringList("favored-baits"));
                species.getFavoredBiomes().addAll(section.getStringList("favored-biomes"));
                pool.addSpecies(species);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid fish species '" + id + "' in fish-pool.yml", e);
            }
        }
    }

    public void save(FishPool pool) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (FishSpecies species : pool.getSpecies()) {
            String path = "species." + species.getId();
            yaml.set(path + ".display-name", species.getDisplayName());
            yaml.set(path + ".rarity", species.getRarity().name());
            yaml.set(path + ".material", species.getMaterial().name());
            yaml.set(path + ".custom-model-data", species.getCustomModelData());
            yaml.set(path + ".drop-weight", species.getDropWeight());
            yaml.set(path + ".category", species.getCategory().name());
            yaml.set(path + ".vanilla-loot", species.isVanillaLoot());
            if (species.getCatchSound() != null) {
                yaml.set(path + ".catch-sound", species.getCatchSound().name());
            }
            yaml.set(path + ".favored-baits", new ArrayList<>(species.getFavoredBaitIds()));
            yaml.set(path + ".favored-biomes", new ArrayList<>(species.getFavoredBiomes()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save fish-pool.yml", e);
        }
    }
}
