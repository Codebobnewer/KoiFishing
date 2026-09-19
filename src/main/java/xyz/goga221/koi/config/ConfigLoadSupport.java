package xyz.goga221.koi.config;

import xyz.goga221.koi.KoiPlugin;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Shared load-time checks used by every catalog config ({@link FishPoolConfig},
 * {@link RodTierConfig}, {@link BaitTypeConfig}, {@link SeaCreatureConfig}) - each entry's id
 * doubles as a Vulcan item id, and each optionally carries a {@code catch-sound}.
 */
final class ConfigLoadSupport {

    private ConfigLoadSupport() {
    }

    static void warnIfMissingVulcanItem(String id, String noun) {
        if (!KoiPlugin.getVulcanApi().exists(id)) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING, noun + " '" + id
                    + "' has no matching Vulcan item yet - it won't be usable until you run "
                    + "/v item create " + id + " in Vulcan.");
        }
    }

    static Sound parseCatchSound(ConfigurationSection section, String id, String noun) {
        String soundName = section.getString("catch-sound");
        if (soundName == null) {
            return null;
        }
        Sound sound = resolveSound(soundName);
        if (sound == null) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING,
                    "Unknown catch-sound '" + soundName + "' for " + noun.toLowerCase(Locale.ROOT) + " '" + id + "'");
        }
        return sound;
    }

    /**
     * Resolves a legacy enum-style sound name (e.g. {@code ENTITY_PLAYER_LEVELUP}, the format
     * config.yml/fish-pool.yml/sea-creatures.yml use) via {@link Registry#SOUNDS} instead of the
     * deprecated {@link Sound#valueOf(String)}.
     */
    static Sound resolveSound(String name) {
        Key key = Key.key(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        return Registry.SOUNDS.get(key);
    }

    /** Inverse of {@link #resolveSound(String)}, for writing a {@link Sound} back to config. */
    static String soundToConfigName(Sound sound) {
        return Registry.SOUNDS.getKey(sound).value().toUpperCase(Locale.ROOT).replace('.', '_');
    }

    /**
     * Parses {@code value} as an {@code enumType} constant, warning and returning
     * {@code fallback} on a bad or missing value instead of throwing.
     */
    static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, E fallback, String id, String fieldName) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            KoiPlugin.getInstance().getLogger().log(Level.WARNING,
                    "Unknown " + fieldName + " '" + value + "' for '" + id + "'");
            return fallback;
        }
    }
}
