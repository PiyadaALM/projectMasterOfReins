package game.bot;

import game.collision.*;
import game.racer.*;
import game.world.*;
/** Uses the shared movement controller to avoid walls, water, and nearby racers. */
public final class BotObstacleBehavior {
    public RacerAction steer(BotController controller, Racer racer, double seconds, CollisionSystem obstacles,
                             int width, int height, TerrainEffects terrain, BotPersonality personality) {
        return steer(controller, racer, seconds, obstacles, width, height, terrain, personality, false);
    }

    public RacerAction steer(BotController controller, Racer racer, double seconds, CollisionSystem obstacles,
                             int width, int height, TerrainEffects terrain, BotPersonality personality,
                             boolean careless) {
        // Careless bots aim straight at the waypoint and let collision sliding
        // handle the wall/water instead of deliberately steering around it.
        if (careless) return controller.createAction(null, racer, seconds);
        return controller.createActionAvoiding(racer, seconds, obstacles, width, height, terrain,
            personality.waterAversion());
    }
}
