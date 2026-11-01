package game.tests;

import game.app.*;
import game.bot.*;
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

public final class BotRaceTest {
    private static int checks;
    private static final String MAP = """
        <map><objectgroup name="race">
         <object name="spawn" x="100" y="100"><point/></object>
         <object name="bot_spawn" x="100" y="140"><point/></object>
         <object name="checkpoint" x="200" y="50" width="10" height="200">
          <properties><property name="order" type="int" value="1"/></properties></object>
         <object name="checkpoint" x="400" y="50" width="10" height="200">
          <properties><property name="order" type="int" value="2"/></properties></object>
         <object name="finish" x="600" y="50" width="2" height="200"/>
         %s
        </objectgroup></map>
        """;

    public static void main(String[] args) throws Exception {
        String route = waypoints(160, 140, 260, 140, 450, 140, 650, 140);
        RaceTrack track = parse(MAP.formatted(route));
        RaceWorld world = new RaceWorld(track, new CollisionSystem(List.of()), 1000, 500,List.of(),TerrainEffects.NONE,1);
        check(world.getRacers().size() == 2 && world.getBot() != null, "one bot created");
        RaceWorld ranking = new RaceWorld(track, new CollisionSystem(List.of()), 1000, 500,
            List.of(), TerrainEffects.NONE, 1);
        InputState rankInput = new InputState();
        ranking.start(rankInput);
        ranking.update(rankInput, 3);
        ranking.getPlayer().placeAt(100 - 48, 100 - 63);
        ranking.getBot().placeAt(180 - 48, 140 - 63);
        check(ranking.getPlayerRank() == 2, "live rank counts a bot farther ahead on the course");
        ranking.getPlayer().placeAt(190 - 48, 150 - 63);
        ranking.getBot().placeAt(100 - 48, 140 - 63);
        check(ranking.getPlayerRank() == 1, "live rank updates when the player moves ahead");
        InputState input = new InputState();
        world.start(input);
        double x = world.getBot().getX();
        world.update(input, 2.9);
        near(x, world.getBot().getX(), "bot waits for countdown");
        world.update(input, .1);
        near(x, world.getBot().getX(), "GO does not add countdown time to movement");
        world.update(input, 5);
        check(world.isBotFinished(), "bot autonomously finishes");
        check(world.getBotPassedCheckpoints() == 2, "bot passes both checkpoints");
        check(world.getPassedCheckpoints() == 0 && world.getState() == GameState.RACING,
            "bot finish does not finish stationary player");
        double finish = world.getBotFinishSeconds();
        x = world.getBot().getX();
        world.update(input, 5);
        near(x, world.getBot().getX(), "finished bot stays still");
        near(finish, world.getBotFinishSeconds(), "bot finish time frozen");
        check(world.getBot().getStamina().getCurrent() < GameConfig.MAX_STAMINA,
            "bot spends real stamina when sprinting");

        Racer bot = racer();
        Racer human = racer();
        BotController controller = new BotController(new BotRoute(new Point2D.Double(100,140),
            List.of(new Point2D.Double(900,140))));
        input.setMovingRight(true);
        input.setSprinting(true);
        for (int i = 0; i < 40; i++) {
            bot.update(controller.createAction(null, bot, .05), .05);
            human.update(new PlayerController().createAction(input, human, .05), .05);
        }
        near(human.getX(), bot.getX(), "same sprint speed as human");
        near(human.getStamina().getCurrent(), bot.getStamina().getCurrent(), "same stamina consumption");
        BotController forwardOnly = new BotController(new BotRoute(new Point2D.Double(100,140),
            List.of(new Point2D.Double(50,140), new Point2D.Double(900,140))), false);
        Racer forwardBot = racer();
        RacerAction forwardAction = forwardOnly.createAction(null, forwardBot, .05);
        check(forwardAction.getHorizontalDirection() >= 0 && forwardOnly.getNextWaypoint() == 1,
            "bot skips a route point behind it instead of running left");
        bot.getStamina().consume(GameConfig.MAX_STAMINA);
        x = bot.getX();
        bot.update(controller.createAction(null, bot, 1), 1);
        check(controller.isResting(), "low energy causes bot to rest");
        near(x, bot.getX(), "resting bot stands still");
        near(.15, bot.getStamina().getCurrent(), "rest recovers at human rate");
        for (int i = 0; i < 40; i++) bot.update(controller.createAction(null, bot, 1), 1);
        check(!controller.isResting() && bot.getX() > x, "bot resumes after rest");

        CollisionShape rock = new CollisionShape(List.of(new Point2D.Double(250,80),
            new Point2D.Double(290,80), new Point2D.Double(290,180), new Point2D.Double(250,180)));
        CollisionSystem collisions = new CollisionSystem(List.of(rock));
        try {
            new RaceWorld(track, collisions, 1000, 500,List.of(),TerrainEffects.NONE,1);
            throw new AssertionError("route through rock accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        RaceTrack detour = parse(MAP.formatted(waypoints(180,140,180,220,330,220,330,140,650,140)));
        RaceWorld aroundRock = new RaceWorld(detour, collisions, 1000, 500,List.of(),TerrainEffects.NONE,1);
        input.clear();
        aroundRock.start(input);
        aroundRock.update(input, 3);
        for (int i = 0; i < 1000 && !aroundRock.isBotFinished(); i++) {
            aroundRock.update(input, .02);
            check(!collisions.overlaps(aroundRock.getBot().getFeetBounds()), "bot never penetrates obstacle");
        }
        check(aroundRock.isBotFinished(), "authored detour reaches finish around obstacle");

        RaceWorld lateBot = new RaceWorld(parse(MAP.formatted(waypoints(100,350,150,350,150,140,650,140))),
            new CollisionSystem(List.of()), 1000, 500,List.of(),TerrainEffects.NONE,1);
        lateBot.start(input);
        lateBot.update(input, 3);
        input.setMovingRight(true);
        input.setSprinting(true);
        lateBot.update(input, 3);
        check(lateBot.getState() == GameState.FINISHED && !lateBot.isBotFinished(), "player can finish first");
        double playerTime = lateBot.getElapsedSeconds();
        lateBot.update(input, 10);
        check(lateBot.isBotFinished(), "bot continues to finish after player result");
        near(playerTime, lateBot.getElapsedSeconds(), "player result time remains frozen while bot runs");
        lateBot.getBot().getStamina().consume(5);
        lateBot.start(input);
        near(100, lateBot.getBot().getX() + 48, "restart restores bot spawn");
        near(GameConfig.MAX_STAMINA, lateBot.getBot().getStamina().getCurrent(), "restart refills bot");
        check(!lateBot.isBotFinished() && lateBot.getBotPassedCheckpoints() == 0,
            "restart clears bot progress and finish");
        lateBot.update(input, 3);
        lateBot.update(input, 12);
        check(lateBot.isBotFinished(), "bot follows full route again after restart");

        reject(MAP.formatted(route).replace("name=\"bot_spawn\"", "name=\"unused\""));
        reject(MAP.formatted(route).replace("name='waypoint'", "name='unused'"));
        reject(MAP.formatted(route).replace("value='4'", "value='3'"));

        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File("assets/Map/Background.tmx"));
        var map = document.getDocumentElement();
        int w = Integer.parseInt(map.getAttribute("width")) * Integer.parseInt(map.getAttribute("tilewidth"));
        int h = Integer.parseInt(map.getAttribute("height")) * Integer.parseInt(map.getAttribute("tileheight"));
        RaceTrack actual = RaceTrack.load(map, w, h);
        if (actual.getBotRoute() != null) {
            RaceWorld actualWorld = new RaceWorld(actual,
                new CollisionSystem(CollisionMapLoader.load(map).shapes()), w, h,List.of(),TerrainEffects.NONE,1);
            input.clear();
            actualWorld.start(input);
            actualWorld.update(input, 3);
            for (int i = 0; i < 18000 && !actualWorld.isBotFinished(); i++) actualWorld.update(input, .02);
            check(actualWorld.isBotFinished(), "bot finishes saved real map within six minutes");
            System.out.println("Saved map bot finish time: " + actualWorld.getBotFinishSeconds());
        } else {
            System.out.println("Saved map: waiting for bot_spawn and waypoint markers.");
        }
        System.out.println("PASS: " + checks + " bot checks.");
    }

    private static Racer racer() {
        return new Racer(52,77,96,65,new Stamina(GameConfig.MAX_STAMINA));
    }
    private static String waypoints(double... xy) {
        StringBuilder xml = new StringBuilder();
        for (int i=0;i<xy.length;i+=2) xml.append("<object name='waypoint' x='").append(xy[i])
            .append("' y='").append(xy[i+1]).append("'><point/><properties><property name='order' type='int' value='")
            .append(i/2+1).append("'/></properties></object>");
        return xml.toString();
    }
    private static RaceTrack parse(String xml) throws Exception {
        return RaceTrack.load(DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement(), 1000, 500);
    }
    private static void reject(String xml) throws Exception {
        try { parse(xml); throw new AssertionError("invalid bot markers accepted"); }
        catch (IOException expected) { checks++; }
    }
    private static void near(double expected,double actual,String label) {
        check(Math.abs(expected-actual)<1e-5,label+" ("+actual+")");
    }
    private static void check(boolean passed,String label) {
        checks++;
        if(!passed) throw new AssertionError(label);
    }
}
