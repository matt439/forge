package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Deck Lab's AI switch "prowess-precombat-1" (forge.ai.ProwessFirst, through
 * ComputerUtil.castSpellInMain1): with a Monastery Swiftspear that can attack, the AI casts Divination in
 * its main 1 only with the switch on. Off, it is Forge's own code: DrawAi holds the draw until main 2.
 */
public class ProwessPrecombatSwitchTest extends DeckLabSwitchTest {

    /** The board: an untapped Swiftspear without summoning sickness, three Islands, Divination in hand. */
    private static final class Board {
        final Player ai;
        final Player opponent;
        final Card swiftspear;
        final SpellAbility divination;

        Board(Player ai, Player opponent, Card swiftspear, SpellAbility divination) {
            this.ai = ai;
            this.opponent = opponent;
            this.swiftspear = swiftspear;
            this.divination = divination;
        }

        boolean castInMain1() {
            return ComputerUtil.castSpellInMain1(ai, divination);
        }

        boolean drawAiWilling() {
            return SpellApiToAi.Converter.get(divination).canPlayWithSubs(ai, divination).willingToPlay();
        }
    }

    private Board board(PhaseType phase, boolean aiTurn) {
        return board(phase, aiTurn, "Monastery Swiftspear", false);
    }

    /** The same board with another creature in the Swiftspear's place, summoning sick or not. */
    private Board board(PhaseType phase, boolean aiTurn, String creature, boolean sick) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card swiftspear = addCard(creature, ai);
        swiftspear.setSickness(sick);
        addCards("Island", 3, ai);
        fillLibrary(ai, 10);
        Card divination = addCardToZone("Divination", ai, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(phase, aiTurn ? ai : opponent);
        game.getAction().checkStateEffects(true);

        SpellAbility sa = divination.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        return new Board(ai, opponent, swiftspear, sa);
    }

    @Test
    public void testDivinationInMain1OnlyWithTheSwitchOn() {
        Board off = board(PhaseType.MAIN1, true);
        gamePatchesOn();
        AssertJUnit.assertFalse("castSpellInMain1 is true with the switch off", off.castInMain1());
        AssertJUnit.assertFalse("DrawAi casts Divination in main 1 with the switch off", off.drawAiWilling());

        Board on = board(PhaseType.MAIN1, true);
        switchOn(on.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertTrue("castSpellInMain1 is false with the switch on", on.castInMain1());
        AssertJUnit.assertTrue("DrawAi holds Divination with the switch on", on.drawAiWilling());
    }

    @Test
    public void testOffForTheOtherSeat() {
        Board b = board(PhaseType.MAIN1, true);
        switchOn(b.opponent, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertFalse("the opponent's switch reaches the AI", b.castInMain1());
    }

    @Test
    public void testOffWithGamePatchesOff() {
        Board b = board(PhaseType.MAIN1, true);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        gamePatchesOff();
        AssertJUnit.assertFalse("castSpellInMain1 is true with game patches off", b.castInMain1());
        AssertJUnit.assertFalse("DrawAi casts Divination in main 1 with game patches off", b.drawAiWilling());
    }

    @Test
    public void testNotForATappedSwiftspear() {
        Board b = board(PhaseType.MAIN1, true);
        b.swiftspear.setTapped(true);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertFalse("a tapped Swiftspear counts", b.castInMain1());
    }

    @Test
    public void testNotForACreatureSpell() {
        Board b = board(PhaseType.MAIN1, true);
        Card bears = addCardToZone("Grizzly Bears", b.ai, ZoneType.Hand);
        SpellAbility sa = bears.getFirstSpellAbility();
        sa.setActivatingPlayer(b.ai);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertFalse("a creature spell counts", ProwessFirst.castNow(b.ai, sa));
        AssertJUnit.assertFalse("a creature spell is cast in main 1", ComputerUtil.castSpellInMain1(b.ai, sa));
    }

    @Test
    public void testNotOnTheOpponentsTurn() {
        Board b = board(PhaseType.MAIN1, false);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertFalse("it counts on the opponent's turn", b.castInMain1());
    }

    @Test
    public void testMain2IsTheSameOnAsOff() {
        Board off = board(PhaseType.MAIN2, true);
        gamePatchesOn();
        boolean castOff = off.castInMain1();
        boolean willingOff = off.drawAiWilling();

        Board on = board(PhaseType.MAIN2, true);
        switchOn(on.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertEquals(castOff, on.castInMain1());
        AssertJUnit.assertEquals(willingOff, on.drawAiWilling());
        AssertJUnit.assertTrue("DrawAi holds Divination in main 2", willingOff);
    }

    @Test
    public void testNotForASummoningSickCreature() {
        // Seeker of the Way has prowess and no haste, so summoning sickness keeps it from attacking.
        Board b = board(PhaseType.MAIN1, true, "Seeker of the Way", true);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertFalse("a summoning-sick Seeker of the Way counts", b.castInMain1());

        Board ready = board(PhaseType.MAIN1, true, "Seeker of the Way", false);
        switchOn(ready.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertTrue("a Seeker of the Way that can attack doesn't count", ready.castInMain1());
    }

    @Test
    public void testASpellCastTriggerCounts() {
        // Young Pyromancer has no prowess, only "whenever you cast an instant or sorcery spell".
        Board b = board(PhaseType.MAIN1, true, "Young Pyromancer", false);
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertTrue("Young Pyromancer's trigger doesn't count", b.castInMain1());
    }

    @Test
    public void testBurnCountsOnlyWithTheSwitchOn() {
        Board b = board(PhaseType.MAIN1, true);
        Card shock = addCardToZone("Shock", b.ai, ZoneType.Hand);
        SpellAbility sa = shock.getFirstSpellAbility();
        sa.setActivatingPlayer(b.ai);
        gamePatchesOn();
        AssertJUnit.assertFalse("Shock counts with the switch off", ProwessFirst.castNow(b.ai, sa));
        switchOn(b.ai, AiSwitches.PROWESS_PRECOMBAT_1);
        AssertJUnit.assertTrue("Shock doesn't count with the switch on", ProwessFirst.castNow(b.ai, sa));
    }
}
