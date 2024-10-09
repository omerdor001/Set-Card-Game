package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class DealerTest {
    Dealer dealer;
    Env env;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;
    private Table table;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Logger logger;


    @BeforeEach
    void setUp() {
        env = new Env(logger, new Config(logger, (String) null), ui, util);
        Player[] players=new Player[3];
        slotToCard = new Integer[4];
        cardToSlot = new Integer[4];
        table = new Table(env, slotToCard, cardToSlot);
        dealer=new Dealer(env,table,players);
        for(int i=0;i<players.length;i++){
            players[i]=new Player(env,dealer,table,i,true);
        }
    }

    private int fillSomeSlots() {
        table.slotToCard[1] = 1;
        table.slotToCard[2] = 2;
        table.slotToCard[3] = 3;
        table.cardToSlot[3] = 3;
        table.cardToSlot[2] = 2;
        table.cardToSlot[1] = 1;

        return 2;
    }

    //Our tests

    @Test
    void announceWinner(){
        boolean exceptedResult=true;
        dealer.getPlayers()[0].setScore(3);
        dealer.getPlayers()[1].setScore(5);
        dealer.getPlayers()[2].setScore(7);
        int [] winners=new int[1];
        winners[0]=dealer.getPlayers()[2].id;
        dealer.announceWinnersTests();
        assertEquals(exceptedResult,dealer.isAnnounced);
        verify(ui).announceWinner(eq(winners));
    }

    @Test
    void removeCardsFromTable(){
        fillSomeSlots();
        int numberCardsP=table.countCards();
        dealer.geCardToRemove().add(1);
        dealer.geCardToRemove().add(2);
        dealer.geCardToRemove().add(3);
        dealer.removeCardsFromTableTest();
        int numberCardsA=table.countCards();
        assertEquals(3,numberCardsP-numberCardsA);
        verify(ui).removeCard(eq(1));
        verify(ui).removeCard(eq(2));
        verify(ui).removeCard(eq(3));
    }






}
