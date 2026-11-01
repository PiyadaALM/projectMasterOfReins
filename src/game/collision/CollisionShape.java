package game.collision;



import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A solid polygon in world coordinates; artwork does not define collision. */
public final class CollisionShape {
    private final Path2D.Double path;
    private final List<Point2D.Double> vertices;
    private final Rectangle2D bounds;
    private double cachedWidth = -1;
    private double cachedHeight = -1;
    private Area expanded;
    private List<Edge> edges;

    public CollisionShape(List<Point2D.Double> points) {
        vertices = points.stream().map(p -> new Point2D.Double(p.x, p.y)).toList();
        path = polygon(vertices);
        bounds = path.getBounds2D();
    }

    public boolean intersects(Rectangle2D feet) {
        return bounds.intersects(feet) && path.intersects(feet);
    }

    public void draw(Graphics2D graphics) {
        graphics.draw(path);
    }

    public Rectangle2D getBounds() {
        return (Rectangle2D) bounds.clone();
    }

    List<Edge> expandedEdges(double width, double height) {
        if (width != cachedWidth || height != cachedHeight) {
            // Expand the solid by half the feet size. Moving the feet rectangle
            // then becomes moving its centre point against the expanded boundary.
            expanded = new Area(path);
            double halfWidth = width / 2;
            double halfHeight = height / 2;
            for (int i = 0; i < vertices.size(); i++) {
                Point2D.Double a = vertices.get(i);
                Point2D.Double b = vertices.get((i + 1) % vertices.size());
                List<Point2D.Double> corners = new ArrayList<>();
                for (Point2D.Double end : List.of(a, b)) {
                    for (int sx : new int[]{-1, 1}) {
                        for (int sy : new int[]{-1, 1}) {
                            corners.add(new Point2D.Double(end.x + sx * halfWidth,
                                end.y + sy * halfHeight));
                        }
                    }
                }
                expanded.add(new Area(polygon(convexHull(corners))));
            }
            edges = boundaryEdges(expanded);
            cachedWidth = width;
            cachedHeight = height;
        }
        return edges;
    }

    private static List<Edge> boundaryEdges(Area area) {
        List<Edge> result = new ArrayList<>();
        PathIterator iterator = area.getPathIterator(null);
        double[] coordinates = new double[6];
        double firstX = 0, firstY = 0, previousX = 0, previousY = 0;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) {
                firstX = previousX = coordinates[0];
                firstY = previousY = coordinates[1];
            } else if (type == PathIterator.SEG_LINETO || type == PathIterator.SEG_CLOSE) {
                double x = type == PathIterator.SEG_CLOSE ? firstX : coordinates[0];
                double y = type == PathIterator.SEG_CLOSE ? firstY : coordinates[1];
                double dx = x - previousX, dy = y - previousY;
                double length = Math.hypot(dx, dy);
                if (length > 1e-9) {
                    double nx = dy / length, ny = -dx / length;
                    double midX = (previousX + x) / 2, midY = (previousY + y) / 2;
                    if (area.contains(midX + nx * 1e-4, midY + ny * 1e-4)) {
                        nx = -nx;
                        ny = -ny;
                    }
                    result.add(new Edge(previousX, previousY, dx, dy, length, nx, ny));
                }
                previousX = x;
                previousY = y;
            }
            iterator.next();
        }
        return List.copyOf(result);
    }

    private static List<Point2D.Double> convexHull(List<Point2D.Double> points) {
        points.sort(Comparator.comparingDouble((Point2D.Double p) -> p.x).thenComparingDouble(p -> p.y));
        List<Point2D.Double> hull = new ArrayList<>();
        for (Point2D.Double point : points) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2),
                    hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lowerSize = hull.size();
        for (int i = points.size() - 2; i >= 0; i--) {
            Point2D.Double point = points.get(i);
            while (hull.size() > lowerSize && cross(hull.get(hull.size() - 2),
                    hull.get(hull.size() - 1), point) <= 0) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        hull.remove(hull.size() - 1);
        return hull;
    }

    private static double cross(Point2D.Double a, Point2D.Double b, Point2D.Double c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static Path2D.Double polygon(List<Point2D.Double> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("A collision polygon needs at least 3 points.");
        }
        Path2D.Double result = new Path2D.Double();
        result.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) {
            result.lineTo(points.get(i).x, points.get(i).y);
        }
        result.closePath();
        return result;
    }

    static final class Edge {
        final double x, y, dx, dy, length, nx, ny;

        Edge(double x, double y, double dx, double dy, double length, double nx, double ny) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy;
            this.length = length; this.nx = nx; this.ny = ny;
        }
    }
}
