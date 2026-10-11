package forge.game;

import java.util.Map.Entry;

import forge.game.player.Player;
import forge.item.PaperCard;

/**
 * The order Match builds a player's library in before the shuffle (Deck Lab's "deck-order-1"; see the
 * note at the end of Match.java). Forge builds it in its deck section's hash order. Deck Lab plugs its
 * decklab.DeckOrder in as it starts, which gives each seat's cards in its .dck's line order. With no
 * hook, or a hook that has no order for the player, it's Forge's own order.
 */
public final class LibraryOrder {
    private LibraryOrder() {}

    /** Gives the entries a library is built from, or section itself when it has no order for player. */
    public interface Hook {
        Iterable<Entry<PaperCard, Integer>> entries(Player player, Iterable<Entry<PaperCard, Integer>> section);
    }

    private static volatile Hook hook;

    /** Plugs a hook in (null: none, Forge's own order). */
    public static void setHook(Hook h) {
        hook = h;
    }

    /** What Match iterates to build player's library from section. */
    public static Iterable<Entry<PaperCard, Integer>> entries(Player player, Iterable<Entry<PaperCard, Integer>> section) {
        Hook h = hook;
        return h == null ? section : h.entries(player, section);
    }
}
