package xyz.goga221.koi.item;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

/**
 * An admin-defined fishing rod tier - the actual ItemStack is a Vulcan item sharing this
 * {@link #id} (see {@link RodItems}), so an admin must author it with {@code /v item create <id>}
 * before it can be given, crafted, or fished with. Mutable and persisted to
 * {@code rod-tiers.yml}. {@link #rarityBoost} skews the catch roll toward rarer fish - scaled by
 * the fish's rarity ordinal, so it does nothing for Common and matters most for Mythic.
 * {@link #minHookTicks}/{@link #maxHookTicks} set the wait-for-a-bite window (vanilla default is
 * 100-600 ticks / 5-30s) - lower values hook faster. An optional
 * {@link #upgradesFromId}/{@link #upgradeMaterial} pair defines a crafting recipe: this tier is
 * an upgrade combining that other tier's rod with the given material.
 */
@Getter
@Setter
public class RodTier {

    private String id;
    private double rarityBoost;
    private int minHookTicks = 100;
    private int maxHookTicks = 600;
    private String upgradesFromId;
    private Material upgradeMaterial;

    public RodTier(String id) {
        this.id = id;
    }
}
