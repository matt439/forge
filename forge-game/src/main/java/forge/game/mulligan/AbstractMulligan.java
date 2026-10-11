/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Deck Lab's changes to Forge's forge.game.mulligan.AbstractMulligan (this fork's decklab branch, from tag forge-2.0.15,
 * forge-game/src/main/java/forge/game/mulligan/AbstractMulligan.java). Forge's file has no licence
 * header of its own; Forge is GPL-3 (its LICENSE), so this copy is too, under the header Forge's
 * other source files carry.
 *
 * What changed: mulligan() no longer sleeps for 100 ms (Thread.sleep(100), in a try/catch for
 * InterruptedException) between putting the hand back and shuffling. Nothing else is changed: the
 * same fields (package-private, as the subclasses read them) and the same methods.
 * Why: the sleep only pauses the game thread. It reads and changes no game state and draws no random
 * numbers, so the same seed plays the same games without it, and Deck Lab's simulations mulligan
 * about every other game, which made it 2-9 % of a game's time (sim-investigation.md, R04).
 *
 * This was a copy in Deck Lab's card-log jar (decklab/lib/forge/simjar.py in Deck Lab's repo) until it
 * moved into this fork's decklab branch. forge-game's resource decklab/forge-patches.txt lists it, and
 * Deck Lab reads that list to know which of these changes the jar holds.
 */
package forge.game.mulligan;

import forge.game.GameLogEntryType;
import forge.game.event.GameEventAddLog;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.Localizer;

public abstract class AbstractMulligan {
    Player player;
    int timesMulliganed = 0;
    boolean firstMulliganFree = false;
    boolean kept = false;

    public AbstractMulligan(Player p, boolean firstMullFree) {
        player = p;
        firstMulliganFree = firstMullFree;
    }

    public Player getPlayer() { return player; }

    public void beforeFirstMulligan() {}
    public abstract boolean canMulligan();
    public abstract int handSizeAfterNextMulligan();

    public int tuckCardsDuringMulligan() {
        return 0;
    }

    public void mulligan() {
        CardCollection toMulligan = new CardCollection(player.getCardsIn(ZoneType.Hand));
        if (toMulligan.isEmpty()) return;
        revealPreMulligan(toMulligan);
        for (final Card c : toMulligan) {
            player.getGame().getAction().moveToLibrary(c, null);
        }
        // Deck Lab: Forge's Thread.sleep(100) was here.
        player.shuffle(null);
        timesMulliganed++;
        mulliganDraw();
        player.onMulliganned();
    }

    public void keep() {
        kept = true;
    }

    public boolean hasKept() {
        return kept;
    }

    public void revealPreMulligan(CardCollection toMulligan) {}

    public void mulliganDraw() {
        player.drawCards(handSizeAfterNextMulligan());
    }

    public void afterMulligan() {
        player.getGame().fireEvent(new GameEventAddLog(GameLogEntryType.MULLIGAN, Localizer.getInstance().getMessage("lblPlayerKeepNCardsHand", player.getName(), String.valueOf(player.getZone(ZoneType.Hand).size()))));
    }
}
