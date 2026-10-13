package forge.ai;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.item.IPaperCard;

/**
 * Deck Lab's decision log (this fork's decklab branch): with -Ddecklab.decisionLog=<names> the AI prints
 * one line for each cast or activation of a named card it weighs at priority
 * (AiController.chooseSpellAbilityToPlayFromList), so an experiment can count when a card could have been
 * cast and why it wasn't. Off (no names) it does nothing but one static read. It only reads the game, so it
 * never changes what a seed plays; it isn't a game patch or an AI switch.
 *
 * The property holds card names separated by ";", each with %XX escapes decoded as UTF-8 (only "%"
 * escapes: "+" is a plus), or "*" for every card. It's read once, as the class loads, so a JVM can't
 * change its names. Each line is
 * DLAI|1|turn|active seat|phase|seat|decision|api|card
 * where a seat is the N of an "Ai(N)-" player name (else "?"), the phase is PhaseType's name (or "-"),
 * the decision is the AiPlayDecision, the api is the ability's ApiType (or "-"), and the card is the host
 * card's name with "|", CR and LF made "/". Deck Lab's decklab/lib/forge/decisionlog.py reads them.
 */
public final class AiDecisionLog {
    private AiDecisionLog() {}

    /** The system property: card names separated by ";" (%XX escaped), or "*" for every card. */
    public static final String PROPERTY = "decklab.decisionLog";

    /** The names the property gives: null when off, an empty set for every card ("*"). */
    private static final Set<String> FROM_PROPERTY = parse(System.getProperty(PROPERTY));

    private static volatile Set<String> names = FROM_PROPERTY;

    /** For tests only: logs the names spec gives, as the property would; null goes back to the property. */
    public static void setForTests(String spec) {
        names = spec == null ? FROM_PROPERTY : parse(spec);
    }

    /**
     * The names a spec gives: null for none (off), an empty set for "*" (every card).
     * Package-private for the tests.
     */
    static Set<String> parse(String spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : spec.split(";")) {
            String name = decode(part).trim();
            if ("*".equals(name)) {
                return Collections.emptySet();
            }
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out.isEmpty() ? null : Collections.unmodifiableSet(out);
    }

    /** %XX escapes decoded as UTF-8; anything else, "+" included, as it is. Package-private for the tests. */
    static String decode(String s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                bytes.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2)));
                i += 3;
            } else {
                int cp = s.codePointAt(i);
                byte[] b = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                bytes.write(b, 0, b.length);
                i += Character.charCount(cp);
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static int hex(char c) {
        return Character.digit(c, 16);
    }

    /** Whether the log is on (some names, or "*"). */
    public static boolean on() {
        return names != null;
    }

    /** Prints the AI's opinion of sa when its host card is a named one. Only getters: no state changes. */
    public static void spell(Player player, SpellAbility sa, AiPlayDecision opinion) {
        Set<String> n = names;
        if (n == null || sa == null) {
            return;
        }
        Card host = sa.getHostCard();
        if (host == null) {
            return;
        }
        String card = host.getName();
        if (!n.isEmpty() && !n.contains(card)) {
            IPaperCard paper = host.getPaperCard();
            if (paper == null || !n.contains(paper.getName())) {
                return;
            }
        }
        Game game = host.getGame();
        PhaseHandler ph = game == null ? null : game.getPhaseHandler();
        PhaseType phase = ph == null ? null : ph.getPhase();
        System.out.println("DLAI|1|" + (ph == null ? 0 : ph.getTurn())
                + "|" + seat(ph == null ? null : ph.getPlayerTurn())
                + "|" + (phase == null ? "-" : phase.name())
                + "|" + seat(player)
                + "|" + (opinion == null ? "-" : opinion.name())
                + "|" + (sa.getApi() == null ? "-" : sa.getApi().name())
                + "|" + card.replace('|', '/').replace('\r', '/').replace('\n', '/'));
    }

    /** The N of an "Ai(N)-..." player name, else "?". Package-private for the tests. */
    static String seat(Player player) {
        String name = player == null ? null : player.getName();
        if (name == null || !name.startsWith("Ai(")) {
            return "?";
        }
        int close = name.indexOf(")-", 3);
        if (close <= 3) {
            return "?";
        }
        String n = name.substring(3, close);
        for (int i = 0; i < n.length(); i++) {
            if (n.charAt(i) < '0' || n.charAt(i) > '9') {
                return "?";
            }
        }
        return n;
    }
}
