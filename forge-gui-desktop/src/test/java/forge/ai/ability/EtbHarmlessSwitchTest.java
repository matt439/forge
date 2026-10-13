package forge.ai.ability;

import forge.ai.AiPlayDecision;
import forge.ai.AiSwitches;
import forge.ai.DeckLabSwitchTest;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.Random;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Deck Lab's AI switch "etb-harmless-1" (ChangeZoneAi and DestroyAllAi): with the switch off the AI plays
 * Forge's own code, which refuses a spell or enters trigger whose optional ("up to one target") or harmless
 * rider has nothing to act on. With it on for the AI player, those riders no longer veto the card. Each
 * card is asked through AiController.canPlaySa, the AI's whole decision on casting it.
 *
 * The AI's chances are made certain (MyRandom's nextFloat is 0): ComputerUtilCard.useRemovalNow ends with a
 * random roll against the target's value for an instant, so Fiery Annihilation would otherwise pass only
 * sometimes with the switch on. The switch-off results don't depend on it: the rider vetoes first.
 */
public class EtbHarmlessSwitchTest extends DeckLabSwitchTest {

    private Random randomBefore;

    @BeforeMethod
    public void certainChances() {
        randomBefore = MyRandom.getRandom();
        MyRandom.setRandom(new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }
        });
    }

    @AfterMethod(alwaysRun = true)
    public void restoreChances() {
        MyRandom.setRandom(randomBefore);
    }

    private static AiPlayDecision decide(Player ai, Card card) {
        SpellAbility sa = card.getFirstSpellAbility();
        sa.setActivatingPlayer(ai);
        return ((PlayerControllerAi) ai.getController()).getAi().canPlaySa(sa);
    }

    private Game game;
    private Player ai;
    private Player opponent;

    private void newGame() {
        game = initAndCreateGame();
        ai = game.getPlayers().get(1);
        opponent = game.getPlayers().get(0);
    }

    private void aiTurn(PhaseType phase) {
        game.getPhaseHandler().devModeSet(phase, ai);
        game.getAction().checkStateEffects(true);
    }

    /** Samwise the Stouthearted's trigger (a TargetMin 0 ChangeZone sub) with nothing in the graveyard. */
    @Test
    public void testSamwiseWithAnEmptyGraveyard() {
        newGame();
        addCards("Plains", 2, ai);
        Card samwise = addCardToZone("Samwise the Stouthearted", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN1);

        AssertJUnit.assertEquals("Forge's own code vetoes Samwise's empty optional trigger",
                AiPlayDecision.BadEtbEffects, decide(ai, samwise));

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        AssertJUnit.assertNotSame("With etb-harmless-1 the empty optional trigger doesn't veto Samwise",
                AiPlayDecision.BadEtbEffects, decide(ai, samwise));
    }

    /** Fiery Annihilation's "exile up to one target Equipment" sub, with no Equipment anywhere. */
    @Test
    public void testFieryAnnihilationWithoutEquipment() {
        newGame();
        addCards("Mountain", 3, ai);
        addCard("Grizzly Bears", opponent);
        Card fiery = addCardToZone("Fiery Annihilation", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN1);

        AssertJUnit.assertFalse("Forge's own code won't cast Fiery Annihilation without an Equipment to exile",
                decide(ai, fiery) == AiPlayDecision.WillPlay);

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        AssertJUnit.assertEquals("With etb-harmless-1 the AI casts Fiery Annihilation at the creature it kills",
                AiPlayDecision.WillPlay, decide(ai, fiery));
    }

    /**
     * Fires of Mount Doom's trigger: 2 damage to Grizzly Bears (which DealDamageAi accepts), then a
     * DestroyAll sub for Equipment attached to it, which destroys nothing here. In the AI's MAIN2, where
     * PermanentAi casts a noncreature permanent without PlayMain1.
     */
    @Test
    public void testFiresOfMountDoomWithoutEquipment() {
        newGame();
        addCards("Mountain", 3, ai);
        addCard("Grizzly Bears", opponent);
        Card fires = addCardToZone("Fires of Mount Doom", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN2);

        AssertJUnit.assertEquals("Forge's own code vetoes Fires of Mount Doom's trigger with no Equipment",
                AiPlayDecision.BadEtbEffects, decide(ai, fires));

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        AssertJUnit.assertNotSame("With etb-harmless-1 the harmless Equipment rider doesn't veto Fires",
                AiPlayDecision.BadEtbEffects, decide(ai, fires));
    }

    /** The switch is the AI player's only: on for the opponent, or with game patches off, it's Forge's code. */
    @Test
    public void testSwitchOnlyForItsPlayerAndOnlyWithGamePatches() {
        newGame();
        addCards("Mountain", 3, ai);
        addCard("Grizzly Bears", opponent);
        Card fires = addCardToZone("Fires of Mount Doom", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN2);

        switchOn(opponent, AiSwitches.ETB_HARMLESS_1);
        AssertJUnit.assertEquals("The opponent's switch doesn't change the AI's decision",
                AiPlayDecision.BadEtbEffects, decide(ai, fires));

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        gamePatchesOff();
        AssertJUnit.assertEquals("With -Ddecklab.gamePatches=off every switch is off",
                AiPlayDecision.BadEtbEffects, decide(ai, fires));
    }

    /** A DestroyAll spell (not a trigger or sub) with nothing to destroy stays refused with the switch on. */
    @Test
    public void testDayOfJudgmentOnEmptyBoardsUnchanged() {
        newGame();
        addCards("Plains", 4, ai);
        Card day = addCardToZone("Day of Judgment", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN1);

        AiPlayDecision off = decide(ai, day);
        AssertJUnit.assertFalse("Forge's own code won't cast Day of Judgment on empty boards",
                off == AiPlayDecision.WillPlay);

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        AiPlayDecision on = decide(ai, day);
        AssertJUnit.assertFalse("etb-harmless-1 doesn't make Day of Judgment castable on empty boards",
                on == AiPlayDecision.WillPlay);
        AssertJUnit.assertEquals(off, on);
    }

    /**
     * Eowyn, Fearless Knight's trigger must target (TargetMin 1): with no opposing creature it stays vetoed,
     * since the switch covers only "up to" targets.
     */
    @Test
    public void testEowynWithoutOpposingCreatureUnchanged() {
        newGame();
        addCards("Mountain", 2, ai);
        addCards("Plains", 2, ai);
        Card eowyn = addCardToZone("\u00c9owyn, Fearless Knight", ai, ZoneType.Hand);
        aiTurn(PhaseType.MAIN1);

        AssertJUnit.assertEquals(AiPlayDecision.BadEtbEffects, decide(ai, eowyn));

        switchOn(ai, AiSwitches.ETB_HARMLESS_1);
        AssertJUnit.assertEquals("etb-harmless-1 leaves a must-target trigger with no target vetoed",
                AiPlayDecision.BadEtbEffects, decide(ai, eowyn));
    }
}
