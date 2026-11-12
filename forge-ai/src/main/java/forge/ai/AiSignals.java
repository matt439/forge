package forge.ai;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counters AiController's Deck Lab changes update while games play, for Deck Lab's game loop and
 * serve mode to read, so a warm worker can tell that a game left a thread of its own behind. (They
 * were decklab.Signals in Deck Lab's card-log jar; Deck Lab's wave 2 contract, "Serve protocol" and
 * "Game-changing patches", says who updates which.)
 *
 *   AI_TIMEOUTS   AI evaluations that hit Forge's AI timeout in this JVM, ever: AiController adds 1
 *                 where Forge prints "AI eval thread at timeout", and (since forge-2.0.16) AITimeoutTracker
 *                 adds 1 each time an AI decision is cut short at its deadline (as this class loads, it
 *                 sets AITimeoutTracker.deckLabOnExpired; Deck Lab's game loop reads AI_TIMEOUTS before
 *                 its first game).
 *   LIVE_EVALS    AI evaluation threads running now: AiController adds 1 as an evaluation starts and
 *                 takes 1 away as it ends (in a finally, inside the task, so before its future
 *                 completes). One still running at a game boundary was left behind by a timeout
 *                 (Forge can't stop it) and may draw from MyRandom, which the next game is reseeded
 *                 with. A virtual thread doesn't show in Thread.getAllStackTraces(), so this count is
 *                 the only way to see it.
 */
public final class AiSignals {
    private AiSignals() {}

    static {
        // forge-2.0.16's AI deadline (forge.util.AITimeoutTracker.check) counts as a timeout too: see its note
        forge.util.AITimeoutTracker.deckLabOnExpired = () -> AI_TIMEOUTS.incrementAndGet();
    }

    /** AI evaluations that hit Forge's AI timeout in this JVM, ever. */
    public static final AtomicInteger AI_TIMEOUTS = new AtomicInteger();

    /** AI evaluation threads running now. */
    public static final AtomicInteger LIVE_EVALS = new AtomicInteger();
}
