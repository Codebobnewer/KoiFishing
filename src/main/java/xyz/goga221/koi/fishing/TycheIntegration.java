package xyz.goga221.koi.fishing;

import org.bukkit.Location;
import org.bukkit.entity.Mob;
import xyz.goga221.koi.KoiPlugin;
import xyz.lordrektor.tyche.processes.mob.MobSpawner;
import xyz.lordrektor.tyche.processes.mob.MobYamlSerializerDeserializer;

import java.util.Optional;

/**
 * Same soft-dependency confinement pattern as
 * {@link xyz.goga221.koi.tournament.ChronosIntegration} - see there for the full rationale. One
 * difference: Tyche has no Bukkit-services-manager registration step to race (its static mob
 * lookups are live as soon as its classes load), so {@link #tryEnable} only guards against
 * {@link LinkageError}, not a Chronos-style "registered but not yet initialized" exception.
 */
public final class TycheIntegration {

    private TycheIntegration() {
    }

    public static Optional<TycheIntegration> tryEnable() {
        if (!isPluginPresent()) {
            return Optional.empty();
        }
        try {
            MobYamlSerializerDeserializer.getList();
            return Optional.of(new TycheIntegration());
        } catch (LinkageError e) {
            // See ChronosIntegration.tryEnable's matching catch for why this can happen even
            // though Tyche is registered with the server.
            KoiPlugin.getInstance().getLogger().warning("Tyche was detected but its classes could not "
                    + "be loaded (" + e + ") - Tyche-backed sea creatures will fall back to their "
                    + "vanilla entity type. Try rebuilding/redeploying Koi.");
            return Optional.empty();
        }
    }

    /**
     * Admin-facing existence check for {@code sea-creatures.yml} validation - doesn't require a
     * live {@link TycheIntegration} instance, since config loading happens well before
     * {@link KoiPlugin#onEnable} finishes wiring the rest of Koi's subsystems together.
     */
    public static boolean mobExists(String mobId) {
        if (!isPluginPresent()) {
            return false;
        }
        try {
            return MobYamlSerializerDeserializer.exists(mobId);
        } catch (LinkageError e) {
            return false;
        }
    }

    private static boolean isPluginPresent() {
        return KoiPlugin.getInstance().getServer().getPluginManager().getPlugin("Tyche") != null;
    }

    /**
     * Spawns one of Tyche's admin-authored mobs (attributes/equipment/AI/loot all come from its
     * own profile - see Tyche's in-game mob editor) at the given location.
     */
    public Optional<Mob> summon(String mobId, Location location) {
        return Optional.ofNullable(MobSpawner.summon(mobId, location));
    }
}
