package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.RodTier;
import lombok.Value;

import java.util.UUID;

/**
 * A single resolved catch event, persisted via {@code CatchRepository}. {@link #seaCreature}
 * distinguishes a killed {@link SeaCreature} from a reeled-in fish/treasure/junk catch, so
 * {@code CatchRepository} can bump the sea-creatures-killed stat alongside the normal total.
 */
@Value
public class FishCatch {
    UUID playerId;
    String fishId;
    FishRarity rarity;
    RodTier rodTier;
    String baitId;
    long caughtAt;
    boolean seaCreature;
}
