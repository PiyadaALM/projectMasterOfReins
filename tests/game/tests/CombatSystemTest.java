package game.tests;

import game.app.*;
import game.bot.*;
import game.collision.*;
import game.combat.*;
import game.racer.*;
import game.world.*;


import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;

public final class CombatSystemTest {
    private static int checks;
    private static final CollisionSystem EMPTY=new CollisionSystem(List.of());
    private static final CombatAction ATTACK=new CombatAction(true,false),GUARD=new CombatAction(false,true);
    public static void main(String[] args) {
        InputState input=new InputState();
        input.setAttackPressed(true);
        check(input.consumeAttackRequest(),"initial press attacks");
        input.setAttackPressed(true);
        check(!input.consumeAttackRequest(),"key repeat cannot spam attack");
        input.setAttackPressed(false);input.setAttackPressed(true);
        check(input.consumeAttackRequest(),"release and press attacks again");
        input.setGuarding(true);input.clear();
        check(!input.isGuarding() && !input.consumeAttackRequest(),"focus/restart clears combat keys");

        CombatSystem combat=new CombatSystem();
        Racer a=racer(100,200),b=racer(160,200);
        List<Racer> pair=List.of(a,b);
        combat.step(pair,Map.of(a,ATTACK),EMPTY,.01);
        check(a.getCombat().getWindupRemaining()>0 && b.getCombat().getSlowRemaining()==0,"attack has visible windup");
        near(GameConfig.MAX_STAMINA-GameConfig.ATTACK_STAMINA_COST,a.getStamina().getCurrent(),"attack spends stamina once");
        combat.step(pair,Map.of(),EMPTY,.13);
        check(combat.getEvents().size()==1 && combat.getEvents().get(0).outcome()==CombatSystem.Outcome.HIT,"strike hits in front");
        near(1,b.getCombat().getSlowRemaining(),"hit slows for one second");
        near(.5,a.getCombat().getBoostRemaining(),"attacker gains half-second boost");
        double x=a.getX();a.update(new RacerAction(1,0,false),.25);
        near(60,a.getX()-x,"boost is twenty percent");
        x=a.getX();a.update(new RacerAction(1,0,false),1);
        near(210,a.getX()-x,"boost expiry integrated within long frame");
        x=b.getX();b.update(new RacerAction(1,0,false),.25);
        near(35,b.getX()-x,"hit slows thirty percent");
        x=b.getX();b.update(new RacerAction(1,0,false),1);
        near(155,b.getX()-x,"one-second slow expiry integrated exactly");
        near(0,b.getCombat().getSlowRemaining(),"hit slow fully expires");

        a.resetAt(52,137);b.resetAt(112,137);combat.reset();
        combat.step(pair,Map.of(a,ATTACK,b,GUARD),EMPTY,.01);
        combat.step(pair,Map.of(b,GUARD),EMPTY,.13);
        check(combat.getEvents().get(0).outcome()==CombatSystem.Outcome.BLOCK,"guard resolves before strike");
        near(0,b.getCombat().getSlowRemaining(),"blocked strike does not slow defender");
        near(1,b.getCombat().getBoostRemaining(),"successful guard rewards one second");
        near(0,a.getCombat().getBoostRemaining(),"blocked attacker receives no reward");
        near(GameConfig.MAX_STAMINA-.14*GameConfig.GUARD_STAMINA_PER_SECOND,b.getStamina().getCurrent(),"holding guard costs stamina");
        double energy=b.getStamina().getCurrent();
        b.update(new RacerAction(1,0,true),.1);
        check(!b.isSprinting(),"guard disables sprint");
        near(energy,b.getStamina().getCurrent(),"guard cannot also drain sprint energy");
        x=b.getX();b.update(new RacerAction(0,0,false),.1);
        near(x,b.getX(),"speed boost never moves stationary actor");
        near(energy,b.getStamina().getCurrent(),"guard does not regain idle stamina");
        b.getCombat().rewardHit();
        near(.8,b.getCombat().getBoostRemaining(),"short reward cannot shorten existing boost");
        b.getCombat().rewardBlock();b.getCombat().rewardBlock();
        near(1,b.getCombat().getBoostRemaining(),"repeated rewards refresh but never add durations");

        a.resetAt(52,137);b.resetAt(112,137);
        Racer c=racer(170,200);
        List<Racer> crowd=List.of(a,b,c);
        c.face(new RacerAction(-1,0,false));
        combat.step(crowd,Map.of(a,ATTACK,c,ATTACK),EMPTY,.01);
        combat.step(crowd,Map.of(),EMPTY,.13);
        long hitsOnB=combat.getEvents().stream().filter(event->event.target()==b).count();
        check(hitsOnB==1,"immunity prevents pile-on in the same frame");
        near(0,c.getCombat().getBoostRemaining(),"immune target cannot farm attack rewards");
        near(GameConfig.HIT_IMMUNITY_DURATION,b.getCombat().getImmunityRemaining(),"anti-chain protection starts");
        combat.step(pair,Map.of(a,ATTACK),EMPTY,.4);
        check(!a.getCombat().isAttacking(),"cooldown rejects early attack");
        combat.step(pair,Map.of(a,ATTACK),EMPTY,.6);
        check(a.getCombat().isAttacking(),"attack allowed again after cooldown");
        combat.step(pair,Map.of(),EMPTY,.13);
        check(combat.getEvents().isEmpty(),"immunity also rejects a later attacker before expiry");
        combat.step(pair,Map.of(),EMPTY,.2);
        near(0,b.getCombat().getImmunityRemaining(),"immunity expires");

        a.resetAt(52,137);b.resetAt(112,137);
        combat.step(pair,Map.of(a,ATTACK),EMPTY,.01);
        b.placeAt(500,137);
        combat.step(pair,Map.of(),EMPTY,.13);
        check(combat.getEvents().isEmpty() && a.getCombat().getBoostRemaining()==0,"moving out during windup dodges strike");
        b.resetAt(-8,137);
        check(!CombatSystem.inRange(a,b,EMPTY,false),"cannot hit behind facing");
        b.resetAt(52,77);
        a.face(new RacerAction(0,-1,false));
        check(CombatSystem.inRange(a,b,EMPTY,false),"vertical facing supported");
        a.face(new RacerAction(1,0,false));b.resetAt(112,137);
        CollisionSystem wall=new CollisionSystem(List.of(rectangle(130,100,5,200)));
        check(!CombatSystem.inRange(a,b,wall,false),"cannot hit through a wall");

        a.resetAt(52,137);b.resetAt(112,137);
        b.getStamina().consume(GameConfig.MAX_STAMINA);
        combat.step(pair,Map.of(a,ATTACK,b,GUARD),EMPTY,.01);
        combat.step(pair,Map.of(b,GUARD),EMPTY,.13);
        check(!b.getCombat().isGuarding() && b.getCombat().getSlowRemaining()>0,"empty stamina cannot block");
        b.getStamina().recover(1);
        combat.step(pair,Map.of(b,GUARD),EMPTY,.01);
        check(!b.getCombat().isGuarding(),"exhausted guard needs a release to prevent flickering");
        combat.step(pair,Map.of(),EMPTY,.01);
        combat.step(pair,Map.of(b,GUARD),EMPTY,.01);
        check(b.getCombat().isGuarding(),"release enables guard when energy is available");
        a.resetAt(52,137);b.resetAt(112,137);
        b.face(new RacerAction(-1,0,false));
        combat.step(pair,Map.of(a,ATTACK,b,ATTACK),EMPTY,.01);
        combat.step(pair,Map.of(),EMPTY,.13);
        check(combat.getEvents().size()==2,"simultaneous committed strikes can trade fairly");

        Racer bot=racer(160,200),human=racer(100,200);
        BotCombatController ai=new BotCombatController(0);
        human.getCombat().startAttack(1,0);
        CombatAction reaction=CombatAction.NONE;
        for(int i=0;i<4;i++) reaction=ai.decide(bot,List.of(human,bot),EMPTY,1.0/60);
        check(reaction.guard(),"bot reacts to visible nearby windup");
        ai.reset();human.getCombat().reset();
        bot.face(new RacerAction(-1,0,false));
        check(ai.decide(bot,List.of(human,bot),EMPTY,.01).attack(),"bot can attack human with same reach rules");
        bot.getStamina().consume(GameConfig.MAX_STAMINA);
        check(!ai.decide(bot,List.of(human,bot),EMPTY,.01).attack(),"tired bot saves energy instead of duelling forever");

        Racer stacked=racer(100,200);
        stacked.setTerrainEffects(new TerrainEffects(List.of(rectangle(0,0,1000,500))));
        CollisionSystem rock=new CollisionSystem(List.of(rectangle(150,100,10,200)));
        stacked.update(new RacerAction(1,0,false),.3,rock);
        check(stacked.isSlowed(),"fixture has terrain collision penalty");
        stacked.getCombat().receiveHit();stacked.getCombat().rewardHit();
        x=stacked.getX();stacked.update(new RacerAction(-1,0,false),.1,rock);
        near(200*.8*.5*.7*1.2*.1,x-stacked.getX(),"water collision hit and boost multiply correctly");
        stacked.resetAt(52,137);
        check(stacked.getCombat().getSlowRemaining()==0 && stacked.getCombat().getBoostRemaining()==0
            && stacked.getCombat().getImmunityRemaining()==0 && !stacked.getCombat().isGuarding(),"restart clears every combat state");
        System.out.println("PASS: "+checks+" combat checks.");
    }
    private static Racer racer(double x,double y) {return new Racer(x-48,y-63,96,65,new Stamina(GameConfig.MAX_STAMINA));}
    private static CollisionShape rectangle(double x,double y,double w,double h) {
        return new CollisionShape(List.of(new Point2D.Double(x,y),new Point2D.Double(x+w,y),
            new Point2D.Double(x+w,y+h),new Point2D.Double(x,y+h)));
    }
    private static void near(double expected,double actual,String label) {check(Math.abs(expected-actual)<1e-6,label+" ("+actual+")");}
    private static void check(boolean condition,String label) {checks++;if(!condition)throw new AssertionError(label);}
}
