package game.tests;

import game.app.*;
import game.collision.*;
import game.racer.*;


import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class CollisionSystemTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        CollisionSystem wall = system(rectangle(100, 100, 20, 100));
        near(90, move(wall, 0, 120, 1000, 0).x, "fast crossing right");
        near(-80, move(wall, 200, 120, -1000, 0).x, "fast crossing left");
        near(90, move(wall, 105, 0, 0, 1000).y, "crossing down");
        near(-100, move(wall, 105, 300, 0, -1000).y, "crossing up");
        Point2D.Double slide = move(wall, 0, 120, 200, 40);
        near(90, slide.x, "wall stops inward motion");
        near(40, slide.y, "wall preserves sliding motion");
        near(-20, move(wall, 90, 120, -20, 0).x, "move away from contact");
        near(50, move(wall, 90, 120, 0, 50).y, "move along exact contact");
        near(300, move(wall, 0, 20, 300, 0).x, "empty area remains traversable");
        Point2D.Double bounded = new CollisionSystem(List.of()).move(
            new Rectangle2D.Double(10, 10, 10, 10), -100, 100,
            new Rectangle2D.Double(5, 5, 90, 90));
        near(-10, bounded.x, "world edge blocks outward x");
        near(80, bounded.y, "world corner blocks outward y");
        near(90, move(system(rectangle(100, 0, .001, 500)), 0, 50, 10000, 0).x,
            "very thin obstacle cannot be skipped");

        CollisionSystem corner = system(rectangle(100, 100, 30, 30));
        Point2D.Double stop = move(corner, 0, 0, 500, 500);
        near(90, stop.x, "corner blocks x");
        near(90, stop.y, "corner blocks y");
        CollisionSystem twoWalls = new CollisionSystem(List.of(rectangle(100, 0, 20, 300),
            rectangle(0, 180, 300, 20)));
        stop = move(twoWalls, 0, 100, 500, 500);
        near(90, stop.x, "two walls x");
        near(70, stop.y, "two walls y");

        CollisionSystem slope = system(shape(100, 200, 200, 100, 200, 200));
        slide = move(slope, 110, 110, 100, 0);
        check(slide.x > 60 && slide.y < -10, "horizontal input slides along slanted boundary");
        check(!slope.overlaps(new Rectangle2D.Double(110 + slide.x, 110 + slide.y, 10, 10)),
            "slanted sliding ends outside solid");
        CollisionSystem reversedSlope = system(shape(200, 200, 200, 100, 100, 200));
        Point2D.Double reversed = move(reversedSlope, 110, 110, 100, 0);
        near(slide.x, reversed.x, "clockwise and counterclockwise shapes agree x");
        near(slide.y, reversed.y, "clockwise and counterclockwise shapes agree y");

        CollisionSystem concave = system(shape(100, 100, 200, 100, 200, 120,
            120, 120, 120, 200, 100, 200));
        near(20, move(concave, 140, 140, 20, 20).x, "L-shaped notch stays empty");
        near(-20, move(concave, 140, 140, -100, 0).x, "L-shaped inner vertical edge");
        near(-20, move(concave, 140, 140, 0, -100).y, "L-shaped inner horizontal edge");
        Point2D.Double feetPosition = new Point2D.Double(0, 120);
        for (int i = 0; i < 500; i++) {
            Point2D.Double delta = move(wall, feetPosition.x, feetPosition.y, 12.5, .05);
            feetPosition.x += delta.x;
            feetPosition.y += delta.y;
            check(!wall.overlaps(new Rectangle2D.Double(feetPosition.x, feetPosition.y, 10, 10)),
                "repeated contact never penetrates");
        }
        near(90, feetPosition.x, "long contact stays at wall");

        Racer racer = new Racer(40, 60, 96, 65, new Stamina(GameConfig.MAX_STAMINA));
        CollisionSystem spawn = system(rectangle(60, 100, 70, 40));
        check(spawn.overlaps(racer.getFeetBounds()), "test starts inside obstacle");
        spawn.placeAtFreeStart(racer, 400, 400);
        check(!spawn.overlaps(racer.getFeetBounds()), "spawn relocates to free area");
        Racer runner = new Racer(0, 70, 96, 65, new Stamina(GameConfig.MAX_STAMINA));
        for (int i = 0; i < 100; i++) {
            runner.update(new RacerAction(1, 0, true), .05, wall);
            check(!wall.overlaps(runner.getFeetBounds()), "racer sprint respects feet collision");
        }
        near(100, runner.getFeetBounds().getMaxX(), "feet stop at wall instead of whole image");
        java.util.Random random = new java.util.Random(7341);
        CollisionSystem forest = new CollisionSystem(List.of(rectangle(100, 100, 20, 100),
            shape(200, 200, 300, 100, 300, 200),
            shape(100, 300, 200, 300, 200, 320, 120, 320, 120, 400, 100, 400)));
        Rectangle2D.Double wanderingFeet = new Rectangle2D.Double(50, 50, 34.56, 10);
        for (int i = 0; i < 2000; i++) {
            Point2D.Double delta = forest.move(wanderingFeet, random.nextDouble() * 200 - 100,
                random.nextDouble() * 200 - 100);
            wanderingFeet.x += delta.x;
            wanderingFeet.y += delta.y;
            check(!forest.overlaps(wanderingFeet), "random movement does not penetrate mixed shapes");
        }

        CollisionMapLoader.Result data = load("""
            <map><group offsetx="100" offsety="200">
              <objectgroup name="collision" visible="0" offsetx="3" offsety="4">
                <object id="1" x="10" y="20" width="30" height="40"/>
                <object id="2" x="100" y="100" width="20" height="10" rotation="90"/>
                <object id="3" x="0" y="0"><polygon points="0,0 50,0 50,10 10,10 10,50 0,50 0,0"/></object>
                <object id="4" x="10" y="20"/>
                <object id="5" width="2" height="2"><ellipse/></object>
                <object id="6"><polyline points="0,0 10,10"/></object>
                <object id="7"><point/></object>
                <object id="8"><polygon points="0,0 10,10 0,10 10,0"/></object>
              </objectgroup>
            </group><objectgroup name="scenery"><object width="500" height="500"/></objectgroup></map>
            """);
        check(data.shapes().size() == 3, "only collision rectangles and valid polygons are loaded");
        check(data.warnings().size() == 5, "unsupported and invalid objects are reported");
        Rectangle2D bounds = data.shapes().get(0).getBounds();
        near(113, bounds.getX(), "group and layer offsets x");
        near(224, bounds.getY(), "group and layer offsets y");
        bounds = data.shapes().get(1).getBounds();
        near(193, bounds.getX(), "rotated rectangle x");
        near(304, bounds.getY(), "rotated rectangle y");
        near(10, bounds.getWidth(), "rotated rectangle width");
        near(20, bounds.getHeight(), "rotated rectangle height");
        check(new CollisionSystem(data.shapes()).overlaps(new Rectangle2D.Double(115, 225, 5, 5)),
            "hidden editor layer remains solid");
        check(load("<map><objectgroup name='collision'/></map>").shapes().isEmpty(),
            "deleting all objects removes walls");
        check(load("<map><objectgroup name='collision'><object id='9999' width='10' height='10'/></objectgroup></map>")
            .shapes().size() == 1, "new object IDs work without Java changes");
        try {
            load("<map><objectgroup name='collision'><object x='NaN' width='10' height='10'/></objectgroup></map>");
            throw new AssertionError("NaN accepted");
        } catch (IOException expected) {
            checks++;
        }
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File("assets/Map/Background.tmx"));
        CollisionMapLoader.Result actual = CollisionMapLoader.load(document.getDocumentElement());
        for (CollisionShape solid : actual.shapes()) {
            Rectangle2D box = solid.getBounds();
            Rectangle2D.Double approach = new Rectangle2D.Double(box.getMinX() - 30,
                box.getCenterY() - 5, 10, 10);
            double distance = box.getWidth() + 60;
            Rectangle2D corridor = new Rectangle2D.Double(approach.x, approach.y,
                distance + approach.width, approach.height);
            if (solid.intersects(corridor)) {
                CollisionSystem single = system(solid);
                Point2D.Double delta = single.move(approach, distance, 0);
                approach.x += delta.x;
                approach.y += delta.y;
                check(delta.x < distance && !single.overlaps(approach), "saved map object blocks crossing");
            }
        }
        System.out.println("PASS: " + checks + " checks. Saved map: " + actual.shapes().size()
            + " solid objects; " + actual.warnings().size() + " skipped objects.");
        actual.warnings().forEach(System.out::println);
    }

    private static CollisionMapLoader.Result load(String xml) throws Exception {
        return CollisionMapLoader.load(DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement());
    }

    private static CollisionShape rectangle(double x, double y, double w, double h) {
        return shape(x, y, x + w, y, x + w, y + h, x, y + h);
    }

    private static CollisionShape shape(double... xy) {
        java.util.ArrayList<Point2D.Double> points = new java.util.ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            points.add(new Point2D.Double(xy[i], xy[i + 1]));
        }
        return new CollisionShape(points);
    }

    private static CollisionSystem system(CollisionShape shape) {
        return new CollisionSystem(List.of(shape));
    }

    private static Point2D.Double move(CollisionSystem system, double x, double y, double dx, double dy) {
        return system.move(new Rectangle2D.Double(x, y, 10, 10), dx, dy);
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 1e-4, label + " (" + actual + ")");
    }

    private static void check(boolean passed, String label) {
        checks++;
        if (!passed) {
            throw new AssertionError(label);
        }
    }
}
