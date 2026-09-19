package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.item.RodTier;
import xyz.goga221.koi.item.RodTierPool;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Loads/saves {@code rod-tiers.yml} into a {@link RodTierPool}. Each tier's actual ItemStack is
 * a Vulcan item sharing its id (see {@link xyz.goga221.koi.item.RodItems}); this file only stores
 * the data that drives hook timing, rarity boost, and the optional upgrade recipe.
 */
public class RodTierConfig {

    private final File file;

    public RodTierConfig() {
        this.file = new File(KoiPlugin.getInstance().getDataFolder(), "rod-tiers.yml");
        if (!file.exists()) {
            KoiPlugin.getInstance().saveResource("rod-tiers.yml", false);
        }
    }

    public void load(RodTierPool pool) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        if (tiersSection == null) {
            return;
        }

        for (String id : tiersSection.getKeys(false)) {
            ConfigurationSection section = tiersSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            ConfigLoadSupport.warnIfMissingVulcanItem(id, "Rod tier");

            RodTier tier = new RodTier(id);
            tier.setRarityBoost(section.getDouble("rarity-boost", 0.0));
            tier.setMinHookTicks(section.getInt("min-hook-ticks", 100));
            tier.setMaxHookTicks(section.getInt("max-hook-ticks", 600));
            tier.setUpgradesFromId(section.getString("upgrades-from"));

            tier.setUpgradeMaterial(ConfigLoadSupport.parseEnum(Material.class,
                    section.getString("upgrade-material"), null, id, "upgrade-material"));

            pool.addTier(tier);
        }
    }

    public void save(RodTierPool pool) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RodTier tier : pool.getTiers()) {
            String path = "tiers." + tier.getId();
            yaml.set(path + ".rarity-boost", tier.getRarityBoost());
            yaml.set(path + ".min-hook-ticks", tier.getMinHookTicks());
            yaml.set(path + ".max-hook-ticks", tier.getMaxHookTicks());
            if (tier.getUpgradesFromId() != null) {
                yaml.set(path + ".upgrades-from", tier.getUpgradesFromId());
            }
            if (tier.getUpgradeMaterial() != null) {
                yaml.set(path + ".upgrade-material", tier.getUpgradeMaterial().name());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to save rod-tiers.yml", e);
        }
    }
}
