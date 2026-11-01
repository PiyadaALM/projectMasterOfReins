package game.world;

import game.collision.*;
import game.racer.*;
import java.awt.geom.Rectangle2D;

/** Each competitor crosses the same ordered gates independently. */
public final class RaceProgress {
    private int passed;
    private double finishTime = Double.NaN;

    public void reset() { passed = 0; finishTime = Double.NaN; }
    public int getPassed() { return passed; }
    public boolean isFinished() { return !Double.isNaN(finishTime); }
    public double getFinishTime() { return finishTime; }
    public void finishAt(double seconds) { finishTime = seconds; }

    public double check(RaceTrack track, Racer racer) {
        Rectangle2D feet = racer.getFeetBounds();
        for (CollisionSystem.MotionSegment segment : racer.getMovementPath()) {
            double cursor = 0;
            while (passed < track.getCheckpoints().size()) {
                double t = track.getCheckpoints().get(passed).entryFraction(segment.x1(), segment.y1(),
                    segment.x2(), segment.y2(), feet.getWidth(), feet.getHeight());
                if (Double.isNaN(t) || t < cursor - 1e-9) break;
                passed++;
                cursor = t;
            }
            double finish = track.getFinish().entryFraction(segment.x1(), segment.y1(), segment.x2(), segment.y2(),
                feet.getWidth(), feet.getHeight());
            if (!Double.isNaN(finish) && passed == track.getCheckpoints().size() && finish >= cursor - 1e-9) {
                double x = segment.x1() + (segment.x2() - segment.x1()) * finish;
                double y = segment.y1() + (segment.y2() - segment.y1()) * finish;
                racer.placeAt(x - racer.getWidth() / 2.0, y - racer.getHeight() + 7);
                racer.stopMotion();
                return segment.startFraction() + finish * (segment.endFraction() - segment.startFraction());
            }
        }
        return Double.NaN;
    }
}
