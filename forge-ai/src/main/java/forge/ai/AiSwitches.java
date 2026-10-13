package forge.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;

import forge.game.GamePatches;
import forge.game.player.Player;

/**
 * Deck Lab's AI switches (this fork's decklab branch): AI fixes that are off unless a seat's pilot names
 * them. Deck Lab plugs a hook in as it starts (its decklab.Pilots, from each seat's -dlpilot), and a
 * changed line of Forge's AI asks on(player, id) first, so with every switch off it plays Forge's own code.
 * With -Ddecklab.gamePatches=off every switch is off. The resource decklab/ai-switches.txt lists each id
 * with the class files that hold it; Deck Lab reads that list to know which switches a build has.
 * An id's behaviour never changes once a build with it has been used: a change gets a new id.
 */
public final class AiSwitches {
    private AiSwitches() {}

    /** Removal of a blocker that would kill the AI's attacker, on the AI's own turn (ComputerUtilCard). */
    public static final String REMOVAL_IN_COMBAT_1 = "removal-in-combat-1";
    /** ETB and sub-ability vetoes that don't apply when an optional target has none to pick. */
    public static final String ETB_HARMLESS_1 = "etb-harmless-1";
    /** Cheap card draw and burn cast in main 1 when a prowess creature can attack. */
    public static final String PROWESS_PRECOMBAT_1 = "prowess-precombat-1";

    /** The resource that lists the switches: one "id class-file..." a line, # for comments. */
    public static final String LIST = "decklab/ai-switches.txt";

    private static volatile BiPredicate<Player, String> hook;

    /** Plugs a hook in (null: none, every switch off). */
    public static void setHook(BiPredicate<Player, String> h) {
        hook = h;
    }

    /**
     * Whether switch id is on for player: never without a hook, or with game patches off. Without a hook
     * it's one volatile read; with one, a second (GamePatches' test setting) before the hook is asked.
     */
    public static boolean on(Player player, String id) {
        BiPredicate<Player, String> h = hook;
        return h != null && player != null && GamePatches.on() && h.test(player, id);
    }

    /** The ids the list names, sorted and distinct (empty if it can't be read). */
    public static List<String> ids() {
        return Ids.IDS;
    }

    private static final class Ids {
        static final List<String> IDS = read();

        private static List<String> read() {
            TreeSet<String> ids = new TreeSet<>();
            ClassLoader loader = AiSwitches.class.getClassLoader();
            try (InputStream in = loader == null ? null : loader.getResourceAsStream(LIST)) {
                if (in == null) {
                    return Collections.emptyList();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    ids.add(line.split("\\s+")[0]);
                }
            } catch (IOException e) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(ids));
        }
    }
}
