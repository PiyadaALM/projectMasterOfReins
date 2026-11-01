package game.bot;

import game.collision.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.util.List;

/** Chooses a connected route using distance, water, traffic, and authored shortcut difficulty. */
public final class BotBranchBehavior {
    public BotRouteGraph.Plan choose(BotRouteGraph graph, Point2D.Double position, int source, int passed,
                                    RaceTrack track, CollisionSystem walls, TerrainEffects terrain,
                                    BotPersonality personality, List<Racer> traffic) {
        return graph.plan(position, source, passed, track, walls, terrain, personality, traffic);
    }
}
