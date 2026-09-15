package xyz.goga221.koi.fishing;

import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

/**
 * Catch rarity tiers. Reeling in a fish clicks through every tier's bars in order, starting
 * from Common - each tier contributes {@link #barCount} bars in its own color before the next
 * tier's bars start, and the roll lands somewhere in the true rarity's own segment. That's why
 * rarer fish take more clicks: landing a Mythic means clicking through Common's, Uncommon's,
 * Rare's, Epic's, and Legendary's bars first.
 */
@Getter
public enum FishRarity {

    COMMON(NamedTextColor.WHITE, Color.WHITE, 100.0, 1),
    UNCOMMON(NamedTextColor.GREEN, Color.LIME, 55.0, 2),
    RARE(NamedTextColor.AQUA, Color.AQUA, 25.0, 3),
    EPIC(NamedTextColor.LIGHT_PURPLE, Color.FUCHSIA, 10.0, 3),
    LEGENDARY(NamedTextColor.GOLD, Color.ORANGE, 3.0, 4),
    MYTHIC(NamedTextColor.RED, Color.RED, 1.0, 4);

    private final TextColor color;
    private final Color particleColor;
    private final double poolWeight;
    private final int barCount;

    FishRarity(TextColor color, Color particleColor, double poolWeight, int barCount) {
        this.color = color;
        this.particleColor = particleColor;
        this.poolWeight = poolWeight;
        this.barCount = barCount;
    }

    /**
     * Sum of every tier's bar count below this one - how many bars are already behind this
     * tier's own segment in the cumulative ladder.
     */
    public int cumulativeBarsBefore() {
        int total = 0;
        for (FishRarity rarity : values()) {
            if (rarity == this) {
                break;
            }
            total += rarity.barCount;
        }
        return total;
    }

    /**
     * Which tier's color a zero-based bar index falls under in the cumulative ladder.
     */
    public static FishRarity forCumulativeIndex(int zeroBasedIndex) {
        int cursor = 0;
        for (FishRarity rarity : values()) {
            cursor += rarity.barCount;
            if (zeroBasedIndex < cursor) {
                return rarity;
            }
        }
        return values()[values().length - 1];
    }
}
