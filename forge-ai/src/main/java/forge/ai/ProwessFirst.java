package forge.ai;

import java.util.EnumSet;
import java.util.Set;

import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;

/**
 * Deck Lab's AI switch "prowess-precombat-1" (this fork's decklab branch): cheap card draw and burn cast in
 * main 1, before attacks, when a creature that can attack this turn gets something from the spell (prowess,
 * or a "whenever you cast" trigger the spell satisfies). Forge's AI holds most draw spells until main 2
 * (DrawAi's checkPhaseRestrictions, upstream issue #6617), so a prowess creature attacks unpumped.
 * ComputerUtil.castSpellInMain1 asks castNow; with the switch off it answers false before anything else,
 * so the AI plays Forge's own code. ProwessPrecombatSwitchTest (forge-gui-desktop's tests) proves both.
 */
public final class ProwessFirst {
    private ProwessFirst() {}

    /** The root APIs it covers: card draw, library filtering and damage. */
    private static final Set<ApiType> APIS = EnumSet.of(ApiType.Draw, ApiType.Dig, ApiType.Scry,
            ApiType.Surveil, ApiType.DealDamage);

    /** The highest mana value it casts early. */
    private static final int MAX_MANA_VALUE = 3;

    /**
     * Whether sa should count as "cast in main 1": the switch is on for ai, sa is a noncreature, nonland
     * spell of mana value 3 or less whose root API is one of APIS, it's the AI's own MAIN1, and an untapped
     * creature of the AI's that can attack has prowess or a SpellCast trigger whose ValidCard the spell's
     * card is valid for.
     */
    public static boolean castNow(final Player ai, final SpellAbility sa) {
        if (!AiSwitches.on(ai, AiSwitches.PROWESS_PRECOMBAT_1)) {
            return false;
        }
        final Card source = sa.getHostCard();
        if (source == null || source.isCreature() || source.isLand() || !sa.isSpell()
                || source.getCMC() > MAX_MANA_VALUE || !APIS.contains(sa.getRootAbility().getApi())) {
            return false;
        }
        final PhaseHandler ph = ai.getGame().getPhaseHandler();
        if (!ph.isPlayerTurn(ai) || !ph.is(PhaseType.MAIN1)) {
            return false;
        }
        for (Card c : ai.getCreaturesInPlay()) {
            if (c.isTapped() || !CombatUtil.canAttack(c)) {
                continue;
            }
            if (c.hasKeyword(Keyword.PROWESS) || hasSpellCastTrigger(c, source)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSpellCastTrigger(final Card creature, final Card spell) {
        for (Trigger t : creature.getTriggers()) {
            if (t.getMode() == TriggerType.SpellCast && t.matchesValidParam("ValidCard", spell)) {
                return true;
            }
        }
        return false;
    }
}
