package game.items;



import java.awt.geom.Rectangle2D;

/** First contact between a moving feet rectangle and a non-solid trigger. */
public final class SweptTrigger {
    private SweptTrigger() {}

    public static double entryFraction(Rectangle2D area, double x1, double y1, double x2, double y2,
                                       double feetWidth, double feetHeight, boolean countStartingInside) {
        double minX = area.getMinX() - feetWidth / 2, maxX = area.getMaxX() + feetWidth / 2;
        double minY = area.getMinY() - feetHeight / 2, maxY = area.getMaxY() + feetHeight / 2;
        if (x1 >= minX && x1 <= maxX && y1 >= minY && y1 <= maxY) {
            return countStartingInside ? 0 : Double.NaN;
        }
        double enter = 0, exit = 1;
        double[] starts = {x1, y1}, changes = {x2 - x1, y2 - y1};
        double[] mins = {minX, minY}, maxs = {maxX, maxY};
        for (int axis = 0; axis < 2; axis++) {
            if (Math.abs(changes[axis]) < 1e-12) {
                if (starts[axis] < mins[axis] || starts[axis] > maxs[axis]) return Double.NaN;
            } else {
                double a = (mins[axis] - starts[axis]) / changes[axis];
                double b = (maxs[axis] - starts[axis]) / changes[axis];
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return Double.NaN;
            }
        }
        return enter <= exit && exit >= 0 && enter <= 1 ? enter : Double.NaN;
    }
}
