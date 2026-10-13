package forge.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.player.Player;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * Deck Lab's AI switches: off unless the hook names the seat and the id, always off with game patches off,
 * and the build lists only switches whose class files it holds.
 */
public class AiSwitchesTest extends DeckLabSwitchTest {

    @Test
    public void testOffWithNoHook() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        gamePatchesOn();
        for (String id : AiSwitches.ids()) {
            AssertJUnit.assertFalse(id + " is on with no hook", AiSwitches.on(ai, id));
        }
        AssertJUnit.assertFalse("a null player has a switch on", AiSwitches.on(null, AiSwitches.ETB_HARMLESS_1));
    }

    @Test
    public void testOnOnlyForTheSeatAndIdTheHookNames() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        Player opponent = game.getPlayers().get(0);
        switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1);

        AssertJUnit.assertTrue(AiSwitches.on(ai, AiSwitches.REMOVAL_IN_COMBAT_1));
        AssertJUnit.assertFalse("another id is on", AiSwitches.on(ai, AiSwitches.ETB_HARMLESS_1));
        AssertJUnit.assertFalse("another seat has it on", AiSwitches.on(opponent, AiSwitches.REMOVAL_IN_COMBAT_1));
        AssertJUnit.assertFalse("a null player has it on", AiSwitches.on(null, AiSwitches.REMOVAL_IN_COMBAT_1));
    }

    @Test
    public void testOffForEveryoneWithGamePatchesOff() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        switchOn(ai, AiSwitches.REMOVAL_IN_COMBAT_1, AiSwitches.ETB_HARMLESS_1, AiSwitches.PROWESS_PRECOMBAT_1);
        gamePatchesOff();
        for (String id : AiSwitches.ids()) {
            AssertJUnit.assertFalse(id + " is on with game patches off", AiSwitches.on(ai, id));
        }
    }

    @Test
    public void testIdsAreTheListedSwitches() {
        AssertJUnit.assertEquals(List.of(AiSwitches.ETB_HARMLESS_1, AiSwitches.PROWESS_PRECOMBAT_1,
                AiSwitches.REMOVAL_IN_COMBAT_1), AiSwitches.ids());
    }

    @Test
    public void testEveryListedClassFileIsInTheBuild() throws IOException {
        ClassLoader loader = AiSwitches.class.getClassLoader();
        List<String> lines = new ArrayList<>();
        try (InputStream in = loader.getResourceAsStream(AiSwitches.LIST)) {
            AssertJUnit.assertNotNull(AiSwitches.LIST + " isn't a resource", in);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        AssertJUnit.assertEquals(AiSwitches.ids().size(), lines.size());
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            AssertJUnit.assertTrue(parts[0] + " lists no class file", parts.length > 1);
            for (int i = 1; i < parts.length; i++) {
                AssertJUnit.assertNotNull(parts[0] + ": " + parts[i] + " isn't in the build", loader.getResource(parts[i]));
            }
        }
    }
}
