package game.world;

import game.app.*;
import game.collision.*;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Non-solid terrain effects are shared by every racer, separate from walls. */
public final class TerrainEffects {
    public static final TerrainEffects NONE = new TerrainEffects(List.of());
    private final List<CollisionShape> waterAreas;

    public TerrainEffects(List<CollisionShape> waterAreas) {
        this.waterAreas = List.copyOf(waterAreas);
    }

    public boolean isWet(Rectangle2D feet) {
        return waterAreas.stream().anyMatch(area -> area.intersects(feet));
    }

    public boolean crossesWater(Rectangle2D feet, List<CollisionSystem.MotionSegment> path) {
        for (CollisionSystem.MotionSegment segment : path) {
            double dx = segment.x2() - segment.x1();
            double dy = segment.y2() - segment.y1();
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / 6));
            for (int i = 0; i <= samples; i++) {
                double t = i / (double) samples;
                double centerX = segment.x1() + dx * t;
                double centerY = segment.y1() + dy * t;
                Rectangle2D probe = new Rectangle2D.Double(
                    centerX - feet.getWidth() / 2, centerY - feet.getHeight() / 2,
                    feet.getWidth(), feet.getHeight());
                if (isWet(probe)) return true;
            }
        }
        return false;
    }

    public void draw(Graphics2D graphics) {
        waterAreas.forEach(area -> area.draw(graphics));
    }


    /** Distance expressed as dry-ground travel time, sampled with the real feet. */
    public double travelCost(Rectangle2D feet, double dx, double dy, double aversion) {
        double distance = Math.hypot(dx,dy);
        if (waterAreas.isEmpty() || distance == 0) return distance;
        int samples = Math.max(1,(int)Math.ceil(distance/8));
        double weight = 0;
        for(int i=0;i<samples;i++) {
            double t = (i+.5)/samples;
            boolean wet = isWet(new Rectangle2D.Double(feet.getX()+dx*t,feet.getY()+dy*t,
                feet.getWidth(),feet.getHeight()));
            weight += wet ? 1 + (1/Math.max(.01,GameConfig.WATER_SPEED_MULTIPLIER)-1)*aversion : 1;
        }
        return distance*weight/samples;
    }
}
