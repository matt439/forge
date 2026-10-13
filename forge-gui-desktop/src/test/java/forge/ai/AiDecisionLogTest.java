package forge.ai;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.Lists;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Deck Lab's decision log (-Ddecklab.decisionLog, AiDecisionLog): nothing prints without names; a named
 * card's DLAI| line prints with them; the AI chooses the same ability either way.
 */
public class AiDecisionLogTest extends AITest {

    @AfterMethod(alwaysRun = true)
    public void resetDecisionLog() {
        AiDecisionLog.setForTests(null);
    }

    /** What the AI chooses, and what it prints, with Grizzly Bears and Llanowar Elves in hand. */
    private static final class Run {
        String chosen;
        List<String> lines = new ArrayList<>();
    }

    private Run choose(PhaseType phase) {
        return choose(phase, 2);
    }

    private Run choose(PhaseType phase, int forests) {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        addCards("Forest", forests, ai);
        addCardToZone("Grizzly Bears", ai, ZoneType.Hand);
        addCardToZone("Llanowar Elves", ai, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(phase, ai);
        game.getAction().checkStateEffects(true);

        Run run = new Run();
        PrintStream before = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            List<SpellAbility> chosen = ((PlayerControllerAi) ai.getController()).getAi().chooseSpellAbilityToPlay();
            run.chosen = chosen == null || chosen.isEmpty() ? null : chosen.get(0).getHostCard().getName() + ": " + chosen.get(0);
        } finally {
            System.setOut(before);
        }
        for (String line : out.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (line.startsWith("DLAI|")) {
                run.lines.add(line);
            }
        }
        return run;
    }

    @Test
    public void testNothingPrintsWithoutNames() {
        AiDecisionLog.setForTests("");
        AssertJUnit.assertFalse(AiDecisionLog.on());
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.MAIN2)) {
            AssertJUnit.assertEquals(List.of(), choose(phase).lines);
        }
    }

    @Test
    public void testNamedCardPrintsItsDecision() {
        AiDecisionLog.setForTests("Grizzly Bears");
        Run run = choose(PhaseType.MAIN1);
        AssertJUnit.assertFalse("no DLAI line for Grizzly Bears", run.lines.isEmpty());
        for (String line : run.lines) {
            String[] f = line.split("\\|", -1);
            AssertJUnit.assertEquals(line, 9, f.length);
            AssertJUnit.assertEquals("DLAI", f[0]);
            AssertJUnit.assertEquals("1", f[1]);
            Integer.parseInt(f[2]);
            AssertJUnit.assertEquals("?", f[3]); // the test's players are p1 and p2, not Ai(N)-...
            AssertJUnit.assertEquals("MAIN1", f[4]);
            AssertJUnit.assertEquals("?", f[5]);
            AiPlayDecision.valueOf(f[6]);
            AssertJUnit.assertEquals("PermanentCreature", f[7]);
            AssertJUnit.assertEquals("Grizzly Bears", f[8]);
        }
    }

    /**
     * "*" logs every card the AI weighs: both creatures here. With one Forest the AI can't pay for Grizzly
     * Bears, so it weighs both cards whichever it weighs first.
     */
    @Test
    public void testStarLogsEveryCard() {
        AiDecisionLog.setForTests("*");
        Set<String> cards = new TreeSet<>();
        Run run = choose(PhaseType.MAIN1, 1);
        for (String line : run.lines) {
            cards.add(line.substring(line.lastIndexOf('|') + 1));
        }
        AssertJUnit.assertTrue(cards.toString(), cards.contains("Grizzly Bears"));
        AssertJUnit.assertTrue(cards.toString(), cards.contains("Llanowar Elves"));
    }

    /** The log only reads the game: the AI chooses the same ability with it on as off, in either main phase. */
    @Test
    public void testChoiceIsTheSameWithTheLog() {
        for (PhaseType phase : List.of(PhaseType.MAIN1, PhaseType.MAIN2)) {
            AiDecisionLog.setForTests(null);
            String off = choose(phase).chosen;
            AiDecisionLog.setForTests("*");
            Run on = choose(phase);
            AssertJUnit.assertFalse(on.lines.isEmpty());
            AssertJUnit.assertEquals(phase.name(), off, on.chosen);
        }
        AiDecisionLog.setForTests(null);
        AssertJUnit.assertNotNull("the AI casts nothing in MAIN2, so the check above shows little", choose(PhaseType.MAIN2).chosen);
    }

    @Test
    public void testNamesDecode() {
        AssertJUnit.assertEquals("Éowyn, Fearless Knight", AiDecisionLog.decode("%C3%89owyn, Fearless Knight"));
        AssertJUnit.assertEquals("A+B 100%", AiDecisionLog.decode("A+B 100%"));
        AssertJUnit.assertEquals("Fire // Ice", AiDecisionLog.decode("Fire %2F%2F Ice"));
        AssertJUnit.assertEquals(Set.of("Éowyn, Fearless Knight", "Pearl of Wisdom", "a;b"),
                AiDecisionLog.parse("%C3%89owyn, Fearless Knight;Pearl of Wisdom;;a%3Bb"));
        AssertJUnit.assertTrue(AiDecisionLog.parse("Pearl of Wisdom;*").isEmpty());
        AssertJUnit.assertTrue(AiDecisionLog.parse("%2A").isEmpty());
        AssertJUnit.assertNull(AiDecisionLog.parse(""));
        AssertJUnit.assertNull(AiDecisionLog.parse(";"));
        AssertJUnit.assertNull(AiDecisionLog.parse(null));
    }

    /** A name given escaped, as Deck Lab gives it, matches the card. */
    @Test
    public void testEscapedNameMatches() {
        AiDecisionLog.setForTests("Grizzly%20Bears");
        AssertJUnit.assertFalse(choose(PhaseType.MAIN1).lines.isEmpty());
    }

    /** A seat is the N of Deck Lab's "Ai(N)-" player names, else "?". */
    @Test
    public void testSeats() {
        List<RegisteredPlayer> players = Lists.newArrayList();
        Deck d = new Deck();
        players.add(new RegisteredPlayer(d).setPlayer(new LobbyPlayerAi("Ai(1)-Izzet Otters", null)));
        players.add(new RegisteredPlayer(d).setPlayer(new LobbyPlayerAi("Ai(2)-The Free Peoples", null)));
        GameRules rules = new GameRules(GameType.Constructed);
        Game game = new Game(players, rules, new Match(rules, players, "Test"));
        AssertJUnit.assertEquals("1", AiDecisionLog.seat(game.getPlayers().get(0)));
        AssertJUnit.assertEquals("2", AiDecisionLog.seat(game.getPlayers().get(1)));
        AssertJUnit.assertEquals("?", AiDecisionLog.seat(initAndCreateGame().getPlayers().get(0)));
        AssertJUnit.assertEquals("?", AiDecisionLog.seat(null));
    }
}
