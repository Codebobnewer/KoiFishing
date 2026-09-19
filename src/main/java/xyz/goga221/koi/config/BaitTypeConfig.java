package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.BaitTypePool;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Loads/saves {@code bait-types.yml} into a {@link BaitTypePool} - see {@link RodTierConfig} for
 * the same pattern applied to rod tiers.
 */
public class BaitTypeConfig {

    private final File file;

    public BaitTypeConfig() {
        this.file = new File(KoiPlugin.getInstance().getDataFolder(), "bait-types.yml");
        if (!file.exists()) {
            KoiPlugin.getInstance().saveResource("bait-types.yml", false);
        }
    }

    public void load(BaitTypePool pool) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection typesSection = yaml.getConfigurationSection("types");
        if (typesSection == null) {
            return;
        }

        for (String id : typesSection.getKeys(false)) {
            ConfigurationSection section = typesSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            ConfigLoadSupport.warnIfMissingVulcanItem(id, "Bait type");

            BaitType type = new BaitType(id);
            type.setCraftMaterial(ConfigLoadSupport.parseEnum(Material.class,
                    section.getString("craft-material"), null, id, "craft-material"));
            type.setPotency(section.getDouble("potency", 1.0));
            for (String rarityName : section.getStringList("favored-rarities")) {
                FishRarity rarity = ConfigLoadSupport.parseEnum(FishRarity.class, rarityName, null, id, "favored rarity");
                if (rarity != null) {
                    type.getFavoredRarities().add(rarity);
                }
            }

            pool.addType(type);
        }
    }

    public void save(BaitTypePool pool) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (BaitType type : pool.getTypes()) {
            String path = "types." + type.getId();
            if (type.getCraftMaterial() != null) {
                yaml.set(path + ".craft-material", type.getCraftMaterial().name());
            }
            yaml.set(path + ".potency", type.getPotency());
            yaml.set(path + ".favored-rarities", type.getFavoredRarities().stream().map(Enum::name).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to save bait-types.yml", e);
        }
    }
}
