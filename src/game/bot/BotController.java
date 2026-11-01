package game.bot;

import game.app.*;
import game.collision.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.util.List;

/** Chooses the same eight-direction commands available to the human player. */
public final class BotController implements RacerController {
    private final List<Point2D.Double> waypoints;
    private int nextWaypoint;
    private List<Point2D.Double> detour = List.of();
    private int detourIndex;
    private int plannedWaypoint = -1;
    private double retryRemaining;

    public BotController(BotRoute route) {
        this(route,true);
    }

    public BotController(BotRoute route, boolean automaticRest) {
        waypoints = route.getWaypoints();
    }

    public void reset() {
        nextWaypoint = 0;
        detour = List.of();
        detourIndex = 0;
        plannedWaypoint = -1;
        retryRemaining = 0;
    }

    @Override
    public RacerAction createAction(InputState ignored, Racer racer, double seconds) {
        if (racer.isStunned()) return new RacerAction(0, 0, false);
        double x = racer.getX() + racer.getWidth() / 2.0, y = racer.getGroundY();
        // Ignore stale/backward route markers: this is a right-scrolling race.
        while (nextWaypoint < waypoints.size() && waypoints.get(nextWaypoint).x < x - 4) {
            nextWaypoint++;
        }
        while (nextWaypoint < waypoints.size() && waypoints.get(nextWaypoint).distance(x, y) <= 4) {
            nextWaypoint++;
        }
        if (nextWaypoint == waypoints.size()) return new RacerAction(0, 0, false);
        Point2D.Double target = waypoints.get(nextWaypoint);
        double dx = target.x - x, dy = target.y - y;
        double threshold = Math.tan(Math.PI / 8);
        int horizontal = Math.abs(dx) >= Math.abs(dy) * threshold ? (dx >= 0 ? 1 : -1) : 0;
        int vertical = Math.abs(dy) >= Math.abs(dx) * threshold ? (dy >= 0 ? 1 : -1) : 0;
        boolean sprint = Math.hypot(dx, dy) > 60 && racer.getStamina().hasEnergy();
        return new RacerAction(horizontal, vertical, sprint);
    }

    public int getNextWaypoint() { return nextWaypoint; }
    /** Kept for compatibility; waypoints no longer trigger a stamina-rest behavior. */
    public boolean isResting() { return false; }
    public Point2D.Double getTarget() {
        return nextWaypoint < waypoints.size() ? (Point2D.Double)waypoints.get(nextWaypoint).clone() : null;
    }

    /** Steer around riders using the same eight inputs and swept feet as movement. */
    public RacerAction createActionAvoiding(Racer racer, double seconds, CollisionSystem obstacles,
                                            int width, int height) {
        return createActionAvoiding(racer,seconds,obstacles,width,height,TerrainEffects.NONE,0);
    }

    public RacerAction createActionAvoiding(Racer racer, double seconds, CollisionSystem obstacles,
                                            int width, int height,TerrainEffects terrain,double waterAversion) {
        // A pack needs room at a shared waypoint; requiring every centre to touch
        // the same four-pixel spot makes two riders wait on each other forever.
        while (nextWaypoint < waypoints.size() - 1
            && waypoints.get(nextWaypoint).distance(racer.getX() + racer.getWidth() / 2.0, racer.getGroundY()) <= 24) {
            nextWaypoint++;
        }
        RacerAction preferred = createAction(null, racer, seconds);
        if (preferred.getHorizontalDirection() == 0 && preferred.getVerticalDirection() == 0) return preferred;
        retryRemaining = Math.max(0,retryRemaining-seconds);
        if (plannedWaypoint != nextWaypoint) detour = List.of();
        double speed = GameConfig.NORMAL_SPEED * (preferred.isSprinting() ? GameConfig.SPRINT_MULTIPLIER : 1);
        if (racer.isInWater()) speed *= GameConfig.WATER_SPEED_MULTIPLIER;
        double lookAhead = Math.max(seconds, .10);
        double preferredLength = Math.hypot(preferred.getHorizontalDirection(),preferred.getVerticalDirection());
        var direct = obstacles.moveWithContact(racer.getFeetBounds(),
            preferred.getHorizontalDirection() * speed * lookAhead / preferredLength,
            preferred.getVerticalDirection() * speed * lookAhead / preferredLength,null);
        double lookX = preferred.getHorizontalDirection()*speed*.3/preferredLength;
        double lookY = preferred.getVerticalDirection()*speed*.3/preferredLength;
        boolean waterAhead = terrain.travelCost(racer.getFeetBounds(),lookX,lookY,waterAversion)
            > Math.hypot(lookX,lookY)+.1;
        if (detour.isEmpty() && !direct.hit() && !waterAhead) return preferred;
        while (detourIndex < detour.size() && detour.get(detourIndex).distance(
            racer.getFeetBounds().getCenterX(),racer.getGroundY()) <= 4) detourIndex++;
        if (detourIndex >= detour.size()) {
            if (retryRemaining > 0) return fallbackMove(preferred, racer, obstacles, speed * lookAhead);
            detour = BotNavigation.find(racer,waypoints.get(nextWaypoint),obstacles,width,height,terrain,waterAversion);
            detourIndex = 0;
            plannedWaypoint = nextWaypoint;
            retryRemaining = .2;
            if (detour.isEmpty()) return fallbackMove(preferred, racer, obstacles, speed * lookAhead);
        }
        Point2D.Double target = detour.get(detourIndex);
        double dx = target.x - racer.getFeetBounds().getCenterX(),dy = target.y - racer.getGroundY();
        double threshold = Math.tan(Math.PI / 8);
        int h = Math.abs(dx) >= Math.abs(dy) * threshold ? (dx >= 0 ? 1 : -1) : 0;
        int v = Math.abs(dy) >= Math.abs(dx) * threshold ? (dy >= 0 ? 1 : -1) : 0;
        if (h < 0) {
            // BotBrain forbids leftward route motion; do not generate a detour
            // that can only be followed by standing still. Sidestep/forward instead.
            detour = List.of();
            detourIndex = 0;
            return fallbackMove(preferred, racer, obstacles, speed * lookAhead);
        }
        double distance = speed * seconds / Math.hypot(h,v);
        if (obstacles.moveWithContact(racer.getFeetBounds(),h*distance,v*distance,null).hit()) {
            detour = List.of();
            detourIndex = 0;
            return fallbackMove(preferred, racer, obstacles, speed * lookAhead);
        }
        return new RacerAction(h,v,preferred.isSprinting());
    }

    /**
     * A failed A* detour must not freeze a bot. Try a short perpendicular
     * sidestep (then the preferred direction) so the shared collision slide
     * can carry it around a rider or corner.
     */
    private static RacerAction fallbackMove(RacerAction preferred, Racer racer,
                                             CollisionSystem obstacles, double distance) {
        int h = preferred.getHorizontalDirection();
        int v = preferred.getVerticalDirection();
        if (h == 0 && v == 0) return preferred;
        int[][] candidates = {
            {h, v},
            {h, v == 0 ? 1 : 0},
            {h, v == 0 ? -1 : 0},
            {h == 0 ? 1 : 0, v},
            {h == 0 ? -1 : 0, v},
            {1, 1}, {1, -1}, {0, 1}, {0, -1}
        };
        for (int[] candidate : candidates) {
            if (candidate[0] < 0) continue; // the course scrolls right
            double length = Math.hypot(candidate[0], candidate[1]);
            if (length == 0) continue;
            double dx = candidate[0] / length * distance;
            double dy = candidate[1] / length * distance;
            if (!obstacles.moveWithContact(racer.getFeetBounds(), dx, dy, null).hit()) {
                return new RacerAction(candidate[0], candidate[1], preferred.isSprinting());
            }
        }
        return preferred;
    }
}
