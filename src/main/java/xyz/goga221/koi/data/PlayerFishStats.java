package xyz.goga221.koi.data;

import xyz.goga221.koi.fishing.FishRarity;
import lombok.Data;

import java.util.UUID;

/**
 * Per-player aggregate stats row, mirroring the {@code player_stats} table.
 */
@Data
public class PlayerFishStats {
    private final UUID playerId;
    private int totalCatches;
    private FishRarity bestRarity;
}
