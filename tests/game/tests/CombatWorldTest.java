package game.tests;

import game.app.*;
import game.collision.*;
import game.combat.*;
import game.racer.*;
import game.world.*;


import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class CombatWorldTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String xml="""
            <map><objectgroup name="race">
            <object name="spawn" x="100" y="100"><point/></object>
            <object name="bot_spawn" x="100" y="140"><point/></object>
            <object name="checkpoint" x="300" y="50" width="5" height="200">
            <properties><property name="order" type="int" value="1"/></properties></object>
            <object name="finish" x="600" y="50" width="5" height="200"/>
            <object name="waypoint" x="650" y="140"><point/>
            <properties><property name="order" type="int" value="1"/></properties></object>
            </objectgroup></map>
            """;
        RaceTrack track=RaceTrack.load(DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement(),1000,500);
        RaceWorld world=new RaceWorld(track,new CollisionSystem(List.of()),1000,500,List.of(),TerrainEffects.NONE,1);
        InputState input=new InputState();
        input.setAttackPressed(true);input.setGuarding(true);
        world.update(input,1);
        check(!world.getPlayer().getCombat().isAttacking() && !world.getPlayer().getCombat().isGuarding(),"menu ignores combat");
        world.start(input);
        input.setAttackPressed(true);input.setGuarding(true);
        world.update(input,3);
        check(!world.getPlayer().getCombat().isAttacking() && !input.isGuarding(),"GO clears countdown combat keys");
        input.setAttackPressed(true);
        world.update(input,.01);
        check(world.getPlayer().getCombat().isAttacking(),"J intent reaches world combat system");
        input.setAttackPressed(false);
        world.update(input,.5);
        input.setGuarding(true);input.setSprinting(true);input.setMovingRight(true);
        world.update(input,.01);
        check(world.getPlayer().getCombat().isGuarding() && !world.getPlayer().isSprinting(),"K guard suppresses world sprint");
        input.clear();
        for(RaceTrack.Gate gate:List.of(track.getCheckpoints().get(0),track.getFinish())) {
            world.getPlayer().placeAt(gate.getArea().getX()-48-25,100-63);
            input.setMovingRight(true);
            world.update(input,.4);
        }
        check(world.getState()==GameState.FINISHED,"combat does not prevent valid race finish");
        world.getPlayer().getCombat().receiveHit();world.getPlayer().getCombat().rewardBlock();
        world.start(input);
        for(Racer racer:world.getRacers()) {
            CombatState state=racer.getCombat();
            check(state.getSlowRemaining()==0 && state.getBoostRemaining()==0 && state.getCooldownRemaining()==0
                && state.getImmunityRemaining()==0 && !state.isAttacking() && !state.isGuarding(),"restart resets every fighter");
        }
        check(world.getCombatSystem().getEvents().isEmpty(),"restart clears combat feedback");
        System.out.println("PASS: "+checks+" combat world checks.");
    }
    private static void check(boolean condition,String label) {checks++;if(!condition)throw new AssertionError(label);}
}
