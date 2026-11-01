package game.tests;

import game.app.*;
import game.collision.*;
import game.racer.*;
import game.world.*;


import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class RaceWorldTest {
    private static int checks;
    private static final String MARKERS = """
        <map><objectgroup name="race">
          <object id="1" name="spawn" x="100" y="100"><point/></object>
          <object id="2" name="checkpoint" x="200" y="50" width="10" height="100">
            <properties><property name="order" type="int" value="1"/></properties>
          </object>
          <object id="3" name="checkpoint" x="300" y="50" width="10" height="100">
            <properties><property name="order" type="int" value="2"/></properties>
          </object>
          <object id="4" name="Finish" x="400" y="50" width="1" height="100"/>
        </objectgroup></map>
        """;

    public static void main(String[] args) throws Exception {
        RaceTrack track = parse(MARKERS);
        RaceWorld world = world(track);
        InputState input = new InputState();
        input.setMovingRight(true);
        world.update(input, 1);
        check(world.getState() == GameState.MENU, "starts on ready screen");
        near(100, world.getPlayer().getFeetBounds().getCenterX(), "menu input cannot move player");
        near(100, world.getPlayer().getGroundY(), "spawn marks ground rather than image top");
        near(0, world.getElapsedSeconds(), "menu time is not race time");
        world.start(input);
        check(input.isMovingRight(), "start keeps held movement keys");
        check(world.getState() == GameState.COUNTDOWN && world.getCountdownNumber() == 3, "countdown starts at 3");
        input.setMovingRight(true);
        world.update(input, 1);
        check(world.getCountdownNumber() == 2, "countdown 2");
        world.update(input, 1);
        check(world.getCountdownNumber() == 1, "countdown 1");
        near(100, world.getPlayer().getFeetBounds().getCenterX(), "countdown locks movement");
        world.update(input, 1);
        check(world.getState() == GameState.RACING && input.isMovingRight(),
            "held movement keys stay active at GO");
        near(0, world.getElapsedSeconds(), "timer begins at GO");

        RaceWorld heldWorld = world(track);
        InputState heldInput = new InputState();
        heldInput.setMovingRight(true);
        heldInput.setMovingDown(true);
        heldInput.setCharging(true);
        heldWorld.start(heldInput);
        heldWorld.update(heldInput, 3.1);
        check(heldInput.isMovingRight() && heldInput.isMovingDown() && heldInput.isCharging(),
            "held Space + S + O survive GO");
        check(heldWorld.getPlayer().getCharge().isCharging(), "held charge starts at GO");
        check(heldWorld.getPlayer().getY() > 37, "held S moves the racer down after GO");

        input.setMovingRight(true);
        world.update(input, 3.2);
        check(world.getState() == GameState.FINISHED, "race completes");
        check(world.getPlayerRank() == 1 && world.getCompetitorCount() == 1, "solo player rank is shown");
        check(world.getPassedCheckpoints() == 2, "both checkpoints counted");
        check(world.getElapsedSeconds() > 1.0 && world.getElapsedSeconds() < 1.5,
            "finish time interpolates crossing inside frame");
        check(world.getPlayer().getFeetBounds().getMinX() >= 400, "rider crosses the finish line");
        double result = world.getElapsedSeconds();
        double finalX = world.getPlayer().getX();
        world.update(input, 20);
        near(result, world.getElapsedSeconds(), "result time frozen");
        near(finalX, world.getPlayer().getX(), "finished input cannot move player");
        check(!world.getPlayer().isMoving(), "finish stops movement animation");

        Racer player = world.getPlayer();
        player.placeAt(30, 70);
        CollisionSystem wall = new CollisionSystem(List.of(new CollisionShape(List.of(
            new Point2D.Double(100, 100), new Point2D.Double(120, 100),
            new Point2D.Double(120, 200), new Point2D.Double(100, 200)))));
        player.update(new RacerAction(1, 0, true), .05, wall);
        check(player.isStunned(), "test has stun to reset");
        player.getStamina().consume(5);
        input.setMovingRight(true);
        input.setSprinting(true);
        world.start(input);
        near(0, world.getElapsedSeconds(), "restart clears clock");
        check(world.getPassedCheckpoints() == 0, "restart clears checkpoints");
        near(100, player.getFeetBounds().getCenterX(), "restart restores spawn x");
        near(100, player.getGroundY(), "restart restores spawn y");
        near(GameConfig.MAX_STAMINA, player.getStamina().getCurrent(), "restart refills stamina");
        near(0, player.getStunRemaining(), "restart clears stun");
        near(0, player.getSlowRemaining(), "restart clears slow");
        check(!player.isMoving() && input.isMovingRight() && input.isSprinting(),
            "restart keeps held movement and sprint keys");
        world.update(input, 3.25);
        near(.25, world.getElapsedSeconds(), "countdown spillover counts only racing portion");
        for (int round = 0; round < 3; round++) {
            input.setMovingRight(true);
            world.update(input, 3.2);
            check(world.getState() == GameState.FINISHED, "multiple restart cycles finish");
            world.start(input);
            world.update(input, 3);
        }

        RaceWorld paused = world(track);
        InputState pauseInput = new InputState();
        paused.start(pauseInput);
        paused.update(pauseInput, 1);
        check(paused.pause(pauseInput) && paused.getState() == GameState.PAUSED,
            "countdown can be paused");
        paused.update(pauseInput, 10);
        near(2, paused.getCountdownNumber(), "pause freezes the countdown");
        check(paused.resume(pauseInput) && paused.getState() == GameState.COUNTDOWN,
            "continue resumes the countdown");
        paused.update(pauseInput, 2);
        check(paused.getState() == GameState.RACING, "resumed countdown reaches GO");
        paused.update(pauseInput, .2);
        double pausedTime = paused.getElapsedSeconds();
        check(paused.pause(pauseInput), "active race pauses");
        paused.update(pauseInput, 5);
        near(pausedTime, paused.getElapsedSeconds(), "pause freezes race time");
        paused.start(pauseInput);
        check(paused.getState() == GameState.COUNTDOWN && paused.getElapsedSeconds() == 0,
            "restart from pause begins a fresh race");
        paused.pause(pauseInput);
        paused.returnToMenu(pauseInput);
        check(paused.getState() == GameState.MENU && paused.getPassedCheckpoints() == 0,
            "return to menu resets the race");

        RaceWorld skipped = world(track);
        skipped.start(input);
        skipped.update(input, 3);
        skipped.getPlayer().placeAt(350 - 48, 100 - 63);
        input.setMovingRight(true);
        skipped.update(input, .3);
        check(skipped.getState() == GameState.RACING && skipped.getPassedCheckpoints() == 0,
            "finish without checkpoints is rejected");
        check(!skipped.getMessage().isEmpty(), "early finish explains missing checkpoints");
        skipped.getPlayer().placeAt(250 - 48, 100 - 63);
        skipped.update(input, .9);
        check(skipped.getPassedCheckpoints() == 0, "out-of-order checkpoint 2 is not credited");
        skipped.getPlayer().placeAt(150 - 48, 100 - 63);
        skipped.update(input, .3);
        check(skipped.getPassedCheckpoints() == 1, "checkpoint 1 counts first");
        skipped.update(input, .9);
        check(skipped.getPassedCheckpoints() == 2, "checkpoint 2 counts after checkpoint 1");

        double t = track.getFinish().entryFraction(0, 100, 1000, 100, 2, 2);
        near(.399, t, "thin gate crossed in one motion segment is detected");
        check(Double.isNaN(track.getFinish().entryFraction(0, 300, 1000, 300, 2, 2)),
            "passing outside gate height is not counted");
        check(Double.isNaN(track.getFinish().entryFraction(400, 100, 400, 100, 2, 2)),
            "standing inside gate cannot create extra crossings");
        reject(MARKERS.replace("value=\"2\"", "value=\"1\""), "duplicate checkpoint order");
        reject(MARKERS.replace("value=\"2\"", "value=\"3\""), "gap in checkpoint orders");
        reject(MARKERS.replace("name=\"order\"", "name=\"wrong\""), "missing checkpoint order");
        reject(MARKERS.replace("type=\"int\"", "type=\"string\""), "wrong property type");
        reject(MARKERS.replace("name=\"Finish\"", "name=\"other\""), "missing finish");
        reject(MARKERS.replace("width=\"1\" height=\"100\"", "width=\"0\" height=\"100\""), "empty finish");
        reject(MARKERS.replace("<point/>", ""), "spawn must be a point");
        reject(MARKERS.replace("x=\"400\"", "x=\"NaN\""), "nonfinite marker coordinate");
        RaceTrack offset = parse(MARKERS.replace("<objectgroup name=\"race\">",
            "<group offsetx=\"10\"><objectgroup name=\"race\" offsety=\"20\">")
            .replace("</objectgroup>", "</objectgroup></group>"));
        near(110, offset.getSpawn().x, "group offset respected");
        near(120, offset.getSpawn().y, "layer offset respected");

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File("assets/Map/Background.tmx"));
        var map = document.getDocumentElement();
        int width = Integer.parseInt(map.getAttribute("width")) * Integer.parseInt(map.getAttribute("tilewidth"));
        int height = Integer.parseInt(map.getAttribute("height")) * Integer.parseInt(map.getAttribute("tileheight"));
        RaceTrack real = RaceTrack.load(map, width, height);
        RaceWorld realWorld = new RaceWorld(real,
            new CollisionSystem(CollisionMapLoader.load(map).shapes()), width, height);
        near(real.getSpawn().y, realWorld.getPlayer().getGroundY(), "saved map spawn valid and clear");
        check(real.getCheckpoints().size() == 2, "saved map has two checkpoints");
        InputState realInput = new InputState();
        realWorld.start(realInput);
        realWorld.update(realInput, 3);
        java.util.ArrayList<RaceTrack.Gate> actualGates = new java.util.ArrayList<>(real.getCheckpoints());
        actualGates.add(real.getFinish());
        for (RaceTrack.Gate gate : actualGates) {
            var area = gate.getArea();
            Racer actualPlayer = realWorld.getPlayer();
            actualPlayer.placeAt(area.getX() - actualPlayer.getFeetBounds().getWidth() / 2 - 4
                - actualPlayer.getWidth() / 2.0, area.getCenterY() - actualPlayer.getHeight() + 7);
            realInput.setMovingRight(true);
            realWorld.update(realInput, .05);
        }
        realWorld.update(realInput, GameConfig.FINISH_EXIT_GRACE_SECONDS + .1);
        check(realWorld.getState() == GameState.FINISHED, "each saved gate can be crossed at its centre");
        realWorld.start(realInput);
        check(realWorld.getState() == GameState.COUNTDOWN && realWorld.getPassedCheckpoints() == 0,
            "saved race restarts cleanly");
        System.out.println("PASS: " + checks + " race checks, including saved Tiled race markers.");
    }

    private static RaceWorld world(RaceTrack track) {
        return new RaceWorld(track, new CollisionSystem(List.of()), 1000, 500);
    }

    private static RaceTrack parse(String xml) throws Exception {
        return RaceTrack.load(DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement(), 1000, 500);
    }

    private static void reject(String xml, String label) throws Exception {
        try {
            parse(xml);
            throw new AssertionError("Accepted " + label);
        } catch (IOException expected) {
            checks++;
        }
    }

    private static void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 1e-5, label + " (" + actual + ")");
    }

    private static void check(boolean passed, String label) {
        checks++;
        if (!passed) {
            throw new AssertionError(label);
        }
    }
}
