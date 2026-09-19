package xyz.goga221.koi.fishing;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.minigame.CastContext;
import xyz.goga221.koi.fishing.minigame.ReelSession;
import xyz.goga221.koi.fishing.minigame.ReelSessionManager;
import xyz.goga221.koi.item.BaitItems;
import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.RodItems;
import xyz.goga221.koi.item.RodTier;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates the custom fishing flow: rolls the caught species and drives the reel-in
 * click-counting minigame via {@link ReelSessionManager}. Once hooked the catch can't escape -
 * the player finishes clicking whenever they're ready and always lands it. A bite has a
 * configurable chance ({@code fishing.sea-creature-chance} in config.yml) to instead be a
 * {@link SeaCreature} encounter: the player reels it in through the exact same minigame as a
 * fish/treasure/junk bite (scaled by the creature's own rarity), and landing that spawns a real
 * mob (see {@link #resolveSession}/{@link #spawnSeaCreature}) that must then be killed before it
 * escapes (see {@link xyz.goga221.koi.listener.SeaCreatureListener}) to actually earn the catch.
 */
@Getter
public class FishManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FishPool pool = new FishPool();
    private final ReelSessionManager reelSessionManager = new ReelSessionManager();
    private final Map<UUID, FishRarity> forcedRarities = new ConcurrentHashMap<>();
    private final Set<UUID> forcedSeaCreatures = ConcurrentHashMap.newKeySet();
    private TycheIntegration tycheIntegration;

    /**
     * Wired from {@link KoiPlugin#onEnable} once Tyche is confirmed present - see
     * {@link TycheIntegration#tryEnable}. Left {@code null} (the default) whenever Tyche isn't
     * installed, in which case sea creatures always spawn via their vanilla {@code entityType}.
     */
    public void attachTycheIntegration(TycheIntegration tycheIntegration) {
        this.tycheIntegration = tycheIntegration;
    }

    public void reload() {
        pool.clear();
        KoiPlugin.getFishPoolConfig().load(pool);
    }

    public boolean isWorldEnabled(World world) {
        return KoiPlugin.getConfigManager().isWorldEnabled(world.getName());
    }

    public boolean hasActiveSession(UUID playerId) {
        return reelSessionManager.hasActiveSession(playerId);
    }

    /**
     * Debug helper for {@code /koi test tier}: forces the given player's next bite to be a
     * species of {@code rarity}, consumed after one catch.
     */
    public void forceNextTier(UUID playerId, FishRarity rarity) {
        forcedRarities.put(playerId, rarity);
    }

    public void clearForcedTier(UUID playerId) {
        forcedRarities.remove(playerId);
    }

    /**
     * Debug helper for {@code /koi test seacreature}: forces the given player's next bite to be
     * a sea creature encounter (skipping the {@code fishing.sea-creature-chance} roll entirely),
     * consumed after one bite. Takes priority over a forced tier if both happen to be set.
     */
    public void forceNextSeaCreature(UUID playerId) {
        forcedSeaCreatures.add(playerId);
    }

    public void clearForcedSeaCreature(UUID playerId) {
        forcedSeaCreatures.remove(playerId);
    }

    public void handleCast(Player player, FishHook hook) {
        ItemStack rodItem = player.getInventory().getItemInMainHand();
        RodTier rodTier = RodItems.tierOf(rodItem);
        if (rodTier == null) {
            // Not a Koi/Vulcan rod - leave vanilla fishing (and bait) alone entirely.
            return;
        }
        hook.setWaitTime(rodTier.getMinHookTicks(), rodTier.getMaxHookTicks());

        // Consumed here, at cast time, even though whether it pays off isn't known until the
        // bite resolves (see rollNormalBite) - deferring this to bite time let a player swap the
        // bait out of their off-hand the instant after casting, keeping its roll-weighting boost
        // (captured below into CastContext regardless) while paying nothing for it, since nothing
        // polices the off-hand during the plain wait-for-a-bite window the way an active reel
        // session's own interrupt listeners police the rod. "Per cast" has to mean "at cast time."
        ItemStack offHand = player.getInventory().getItemInOffHand();
        BaitType bait = BaitItems.typeOf(offHand);
        if (bait != null) {
            int remaining = offHand.getAmount() - 1;
            if (remaining <= 0) {
                player.getInventory().setItemInOffHand(null);
            } else {
                offHand.setAmount(remaining);
            }
        }

        String biomeKey = player.getLocation().getBlock().getBiome().getKey().toString();
        reelSessionManager.prepareCast(player.getUniqueId(), new CastContext(rodTier, bait, biomeKey));
    }

    public void handleBite(Player player, FishHook hook) {
        CastContext context = reelSessionManager.consumeCast(player.getUniqueId());
        if (context == null) {
            // No prepared cast means handleCast saw no Koi/Vulcan rod (or the biteless cast was
            // never registered, e.g. a plugin reload mid-cast) - let vanilla fishing resolve it.
            return;
        }

        // A forced sea-creature debug roll (/koi test seacreature) takes priority over a forced
        // tier, which in turn always wins over a random sea creature encounter - an admin
        // testing a specific rarity shouldn't have that overridden by a random encounter.
        boolean forcedSeaCreature = forcedSeaCreatures.remove(player.getUniqueId());
        FishRarity forcedRarity = forcedSeaCreature ? null : forcedRarities.remove(player.getUniqueId());

        if (forcedRarity == null && (forcedSeaCreature || rollsSeaCreature())) {
            SeaCreature creature = KoiPlugin.getSeaCreaturePool().roll().orElse(null);
            if (creature != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f);
                String baitId = context.getBait() != null ? context.getBait().getId() : null;
                reelSessionManager.startSession(player, creature, context.getRodTier(), baitId, hook, this::resolveSession);
                return;
            }
        }

        rollNormalBite(player, hook, context, forcedRarity);
    }

    private boolean rollsSeaCreature() {
        double chance = KoiPlugin.getConfigManager().getSeaCreatureChance();
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private void rollNormalBite(Player player, FishHook hook, CastContext context, FishRarity forcedRarity) {
        FishSpecies species = forcedRarity != null
                ? pool.rollWithinRarity(forcedRarity).orElse(null)
                : pool.roll(context.getBait(), context.getBiomeKey(), context.getRodTier()).orElse(null);
        if (species == null) {
            // Nothing rolled (empty/misconfigured pool, or every species excluded by a biome
            // whitelist here) - bait was already spent at cast time regardless (see handleCast),
            // so at least tell the player rather than leaving the bite silently do nothing.
            player.sendMessage(MM.deserialize("<gray>Nothing bites here.</gray>"));
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.7f);

        String baitId = context.getBait() != null ? context.getBait().getId() : null;
        reelSessionManager.startSession(player, species, context.getRodTier(), baitId, hook, this::resolveSession);
    }

    /**
     * Attempts the Tyche-backed spawn path for a creature with a {@link SeaCreature#getTycheMobId()}
     * set - empty if Tyche isn't installed, the creature has no Tyche mob id, or the id doesn't
     * resolve to a real Tyche mob (e.g. it was deleted in Tyche after {@code sea-creatures.yml}
     * was last edited).
     */
    private Optional<LivingEntity> spawnViaTyche(SeaCreature creature, Location location) {
        if (creature.getTycheMobId() == null || tycheIntegration == null) {
            return Optional.empty();
        }
        return tycheIntegration.summon(creature.getTycheMobId(), location)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
    }

    /**
     * Spawns a real mob at {@code location} - one of Tyche's admin-authored mobs (see
     * {@link TycheIntegration}), or its vanilla {@link SeaCreature#getEntityType()} fallback if
     * Tyche isn't installed or the mob id no longer resolves - once the player has already reeled
     * in the encounter (see {@link #resolveSession}). Tagged via {@link SeaCreatureKeys} so
     * {@link xyz.goga221.koi.listener.SeaCreatureListener} can reward a kill, and its escape is
     * scheduled after {@link SeaCreature#getDespawnSeconds()}.
     */
    private void spawnSeaCreature(Player player, Location location, SeaCreature creature, RodTier rodTier, String baitId) {
        LivingEntity livingEntity = spawnViaTyche(creature, location).orElse(null);
        boolean fromTyche = livingEntity != null;
        if (livingEntity == null) {
            Entity spawned = location.getWorld().spawnEntity(location, creature.getEntityType());
            if (!(spawned instanceof LivingEntity vanilla)) {
                spawned.remove();
                return;
            }
            livingEntity = vanilla;
        }

        if (!fromTyche) {
            // A Tyche mob profile already sets its own health - only apply Koi's own default to
            // the vanilla fallback path.
            AttributeInstance maxHealthAttribute = livingEntity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                maxHealthAttribute.setBaseValue(creature.getMaxHealth());
            }
            livingEntity.setHealth(creature.getMaxHealth());
        }

        if (livingEntity instanceof Mob mob) {
            // Keep it from despawning as "too far away" before our own escape timer fires.
            mob.setPersistent(true);
        }

        SeaCreatureKeys.tag(livingEntity, creature, rodTier, baitId);
        player.sendMessage(MM.deserialize("<red><bold>Something massive is attacking your line!</bold></red>"));

        long despawnTicks = creature.getDespawnSeconds() * 20L;
        LivingEntity spawnedEntity = livingEntity;
        KoiPlugin.getScheduler().runTaskLater(spawnedEntity, () -> {
            if (spawnedEntity.isValid() && !spawnedEntity.isDead()) {
                player.sendMessage(MM.deserialize("<gray>The creature broke free and escaped.</gray>"));
                spawnedEntity.remove();
            }
        }, despawnTicks);
    }

    public void pulseReel(Player player) {
        reelSessionManager.pulse(player, this::resolveSession);
    }

    /**
     * Ends a player's active reel session without landing the catch - they switched away from
     * their rod (or dropped it) mid-reel.
     */
    public void cancelSession(Player player) {
        reelSessionManager.cancel(player).ifPresent(session -> interruptSession(player, session));
    }

    /**
     * Ends a session that {@link ReelSessionManager} caught mid-tick rather than through a
     * dedicated event - the player died, the hook was invalidated (out of range, damage-snapped
     * line, etc.), or the rod left the held slot by some means {@link
     * xyz.goga221.koi.listener.RodInteractListener} doesn't directly see (e.g. an inventory click
     * swapping the item in the already-held slot). Also reused by {@link #cancelSession} for the
     * event-driven path so both go through the same cleanup/messaging.
     */
    private void interruptSession(Player player, ReelSession session) {
        if (session.getHook() != null && !session.getHook().isDead()) {
            session.getHook().remove();
        }
        if (!player.isOnline()) {
            return;
        }
        String message = session.getCatchable() instanceof SeaCreature
                ? "<red>You stopped reeling - the creature slips away.</red>"
                : "<red>You stopped reeling - the fish gets away.</red>";
        player.sendMessage(MM.deserialize(message));
    }

    /**
     * Landing a reel session ends the minigame - for a fish/treasure/junk bite that's the catch
     * itself (see {@link #grantCatch}); for a {@link SeaCreature} it only spawns the actual mob
     * (see {@link #spawnSeaCreature}), which the player must then kill (see {@link
     * xyz.goga221.koi.listener.SeaCreatureListener}) to earn the reward.
     */
    private void resolveSession(Player player, ReelSession session) {
        Location location = session.getHook() != null ? session.getHook().getLocation() : player.getLocation();
        if (session.getHook() != null && !session.getHook().isDead()) {
            session.getHook().remove();
        }

        if (session.getCatchable() instanceof SeaCreature creature) {
            // SQLite I/O off the region thread - nothing here needs the result back.
            KoiPlugin.getScheduler().runTaskAsynchronously(() ->
                    KoiPlugin.getDatabaseManager().getCatchRepository().recordSeaCreatureCaught(player.getUniqueId(), creature.getRarity()));
            spawnSeaCreature(player, location, creature, session.getRodTier(), session.getBaitId());
            return;
        }

        FishSpecies species = (FishSpecies) session.getCatchable();
        String verb = switch (species.getCategory()) {
            case FISH -> "reeled in a";
            case TREASURE -> "found";
            case JUNK -> "fished up";
        };
        grantCatch(player, species.getId(), species.getRarity(), species.getCatchSound(), verb,
                session.getRodTier(), session.getBaitId());
    }

    /**
     * Rewards a kill of a {@link SeaCreature} spawned from {@link #spawnSeaCreature} - called by
     * {@link xyz.goga221.koi.listener.SeaCreatureListener} once it identifies a tagged death.
     * Unlike {@link #grantCatch}, this doesn't hand over an ItemStack itself - a sea creature has
     * no Vulcan item of its own, and its actual loot (if any) comes from Tyche's own loot table
     * on the mob, dropped independently by Tyche's own death listener before Koi's ever runs.
     * Koi's role is just the catch record (leaderboard/tournament credit), effects, and message.
     */
    public void grantSeaCreatureKillReward(Player player, SeaCreature creature, RodTier rodTier, String baitId) {
        FishRarity rarity = creature.getRarity();
        FishCatch catchRecord = new FishCatch(player.getUniqueId(), creature.getId(), rarity, rodTier, baitId,
                System.currentTimeMillis(), true);
        // SQLite I/O off the region thread - nothing here needs the result back.
        KoiPlugin.getScheduler().runTaskAsynchronously(() ->
                KoiPlugin.getDatabaseManager().getCatchRepository().record(catchRecord));

        playCatchEffects(player, rarity, creature.getCatchSound());

        player.sendMessage(MM.deserialize("<green>You defeated the <name>! <gray>(" + rarity.name() + ")</gray></green>",
                Placeholder.unparsed("name", creature.getId())));

        KoiPlugin.getTournamentManager().recordCatch(player, rarity);
    }

    /**
     * Reward path for a reeled-in fish/treasure/junk catch: gives the Vulcan item, records the
     * catch, plays effects, messages the player, and reports it to the active tournament.
     */
    private void grantCatch(Player player, String vulcanId, FishRarity rarity, Sound catchSound, String verb,
                             RodTier rodTier, String baitId) {
        ItemStack item = FishItems.create(vulcanId, rarity);
        if (item == null) {
            KoiPlugin.getInstance().getLogger().warning("'" + vulcanId
                    + "' has no matching Vulcan item - author it with /v item create " + vulcanId);
            player.sendMessage(MM.deserialize("<red>Something went wrong landing that catch - let an admin know.</red>"));
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        FishCatch catchRecord = new FishCatch(player.getUniqueId(), vulcanId, rarity, rodTier, baitId,
                System.currentTimeMillis(), false);
        // SQLite I/O off the region thread - nothing here needs the result back.
        KoiPlugin.getScheduler().runTaskAsynchronously(() ->
                KoiPlugin.getDatabaseManager().getCatchRepository().record(catchRecord));

        playCatchEffects(player, rarity, catchSound);

        // The item's own display name is a built Vulcan Component (already carries Vulcan's own
        // color/formatting), so it's inserted with Placeholder.component rather than re-parsed
        // as MiniMessage text - and since it's a rendered Component rather than raw admin input,
        // there's no injection risk the way there would be with a raw string placeholder.
        Component name = item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.text(vulcanId);
        player.sendMessage(MM.deserialize("<green>You " + verb + " <fish_name>! <gray>(" + rarity.name() + ")</gray></green>",
                Placeholder.component("fish_name", name)));

        KoiPlugin.getTournamentManager().recordCatch(player, rarity);
    }

    private void playCatchEffects(Player player, FishRarity rarity, Sound catchSound) {
        Sound sound = catchSound != null ? catchSound : KoiPlugin.getConfigManager().getDefaultCatchSound();
        int ordinal = rarity.ordinal();

        player.playSound(player.getLocation(), sound, 0.8f + ordinal * 0.15f, 0.9f + ordinal * 0.05f);

        Particle.DustOptions dust = new Particle.DustOptions(rarity.getParticleColor(), 1.2f + ordinal * 0.2f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation(), 10 + ordinal * 15,
                0.4, 0.2, 0.4, 0.0, dust);
    }
}
