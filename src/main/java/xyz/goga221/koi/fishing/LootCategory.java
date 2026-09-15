package xyz.goga221.koi.fishing;

/**
 * What kind of catch a {@link FishSpecies} entry represents. Fish, treasure, and junk all share
 * the same pool, roll, and reel-in minigame - this only changes flavor (the catch message,
 * Koi-dex grouping).
 */
public enum LootCategory {
    FISH,
    TREASURE,
    JUNK
}
