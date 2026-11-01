package game.combat;

import game.app.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One racer's independent cooldowns, temporary effects and visual feedback. */
public final class CombatState {
    private boolean guarding;
    private boolean pending;
    private double cooldown,windup,swing,slow,boost,immunity;
    private double swingX=1,swingY;
    public void reset() {
        guarding=pending=false;
        cooldown=windup=swing=slow=boost=immunity=0;
        swingX=1;swingY=0;
    }
    public void stopActions() {guarding=pending=false;windup=swing=0;}
    public void advanceActions(double seconds) {
        cooldown=left(cooldown-seconds);windup=left(windup-seconds);swing=left(swing-seconds);
        immunity=left(immunity-seconds);
    }
    public boolean startAttack(double facingX,double facingY) {
        if(cooldown>0 || pending || guarding) return false;
        pending=true;
        cooldown=GameConfig.ATTACK_COOLDOWN;
        windup=GameConfig.ATTACK_WINDUP;
        swing=GameConfig.ATTACK_WINDUP+.14;
        swingX=facingX;swingY=facingY;
        return true;
    }
    public boolean isStrikeReady() {return pending && windup==0;}
    public void consumeStrike() {pending=false;}
    public void setGuarding(boolean guarding) {this.guarding=guarding;}
    public boolean isGuarding() {return guarding;}
    public boolean isAttacking() {return pending || swing>0;}
    public double getWindupRemaining() {return windup;}
    public double getCooldownRemaining() {return cooldown;}
    public double getSwingX() {return swingX;}
    public double getSwingY() {return swingY;}
    public double getSlowRemaining() {return slow;}
    public double getBoostRemaining() {return boost;}
    public double getImmunityRemaining() {return immunity;}
    public void receiveHit() {
        slow=GameConfig.COMBAT_SLOW_DURATION;
        immunity=GameConfig.HIT_IMMUNITY_DURATION;
    }
    public void rewardHit() {boost=Math.max(boost,GameConfig.HIT_SPEED_DURATION);}
    public void rewardBlock() {boost=Math.max(boost,GameConfig.BLOCK_SPEED_DURATION);}

    /** Integrate across timer boundaries so a partial-frame expiry has the right distance. */
    public double movementTime(double seconds,double stunnedTime,double collisionSlowTime) {
        List<Double> boundaries=new ArrayList<>(List.of(0.0,seconds));
        for(double time:new double[]{stunnedTime,stunnedTime+collisionSlowTime,slow,boost})
            if(time>0 && time<seconds) boundaries.add(time);
        Collections.sort(boundaries);
        double weighted=0;
        for(int i=1;i<boundaries.size();i++) {
            double from=boundaries.get(i-1),to=boundaries.get(i),mid=(from+to)/2;
            if(mid<stunnedTime) continue;
            double multiplier=mid<stunnedTime+collisionSlowTime?GameConfig.COLLISION_SLOW_MULTIPLIER:1;
            if(mid<slow) multiplier*=GameConfig.COMBAT_SLOW_MULTIPLIER;
            if(mid<boost) multiplier*=GameConfig.COMBAT_SPEED_MULTIPLIER;
            weighted+=(to-from)*multiplier;
        }
        return weighted;
    }
    public void advanceMovement(double seconds) {slow=left(slow-seconds);boost=left(boost-seconds);}
    private static double left(double value) {return value<1e-9?0:value;}
}
