package game.bot;

import game.app.*;
import game.collision.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

/** Finds a clear side path when a slower competitor blocks the current route. */
public final class BotPassingBehavior {
    private final RaceTrack track;
    private final int botIndex;
    public BotPassingBehavior(RaceTrack track, int botIndex) { this.track = track; this.botIndex = botIndex; }

    public Point2D.Double choose(Racer racer, Point2D.Double target, List<Racer> racers, CollisionSystem walls,
                                 TerrainEffects terrain, int width, int height, int passed,
                                 BotPersonality personality) {
        Point2D.Double start = position(racer);
        double dx = target.x - start.x, dy = target.y - start.y, length = Math.hypot(dx, dy);
        if (length < 100) return null;
        dx /= length; dy /= length;
        double ownSpeed = GameConfig.NORMAL_SPEED * (racer.getStamina().getRatio() > personality.sprintReserve() ? GameConfig.SPRINT_MULTIPLIER : 1)
            * (racer.isInWater() ? GameConfig.WATER_SPEED_MULTIPLIER : 1);
        Racer ahead = null;
        double nearest = personality.passingDistance();
        for (Racer other : racers) {
            if (other == racer) continue;
            double x = other.getFeetBounds().getCenterX() - start.x, y = other.getGroundY() - start.y;
            double forward = x * dx + y * dy, lateral = Math.abs(x * dy - y * dx);
            double otherSpeed = other.isMoving() ? GameConfig.NORMAL_SPEED * (other.isSprinting() ? GameConfig.SPRINT_MULTIPLIER : 1)
                * (other.isInWater() ? GameConfig.WATER_SPEED_MULTIPLIER : 1) : 0;
            if (forward > 0 && forward < nearest && lateral < racer.getFeetBounds().getWidth() && ownSpeed > otherSpeed + 5) {
                nearest = forward; ahead = other;
            }
        }
        if (ahead == null) return null;
        Point2D.Double selected = null;
        double best = Double.POSITIVE_INFINITY;
        for (int side : new int[]{botIndex % 2 == 0 ? 1 : -1, botIndex % 2 == 0 ? -1 : 1}) {
            double forward = Math.min(length - 24, nearest + 45), offset = racer.getFeetBounds().getWidth() + 18;
            Point2D.Double point = new Point2D.Double(start.x + dx * forward - dy * offset * side,
                start.y + dy * forward + dx * offset * side);
            if (point.x < racer.getWidth() / 2.0 || point.x > width - racer.getWidth() / 2.0
                || point.y < racer.getHeight() - 2 || point.y > height - 2
                || !BotItemBehavior.safeTactic(track, racer, point, target, walls, passed)) continue;
            double cost = terrain.travelCost(racer.getFeetBounds(), point.x - start.x, point.y - start.y, personality.waterAversion());
            for (Racer other : racers) if (other != racer && Line2D.ptSegDist(start.x, start.y, point.x, point.y,
                other.getFeetBounds().getCenterX(), other.getGroundY()) < 30) cost += 80;
            if (cost < best) { best = cost; selected = point; }
        }
        return selected;
    }
    private static Point2D.Double position(Racer racer) {
        return new Point2D.Double(racer.getFeetBounds().getCenterX(), racer.getGroundY());
    }
}
