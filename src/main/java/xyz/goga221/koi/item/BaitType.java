package xyz.goga221.koi.item;

import xyz.goga221.koi.fishing.FishRarity;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

/**
 * An admin-defined bait type - the actual ItemStack is a Vulcan item sharing this {@link #id}
 * (see {@link BaitItems}), so an admin must author it with {@code /v item create <id>} before it
 * can be given, crafted, or fished with. Mutable and persisted to {@code bait-types.yml}.
 * {@link #craftMaterial} is only the raw crafting ingredient surrounding the recipe, not the
 * bait's own appearance - Vulcan owns that.
 */
@Getter
@Setter
public class BaitType {

    private String id;
    private Material craftMaterial;
    private double potency = 1.0;
    private final Set<FishRarity> favoredRarities = new HashSet<>();

    public BaitType(String id) {
        this.id = id;
    }
}
