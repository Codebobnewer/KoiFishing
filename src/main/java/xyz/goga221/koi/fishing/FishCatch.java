package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.RodTier;
import lombok.Value;

import java.util.UUID;

/**
 * A single resolved catch event, persisted via {@code CatchRepository}.
 */
@Value
public class FishCatch {
    UUID playerId;
    String fishId;
    FishRarity rarity;
    RodTier rodTier;
    String baitId;
    long caughtAt;
}
