package game.world;

import game.bot.*;
import game.items.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Race markers come from Tiled's race object layer, never fixed object IDs. */
public final class RaceTrack {
    private final Point2D.Double spawn;
    private final List<Gate> checkpoints;
    private final Gate finish;
    private final BotRoute botRoute;

    private RaceTrack(Point2D.Double spawn, List<Gate> checkpoints, Gate finish, BotRoute botRoute) {
        this.spawn = spawn;
        this.checkpoints = List.copyOf(checkpoints);
        this.finish = finish;
        this.botRoute = botRoute;
    }

    public static RaceTrack load(Element map, int worldWidth, int worldHeight) throws IOException {
        List<Marker> markers = new ArrayList<>();
        readMarkers(map, 0, 0, markers);
        Point2D.Double spawn = null;
        Gate finish = null;
        List<Gate> checkpoints = new ArrayList<>();
        Point2D.Double botSpawn = null;
        List<OrderedPoint> waypoints = new ArrayList<>();
        for (Marker marker : markers) {
            Element object = marker.object;
            String name = object.getAttribute("name").trim().toLowerCase(Locale.ROOT);
            if (!List.of("spawn", "checkpoint", "finish", "bot_spawn", "waypoint").contains(name)) {
                continue;
            }
            double x = marker.offsetX + number(object, "x", 0);
            double y = marker.offsetY + number(object, "y", 0);
            if (object.hasAttribute("gid") || object.hasAttribute("template")) {
                throw new IOException(name + " must be a plain race marker, not a tile or template.");
            }
            if (List.of("spawn", "bot_spawn", "waypoint").contains(name)) {
                if (child(object, "point") == null) {
                    throw new IOException(name + " must be a Point.");
                }
                if (x < 0 || y < 0 || x > worldWidth || y > worldHeight) {
                    throw new IOException(name + " must be inside the map.");
                }
                if (name.equals("spawn")) {
                    if (spawn != null) throw new IOException("race needs exactly one Point named spawn.");
                    spawn = new Point2D.Double(x, y);
                } else if (name.equals("bot_spawn")) {
                    if (botSpawn != null) throw new IOException("Use one bot_spawn as the anchor for the bot starting grid.");
                    botSpawn = new Point2D.Double(x, y);
                } else {
                    waypoints.add(new OrderedPoint(order(object, "waypoint"), new Point2D.Double(x, y),
                        nextProperty(object), difficultyProperty(object)));
                }
                continue;
            }
            for (String unsupported : List.of("point", "polygon", "ellipse", "polyline", "text")) {
                if (child(object, unsupported) != null) {
                    throw new IOException(name + " must be a Rectangle.");
                }
            }
            double width = number(object, "width", 0), height = number(object, "height", 0);
            if (width <= 0 || height <= 0 || Math.abs(number(object, "rotation", 0)) > 1e-9) {
                throw new IOException(name + " needs positive width/height and rotation 0.");
            }
            if (x < 0 || y < 0 || x + width > worldWidth || y + height > worldHeight) {
                throw new IOException(name + " must be entirely inside the map.");
            }
            Rectangle2D.Double area = new Rectangle2D.Double(x, y, width, height);
            if (name.equals("finish")) {
                if (finish != null) {
                    throw new IOException("race needs exactly one Rectangle named finish.");
                }
                finish = new Gate(0, area);
            } else {
                Element properties = child(object, "properties");
                int order = 0;
                if (properties != null) {
                    for (Node node = properties.getFirstChild(); node != null; node = node.getNextSibling()) {
                        if (node instanceof Element property && property.getTagName().equals("property")
                            && property.getAttribute("name").equals("order")) {
                            try {
                                if (!property.getAttribute("type").equals("int")) {
                                    throw new NumberFormatException();
                                }
                                order = Integer.parseInt(property.getAttribute("value"));
                            } catch (NumberFormatException exception) {
                                throw new IOException("checkpoint order must be an int: 1, 2, 3, ...");
                            }
                        }
                    }
                }
                if (order <= 0) {
                    throw new IOException("checkpoint " + object.getAttribute("id")
                        + " is missing a positive int property named order.");
                }
                checkpoints.add(new Gate(order, area));
            }
        }
        if (spawn == null || finish == null || checkpoints.isEmpty()) {
            throw new IOException("Add race layer with spawn Point, checkpoint Rectangle (order=1), and finish Rectangle.");
        }
        checkpoints.sort(Comparator.comparingInt(Gate::getOrder));
        for (int i = 0; i < checkpoints.size(); i++) {
            if (checkpoints.get(i).getOrder() != i + 1) {
                throw new IOException("checkpoint orders must be unique and consecutive: 1, 2, 3, ...");
            }
        }
        BotRoute botRoute = null;
        if (botSpawn != null || !waypoints.isEmpty()) {
            if (botSpawn == null || waypoints.isEmpty()) {
                throw new IOException("Add bot_spawn Point and waypoint Points with int order=1,2,3,... in race.");
            }
            waypoints.sort(Comparator.comparingInt(OrderedPoint::order));
            boolean graphMode=waypoints.stream().anyMatch(point->point.next()!=null);
            Map<Integer,Point2D.Double> points=new LinkedHashMap<>();
            Map<Integer,List<Integer>> links=new LinkedHashMap<>();
            Map<Integer,Map<Integer,Double>> difficulties=new LinkedHashMap<>();
            for (int i = 0; i < waypoints.size(); i++) {
                OrderedPoint point=waypoints.get(i);
                if(points.put(point.order(),point.point())!=null) throw new IOException("waypoint order IDs must be unique.");
                if (!graphMode && point.order() != i + 1) {
                    throw new IOException("waypoint orders must be unique and consecutive: 1, 2, 3, ...");
                }
                if(graphMode) {
                    if(point.next()==null) throw new IOException("waypoint "+point.order()
                        +" is missing a string property next. At the last waypoint, add next and leave its value empty.");
                    List<Integer> next=new ArrayList<>();
                    if(!point.next().isBlank()) for(String token:point.next().split(",",-1)) {
                        try {
                            int id=Integer.parseInt(token.trim());
                            if(id<=0 || next.contains(id)) throw new NumberFormatException();
                            next.add(id);
                        } catch(NumberFormatException bad) {throw new IOException("waypoint next must contain unique positive IDs separated by commas.");}
                    }
                    links.put(point.order(),next);
                    if(point.difficulty()!=null) {
                        String[] values=point.difficulty().isBlank()?new String[0]:point.difficulty().split(",",-1);
                        if(values.length!=next.size()) throw new IOException("waypoint "+point.order()
                            +" nextDifficulty must have one comma-separated number for each next ID.");
                        Map<Integer,Double> edgeDifficulties=new LinkedHashMap<>();
                        for(int edge=0;edge<values.length;edge++) {
                            try {
                                double difficulty=Double.parseDouble(values[edge].trim());
                                if(!Double.isFinite(difficulty) || difficulty<0) throw new NumberFormatException();
                                edgeDifficulties.put(next.get(edge),difficulty);
                            } catch(NumberFormatException bad) {
                                throw new IOException("waypoint nextDifficulty must contain non-negative numbers separated by commas.");
                            }
                        }
                        difficulties.put(point.order(),edgeDifficulties);
                    }
                }
            }
            try {
                botRoute = new BotRoute(botSpawn, waypoints.stream().map(OrderedPoint::point).toList(),
                    graphMode?new BotRouteGraph(points,links,difficulties):null);
            } catch(IllegalArgumentException bad) {throw new IOException(bad.getMessage());}
        }
        return new RaceTrack(spawn, checkpoints, finish, botRoute);
    }

    private static String nextProperty(Element object) throws IOException {
        return stringProperty(object,"next");
    }

    private static String difficultyProperty(Element object) throws IOException {
        return stringProperty(object,"nextDifficulty");
    }

    private static String stringProperty(Element object,String propertyName) throws IOException {
        Element properties=child(object,"properties");
        if(properties==null) return null;
        String result=null;
        for(Node node=properties.getFirstChild();node!=null;node=node.getNextSibling()) {
            if(node instanceof Element property && property.getTagName().equals("property")
                && property.getAttribute("name").equals(propertyName)) {
                if(result!=null) throw new IOException("Duplicate waypoint "+propertyName+" property.");
                String type=property.getAttribute("type");
                if(!type.isEmpty() && !type.equals("string")) throw new IOException("waypoint "+propertyName+" must be a string property.");
                result=property.hasAttribute("value")?property.getAttribute("value"):property.getTextContent();
            }
        }
        return result;
    }

    private static int order(Element object, String label) throws IOException {
        Element properties = child(object, "properties");
        if (properties != null) {
            for (Node node = properties.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element property && property.getTagName().equals("property")
                    && property.getAttribute("name").equals("order")) {
                    try {
                        int value = Integer.parseInt(property.getAttribute("value"));
                        if (value <= 0 || !property.getAttribute("type").equals("int")) throw new NumberFormatException();
                        return value;
                    } catch (NumberFormatException exception) {
                        throw new IOException(label + " order must be a positive int.");
                    }
                }
            }
        }
        throw new IOException(label + " is missing int property order.");
    }

    private static void readMarkers(Element parent, double x, double y, List<Marker> markers) throws IOException {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element layer)) {
                continue;
            }
            if (layer.getTagName().equals("group")) {
                readMarkers(layer, x + number(layer, "offsetx", 0), y + number(layer, "offsety", 0), markers);
            } else if (layer.getTagName().equals("objectgroup")
                && layer.getAttribute("name").trim().equalsIgnoreCase("race")) {
                for (Node object = layer.getFirstChild(); object != null; object = object.getNextSibling()) {
                    if (object instanceof Element element && element.getTagName().equals("object")) {
                        markers.add(new Marker(element, x + number(layer, "offsetx", 0),
                            y + number(layer, "offsety", 0)));
                    }
                }
            }
        }
    }

    private static Element child(Element parent, String tag) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(tag)) {
                return element;
            }
        }
        return null;
    }

    private static double number(Element object, String name, double fallback) throws IOException {
        if (!object.hasAttribute(name)) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(object.getAttribute(name));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid race marker number: " + name);
        }
    }

    public Point2D.Double getSpawn() { return (Point2D.Double) spawn.clone(); }
    public List<Gate> getCheckpoints() { return checkpoints; }
    public Gate getFinish() { return finish; }
    public BotRoute getBotRoute() { return botRoute; }

    private record Marker(Element object, double offsetX, double offsetY) {}
    private record OrderedPoint(int order, Point2D.Double point,String next,String difficulty) {}

    public static final class Gate {
        private final int order;
        private final Rectangle2D.Double area;

        private Gate(int order, Rectangle2D.Double area) {
            this.order = order;
            this.area = area;
        }

        public int getOrder() { return order; }
        public Rectangle2D getArea() { return (Rectangle2D) area.clone(); }

        /** Swept feet vs rectangle, including gates thinner than a frame's movement. */
        public double entryFraction(double x1, double y1, double x2, double y2,
                                    double feetWidth, double feetHeight) {
            return SweptTrigger.entryFraction(area, x1, y1, x2, y2, feetWidth, feetHeight, false);
        }
    }
}
