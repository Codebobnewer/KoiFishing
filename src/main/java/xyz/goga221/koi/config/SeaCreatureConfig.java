package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.fishing.SeaCreature;
import xyz.goga221.koi.fishing.SeaCreaturePool;
import xyz.goga221.koi.fishing.TycheIntegration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Loads/saves {@code sea-creatures.yml} into a {@link SeaCreaturePool}. A sea creature has no
 * Vulcan item of its own - its appearance/loot come from its Tyche mob (see
 * {@link xyz.goga221.koi.fishing.TycheIntegration}); this file stores the data that drives the
 * spawn roll, the reel-in stage's difficulty ({@code rarity}), and the kill stage that follows it.
 */
public class SeaCreatureConfig {

    private final File file;

    public SeaCreatureConfig() {
        this.file = new File(KoiPlugin.getInstance().getDataFolder(), "sea-creatures.yml");
        if (!file.exists()) {
            KoiPlugin.getInstance().saveResource("sea-creatures.yml", false);
        }
    }

    public void load(SeaCreaturePool pool) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection creaturesSection = yaml.getConfigurationSection("creatures");
        if (creaturesSection == null) {
            return;
        }

        for (String id : creaturesSection.getKeys(false)) {
            ConfigurationSection section = creaturesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            String tycheMobId = section.getString("tyche-mob-id");
            if (tycheMobId == null || tycheMobId.isBlank()) {
                KoiPlugin.getInstance().getLogger().warning("Skipping sea creature '" + id
                        + "' in sea-creatures.yml - it has no tyche-mob-id set.");
                continue;
            }
            if (!TycheIntegration.mobExists(tycheMobId)) {
                KoiPlugin.getInstance().getLogger().warning("Sea creature '" + id
                        + "' references Tyche mob '" + tycheMobId
                        + "', which doesn't exist (or Tyche isn't installed) - it won't be rollable "
                        + "until that's fixed, but stays loaded in case Tyche comes back.");
            }

            try {
                EntityType entityType = EntityType.valueOf(section.getString("entity-type", "DROWNED").toUpperCase(Locale.ROOT));
                SeaCreature creature = new SeaCreature(id, tycheMobId, entityType);
                creature.setRarity(ConfigLoadSupport.parseEnum(FishRarity.class, section.getString("rarity"), FishRarity.COMMON, id, "rarity"));
                creature.setMaxHealth(section.getDouble("max-health", 20.0));
                creature.setSpawnWeight(section.getDouble("spawn-weight", 1.0));
                creature.setDespawnSeconds(section.getInt("despawn-seconds", 30));
                creature.setCatchSound(ConfigLoadSupport.parseCatchSound(section, id, "sea creature"));
                pool.addCreature(creature);
            } catch (IllegalArgumentException e) {
                KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Skipping invalid sea creature '" + id + "' in sea-creatures.yml", e);
            }
        }
    }

    public void save(SeaCreaturePool pool) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (SeaCreature creature : pool.getCreatures()) {
            String path = "creatures." + creature.getId();
            yaml.set(path + ".tyche-mob-id", creature.getTycheMobId());
            yaml.set(path + ".entity-type", creature.getEntityType().name());
            yaml.set(path + ".rarity", creature.getRarity().name());
            yaml.set(path + ".max-health", creature.getMaxHealth());
            yaml.set(path + ".spawn-weight", creature.getSpawnWeight());
            yaml.set(path + ".despawn-seconds", creature.getDespawnSeconds());
            if (creature.getCatchSound() != null) {
                yaml.set(path + ".catch-sound", ConfigLoadSupport.soundToConfigName(creature.getCatchSound()));
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to save sea-creatures.yml", e);
        }
    }
}
