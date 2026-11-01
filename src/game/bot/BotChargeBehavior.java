package game.bot;

import game.app.GameConfig;
import game.collision.CollisionSystem;
import game.racer.Racer;
import game.racer.RacerAction;
import game.world.TerrainEffects;
import java.awt.geom.Point2D;
import java.util.List;

/** Lets bots charge on a clear stretch, using the same charge state and reward as the player. */
public final class BotChargeBehavior {
    private final int botIndex;
    private double cooldown;
    private boolean holding;

    public BotChargeBehavior(int botIndex) {
        this.botIndex = botIndex;
        reset();
    }

    public boolean update(Racer bot, double seconds, RacerAction motion, Point2D.Double routeTarget,
                          CollisionSystem walls, TerrainEffects terrain, List<Racer> racers) {
        if (bot.getCharge().isAwaitingRelease()) finishAttempt();
        if (holding) return true;
        cooldown = Math.max(0, cooldown - seconds);
        if (cooldown > 0 || bot.isStunned() || bot.isInWater()
            || bot.getStamina().getCurrent() > GameConfig.MAX_STAMINA - GameConfig.CHARGE_MIN_SECONDS) {
            return false;
        }
        for (Racer other : racers) {
            if (other != bot && Math.hypot(
                other.getFeetBounds().getCenterX() - bot.getFeetBounds().getCenterX(),
                other.getGroundY() - bot.getGroundY()) < 150) return false;
        }

        double dx = motion.getHorizontalDirection();
        double dy = motion.getVerticalDirection();
        if (Math.hypot(dx, dy) < 1e-9 && routeTarget != null) {
            dx = routeTarget.x - bot.getFeetBounds().getCenterX();
            dy = routeTarget.y - bot.getGroundY();
        }
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) return false;
        double lookAhead = GameConfig.NORMAL_SPEED * 1.4;
        dx = dx / length * lookAhead;
        dy = dy / length * lookAhead;
        if (walls.moveWithContact(bot.getFeetBounds(), dx, dy, null).hit()
            || terrain.travelCost(bot.getFeetBounds(), dx, dy, 1.4) > lookAhead + .1) return false;

        holding = true;
        return true;
    }

    public boolean isHolding() { return holding; }

    public void finishAttempt() {
        holding = false;
        cooldown = 7.0 + Math.floorMod(botIndex, 4) * 1.5;
    }

    public void reset() {
        holding = false;
        cooldown = 2.0 + Math.floorMod(botIndex, 5) * 1.25;
    }
}
