package bguspl.set.ex;

import bguspl.set.Env;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The tokens which the player put on the table.
     */
    private final ConcurrentLinkedQueue<Integer> playerClaims;

    /**
     * Cards to remove from the table.
     */
    private final ConcurrentLinkedQueue<Integer> cardToRemove;

    /**
     * Object lock on the dealer.
     */
    public final Object lockDealer;

    /**
     * True iff game should be terminated.
     */
    public boolean isAnnounced;



    /**
     *
     *
     * MAGIC-NUMBERS
     *
     *
     * Max tokens for a player.
     */
    public final int MAX_TOKENS_FOR_PLAYER = 3;

    /**
     * Sleep time for the dealer.
     */
    private final long DEALER_TIMEOUT = 5;

    /**
     * Zero penalty time
     */
    private final int NO_PENALTY = 0;

    /**
     * SECOND - 1000 millis.
     */
    private final int SECOND = 1000;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param table  - the table object.
     * @param players     - the players' ids.
     */
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playerClaims = new ConcurrentLinkedQueue<>();
        this.cardToRemove = new ConcurrentLinkedQueue<>();
        this.lockDealer = new Object();
        this.isAnnounced=false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        initiatePlayersThreads();
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            if(env.config.hints == true){
                table.hints();
            }
            updateTimerDisplay(true);
            timerLoop();
            removeAllCardsFromTable();
        }
        terminate();
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            if(!terminate){
                updateTimerDisplay(false);
                removeCardsFromTable();
                placeCardsOnTable();
            }
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        for(Player player : players){
            player.terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        table.setReshuffle(true);
        for (int slot : cardToRemove) {
            for (Player player : players) {
                if (player.getTokens().contains(slot)) {
                    table.removeToken(player.id, slot);
                    player.getTokens().remove(slot);
                }
            }
        }
        for (int slot : cardToRemove) {
            table.removeCard(slot);
            cardToRemove.remove(slot);
        }
        table.setReshuffle(false);
    }

    /**
     * Checks cards should be removed from the table and removes them - for tests use.
     */
    public void removeCardsFromTableTest(){
        removeCardsFromTable();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.setReshuffle(true);
        List<Integer> slotsToPlace = new ArrayList<>();
        for (int i = 0; i < table.getSlotToCardLength(); i++) {
            if (table.isSlotEmpty(i)) {
                slotsToPlace.add(i);
            }
        }
        Collections.shuffle(slotsToPlace);
        for (int i = 0; i < slotsToPlace.size(); i++) {
            if (deck.size() > 0) {
                table.placeCard(deck.get(0), slotsToPlace.get(i));
                deck.remove(0);
            }
        }
        table.setReshuffle(false);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized (lockDealer) {
            try {
                lockDealer.wait(DEALER_TIMEOUT);
            } catch (InterruptedException ignore) {
            }
        }
        if(!terminate) {
            checkSet();
            updatePlayersPenalty();
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        } else if (reshuffleTime - System.currentTimeMillis() - DEALER_TIMEOUT > env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis() - DEALER_TIMEOUT, false);
        } else {
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis() - DEALER_TIMEOUT, 0), true);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.setReshuffle(true);
        for (Player player : players) {
            player.getTokens().clear();
        }
        List<Integer> slotsToRemove = new ArrayList<>();
        for (int i = 0; i < table.getSlotToCardLength(); i++) {
            if (!table.isSlotEmpty(i)) {
                slotsToRemove.add(i);
            }
        }
        env.ui.removeTokens();
        Collections.shuffle(slotsToRemove);
        for (int slot = 0; slot < slotsToRemove.size(); slot++) {
            int cardToDeck = table.getCard(slotsToRemove.get(slot));
            deck.add(cardToDeck);
            table.removeCard(slotsToRemove.get(slot));
        }
        table.setReshuffle(false);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Integer> winnersTemp = new ArrayList<>();
        int max = players[0].score();
        for (int i = 1; i < players.length; i++) {
            if (players[i].score() > max) {
                max = players[i].score();
            }
        }
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == max) {
                winnersTemp.add(players[i].id);
            }
        }
        int[] winners = new int[winnersTemp.size()];
        for (int i = 0; i < winners.length; i++) {
            winners[i] = winnersTemp.get(i);
        }
        env.ui.announceWinner(winners);
        isAnnounced=true;
    }

    /**
     * Check who is/are the winner/s and displays them - for tests use.
     */
    public void announceWinnersTests(){
        announceWinners();
    }

    /**
     * create and run players threads.
     */
    private void initiatePlayersThreads() {
        for (int i = 0; i < players.length; i++) {
            Thread playerThread = new Thread(players[i], "player-" + players[i].id);
            playerThread.start();
        }
    }

    /**
     * This method coverts the tokens queue to array int cards.
     *
     * @return - int[] set of cards.
     */
    private int[] getSetByCards(ConcurrentLinkedQueue<Integer> playersTokens) {
        int[] setToReturn = new int[MAX_TOKENS_FOR_PLAYER];
        int index = 0;
        for (Integer integer : playersTokens) {
            setToReturn[index] = table.getCard(integer);
            index++;
        }
        return setToReturn;
    }

    /**
     *
     * Returns players list.
     * @return - player's list
     */
    public Player[] getPlayers(){
        return players;
    }

    /**
     * Update which cards need to be removed.
     *
     * @param slots - the slots to remove.
     */
    public void updateCardToRemove(ConcurrentLinkedQueue<Integer> slots) {
        for(int slot: slots){
            cardToRemove.add(slot);
        }
    }

    /**
     * Update players penalty (if theres one).
     *
     */
    private void updatePlayersPenalty() {
        for (Player player : players) {
            if (player.getPenaltyTime() > NO_PENALTY) {
                player.setPenaltyTime(Math.max(0,player.getPenaltyTime() - DEALER_TIMEOUT));
                if (player.getPenaltyTime() % SECOND == NO_PENALTY) {
                    env.ui.setFreeze(player.id, player.getPenaltyTime());
                }
            }
        }
    }


    /**
     * Check if there are players who claimed set.
     */
    private void checkSet() {
        if (!playerClaims.isEmpty()) {
            int playerId = playerClaims.remove();
            ConcurrentLinkedQueue<Integer> playersTokens = players[playerId].getTokens();
            if (playersTokens.size() != MAX_TOKENS_FOR_PLAYER) {
                synchronized (players[playerId].pointPenaltyLock) {
                    players[playerId].setFreeze(false);
                    players[playerId].pointPenaltyLock.notifyAll();
                }
            } else {
                int[] intTokens = getSetByCards(playersTokens);
                if (!env.util.testSet(intTokens)) {
                    players[playerId].penalty();
                } else {
                    updateCardToRemove(playersTokens);
                    players[playerId].point();
                }
                synchronized (players[playerId].pointPenaltyLock){
                    players[playerId].pointPenaltyLock.notifyAll();
                }
            }
        }
    }

    /**
     * Add a player id to the players who finished sets.
     *
     * @param player - the player id.
     */
    public void playerIdFinishedSet(int player) {
        playerClaims.add(player);
    }

    /**
     *
     * Returns cards to remove
     * @return - cards to remove
     */
    public ConcurrentLinkedQueue<Integer> geCardToRemove(){
        return cardToRemove;
    }




}
