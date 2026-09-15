package xyz.goga221.koi.fishing.minigame;

import xyz.goga221.koi.fishing.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Renders a {@link ReelSession}'s reveal progress as action bar text. Unrevealed bars aren't
 * shown at all - the total needed stays hidden so the player can't guess the rarity ahead of
 * time. Each click adds one more bar, colored by whichever tier's segment that bar index falls
 * in on the cumulative ladder - so the row visibly passes through white, green, aqua, etc. as
 * it climbs through the rarities, and only the very last bar reveals the true catch.
 */
public final class ClickBarRenderer {

    private ClickBarRenderer() {
    }

    public static void render(Player player, ReelSession session) {
        int revealed = session.getRevealedBars();

        if (revealed == 0) {
            player.sendActionBar(Component.text("Something's biting... click to reel it in!", NamedTextColor.GRAY));
            return;
        }

        Component bars = Component.empty();
        for (int i = 0; i < revealed; i++) {
            if (i > 0) {
                bars = bars.append(Component.text(" "));
            }
            bars = bars.append(Component.text("▮", FishRarity.forCumulativeIndex(i).getColor()));
        }

        Component text = Component.text("Reeling in: ", NamedTextColor.GRAY).append(bars);
        player.sendActionBar(text);
    }

    public static void clear(Player player) {
        player.sendActionBar(Component.empty());
    }
}
