package game.bot;

import game.app.*;
import game.collision.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Authored ground positions, independent of tile artwork and object IDs. */
public final class BotRoute {
    private final Point2D.Double spawn;
    private final List<Point2D.Double> waypoints;
    private final BotRouteGraph graph;

    public BotRoute(Point2D.Double spawn, List<Point2D.Double> waypoints) {
        this(spawn,waypoints,null);
    }
    public BotRoute(Point2D.Double spawn, List<Point2D.Double> waypoints,BotRouteGraph graph) {
        this.spawn = (Point2D.Double) spawn.clone();
        this.waypoints = waypoints.stream().map(p -> (Point2D.Double) p.clone()).toList();
        this.graph=graph;
    }
    public BotRouteGraph getGraph() {return graph;}
    public BotRoute withSpawn(Point2D.Double point) {return new BotRoute(point,waypoints,graph);}

    public Point2D.Double getSpawn() { return (Point2D.Double) spawn.clone(); }
    public List<Point2D.Double> getWaypoints() {
        return waypoints.stream().map(p -> (Point2D.Double) p.clone()).toList();
    }

    public void validate(RaceTrack track, CollisionSystem collisions, int width, int height) {
        if(graph!=null) {graph.validate(spawn,track,collisions,width,height);return;}
        double feetWidth = GameConfig.RACER_WIDTH * .36;
        Point2D.Double previous = spawn;
        int passed = 0;
        boolean finished = false;
        List<RaceTrack.Gate> gates = track.getCheckpoints();
        for (int i = 0; i <= waypoints.size(); i++) {
            Point2D.Double point = i == 0 ? spawn : waypoints.get(i - 1);
            if (point.x < GameConfig.RACER_WIDTH / 2.0
                || point.x > width - GameConfig.RACER_WIDTH / 2.0
                || point.y < GameConfig.RACER_HEIGHT - 2 || point.y > height - 2) {
                throw new IllegalArgumentException("bot_spawn / waypoint " + i + " is too close to the map edge.");
            }
            Rectangle2D feet = new Rectangle2D.Double(previous.x - feetWidth / 2,
                previous.y - 10, feetWidth, 10);
            if (collisions.overlaps(feet) || collisions.moveWithContact(feet,
                point.x - previous.x, point.y - previous.y, null).hit()) {
                throw new IllegalArgumentException("Bot route to waypoint " + i
                    + " crosses collision. Add a waypoint around the obstacle.");
            }
            double cursor = 0;
            while (passed < gates.size()) {
                double t = gates.get(passed).entryFraction(previous.x, previous.y - 5,
                    point.x, point.y - 5, feetWidth, 10);
                if (Double.isNaN(t) || t < cursor) break;
                passed++;
                cursor = t;
            }
            double finish = track.getFinish().entryFraction(previous.x, previous.y - 5,
                point.x, point.y - 5, feetWidth, 10);
            if (passed == gates.size() && !Double.isNaN(finish) && finish >= cursor) finished = true;
            previous = point;
        }
        if (!finished) {
            throw new IllegalArgumentException("Bot waypoints must cross every checkpoint in order, then finish.");
        }
    }
}
