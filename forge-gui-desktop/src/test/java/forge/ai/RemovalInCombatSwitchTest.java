package forge.ai;

import java.util.Random;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Deck Lab's AI switch "removal-in-combat-1": ComputerUtilCard.useRemovalNow's interrupt 2 ("remove
 * blocker to save my attacker") on the AI's own turn. With the switch off it plays Forge's own code, which
 * looks for the blocker only on the opponent's turn. With it on, that opponent's-turn reading is gone.
 */
public class RemovalInCombatSwitchTest extends DeckLabSwitchTest {

    /**
     * The AI's Grizzly Bears (2/2) attacks and the opponent's Savannah Lions (2/1) blocks it, so the Bears
     * would die. Murdering the Lions leaves the Bears blocked by nothing, so they survive. The board is
     * picked so that Forge's own code always says no: the Lions are a one-mana 2/1 (tempo 0.1 * 1 / 3) and
     * the AI is at 100 life, so the threat is about 0.17, under useRemovalNow's 0.2 floor, and no random
     * draw is made. (At declare blockers the later threat checks don't yet multiply by 0.1, so a bigger
     * blocker would make Forge's answer random.)
     */
    private Object[] blockedAttackerBoard(Game game) {
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(100, null);

        Card bears = addCard("Grizzly Bears", ai);
        addCards("Swamp", 3, ai);
        Card murder = addCardToZone("Murder", ai, ZoneType.Hand);
        Card lions = addCard("Savannah Lions", opponent);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, ai);
        game.getAction().checkStateEffects(true);

        Combat combat = new Combat(ai);
        combat.addAttacker(bears, opponent);
        combat.addBlocker(bears, lions);
        game.getPhaseHandler().setCombat(combat);

        SpellAbility murderSa = murder.getFirstSpellAbility();
        murderSa.setActivatingPlayer(ai);
        return new Object[] {ai, murderSa, lions};
    }

    @Test
    public void testRemovesBlockerToSaveAttackerOnlyWithSwitchOn() {
        Game game = initAndCreateGame();
        Object[] board = blockedAttackerBoard(game);
        Player ai = (Player) board[0];
        SpellAbility murderSa = (SpellAbility) board[1];
        Card lions = (Card) board[2];

        // No hook: the switch is off, and Forge's own code never says yes on this board.
        for (int i = 0; i < 20; i++) {
            AssertJUnit.assertFalse("switch off: Forge's own code holds the removal",
                    ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));
        }

        // Game patches off: every switch is off even with the hook naming it.
        switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1);
        gamePatchesOff();
        AssertJUnit.assertFalse("game patches off: Forge's own code holds the removal",
                ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));

        // The switch on for the other player only: still off for the AI.
        switchOn(game.getPlayers().get(0), AiSwitches.REMOVAL_IN_COMBAT_1);
        AssertJUnit.assertFalse("switch on for the other seat only",
                ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));

        switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1);
        AssertJUnit.assertTrue("switch on: removes the blocker to save the attacker",
                ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));
    }

    /**
     * The opponent's Savannah Lions attacks and the AI's Grizzly Bears blocks it, on the opponent's turn,
     * and the target is the attacker, not a blocker. Interrupt 2 looks only at blockers, so the switch
     * changes nothing for this target: with it on the answer is the switch-off answer, for the same random
     * draws. (A blocker as the target on the opponent's turn is the next test.)
     */
    @Test
    public void testNonBlockerTargetUnchangedOnOpponentsTurn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);

        Card bears = addCard("Grizzly Bears", ai);
        addCards("Swamp", 3, ai);
        Card murder = addCardToZone("Murder", ai, ZoneType.Hand);
        Card lions = addCard("Savannah Lions", opponent);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, opponent);
        game.getAction().checkStateEffects(true);

        Combat combat = new Combat(opponent);
        combat.addAttacker(lions, ai);
        combat.addBlocker(lions, bears);
        game.getPhaseHandler().setCombat(combat);

        SpellAbility murderSa = murder.getFirstSpellAbility();
        murderSa.setActivatingPlayer(ai);

        Random before = MyRandom.getRandom();
        try {
            for (int seed = 0; seed < 20; seed++) {
                AiSwitches.setHook(null);
                MyRandom.setRandom(new Random(seed));
                boolean off = ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard);

                switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1);
                MyRandom.setRandom(new Random(seed));
                boolean on = ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard);

                AssertJUnit.assertEquals("seed " + seed + ": the switch changes nothing for a target that isn't a blocker", off, on);
            }
        } finally {
            MyRandom.setRandom(before);
        }
    }

    /**
     * The opponent's Grizzly Bears attacks and the AI's own Savannah Lions blocks it, on the opponent's
     * turn, and the target is the Lions. Forge's own interrupt 2 (the switch off) says yes, since removing
     * the Lions would leave the Bears alive: it always reads the blocker as the one to remove, whoever
     * controls it. No caller reaches this in a two-player game (removal targets an opponent's card), but in
     * multiplayer a third player's blocker could be the target. With the switch on that reading is gone:
     * the Lions are worth about 0.17 (a one-mana 2/1 against a three-mana spell, the AI at 100 life),
     * under useRemovalNow's 0.2 floor, so the answer is no without any random draw.
     */
    @Test
    public void testOpponentsTurnBlockerReadingGoneWithSwitchOn() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        ai.setLife(100, null);

        Card lions = addCard("Savannah Lions", ai);
        addCards("Swamp", 3, ai);
        Card murder = addCardToZone("Murder", ai, ZoneType.Hand);
        Card bears = addCard("Grizzly Bears", opponent);

        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, opponent);
        game.getAction().checkStateEffects(true);

        Combat combat = new Combat(opponent);
        combat.addAttacker(bears, ai);
        combat.addBlocker(bears, lions);
        game.getPhaseHandler().setCombat(combat);

        SpellAbility murderSa = murder.getFirstSpellAbility();
        murderSa.setActivatingPlayer(ai);

        AssertJUnit.assertTrue("switch off: Forge's own opponent's-turn reading removes the blocker",
                ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));

        switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1);
        AssertJUnit.assertFalse("switch on: the opponent's-turn reading is gone",
                ComputerUtilCard.useRemovalNow(murderSa, lions, 0, ZoneType.Graveyard));
    }
}
