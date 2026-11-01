package game.bot;

import game.collision.*;
import game.combat.*;
import game.racer.*;
import java.util.List;

/** Bots react to a visible wind-up, rather than inspecting the player's keys. */
public final class BotCombatController {
    private final double reactionDelay;
    private BotPersonality personality = BotPersonality.BALANCED;
    private double threatTime,guardHold;
    public BotCombatController(int index) {reactionDelay=.06+(index%3)*.02;}
    public void setPersonality(BotPersonality personality) {this.personality=personality;}
    public void reset() {threatTime=guardHold=0;}
    public CombatAction decide(Racer bot,List<Racer> active,CollisionSystem walls,double seconds) {
        if(bot.isStunned()) {
            threatTime=guardHold=0;
            return CombatAction.NONE;
        }
        boolean threatened=active.stream().anyMatch(other->other!=bot && other.getCombat().getWindupRemaining()>0
            && CombatSystem.inRange(other,bot,walls,true));
        threatTime=threatened?threatTime+seconds:0;
        guardHold=Math.max(0,guardHold-seconds);
        double personalityReaction=Math.max(.02,reactionDelay+(.55-personality.combatAggression())*.04);
        if(threatTime>=personalityReaction) guardHold=.14+personality.combatAggression()*.18;
        boolean guard=guardHold>0;
        // Bots defend themselves but do not start attacks. This keeps them from
        // trading hit-stuns in a pack and makes the race easier for the player.
        boolean attack=false;
        return new CombatAction(attack,guard);
    }
}
