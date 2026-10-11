package forge.game;

import com.google.common.collect.*;
import com.google.common.eventbus.EventBus;
import forge.LobbyPlayer;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.event.Event;
import forge.game.event.GameEventAddLog;
import forge.game.event.GameEventAnteCardsSelected;
import forge.game.event.GameEventGameFinished;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.trigger.Trigger;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.util.Localizer;
import forge.util.MyRandom;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.Map.Entry;

public class Match {
    private List<PaperCard> removedCards = Lists.newArrayList();
    private final List<RegisteredPlayer> players;
    private final GameRules rules;
    private final String title;
    private static final boolean DECKLAB_ORDER = !"off".equals(System.getProperty("decklab.gamePatches")); // Deck Lab: deck-order-1 on
    private final EventBus events = new EventBus("match events");
    private final Map<Integer, GameOutcome> gameOutcomes = Maps.newHashMap();

    private GameOutcome lastOutcome = null;

    public Match(final GameRules rules0, final List<RegisteredPlayer> players0, final String title) {
        players = Collections.unmodifiableList(Lists.newArrayList(players0));
        rules = rules0;
        this.title = title;
    }

    public GameRules getRules() {
        return rules;
    }
    String getTitle() {
        final Multiset<RegisteredPlayer> wins = getGamesWon();
        final StringBuilder titleAppend = new StringBuilder(title);
        titleAppend.append(" (");
        for (final RegisteredPlayer rp : players) {
            titleAppend.append(wins.count(rp)).append('-');
        }
        titleAppend.deleteCharAt(titleAppend.length() - 1);
        titleAppend.append(')');
        return titleAppend.toString();
    }

    public void addGamePlayed(Game finished) {
        if (!finished.isGameOver()) {
            throw new IllegalStateException("Game is not over yet.");
        }
        lastOutcome = finished.getOutcome();
        gameOutcomes.put(finished.getId(), finished.getOutcome());
    }

    public Game createGame() {
        return new Game(players, rules, this);
    }

    public void startGame(final Game game) {
        startGame(game, null);
    }

    public void startGame(final Game game, Runnable startGameHook) {
        prepareAllZones(game);
        if (rules.useAnte()) {  // Deciding which cards go to ante
            Multimap<Player, Card> list = game.chooseCardsForAnte(rules.getMatchAnteRarity(), rules.getAnteIncludeBasicLands());
            for (Entry<Player, Card> kv : list.entries()) {
                Player p = kv.getKey();
                game.getAction().moveTo(ZoneType.Ante, kv.getValue(), null, AbilityKey.newMap());
                game.fireEvent(new GameEventAddLog(GameLogEntryType.ANTE, p + " anted " + kv.getValue()));
            }
            game.fireEvent(GameEventAnteCardsSelected.fromCards(list));
        }

        game.getAction().startGame(this.lastOutcome, startGameHook);

        // Typically ante, but also tearing up a blacker lotus
        executeOwnershipChanges(game);

        game.clearCaches();

        // will pull UI dialog, when the UI is listening
        game.fireEvent(new GameEventGameFinished());

        //run GC after game is finished
        System.gc();
    }

    public GameOutcome getOutcomeById(int id) {
        return gameOutcomes.get(id);
    }

    public void clearGamesPlayed() {
        gameOutcomes.clear();
        for (RegisteredPlayer p : players) {
            p.restoreDeck();
        }
    }

    public Collection<GameOutcome> getOutcomes() {
        return gameOutcomes.values();
    }

    public GameOutcome getLastOutcome() {
        return lastOutcome;
    }

    public boolean isMatchOver() {
        int[] victories = new int[players.size()];
        for (GameOutcome go : getOutcomes()) {
            LobbyPlayer winner = go.getWinningLobbyPlayer();
            int i = 0;
            for (RegisteredPlayer p : players) {
                if (p.getPlayer().equals(winner)) {
                    victories[i]++;
                    if (victories[i] >= rules.getGamesToWinMatch()) {
                        return true;
                    }
                }
                i++;
            }
        }

        // Games are first to X wins, not first to X wins or Y total games played
        return false;
    }

    public int getGamesWonBy(LobbyPlayer questPlayer) {
        int sum = 0;
        for (GameOutcome go : getOutcomes()) {
            if (questPlayer.equals(go.getWinningLobbyPlayer())) {
                sum++;
            }
        }
        return sum;
    }
    public Multiset<RegisteredPlayer> getGamesWon() {
        final Multiset<RegisteredPlayer> won = HashMultiset.create(players.size());
        for (final GameOutcome go : getOutcomes()) {
            if (go.getWinningPlayer() == null) {
                // Game hasn't finished yet. Exit early.
                return won;
            }
            won.add(go.getWinningPlayer());
        }
        return won;
    }

    public boolean isWonBy(LobbyPlayer questPlayer) {
        return getGamesWonBy(questPlayer) >= rules.getGamesToWinMatch();
    }

    public RegisteredPlayer getWinner() {
        if (this.isMatchOver()) {
            return lastOutcome.getWinningPlayer();
        }
        return null;
    }

    public List<RegisteredPlayer> getPlayers() {
        return players;
    }

    private static Set<PaperCard> getRemovedAnteCards(Deck toUse) {
        final String keywordToRemove = "Remove CARDNAME from your deck before playing if you're not playing for ante.";
        Set<PaperCard> myRemovedAnteCards = new HashSet<>();
        for (Entry<DeckSection, CardPool> ds : toUse) {
            for (Entry<PaperCard, Integer> cp : ds.getValue()) {
                if (Iterables.contains(cp.getKey().getRules().getMainPart().getKeywords(), keywordToRemove)) {
                    myRemovedAnteCards.add(cp.getKey());
                }
            }
        }
        return myRemovedAnteCards;
    }

    public List<PaperCard> getRemovedCards() { return removedCards; }

    public void removeCard(PaperCard c) {
        removedCards.add(c);
    }

    private static void preparePlayerZone(Player player, final ZoneType zoneType, CardPool section, boolean canRandomFoil) {
        PlayerZone library = player.getZone(zoneType);
        List<Card> newLibrary = new ArrayList<>();
        for (final Entry<PaperCard, Integer> stackOfCards : DECKLAB_ORDER && zoneType == ZoneType.Library ? LibraryOrder.entries(player, section) : section) { // Deck Lab: the .dck's line order
            final PaperCard cp = stackOfCards.getKey();
            for (int i = 0; i < stackOfCards.getValue(); i++) {
                final Card card = Card.fromPaperCard(cp, player);

                // Assign card-specific foiling or random foiling on approximately 1:20 cards if enabled
                if (cp.isFoil() || (canRandomFoil && MyRandom.percentTrue(5))) {
                    card.setRandomFoil();
                }
                card.setCollectible(true);

                newLibrary.add(card);
            }
        }
        library.setCards(newLibrary);
    }

    private void prepareAllZones(final Game game) {
        // need this code here, otherwise observables fail
        Trigger.resetIDs();
        game.getTriggerHandler().clearDelayedTrigger();

        // friendliness
        Map<Player, Map<DeckSection, List<? extends PaperCard>>> rAICards = new HashMap<>();
        Multimap<Player, PaperCard> removedAnteCards = ArrayListMultimap.create();
        Map<Player, List<PaperCard>> unsupported = new HashMap<>();

        final FCollectionView<Player> players = game.getPlayers();
        final List<RegisteredPlayer> playersConditions = game.getMatch().getPlayers();

        boolean isFirstGame = gameOutcomes.isEmpty();
        boolean canSideBoard = !isFirstGame && rules.getGameType().isSideboardingAllowed();
        // Only allow this if feature flag is on AND for certain match types
        boolean sideboardForAIs = rules.getSideboardForAI() &&
            rules.getGameType().getDeckFormat().equals(DeckFormat.Constructed);
        PlayerController sideboardProxy = null;
        if (canSideBoard && sideboardForAIs) {
            for (int i = 0; i < players.size(); i++) {
                final Player player = players.get(i);
                //final RegisteredPlayer psc = playersConditions.get(i);
                if (!player.getController().isAI()) {
                    sideboardProxy = player.getController();
                    break;
                }
            }
        }

        for (int i = 0; i < playersConditions.size(); i++) {
            final Player player = players.get(i);
            final RegisteredPlayer psc = playersConditions.get(i);
            PlayerController person = player.getController();

            if (canSideBoard) {
                if (sideboardProxy != null && person.isAI()) {
                    person = sideboardProxy;
                }

                Deck toChange = psc.getDeck();
                if (!getRemovedCards().isEmpty()) {
                    CardPool main = new CardPool();
                    main.addAll(toChange.get(DeckSection.Main));
                    CardPool sideboard = new CardPool();
                    sideboard.addAll(toChange.getOrCreate(DeckSection.Sideboard));
                    for (PaperCard c : removedCards) {
                        if (main.contains(c)) {
                            main.remove(c, 1);
                        } else if (sideboard.contains(c)) {
                            sideboard.remove(c, 1);
                        }
                    }
                    toChange.getMain().clear();
                    toChange.getMain().addAll(main);
                    toChange.get(DeckSection.Sideboard).clear();
                    toChange.get(DeckSection.Sideboard).addAll(sideboard);
                }
                List<PaperCard> newMain = person.sideboard(toChange, rules.getGameType(), player.getName());
                if (null != newMain) {
                    CardPool allCards = new CardPool();
                    allCards.addAll(toChange.get(DeckSection.Main));
                    allCards.addAll(toChange.getOrCreate(DeckSection.Sideboard));
                    for (PaperCard c : newMain) {
                        allCards.remove(c);
                    }
                    toChange.getMain().clear();
                    toChange.getMain().add(newMain);
                    toChange.get(DeckSection.Sideboard).clear();
                    toChange.get(DeckSection.Sideboard).addAll(allCards);
                }
            }

            Deck toCheck = psc.getDeck();
            if (toCheck == null) {
                try {
                    System.err.println(psc.getPlayer().getName() + " Deck is NULL...");
                    int val = rules.getGameType().getDeckFormat().getMainRange().getMinimum();
                    toCheck = new Deck("NULL");
                    if (val > 0)
                        toCheck.getMain().add("Wastes", val);
                } catch (Exception ignored) {}
            }
            Pair<Deck, List<PaperCard>> myDeck = toCheck.getValid();
            player.setDraftNotes(myDeck.getLeft().getDraftNotes());

            Set<PaperCard> myRemovedAnteCards = null;
            if (!rules.useAnte()) {
                myRemovedAnteCards = getRemovedAnteCards(myDeck.getLeft());
                for (PaperCard cp: myRemovedAnteCards) {
                    for (Entry<DeckSection, CardPool> ds : myDeck.getLeft()) {
                        ds.getValue().removeAll(cp);
                    }
                }
            }

            preparePlayerZone(player, ZoneType.Library, myDeck.getLeft().getMain(), psc.useRandomFoil());
            if (myDeck.getLeft().has(DeckSection.Sideboard)) {
                preparePlayerZone(player, ZoneType.Sideboard, myDeck.getLeft().get(DeckSection.Sideboard), psc.useRandomFoil());

                player.assignCompanion(game, person);
            }

            player.initVariantsZones(psc);

            player.shuffle(null);

            if (isFirstGame) {
                Map<DeckSection, List<? extends PaperCard>> cardsComplained = player.getController().complainCardsCantPlayWell(myDeck.getLeft());
                if (cardsComplained != null && !cardsComplained.isEmpty()) {
                    rAICards.put(player, cardsComplained);
                }
            } else {
                //reset cards to fix weird issues on netplay nextgame client
                for (Card c : player.getCardsIn(ZoneType.Library)) {
                    c.setTapped(false);
                    c.resetActivationsPerTurn();
                }
            }

            if (myRemovedAnteCards != null && !myRemovedAnteCards.isEmpty()) {
                removedAnteCards.putAll(player, myRemovedAnteCards);
            }
            unsupported.put(player, myDeck.getRight());
        }

        final Localizer localizer = Localizer.getInstance();
        if (!rAICards.isEmpty() && !rules.getGameType().isCardPoolLimited() && rules.warnAboutAICards()) {
            game.getAction().revealUnplayableByAI(localizer.getMessage("lblAICantPlayCards"), rAICards);
        }

        if (!removedAnteCards.isEmpty()) {
            game.getAction().revealAnte(localizer.getMessage("lblAnteCardsRemoved"), removedAnteCards);
        }

        if (!unsupported.isEmpty()) {
            game.getAction().revealUnsupported(unsupported);
        }
    }

    private void executeOwnershipChanges(Game lastGame) {
        GameOutcome outcome = lastGame.getOutcome();

        // remove all the lost cards from owners' decks
        List<PaperCard> losses = new ArrayList<>();
        int cntPlayers = players.size();
        int iWinner = -1;
        for (int i = 0; i < cntPlayers; i++) {
            Player gamePlayer = lastGame.getRegisteredPlayers().get(i);
            RegisteredPlayer registered = gamePlayer.getRegisteredPlayer();

            // Add/Remove Cards lost via ChangeOwnership cards like Darkpact
            CardCollectionView lostOwnership = gamePlayer.getLostOwnership();
            CardCollectionView gainedOwnership = gamePlayer.getGainedOwnership();

            if (!lostOwnership.isEmpty()) {
                List<PaperCard> lostPaperOwnership = new ArrayList<>();
                for (Card c : lostOwnership) {
                    lostPaperOwnership.add((PaperCard)c.getPaperCard());
                }
                outcome.addAnteLost(registered, lostPaperOwnership);
            }

            if (!gainedOwnership.isEmpty()) {
                List<PaperCard> gainedPaperOwnership = new ArrayList<>();
                for (Card c : gainedOwnership) {
                    gainedPaperOwnership.add((PaperCard)c.getPaperCard());
                }
                outcome.addAnteWon(registered, gainedPaperOwnership);
            }

            if (!getRules().useAnte()) {
                continue;
            }

            if (outcome.isDraw()) {
                continue;
            }

            if (!gamePlayer.hasLost()) {
                iWinner = i;
                continue; // not a loser
            }

            Deck losersDeck = players.get(i).getDeck();
            List<PaperCard> personalLosses = new ArrayList<>();
            for (Card c : gamePlayer.getCardsIn(ZoneType.Ante)) {
                if (!c.isCollectible())
                    continue;
                PaperCard toRemove = (PaperCard) c.getPaperCard();
                // this could miss the cards by returning instances that are not equal to cards found in deck
                // (but only if the card has multiple prints in a set)
                losersDeck.getMain().remove(toRemove);
                personalLosses.add(toRemove);
                losses.add(toRemove);
            }

            outcome.addAnteLost(registered, personalLosses);
        }

        if (rules.useAnte() && iWinner >= 0) {
            // Winner gains these cards always
            Player fromGame = lastGame.getRegisteredPlayers().get(iWinner);
            RegisteredPlayer registered = fromGame.getRegisteredPlayer();
            outcome.addAnteWon(registered, losses);

            if (rules.getGameType().canAddWonCardsMidGame()) {
                // But only certain game types lets you swap midgame
                List<PaperCard> chosen = fromGame.getController().chooseCardsYouWonToAddToDeck(losses);
                if (null != chosen) {
                    Deck deck = players.get(iWinner).getDeck();
                    for (PaperCard c : chosen) {
                        deck.getMain().add(c);
                    }
                }
            }
            // Other game types (like Quest) need to do something in their own calls to actually update data
        }
    }

    public GameOutcome.AnteResult getAnteResult(RegisteredPlayer player) {
        GameOutcome.AnteResult out = new GameOutcome.AnteResult();
        for (GameOutcome outcome : gameOutcomes.values()) {
            GameOutcome.AnteResult gameAnte = outcome.getAnteResult(player);
            if (gameAnte == null) {
                continue;
            }
            out.addWon(gameAnte.wonCards);
            out.addLost(gameAnte.lostCards);
        }
        return out;
    }

    /**
     * Fire only the events after they became real for gamestate and won't get replaced.<br>
     * The events are sent to UI, log and sound system. Network listeners are under development.
     */
    public void fireEvent(final Event event) {
        events.post(event);
    }
    public void subscribeToEvents(final Object subscriber) {
        events.register(subscriber);
    }

}

/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Deck Lab's changes to Forge's forge.game.Match (this fork's decklab branch, from tag forge-2.0.15,
 * forge-game/src/main/java/forge/game/Match.java). Forge's file has no licence header of its own;
 * Forge is GPL-3 (its LICENSE), so this copy is too, under the header Forge's other source files
 * carry, above. This note is at the end so that every line above it keeps its line number in
 * Forge's file, and stack traces line up with Forge's.
 *
 * What changed, each line marked "Deck Lab" (the wave 3 contract's "deck-order-1",
 * decklab/forge-trial/sim-wave3-contract.md section 3.2): preparePlayerZone builds a player's library
 * from the order forge.game.LibraryOrder's hook gives, when there is one
 * (Deck Lab plugs its decklab.DeckOrder in as it starts): the request's .dck [Main] lines in their order,
 * copies expanded, one (card, 1) entry each, so Forge's own loop body makes the Cards with the same
 * Card.fromPaperCard, foil and setCollectible calls per card, in that order, and draws as many
 * MyRandom numbers as it would. The hook gives Forge's own section back (its hash order) when it has
 * no order for the player (v1 games, tournament mode, a seat whose names don't match: an unsupported
 * card left out, or a name Forge rewrote), and for every zone but the library. Nothing else is
 * changed, but the one field that says which way it plays (DECKLAB_ORDER).
 * With -Ddecklab.gamePatches=off (read once, as the class loads) it plays Forge's own code: the
 * library in the section's hash order. Deck Lab's card-log check plays it that way to compare with
 * plain Forge.
 * Why: a deck section is a CardPool backed by a hash map, so Forge builds the library in the hash
 * order of its PaperCards before the shuffle, and a one-card change to a list moves many other cards
 * to new places in it. In the .dck's line order, a variant written in its base list's order (a new
 * card in a removed copy's line) keeps every other card in its place, so the same seed shuffles both
 * libraries the same way and a paired test's two games stay the same until the changed card is seen
 * (sim-investigation.md R15). It changes the games a seed plays, so Deck Lab's engine id names it
 * ("deck-order-1" in decklab/forge-patches.txt), and a change to what it plays needs a new id.
 *
 * This was a copy in Deck Lab's card-log jar (decklab/lib/forge/simjar.py in Deck Lab's repo) until it
 * moved into this fork's decklab branch. forge-game's resource decklab/forge-patches.txt lists it, and
 * Deck Lab reads that list to know which of these changes the jar holds.
 */
