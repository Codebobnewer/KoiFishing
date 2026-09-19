package xyz.goga221.koi.fishing;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;

/**
 * An admin-defined encounter that can occur instead of a normal bite - see
 * {@link xyz.goga221.koi.fishing.FishManager#handleBite}. Two stages: first the player reels it
 * in exactly like a fish/treasure/junk catch ({@link #rarity} drives the reel minigame's
 * difficulty the same way {@link FishSpecies#getRarity()} does); landing that spawns a real mob
 * near the player, which must then be killed within {@link #despawnSeconds} before it escapes -
 * see {@link FishManager#resolveSession}.
 * <p>
 * Unlike {@link FishSpecies}, a sea creature has no Vulcan item of its own: it's identified by
 * its own free-form {@link #id}, and its appearance/attributes/equipment/AI/loot all come from
 * {@link #tycheMobId} - a Tyche-authored mob (see {@link TycheIntegration}), summoned by
 * {@link FishManager#spawnSeaCreature}. {@link SeaCreaturePool#roll()} only excludes a creature
 * when Tyche is installed but the specific mob id doesn't resolve (a broken reference) - see
 * {@link TycheIntegration#canSpawn}; with Tyche absent entirely, or once it drops out between the
 * roll and the actual spawn, {@link #entityType} is the vanilla-mob fallback used instead.
 * Mutable and admin-editable at runtime via the sea creature editor menu, persisted to
 * {@code sea-creatures.yml}.
 */
@Getter
@Setter
public class SeaCreature implements Catchable {

    private String id;
    private String tycheMobId;
    private EntityType entityType;
    private FishRarity rarity = FishRarity.COMMON;
    private double maxHealth = 20.0;
    private double spawnWeight = 1.0;
    private int despawnSeconds = 30;
    private Sound catchSound;

    public SeaCreature(String id, String tycheMobId, EntityType entityType) {
        this.id = id;
        this.tycheMobId = tycheMobId;
        this.entityType = entityType;
    }
}
