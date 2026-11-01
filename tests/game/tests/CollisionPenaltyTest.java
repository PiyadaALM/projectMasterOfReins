package game.tests;

import game.app.*;
import game.collision.*;
import game.racer.*;


import java.awt.geom.Point2D;
import java.util.List;

public final class CollisionPenaltyTest {
    private static int checks;
    private static final RacerAction RIGHT = new RacerAction(1, 0, false);
    private static final RacerAction LEFT = new RacerAction(-1, 0, false);
    private static final RacerAction SHIFT_RIGHT = new RacerAction(1, 0, true);
    private static final RacerAction SHIFT_LEFT = new RacerAction(-1, 0, true);
    private static final RacerAction IDLE = new RacerAction(0, 0, false);

    public static void main(String[] args) {
        CollisionShape shape = new CollisionShape(List.of(new Point2D.Double(100, 100),
            new Point2D.Double(120, 100), new Point2D.Double(120, 200), new Point2D.Double(100, 200)));
        CollisionSystem wall = new CollisionSystem(List.of(shape));
        Racer walking = racer();
        walking.update(RIGHT, .05, wall);
        check(walking.isSlowed() && !walking.isStunned(), "normal hit slows without stun");
        near(1, walking.getSlowRemaining(), "normal slow duration");
        double x = walking.getX();
        walking.update(LEFT, .2, wall);
        near(20, x - walking.getX(), "normal movement is halved during slow");
        near(.8, walking.getSlowRemaining(), "slow timer progresses while moving");
        x = walking.getX();
        walking.update(LEFT, .9, wall);
        near(100, x - walking.getX(), "speed recovers part way through frame");
        check(!walking.isSlowed(), "normal slow expires");
        x = walking.getX();
        walking.update(LEFT, .1, wall);
        near(20, x - walking.getX(), "normal speed returns");

        Racer sprinting = racer();
        sprinting.update(SHIFT_RIGHT, .05, wall);
        check(sprinting.isStunned() && !sprinting.isSprinting(), "shift hit stuns and stops sprint animation");
        near(.3, sprinting.getStunRemaining(), "stun lasts 0.3 seconds");
        near(1, sprinting.getSlowRemaining(), "slow waits behind stun");
        x = sprinting.getX();
        double energy = sprinting.getStamina().getCurrent();
        sprinting.update(SHIFT_LEFT, .1, wall);
        near(x, sprinting.getX(), "stun blocks attempted escape");
        near(energy, sprinting.getStamina().getCurrent(), "stun does not drain sprint stamina");
        check(!sprinting.isMoving(), "stun stops movement animation");
        sprinting.update(SHIFT_LEFT, .2, wall);
        near(x, sprinting.getX(), "full stun interval stays still");
        check(!sprinting.isStunned() && sprinting.isSlowed(), "slow starts after stun finishes");
        near(1, sprinting.getSlowRemaining(), "stun did not consume slow duration");
        sprinting.update(LEFT, .2, wall);
        near(20, x - sprinting.getX(), "slow applies after stun");

        Racer crossing = racer();
        crossing.update(SHIFT_RIGHT, .05, wall);
        x = crossing.getX();
        crossing.update(LEFT, .4, wall);
        near(10, x - crossing.getX(), "frame spanning stun and slow only moves in slow portion");
        near(.9, crossing.getSlowRemaining(), "correct remaining slow across phase boundary");

        Racer held = racer();
        held.update(SHIFT_RIGHT, .05, wall);
        for (int i = 0; i < 40; i++) {
            held.update(SHIFT_RIGHT, .05, wall);
            check(!wall.overlaps(held.getFeetBounds()), "holding wall never penetrates");
        }
        check(!held.isStunned() && !held.isSlowed(), "holding against wall does not refresh penalties");
        held.update(IDLE, .1, wall);
        held.update(SHIFT_RIGHT, .05, wall);
        check(!held.isStunned(), "releasing input while touching does not count as a fresh impact");
        held.update(LEFT, .1, wall);
        held.update(RIGHT, .2, wall);
        check(held.isSlowed() && !held.isStunned(), "leave and hit again applies normal penalty");

        Racer idle = racer();
        idle.update(RIGHT, .05, wall);
        idle.update(IDLE, 1, wall);
        check(!idle.isSlowed(), "slow expires while idle");
        Racer exhausted = racer();
        exhausted.getStamina().consume(GameConfig.MAX_STAMINA);
        exhausted.update(SHIFT_RIGHT, .05, wall);
        check(exhausted.isStunned(), "sprinting at impact counts even when stamina is empty");
        Racer touching = racer();
        touching.placeAt(100 - 96 * .68, 70);
        touching.update(new RacerAction(0, 1, false), .05, wall);
        check(!touching.isSlowed(), "tangential contact alone is not an impact");
        Racer emptyWorld = racer();
        emptyWorld.update(SHIFT_RIGHT, .05, new CollisionSystem(List.of()));
        check(!emptyWorld.isStunned() && !emptyWorld.isSlowed(), "free movement has no penalty");
        System.out.println("PASS: " + checks + " collision penalty checks.");
    }

    private static Racer racer() {
        return new Racer(30, 70, 96, 65, new Stamina(GameConfig.MAX_STAMINA));
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < .00001, label + " (" + actual + ")");
    }

    private static void check(boolean passed, String label) {
        checks++;
        if (!passed) {
            throw new AssertionError(label);
        }
    }
}
