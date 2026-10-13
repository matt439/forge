package forge.ai;

import java.util.Arrays;
import java.util.List;

import forge.game.GamePatches;
import forge.game.player.Player;

import org.testng.annotations.AfterMethod;

/**
 * A base for tests of Deck Lab's AI switches and game patches (this fork's decklab branch). It installs its
 * own AiSwitches hook rather than Deck Lab's decklab.Pilots.on, which finds a seat from a player's name
 * ("Ai(1)-..."): these tests' players are "p1" and "p2", which Pilots.on can't map to a seat. Each test
 * method starts and ends with no hook and the game patches as the property says.
 */
public abstract class DeckLabSwitchTest extends AITest {

    /** Turns the switches ids on for player p only, and the game patches on. */
    protected void switchOn(Player p, String... ids) {
        final List<String> on = Arrays.asList(ids);
        GamePatches.setForTests(true);
        AiSwitches.setHook((pl, id) -> pl == p && on.contains(id));
    }

    /** Plays as with -Ddecklab.gamePatches=on (the default). */
    protected void gamePatchesOn() {
        GamePatches.setForTests(true);
    }

    /** Plays as with -Ddecklab.gamePatches=off: Forge's own code, every switch off. */
    protected void gamePatchesOff() {
        GamePatches.setForTests(false);
    }

    @AfterMethod(alwaysRun = true)
    public void resetDeckLabSwitches() {
        AiSwitches.setHook(null);
        GamePatches.setForTests(null);
    }
}
