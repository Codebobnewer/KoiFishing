package xyz.goga221.koi.fishing;

import org.bukkit.Sound;

/**
 * Anything {@link xyz.goga221.koi.fishing.minigame.ReelSessionManager} can run the reel-in
 * minigame for - a {@link FishSpecies} bite or a {@link SeaCreature} encounter. Rarity alone is
 * what the minigame itself needs (see {@link xyz.goga221.koi.fishing.minigame.ReelSession}); id
 * and catch sound are for the reward path once it resolves.
 */
public interface Catchable {

    String getId();

    FishRarity getRarity();

    Sound getCatchSound();
}
