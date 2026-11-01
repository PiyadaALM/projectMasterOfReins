package game.tests;

import game.app.*;
import game.bot.*;
import game.collision.*;
import game.combat.*;
import game.items.*;
import game.racer.*;
import game.world.*;


import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class RaceItemPickupTest {
    public static void main(String[] args) throws Exception {
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        check(map.getItemPickups().stream().anyMatch(p -> p.type() == StaminaPickup.Type.STAMINA),
            "Object Layer stamina loads as a pickup");
        check(map.getItemPickups().stream().anyMatch(p -> p.type() == StaminaPickup.Type.SPEED),
            "Object Layer speed loads as a pickup");
        check(map.getItemPickups().stream().anyMatch(p -> p.type() == StaminaPickup.Type.SHIELD),
            "Object Layer shield loads as a pickup");
        StaminaPickup.Definition speedDef = map.getItemPickups().stream()
            .filter(p -> p.type() == StaminaPickup.Type.SPEED).findFirst().orElseThrow();
        StaminaPickup speedPickup = new StaminaPickup(speedDef);
        check(speedPickup.getArea().getWidth() <= speedDef.image().getWidth() * GameConfig.ITEM_ART_SCALE
            && speedPickup.getArea().getHeight() <= speedDef.image().getHeight() * GameConfig.ITEM_ART_SCALE,
            "speed pickup contact area stays within the rendered artwork bounds");
        BufferedImage before = copy(speedDef.image());
        var fixedArea = speedPickup.getArea();
        map.update(.11);
        check(changed(before, speedDef.image()), "animated speed tile advances in its Object Layer");
        map.update(.35);
        check(fixedArea.equals(speedPickup.getArea()), "pickup contact area stays fixed across animation frames");

        Racer racer = racer();
        StaminaPickup speed1 = new StaminaPickup(definition(StaminaPickup.Type.SPEED, 2));
        StaminaPickup speed2 = new StaminaPickup(definition(StaminaPickup.Type.SPEED, 2));
        check(speed1.collectItem(racer) && speed2.collectItem(racer), "speed pickups collect");
        near(4, racer.getStatus().getSpeedBoostRemaining(), "speed pickup durations accumulate");
        double x = racer.getX();
        racer.update(new RacerAction(1, 0, false), .1);
        near(GameConfig.NORMAL_SPEED * GameConfig.ITEM_SPEED_MULTIPLIER * .1,
            racer.getX() - x, "speed power-up doubles movement speed");

        StaminaPickup shield1 = new StaminaPickup(definition(StaminaPickup.Type.SHIELD, 1));
        StaminaPickup shield2 = new StaminaPickup(definition(StaminaPickup.Type.SHIELD, 1));
        check(shield1.collectItem(racer) && shield2.collectItem(racer), "shield pickups collect");
        check(racer.getStatus().getShieldCharges() == 2, "shield charges accumulate");
        CollisionShape wallShape = new CollisionShape(List.of(new Point2D.Double(150, 0),
            new Point2D.Double(170, 0), new Point2D.Double(170, 100), new Point2D.Double(150, 100)));
        racer.update(new RacerAction(1, 0, true), 1, new CollisionSystem(List.of(wallShape)));
        check(racer.getStatus().getShieldCharges() == 1 && !racer.isStunned() && !racer.isSlowed(),
            "shield consumes one charge to block collision slow and stun");

        Racer attacker = racer();
        Racer defender = racer();
        defender.placeAt(35, 0);
        defender.getStatus().addShield(1);
        attacker.face(new RacerAction(1, 0, false));
        CollisionSystem empty = new CollisionSystem(List.of());
        CombatSystem combat = new CombatSystem();
        combat.step(List.of(attacker, defender), Map.of(attacker, new CombatAction(true, false)), empty, .12);
        combat.step(List.of(attacker, defender), Map.of(), empty, .12);
        check(combat.getEvents().size() == 1
            && combat.getEvents().get(0).outcome() == CombatSystem.Outcome.SHIELD,
            "shield blocks a combat hit");
        check(defender.getStatus().getShieldCharges() == 0 && defender.getCombat().getSlowRemaining() == 0,
            "blocked combat hit consumes one shield and avoids slow");

        RaceTrack track = testTrack();
        BotItemBehavior botItems = new BotItemBehavior(track);
        Racer bot = racer();
        bot.placeAt(52, 37);
        StaminaPickup speed = new StaminaPickup(definition(StaminaPickup.Type.SPEED, 0, 180, 84));
        StaminaPickup shield = new StaminaPickup(definition(StaminaPickup.Type.SHIELD, 1, 180, 84));
        Point2D.Double routeTarget = new Point2D.Double(600, 100);
        check(botItems.choose(bot, routeTarget, List.of(speed), empty, TerrainEffects.NONE, 0,
            BotPersonality.BALANCED) == speed, "bot seeks a useful speed boost at full stamina");
        check(botItems.choose(bot, routeTarget, List.of(shield), empty, TerrainEffects.NONE, 0,
            BotPersonality.BALANCED) == shield, "bot seeks a shield at full stamina");
        System.out.println("PASS: 13 Object Layer pickup, status, and bot-choice checks.");
    }

    private static StaminaPickup.Definition definition(StaminaPickup.Type type, double amount) {
        return definition(type, amount, 0, 0);
    }
    private static StaminaPickup.Definition definition(StaminaPickup.Type type, double amount, int x, int y) {
        return new StaminaPickup.Definition(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
            x, y, 1, amount, 10, type, type == StaminaPickup.Type.SPEED ? 2 : 0);
    }
    private static Racer racer() {
        return new Racer(0, 20, 96, 65, new Stamina(GameConfig.MAX_STAMINA));
    }
    private static RaceTrack testTrack() throws Exception {
        String xml = """
            <map><objectgroup name="race">
              <object name="spawn" x="100" y="100"><point/></object>
              <object name="bot_spawn" x="100" y="160"><point/></object>
              <object name="checkpoint" x="700" y="50" width="5" height="100">
                <properties><property name="order" type="int" value="1"/></properties></object>
              <object name="finish" x="900" y="50" width="5" height="100"/>
              <object name="waypoint" x="950" y="100"><point/>
                <properties><property name="order" type="int" value="1"/></properties></object>
            </objectgroup></map>
            """;
        var root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement();
        return RaceTrack.load(root, 1000, 300);
    }
    private static BufferedImage copy(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        result.getGraphics().drawImage(image, 0, 0, null);
        return result;
    }
    private static boolean changed(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++)
            if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
        return false;
    }
    private static void near(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-6) throw new AssertionError(message + ": " + actual);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
