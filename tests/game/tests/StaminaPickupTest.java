package game.tests;

import game.app.*;
import game.collision.*;
import game.items.*;
import game.racer.*;
import game.world.*;


import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class StaminaPickupTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        StaminaPickup pickup = new StaminaPickup(definition(100, 100, 8, 8));
        Racer racer = new Racer(0, 47, 96, 65, new Stamina(15));
        check(Double.isNaN(pickup.contactFraction(racer)), "full stamina does not consume item");
        near(0, pickup.collect(racer), "full stamina receives nothing");
        check(pickup.isAvailable(), "full stamina leaves item available");
        racer.getStamina().consume(10);
        racer.update(new RacerAction(1, 0, false), .5);
        check(!pickup.getArea().intersects(racer.getFeetBounds()), "test runner ends beyond small pickup");
        check(!Double.isNaN(pickup.contactFraction(racer)), "swept pickup catches fast crossing");
        near(5, pickup.collect(racer), "pickup restores configured amount");
        near(10, racer.getStamina().getCurrent(), "stamina increased");
        check(!pickup.isAvailable(), "collected pickup disappears");
        near(10, pickup.getCooldownRemaining(), "respawn timer begins");
        Racer other = new Racer(50,47,96,65,new Stamina(15));
        other.getStamina().consume(10);
        near(0, pickup.collect(other), "second competitor cannot collect same spawn");
        pickup.update(9.9);
        check(!pickup.isAvailable(), "does not respawn too early");
        pickup.update(.1);
        check(pickup.isAvailable(), "respawns at ten seconds");
        racer.getStamina().recover(3);
        near(2, pickup.collect(racer), "refill capped at maximum");
        near(15, racer.getStamina().getCurrent(), "no overfill");
        pickup.reset();
        check(pickup.isAvailable(), "reset restores item");
        Racer idle = new Racer(52,47,96,65,new Stamina(15));
        idle.getStamina().consume(5);
        check(!Double.isNaN(pickup.contactFraction(idle)), "standing on available item can collect");

        String xml = """
          <map><objectgroup name="race">
           <object name="spawn" x="100" y="100"><point/></object>
           <object name="bot_spawn" x="100" y="140"><point/></object>
           <object name="checkpoint" x="300" y="50" width="5" height="200">
            <properties><property name="order" type="int" value="1"/></properties></object>
           <object name="finish" x="600" y="50" width="5" height="200"/>
           <object name="waypoint" x="650" y="140"><point/>
            <properties><property name="order" type="int" value="1"/></properties></object>
          </objectgroup></map>
          """;
        RaceTrack track = RaceTrack.load(DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml))).getDocumentElement(),1000,500);
        RaceWorld world = new RaceWorld(track, new CollisionSystem(List.of()),1000,500,
            List.of(definition(180,80,20,80)),TerrainEffects.NONE,1);
        InputState input = new InputState();
        world.start(input);
        world.update(input,3);
        world.getPlayer().getStamina().consume(5);
        world.getBot().getStamina().consume(5);
        // Keep the player outside the now enlarged pickup footprint; the bot crosses it first.
        world.getPlayer().placeAt(160-48,-100-63);
        world.getBot().placeAt(161-48,140-63);
        input.setMovingRight(true);
        world.update(input,1.0/60);
        near(GameConfig.MAX_STAMINA-5, world.getPlayer().getStamina().getCurrent(), "player cannot take bot's earlier contact");
        check(world.getBot().getStamina().getCurrent()>GameConfig.MAX_STAMINA-GameConfig.ATTACK_STAMINA_COST-.1,
            "earlier bot wins even though player updates first");
        check(!world.getStaminaPickups().get(0).isAvailable(), "shared item depleted once");
        input.clear();
        world.getPlayer().placeAt(280-48,100-63);
        input.setMovingRight(true);
        world.update(input,.2);
        world.getPlayer().placeAt(580-48,100-63);
        world.update(input,.2);
        check(world.getState()==GameState.FINISHED, "test race reaches result");
        world.start(input);
        check(world.getStaminaPickups().get(0).isAvailable(), "race restart resets item cooldown");
        world.getPlayer().getStamina().consume(5);
        world.getStaminaPickups().get(0).collect(world.getPlayer());
        world.update(input,2);
        near(10, world.getStaminaPickups().get(0).getCooldownRemaining(), "countdown does not age pickup timers");

        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        var savedXml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new File("assets/Map/Background.tmx"));
        var layers = savedXml.getElementsByTagName("layer");
        boolean hiddenStamina = false;
        for (int i=0;i<layers.getLength();i++) {
            var layer = (org.w3c.dom.Element) layers.item(i);
            if (layer.getAttribute("name").equalsIgnoreCase("stamina"))
                hiddenStamina = layer.getAttribute("visible").equals("0");
        }
        check(hiddenStamina ? map.getStaminaPickups().isEmpty() : !map.getStaminaPickups().isEmpty(),
            "saved pickup visibility follows Tiled layer");
        for (StaminaPickup.Definition definition : map.getStaminaPickups()) {
            StaminaPickup placed = new StaminaPickup(definition);
            BufferedImage ground = render(map, null, definition);
            BufferedImage available = render(map, placed, definition);
            check(!Arrays.equals(pixels(ground), pixels(available)), "pickup art is dynamic, not baked in map cache");
            Racer collector = new Racer(0,0,96,65,new Stamina(15));
            collector.getStamina().consume(8);
            placed.collect(collector);
            check(Arrays.equals(pixels(ground),pixels(render(map,placed,definition))), "collected art is fully hidden");
            placed.update(10);
            check(Arrays.equals(pixels(available),pixels(render(map,placed,definition))), "respawn restores exact original art");
        }
        RaceWorld real = new RaceWorld(map.getRaceTrack(), new CollisionSystem(map.getCollisionShapes()),
            map.getPixelWidth(),map.getPixelHeight(),map.getStaminaPickups());
        input.clear();
        real.start(input);
        real.update(input,3);
        for(int i=0;i<18000&&!real.isBotFinished();i++) real.update(input,.02);
        check(real.isBotFinished(), "bot still finishes actual map with active pickups");
        System.out.println("PASS: "+checks+" pickup checks; "+map.getStaminaPickups().size()
            +" saved pickups; bot finish "+real.getBotFinishSeconds()+" s.");
    }

    private static StaminaPickup.Definition definition(int x,int y,int w,int h) {
        BufferedImage art=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=art.createGraphics(); g.setColor(Color.YELLOW);g.fillRect(0,0,w,h);g.dispose();
        return new StaminaPickup.Definition(art,x,y,1,5,10);
    }
    private static BufferedImage render(TiledMap map,StaminaPickup pickup,StaminaPickup.Definition d) {
        var area = new StaminaPickup(d).getArea();
        int x = (int)Math.floor(area.getX()), y = (int)Math.floor(area.getY());
        int width = (int)Math.ceil(area.getWidth()), height = (int)Math.ceil(area.getHeight());
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.translate(-x,-y);
        map.draw(g,x,y,width,height);
        if(pickup!=null)pickup.draw(g,x,y,width,height);
        g.dispose();return image;
    }
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
    }
    private static void near(double expected,double actual,String label) {
        check(Math.abs(expected-actual)<1e-5,label+" ("+actual+")");
    }
    private static void check(boolean passed,String label) {
        checks++;if(!passed)throw new AssertionError(label);
    }
}
