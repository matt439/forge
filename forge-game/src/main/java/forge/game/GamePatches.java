package forge.game;

/**
 * Whether Deck Lab's game patches play (this fork's decklab branch). Each change listed with an id in
 * forge-game's resource decklab/forge-patches.txt plays Forge's own code with -Ddecklab.gamePatches=off,
 * and changes the games a seed plays otherwise. Deck Lab's card-log check plays with them off to compare
 * with plain Forge. Match and AiAttackController keep their own DECKLAB_* fields, read the same way.
 *
 * "gisa-bontu-1" (commit af513b9fd): the AI plays Ghoulcaller Gisa and God-Eternal Bontu. With game
 * patches off, its Java paths (TokenAi, ComputerUtil's SacToDraw) play Forge's own code, and both cards
 * count as removed from AI decks (removedFromAiWhenOff), the hint Forge's own scripts carry
 * (AI:RemoveDeck:All), which this branch's scripts no longer say. The readers of that hint that can see
 * this class check it too: ComputerUtilCard.isCardRemAIDeck, CardPredicates.isRemAIDeck, PlayEffect,
 * CopyPermanentEffect and PlayerControllerAi's sideboarding. Left open: forge-core's
 * Deck.UnplayableAICards, which can't see forge-game (it lists cards, it doesn't play them).
 */
public final class GamePatches {
    private GamePatches() {}

    /** The system property: "off" plays Forge's own code. */
    public static final String PROPERTY = "decklab.gamePatches";

    /** The Ghoulcaller Gisa and God-Eternal Bontu AI (commit af513b9fd). */
    public static final String GISA_BONTU_1 = "gisa-bontu-1";

    private static final boolean ON = !"off".equals(System.getProperty(PROPERTY));

    private static volatile Boolean forTests;

    /** Whether the game patches play: the property, read once as the class loads, unless a test set it. */
    public static boolean on() {
        Boolean t = forTests;
        return t != null ? t : ON;
    }

    /** For tests only: plays as if the property said on (true) or off (false); null goes back to the property. */
    public static void setForTests(Boolean on) {
        forTests = on;
    }

    /**
     * Whether a card counts as removed from AI decks only because game patches are off: the cards
     * gisa-bontu-1 took AI:RemoveDeck:All from.
     */
    public static boolean removedFromAiWhenOff(String cardName) {
        return !on() && ("Ghoulcaller Gisa".equals(cardName) || "God-Eternal Bontu".equals(cardName));
    }
}
