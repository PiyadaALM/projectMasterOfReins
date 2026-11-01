package game.collision;

import game.racer.*;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.ArrayList;

/** Sweeps the feet for the entire frame, then slides along the hit boundary. */
public final class CollisionSystem {
    private static final double EPSILON = 1e-6;
    private final List<CollisionShape> shapes;

    public CollisionSystem(List<CollisionShape> shapes) {
        this.shapes = List.copyOf(shapes);
    }

    /** Other feet are temporary solid bodies; original map geometry stays shared. */
    public CollisionSystem withBodies(List<Racer> racers, Racer moving) {
        List<CollisionShape> combined = new ArrayList<>(shapes);
        for (Racer racer : racers) {
            if (racer == moving) continue;
            Rectangle2D feet = racer.getFeetBounds();
            combined.add(new CollisionShape(List.of(
                new Point2D.Double(feet.getMinX(), feet.getMinY()),
                new Point2D.Double(feet.getMaxX(), feet.getMinY()),
                new Point2D.Double(feet.getMaxX(), feet.getMaxY()),
                new Point2D.Double(feet.getMinX(), feet.getMaxY()))));
        }
        return new CollisionSystem(combined);
    }

    public boolean overlaps(Rectangle2D feet) {
        return shapes.stream().anyMatch(shape -> shape.intersects(feet));
    }

    public Point2D.Double move(Rectangle2D feet, double dx, double dy) {
        return move(feet, dx, dy, null);
    }

    public Point2D.Double move(Rectangle2D feet, double dx, double dy, Rectangle2D centreLimits) {
        Movement result = moveWithContact(feet, dx, dy, centreLimits);
        return new Point2D.Double(result.dx(), result.dy());
    }

    public boolean isTouching(Rectangle2D feet, Rectangle2D centreLimits) {
        double margin = 0.01;
        Rectangle2D probe = new Rectangle2D.Double(feet.getX() - margin, feet.getY() - margin,
            feet.getWidth() + 2 * margin, feet.getHeight() + 2 * margin);
        return overlaps(probe) || (centreLimits != null
            && (feet.getCenterX() <= centreLimits.getMinX() + margin
                || feet.getCenterX() >= centreLimits.getMaxX() - margin
                || feet.getCenterY() <= centreLimits.getMinY() + margin
                || feet.getCenterY() >= centreLimits.getMaxY() - margin));
    }

    public Movement moveWithContact(Rectangle2D feet, double dx, double dy, Rectangle2D centreLimits) {
        double startX = feet.getCenterX(), startY = feet.getCenterY();
        double x = startX, y = startY;
        List<MotionSegment> path = new ArrayList<>();
        double time = 0;
        if (overlaps(feet)) {
            // Spawn recovery handles this case; do not push deeper into a solid.
            return new Movement(0, 0, true);
        }
        boolean hit = false;
        for (int iteration = 0; iteration < 8 && Math.hypot(dx, dy) > EPSILON; iteration++) {
            Hit first = boundaryHit(x, y, dx, dy, centreLimits);
            Rectangle2D sweep = new Rectangle2D.Double(
                Math.min(x, x + dx) - feet.getWidth() / 2 - EPSILON,
                Math.min(y, y + dy) - feet.getHeight() / 2 - EPSILON,
                Math.abs(dx) + feet.getWidth() + 2 * EPSILON,
                Math.abs(dy) + feet.getHeight() + 2 * EPSILON);
            for (CollisionShape shape : shapes) {
                if (!shape.getBounds().intersects(sweep)) {
                    continue;
                }
                for (CollisionShape.Edge edge : shape.expandedEdges(feet.getWidth(), feet.getHeight())) {
                    double approach = dx * edge.nx + dy * edge.ny;
                    if (approach >= -EPSILON) {
                        continue;
                    }
                    double t = ((edge.x - x) * edge.nx + (edge.y - y) * edge.ny) / approach;
                    if (t < -EPSILON || t > 1 + EPSILON) {
                        continue;
                    }
                    t = Math.max(0, Math.min(1, t));
                    double along = ((x + dx * t - edge.x) * edge.dx
                        + (y + dy * t - edge.y) * edge.dy) / edge.length;
                    if (along < -EPSILON || along > edge.length + EPSILON) {
                        continue;
                    }
                    if (first == null || t < first.time) {
                        first = new Hit(t, edge.nx, edge.ny);
                    }
                }
            }
            if (first == null) {
                path.add(new MotionSegment(x, y, x + dx, y + dy, time, 1));
                x += dx;
                y += dy;
                break;
            }
            hit = true;
            double safeTime = Math.max(0, first.time - EPSILON / Math.hypot(dx, dy));
            double endTime = time + (1 - time) * safeTime;
            if (safeTime > 0) {
                path.add(new MotionSegment(x, y, x + dx * safeTime, y + dy * safeTime, time, endTime));
            }
            time = endTime;
            x += dx * safeTime;
            y += dy * safeTime;
            dx *= 1 - safeTime;
            dy *= 1 - safeTime;
            double inward = dx * first.nx + dy * first.ny;
            dx -= inward * first.nx;
            dy -= inward * first.ny;
        }
        return new Movement(x - startX, y - startY, hit, List.copyOf(path));
    }

    public record MotionSegment(double x1, double y1, double x2, double y2,
                                double startFraction, double endFraction) {}

    public record Movement(double dx, double dy, boolean hit, List<MotionSegment> path) {
        public Movement(double dx, double dy, boolean hit) {
            this(dx, dy, hit, List.of());
        }
    }

    private static Hit boundaryHit(double x, double y, double dx, double dy, Rectangle2D limits) {
        if (limits == null) {
            return null;
        }
        Hit first = null;
        Hit[] candidates = {
            dx < -EPSILON ? new Hit((limits.getMinX() - x) / dx, 1, 0) : null,
            dx > EPSILON ? new Hit((limits.getMaxX() - x) / dx, -1, 0) : null,
            dy < -EPSILON ? new Hit((limits.getMinY() - y) / dy, 0, 1) : null,
            dy > EPSILON ? new Hit((limits.getMaxY() - y) / dy, 0, -1) : null
        };
        for (Hit hit : candidates) {
            if (hit != null && hit.time >= -EPSILON && hit.time <= 1 + EPSILON
                && (first == null || hit.time < first.time)) {
                first = new Hit(Math.max(0, Math.min(1, hit.time)), hit.nx, hit.ny);
            }
        }
        return first;
    }

    public void placeAtFreeStart(Racer racer, int worldWidth, int worldHeight) {
        if (!overlaps(racer.getFeetBounds())) {
            return;
        }
        double x = racer.getX(), y = racer.getY();
        for (int radius = 8; radius <= Math.max(worldWidth, worldHeight) + 8; radius += 8) {
            for (int offset = -radius; offset <= radius; offset += 8) {
                int[][] candidates = {{offset, -radius}, {offset, radius}, {-radius, offset}, {radius, offset}};
                for (int[] candidate : candidates) {
                    racer.placeAt(x + candidate[0], y + candidate[1]);
                    racer.keepInside(worldWidth, worldHeight);
                    if (!overlaps(racer.getFeetBounds())) {
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("The map has no free starting position.");
    }

    public void draw(Graphics2D graphics) {
        for (CollisionShape shape : shapes) {
            shape.draw(graphics);
        }
    }

    private record Hit(double time, double nx, double ny) {}
}
