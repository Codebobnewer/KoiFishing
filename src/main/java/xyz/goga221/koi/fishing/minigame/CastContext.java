package xyz.goga221.koi.fishing.minigame;

import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.RodTier;
import lombok.Value;

/**
 * Rod tier, bait, and biome captured at cast time ({@code State.FISHING}), consumed when the
 * fish bites ({@code State.BITE}) to size the reel-in minigame and weight the species roll.
 */
@Value
public class CastContext {
    RodTier rodTier;
    BaitType bait;
    String biomeKey;
}
