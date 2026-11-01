package game.bot;

import game.app.*;
import game.collision.*;
import game.items.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Decides whether a pickup is useful enough to justify a safe short detour. */
public final class BotItemBehavior {
    private final RaceTrack track;
    public BotItemBehavior(RaceTrack track) { this.track = track; }

    public StaminaPickup choose(Racer racer, Point2D.Double target, List<StaminaPickup> pickups,
                                CollisionSystem walls, TerrainEffects terrain, int passed,
                                BotPersonality personality) {
        Point2D.Double start = position(racer);
        double dx = target.x - start.x, dy = target.y - start.y, length = Math.max(1, Math.hypot(dx, dy));
        double best = 0;
        StaminaPickup selected = null;
        for (StaminaPickup candidate : pickups) {
            if (!candidate.isAvailable() || !isUseful(racer, candidate, personality)) continue;
            Rectangle2D area = candidate.getArea();
            Point2D.Double item = new Point2D.Double(area.getCenterX(), area.getCenterY() + 5);
            double distance = start.distance(item);
            if (distance > personality.pickupRadius() || ((item.x - start.x) * dx + (item.y - start.y) * dy) / length < -32) continue;
            if (!safeTactic(track, racer, item, target, walls, passed)) continue;
            double extra = (terrain.travelCost(racer.getFeetBounds(), item.x - start.x, item.y - start.y, personality.waterAversion())
                + item.distance(target) - start.distance(target)) / GameConfig.NORMAL_SPEED;
            double benefit = benefitSeconds(racer, candidate);
            double score = benefit - extra;
            if (score > best) { best = score; selected = candidate; }
        }
        return selected;
    }

    public boolean isUseful(Racer racer, StaminaPickup pickup, BotPersonality personality) {
        return switch (pickup.getType()) {
            case STAMINA -> racer.getStamina().getRatio() < personality.pickupThreshold();
            case SPEED -> racer.getStatus().getSpeedBoostRemaining() < GameConfig.SPEED_PICKUP_DURATION_SECONDS;
            case SHIELD -> racer.getStatus().getShieldCharges() < 3;
        };
    }

    private double benefitSeconds(Racer racer, StaminaPickup pickup) {
        return switch (pickup.getType()) {
            case STAMINA -> {
                double gain = Math.min(pickup.getAmount(),
                    racer.getStamina().getMaximum() - racer.getStamina().getCurrent());
                double horizon = position(racer).distance(track.getFinish().getArea().getCenterX(),
                    track.getFinish().getArea().getCenterY()) / GameConfig.NORMAL_SPEED;
                yield Math.min(gain / GameConfig.STAMINA_DRAIN_PER_SECOND, horizon)
                    * (1 - 1 / GameConfig.SPRINT_MULTIPLIER);
            }
            case SPEED -> GameConfig.SPEED_PICKUP_DURATION_SECONDS
                * (1 - 1 / GameConfig.ITEM_SPEED_MULTIPLIER);
            case SHIELD -> 1.0;
        };
    }

    static boolean safeTactic(RaceTrack track, Racer racer, Point2D.Double point, Point2D.Double target,
                              CollisionSystem walls, int passed) {
        Point2D.Double start = position(racer);
        Rectangle2D feet = racer.getFeetBounds();
        if (walls.moveWithContact(feet, point.x - start.x, point.y - start.y, null).hit()) return false;
        Rectangle2D atPoint = new Rectangle2D.Double(point.x - feet.getWidth() / 2, point.y - 10, feet.getWidth(), 10);
        if (walls.moveWithContact(atPoint, target.x - point.x, target.y - point.y, null).hit()) return false;
        if (passed < track.getCheckpoints().size()) {
            RaceTrack.Gate gate = track.getCheckpoints().get(passed);
            if (crosses(gate, start, target, feet) && !crosses(gate, start, point, feet) && !crosses(gate, point, target, feet)) return false;
        }
        return true;
    }
    static boolean crosses(RaceTrack.Gate gate, Point2D.Double a, Point2D.Double b, Rectangle2D feet) {
        return !Double.isNaN(gate.entryFraction(a.x, a.y - 5, b.x, b.y - 5, feet.getWidth(), feet.getHeight()));
    }
    private static Point2D.Double position(Racer racer) {
        return new Point2D.Double(racer.getFeetBounds().getCenterX(), racer.getGroundY());
    }
}
