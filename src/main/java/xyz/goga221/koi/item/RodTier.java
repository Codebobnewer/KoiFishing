package xyz.goga221.koi.item;

import lombok.Getter;

/**
 * Fishing rod tiers. Wood is the vanilla baseline, Koi is the top tier - all tiers reuse
 * {@link org.bukkit.Material#FISHING_ROD}, distinguished by CustomModelData and a PDC tag
 * (see {@link RodItems}). {@link #rarityBoost} skews the catch roll toward rarer fish - it's
 * scaled by the fish's rarity ordinal, so it does nothing for Common and matters most for
 * Mythic. {@link #minHookTicks}/{@link #maxHookTicks} shrink the vanilla wait-for-a-bite window
 * (100-600 ticks / 5-30s by default) - better rods hook faster.
 */
@Getter
public enum RodTier {

    WOOD("Wood Rod", 1001, 0.0, 100, 600),
    IRON("Iron Rod", 1002, 0.15, 80, 440),
    DIAMOND("Diamond Rod", 1003, 0.35, 55, 280),
    KOI("Koi Rod", 1004, 0.6, 30, 150);

    private final String displayName;
    private final int customModelData;
    private final double rarityBoost;
    private final int minHookTicks;
    private final int maxHookTicks;

    RodTier(String displayName, int customModelData, double rarityBoost, int minHookTicks, int maxHookTicks) {
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.rarityBoost = rarityBoost;
        this.minHookTicks = minHookTicks;
        this.maxHookTicks = maxHookTicks;
    }

    public RodTier next() {
        int nextOrdinal = ordinal() + 1;
        RodTier[] values = values();
        return nextOrdinal < values.length ? values[nextOrdinal] : null;
    }
}
