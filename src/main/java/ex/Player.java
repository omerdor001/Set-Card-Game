package bguspl.set.ex;

import java.util.concurrent.ConcurrentLinkedQueue;
import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Game's dealer.
     */
    private final Dealer dealer;

    /**
     * The tokens which the player put on the table.
     */
    private final ConcurrentLinkedQueue<Integer> tokens;

    /**
     * Penalty time of player.
     */
    private volatile long penaltyTime;

    /**
     *  True iff player got a penalty.
     */
    private volatile boolean penalty;

    /**
     * True iff player got a point.
     */
    private volatile boolean point;

    /**
     * True iff player is frozen.
     */
    private volatile boolean freeze;

    /**
     * Last key press.
     */
    private volatile int keyPress;


    /**
     * Key Press Object.
     */
    private Object keyPressLock;

    /**
     * point/penalty  Object.
     */
    public Object pointPenaltyLock;


    /**
     *
     *
     * MAGIC-NUMBERS
     *
     *
     * AI key press delay.
     */
    private final long PRESS_DELAY = 1;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.tokens = new ConcurrentLinkedQueue<>();
        this.penaltyTime = 0;
        this.freeze = false;
        this.pointPenaltyLock = new Object();
        this.keyPressLock = new Object();
        this.keyPress = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            synchronized (keyPressLock) {
                try {
                    keyPressLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
            if (tokens.contains(keyPress)) {
                tokens.remove(keyPress);
                table.removeToken(id, keyPress);
            } else if (tokens.size() < dealer.MAX_TOKENS_FOR_PLAYER) {
                tokens.add(keyPress);
                table.placeToken(id,keyPress);
                if (tokens.size() == dealer.MAX_TOKENS_FOR_PLAYER) {
                    freeze = true;
                    dealer.playerIdFinishedSet(id);
                    synchronized (pointPenaltyLock) {
                        try {
                            pointPenaltyLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
            if(point){
                pointUpdate();
            }
            else if(penalty){
                penaltyUpdate();
            }
        }
        if (!human) {
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                keyPressedGeneral((int) (Math.random() * table.getSlotToCardLength()));
                try {
                    synchronized (this) {
                        wait(PRESS_DELAY);
                    }
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(this.human){
            keyPressedGeneral(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        score = score + 1;
        env.ui.setScore(id, score);
        point = true;
    }

    /**
     * /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        penalty = true;
    }

    /**
     * This method is called when a key is pressed or AI called it.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    private void keyPressedGeneral(int slot){
        if (freeze || table.getReshuffle() || table.isSlotEmpty(slot)) {
            return;
        }
        synchronized (keyPressLock) {
            keyPress = slot;
            keyPressLock.notifyAll();
        }
    }

    /**
     * Update player and dealer after the player got a point.
     *
     */
    private void pointUpdate() {
        for (int slot : tokens) {
            tokens.remove(slot);
            table.removeToken(id, slot);
        }
        point = false;
        penaltyTime = env.config.pointFreezeMillis;
        env.ui.setFreeze(id, penaltyTime);
        try {
            Thread.sleep(penaltyTime);
        } catch (InterruptedException ignored) {
        }
        freeze = false;
    }

    /**
     * Update player and dealer after the player got a penalty.
     *
     */
    private void penaltyUpdate() {
        penalty = false;
        penaltyTime = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(id, penaltyTime);
        try {
            Thread.sleep(penaltyTime);
        } catch (InterruptedException ignored) {
        }
        freeze = false;
    }

    /**
     *
     * Returns player's score
     * @return - player's score.
     */
    public int score() {
        return score;
    }

    /**
     *
     * Set player's score.
     * @param updateScore - player score to set
     */
    public void setScore(int updateScore){ score=updateScore;}
    /**
     *
     * Returns player's tokens
     * @return - player's tokens.
     */
    public ConcurrentLinkedQueue<Integer> getTokens() {
        return tokens;
    }

    /**
     *
     * Returns player's penalty time.
     * @return - player's penalty time
     */
    public long getPenaltyTime() {
        return penaltyTime;
    }

    /**
     *
     * Set player's penalty time.
     * @param toUpdate - player penaltyTime to set
     */
    public void setPenaltyTime(long toUpdate) {
        penaltyTime = toUpdate;
    }

    /**
     *
     * Set player freeze state.
     * @param toUpdate - player penaltyTime to set
     */
    public void setFreeze(boolean toUpdate) {
        freeze = toUpdate;
    }

    /**
     *
     * Returns penalty state.
     * @return - player's penalty state
     */
    public boolean getPenaltyState() {
        return penalty;
    }

    /**
     *
     * Returns key press.
     * @return - player's key press
     */
    public int getKeyPress(){
        return keyPress;
    }




}
