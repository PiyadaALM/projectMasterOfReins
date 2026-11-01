package game.combat;

import game.app.*;
import game.collision.*;
import game.racer.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolve all guards before strikes, using one rule set for human and bots. */
public final class CombatSystem {
    public enum Outcome { HIT, BLOCK, SHIELD }
    public record Event(Racer attacker,Racer target,Outcome outcome) {}
    private List<Event> events=List.of();
    public List<Event> getEvents() {return events;}
    public void reset() {events=List.of();}

    public void step(List<Racer> active,Map<Racer,CombatAction> commands,CollisionSystem walls,double seconds) {
        step(active,commands,walls,seconds,List.of());
    }

    public void step(List<Racer> active,Map<Racer,CombatAction> commands,CollisionSystem walls,double seconds,
                     List<Racer> botAttackers) {
        Set<Racer> bots=Set.copyOf(botAttackers);
        List<Event> results=new ArrayList<>();
        for(Racer racer:active) {
            CombatState state=racer.getCombat();
            state.advanceActions(seconds);
            CombatAction action=commands.getOrDefault(racer,CombatAction.NONE);
            boolean guard=action.guard() && !racer.isStunned() && !state.isAttacking();
            state.setGuarding(guard);
        }
        for(Racer racer:active) {
            CombatAction action=commands.getOrDefault(racer,CombatAction.NONE);
            CombatState state=racer.getCombat();
            if(action.attack() && !racer.isStunned())
                state.startAttack(racer.getFacingX(),racer.getFacingY());
        }
        // An attack already committed during its wind-up can trade with an opposing attack.
        List<Racer> striking=active.stream().filter(racer->racer.getCombat().isStrikeReady()).toList();
        for(Racer attacker:striking) {
            attacker.getCombat().consumeStrike();
            if(attacker.isStunned()) continue;
            Racer target=findTarget(attacker,active,walls,true);
            if(target==null) continue;
            // A bot's proximity attack must not turn a physical body bump into a stun.
            if(bots.contains(attacker) && bodiesTouch(attacker,target)) continue;
            CombatState defender=target.getCombat();
            if(defender.isGuarding()) {
                defender.rewardBlock();
                results.add(new Event(attacker,target,Outcome.BLOCK));
            } else if(target.getStatus().consumeShield()) {
                results.add(new Event(attacker,target,Outcome.SHIELD));
            } else if(defender.getImmunityRemaining()==0) {
                defender.receiveHit();
                target.applyCombatHitStun();
                attacker.getCombat().rewardHit();
                results.add(new Event(attacker,target,Outcome.HIT));
            }
        }
        events=List.copyOf(results);
    }
    public static Racer findTarget(Racer attacker,List<Racer> racers,CollisionSystem walls,boolean committed) {
        Racer nearest=null;
        double distance=Double.POSITIVE_INFINITY;
        for(Racer target:racers) {
            if(target==attacker || !inRange(attacker,target,walls,committed)) continue;
            double d=attacker.getFeetBounds().getCenterX()-target.getFeetBounds().getCenterX();
            double e=attacker.getFeetBounds().getCenterY()-target.getFeetBounds().getCenterY();
            if(d*d+e*e<distance) {distance=d*d+e*e;nearest=target;}
        }
        return nearest;
    }
    public static boolean inRange(Racer attacker,Racer target,CollisionSystem walls,boolean committed) {
        Rectangle2D a=attacker.getFeetBounds(),b=target.getFeetBounds();
        double dx=b.getCenterX()-a.getCenterX(),dy=b.getCenterY()-a.getCenterY(),distance=Math.hypot(dx,dy);
        if(distance>GameConfig.ATTACK_RANGE || distance<1e-9) return false;
        Rectangle2D ray=new Rectangle2D.Double(a.getCenterX()-.05,a.getCenterY()-.05,.1,.1);
        return !walls.moveWithContact(ray,dx,dy,null).hit();
    }

    private static boolean bodiesTouch(Racer first,Racer second) {
        Rectangle2D a=first.getFeetBounds(),b=second.getFeetBounds();
        double gapX=Math.max(0,Math.max(a.getMinX()-b.getMaxX(),b.getMinX()-a.getMaxX()));
        double gapY=Math.max(0,Math.max(a.getMinY()-b.getMaxY(),b.getMinY()-a.getMaxY()));
        return Math.hypot(gapX,gapY)<3;
    }
}
