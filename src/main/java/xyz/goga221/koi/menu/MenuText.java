package xyz.goga221.koi.menu;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.inventoryaccess.component.ComponentWrapper;

/**
 * Bridges MiniMessage text into InvUI's {@link ComponentWrapper}-based item name/lore API, so
 * GUI items are built the same way as every other player-facing string in the plugin instead
 * of legacy {@code §} codes.
 */
final class MenuText {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MenuText() {
    }

    static ComponentWrapper mmName(String miniMessageTemplate, TagResolver... resolvers) {
        return new AdventureComponentWrapper(MM.deserialize(miniMessageTemplate, resolvers));
    }

    static ComponentWrapper[] mmLore(String... miniMessageLines) {
        ComponentWrapper[] wrapped = new ComponentWrapper[miniMessageLines.length];
        for (int i = 0; i < miniMessageLines.length; i++) {
            wrapped[i] = new AdventureComponentWrapper(MM.deserialize(miniMessageLines[i]));
        }
        return wrapped;
    }
}
