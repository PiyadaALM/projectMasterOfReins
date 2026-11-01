package game.collision;



import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Reads rectangle/polygon geometry from a selected Tiled object layer. */
public final class CollisionMapLoader {
    private CollisionMapLoader() {}

    public record Result(List<CollisionShape> shapes, List<String> warnings) {}

    public static Result load(Element map) throws IOException {
        return load(map, "collision");
    }

    public static Result load(Element map, String layerName) throws IOException {
        List<CollisionShape> shapes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        readLayers(map, 0, 0, shapes, warnings, layerName);
        return new Result(List.copyOf(shapes), List.copyOf(warnings));
    }

    private static void readLayers(Element parent, double parentX, double parentY,
                                   List<CollisionShape> shapes, List<String> warnings, String layerName) throws IOException {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element layer)) {
                continue;
            }
            String tag = layer.getTagName();
            if (!tag.equals("group") && !tag.equals("objectgroup")) {
                continue;
            }
            double x = parentX + number(layer, "offsetx", 0);
            double y = parentY + number(layer, "offsety", 0);
            if (tag.equals("group")) {
                readLayers(layer, x, y, shapes, warnings, layerName);
            } else if (layerName.equalsIgnoreCase(layer.getAttribute("name").trim())) {
                // Editor visibility is visual only: hiding this layer must not open the walls.
                for (Node child = layer.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element object && object.getTagName().equals("object")) {
                        readObject(object, x, y, shapes, warnings, layerName);
                    }
                }
            }
        }
    }

    private static void readObject(Element object, double offsetX, double offsetY,
                                   List<CollisionShape> shapes, List<String> warnings, String layerName) throws IOException {
        String label = layerName + " object " + object.getAttribute("id");
        if (!object.getAttribute("name").isBlank()) {
            label += " (" + object.getAttribute("name") + ")";
        }
        for (String unsupported : List.of("polyline", "point", "text")) {
            if (child(object, unsupported) != null) {
                warnings.add(label + ": " + unsupported + " is not a supported solid shape.");
                return;
            }
        }
        if (object.hasAttribute("gid") || object.hasAttribute("template")) {
            warnings.add(label + ": tile objects and object templates are not supported.");
            return;
        } 
        List<Point2D.Double> points = new ArrayList<>();

        Element polygon = child(object, "polygon");
        Element ellipse = child(object, "ellipse");

        double width = number(object, "width", 0);
        double height = number(object, "height", 0);

        if (polygon != null) {
            String text = polygon.getAttribute("points").trim();

            if (!text.isEmpty()) {
                for (String pair : text.split("\\s+")) {
                    String[] xy = pair.split(",", -1);

                    if (xy.length != 2) {
                        throw new IOException(label + ": invalid polygon point " + pair);
                    }

                    points.add(new Point2D.Double(
                        decimal(xy[0], label),
                        decimal(xy[1], label)
                    ));
                }
            }
        } else if (ellipse != null) {
            if (width <= 0 || height <= 0) {
                warnings.add(label + ": ellipse needs positive width and height.");
                return;
            }

            // Convert ellipse into a 32-point polygon.
            double centerX = width / 2.0;
            double centerY = height / 2.0;
            double radiusX = width / 2.0;
            double radiusY = height / 2.0;

            int segments = 32;

            for (int i = 0; i < segments; i++) {
                double angle = 2.0 * Math.PI * i / segments;

                double x = centerX + radiusX * Math.cos(angle);
                double y = centerY + radiusY * Math.sin(angle);

                points.add(new Point2D.Double(x, y));
            }
        } else {
            // Rectangle
            if (width <= 0 || height <= 0) {
                warnings.add(label + ": rectangle needs positive width and height.");
                return;
            }

            points.addAll(List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(width, 0),
                new Point2D.Double(width, height),
                new Point2D.Double(0, height)
            ));
        }//fixed from 71-127

        for (int i = points.size() - 1; i >= 0 && points.size() > 1; i--) {
            if (points.get(i).distanceSq(points.get((i + 1) % points.size())) < 1e-16) {
                points.remove(i);
            }
        }
        if (!validPolygon(points)) {
            warnings.add(label + ": polygon must have area and no crossing or touching edges.");
            return;
        }
        double radians = Math.toRadians(number(object, "rotation", 0));
        double cos = Math.cos(radians), sin = Math.sin(radians);
        double x = offsetX + number(object, "x", 0), y = offsetY + number(object, "y", 0);
        List<Point2D.Double> worldPoints = new ArrayList<>();
        for (Point2D.Double point : points) {
            worldPoints.add(new Point2D.Double(x + point.x * cos - point.y * sin,
                y + point.x * sin + point.y * cos));
        }
        shapes.add(new CollisionShape(worldPoints));
    }

    private static boolean validPolygon(List<Point2D.Double> points) {
        if (points.size() < 3) {
            return false;
        }
        double twiceArea = 0;
        for (int i = 0; i < points.size(); i++) {
            Point2D.Double a = points.get(i), b = points.get((i + 1) % points.size());
            twiceArea += a.x * b.y - b.x * a.y;
            for (int j = i + 1; j < points.size(); j++) {
                if (j == i + 1 || (i == 0 && j == points.size() - 1)) {
                    continue;
                }
                Point2D.Double c = points.get(j), d = points.get((j + 1) % points.size());
                if (Line2D.linesIntersect(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y)) {
                    return false;
                }
            }
        }
        return Math.abs(twiceArea) > 1e-8;
    }

    private static Element child(Element parent, String tag) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(tag)) {
                return element;
            }
        }
        return null;
    }

    private static double number(Element element, String name, double fallback) throws IOException {
        return element.hasAttribute(name)
            ? decimal(element.getAttribute(name), name) : fallback;
    }

    private static double decimal(String text, String label) throws IOException {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid finite number for " + label + ": " + text, exception);
        }
    }
}
