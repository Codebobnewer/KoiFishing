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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the custom fishing flow: rolls the caught species and drives the reel-in
 * click-counting minigame via {@link ReelSessionManager}. Once hooked the catch can't escape -
 * the player finishes clicking whenever they're ready and always lands the fish.
 */
@Getter
public class FishManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KoiPlugin plugin;
    private final FishPool pool = new FishPool();
    private final ReelSessionManager reelSessionManager;
    private final Map<UUID, FishRarity> forcedRarities = new ConcurrentHashMap<>();

    public FishManager(KoiPlugin plugin) {
        this.plugin = plugin;
        this.reelSessionManager = new ReelSessionManager(plugin);
    }

    public void reload() {
        pool.clear();
        plugin.getFishPoolConfig().load(pool);
    }

    public boolean isWorldEnabled(World world) {
        return plugin.getConfigManager().isWorldEnabled(world.getName());
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

    public void handleCast(Player player, FishHook hook) {
        ItemStack rodItem = player.getInventory().getItemInMainHand();
        RodTier rodTier = RodItems.tierOf(rodItem);
        if (rodTier == null) {
            rodTier = RodTier.WOOD;
        }

        hook.setWaitTime(rodTier.getMinHookTicks(), rodTier.getMaxHookTicks());

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
            context = new CastContext(RodTier.WOOD, null, null);
        }

        FishRarity forcedRarity = forcedRarities.remove(player.getUniqueId());
        FishSpecies species = forcedRarity != null
                ? pool.rollWithinRarity(forcedRarity).orElse(null)
                : pool.roll(context.getBait(), context.getBiomeKey(), context.getRodTier()).orElse(null);
        if (species == null) {
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.7f);

        String baitId = context.getBait() != null ? context.getBait().name() : null;
        reelSessionManager.startSession(player, species, context.getRodTier(), baitId, hook, this::resolveSession);
    }

    public void pulseReel(Player player) {
        reelSessionManager.pulse(player, this::resolveSession);
    }

    /**
     * Ends a player's active reel session without landing the fish - they switched away from
     * their rod (or dropped it) mid-reel.
     */
    public void cancelSession(Player player) {
        reelSessionManager.cancel(player).ifPresent(session -> {
            if (session.getHook() != null && !session.getHook().isDead()) {
                session.getHook().remove();
            }
            player.sendMessage(MM.deserialize("<red>You stopped reeling - the fish gets away.</red>"));
        });
    }

    private void resolveSession(Player player, ReelSession session) {
        if (session.getHook() != null && !session.getHook().isDead()) {
            session.getHook().remove();
        }

        FishSpecies species = session.getSpecies();
        ItemStack item = FishItems.create(species);
        player.getInventory().addItem(item);

        FishCatch catchRecord = new FishCatch(player.getUniqueId(), species.getId(),
                species.getRarity(), session.getRodTier(), session.getBaitId(),
                System.currentTimeMillis());
        // SQLite I/O off the region thread - nothing here needs the result back.
        plugin.getScheduler().runTaskAsynchronously(() ->
                plugin.getDatabaseManager().getCatchRepository().record(catchRecord));

        playCatchEffects(player, species);

        String verb = switch (species.getCategory()) {
            case FISH -> "reeled in a";
            case TREASURE -> "found";
            case JUNK -> "fished up";
        };
        // species.getDisplayName() is admin-set (fish editor chat wizard) and broadcast to
        // every player who catches this species - never splice it into the MiniMessage
        // template directly, or a malicious display name could smuggle in <click>/<hover> tags
        // rendered in other players' chat. Placeholder.unparsed treats it as literal text.
        player.sendMessage(MM.deserialize("<green>You " + verb + " <yellow><fish_name></yellow>! <gray>(" + species.getRarity().name() + ")</gray></green>",
                Placeholder.unparsed("fish_name", species.getDisplayName())));

        plugin.getTournamentManager().recordCatch(player, species.getRarity());
    }

    private void playCatchEffects(Player player, FishSpecies species) {
        Sound sound = species.getCatchSound() != null ? species.getCatchSound() : plugin.getConfigManager().getDefaultCatchSound();
        int ordinal = species.getRarity().ordinal();

        player.playSound(player.getLocation(), sound, 0.8f + ordinal * 0.15f, 0.9f + ordinal * 0.05f);

        Particle.DustOptions dust = new Particle.DustOptions(species.getRarity().getParticleColor(), 1.2f + ordinal * 0.2f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation(), 10 + ordinal * 15,
                0.4, 0.2, 0.4, 0.0, dust);
    }
}
