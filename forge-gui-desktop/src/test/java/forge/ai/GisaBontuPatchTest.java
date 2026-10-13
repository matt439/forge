package forge.ai;

import java.util.List;
import java.util.Random;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardPredicates;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Deck Lab's game patch "gisa-bontu-1" (commit af513b9fd): with game patches on the AI plays Ghoulcaller
 * Gisa and God-Eternal Bontu; with -Ddecklab.gamePatches=off it plays Forge's own code, and both cards
 * count as removed from AI decks, as Forge's own scripts say.
 */
public class GisaBontuPatchTest extends DeckLabSwitchTest {

    @Test
    public void testRemovedFromAiDecksOnlyWithGamePatchesOff() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card gisa = createCard("Ghoulcaller Gisa", ai);
        Card bontu = createCard("God-Eternal Bontu", ai);
        Card bears = createCard("Grizzly Bears", ai);

        gamePatchesOn();
        for (Card c : List.of(gisa, bontu, bears)) {
            AssertJUnit.assertFalse(c.getName() + " is removed with game patches on", ComputerUtilCard.isCardRemAIDeck(c));
            AssertJUnit.assertFalse(c.getName() + " is removed with game patches on", CardPredicates.isRemAIDeck().test(c));
        }

        gamePatchesOff();
        for (Card c : List.of(gisa, bontu)) {
            AssertJUnit.assertTrue(c.getName() + " isn't removed with game patches off", ComputerUtilCard.isCardRemAIDeck(c));
            AssertJUnit.assertTrue(c.getName() + " isn't removed with game patches off", CardPredicates.isRemAIDeck().test(c));
        }
        AssertJUnit.assertFalse(ComputerUtilCard.isCardRemAIDeck(bears));
        AssertJUnit.assertFalse(CardPredicates.isRemAIDeck().test(bears));
    }

    /**
     * Gisa's ability on the opponent's end step: TokenAi wants a token ability without haste on the
     * opponent's turn, after attackers (checkPhaseRestrictions), and its token chance (80% in the Default
     * profile) is made certain here. With game patches on X is the Hill Giant's power, 3; off, X is worked
     * out before the cost is paid, 0, and the AI won't.
     */
    @Test
    public void testGisaActivatesOnlyWithGamePatchesOn() {
        Random before = MyRandom.getRandom();
        MyRandom.setRandom(new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }
        });
        try {
            gamePatchesOn();
            AssertJUnit.assertTrue("the AI doesn't activate Gisa with game patches on", gisaWillingToActivate());
            gamePatchesOff();
            AssertJUnit.assertFalse("the AI activates Gisa with game patches off", gisaWillingToActivate());
        } finally {
            MyRandom.setRandom(before);
        }
    }

    private boolean gisaWillingToActivate() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        Card gisa = addCard("Ghoulcaller Gisa", ai);
        gisa.setSickness(false);
        addCard("Swamp", ai);
        addCard("Hill Giant", ai).setSickness(false);
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, opponent);
        game.getAction().checkStateEffects(true);

        SpellAbility sa = null;
        for (SpellAbility ability : gisa.getSpellAbilities()) {
            if (ability.getApi() == ApiType.Token) {
                sa = ability;
            }
        }
        AssertJUnit.assertNotNull("no token ability on Gisa", sa);
        sa.setActivatingPlayer(ai);
        return SpellApiToAi.Converter.get(sa).canPlayWithSubs(ai, sa).willingToPlay();
    }

    /**
     * Gisa in hand with five Swamps, in the AI's MAIN2: PermanentAi holds a creature with nothing to gain
     * from main 1 until main 2. chooseSpellAbilityToPlay reaches the RemAIDeck filter
     * (chooseSpellAbilityToPlayFromList), which canPlaySa never does.
     */
    @Test
    public void testGisaIsCastOnlyWithGamePatchesOn() {
        gamePatchesOn();
        SpellAbility on = chosenWithGisaInHand();
        AssertJUnit.assertNotNull("the AI doesn't cast Gisa with game patches on", on);
        AssertJUnit.assertEquals("Ghoulcaller Gisa", on.getHostCard().getName());

        gamePatchesOff();
        AssertJUnit.assertNull("the AI casts Gisa with game patches off", chosenWithGisaInHand());
    }

    private SpellAbility chosenWithGisaInHand() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCards("Swamp", 5, ai);
        addCardToZone("Ghoulcaller Gisa", ai, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, ai);
        game.getAction().checkStateEffects(true);

        List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
        return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
    }

    /**
     * Bontu's "sacrifice any number of other permanents, then draw that many": with four Forests and an
     * empty hand, SacToDraw keeps every land (the hand may still need six); Forge's own default
     * sacrifices all four.
     */
    @Test
    public void testBontuKeepsItsLandsOnlyWithGamePatchesOn() {
        gamePatchesOn();
        AssertJUnit.assertEquals("SacToDraw sacrifices lands the hand needs", 0, bontuSacrifices());
        gamePatchesOff();
        AssertJUnit.assertEquals("Forge's own default doesn't sacrifice every permanent offered", 4, bontuSacrifices());
    }

    private int bontuSacrifices() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Card bontu = addCard("God-Eternal Bontu", ai);
        CardCollection forests = new CardCollection(addCards("Forest", 4, ai));
        fillLibrary(ai, 10);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai);
        game.getAction().checkStateEffects(true);

        SpellAbility sac = null;
        for (Trigger t : bontu.getTriggers()) {
            SpellAbility ability = t.ensureAbility();
            if (ability != null && ability.getApi() == ApiType.Sacrifice) {
                sac = ability;
            }
        }
        AssertJUnit.assertNotNull("no sacrifice trigger on Bontu", sac);
        AssertJUnit.assertEquals("SacToDraw", sac.getParam("AILogic"));
        sac.setActivatingPlayer(ai);
        return ComputerUtil.choosePermanentsToSacrifice(ai, forests, forests.size(), sac, false, true).size();
    }
}
