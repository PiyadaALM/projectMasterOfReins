package game.tests;

import game.app.*;
import game.collision.*;
import game.racer.*;
import game.world.*;


import java.awt.geom.Point2D;
import java.io.File;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class WaterTerrainTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader("""
            <map><group offsetx="10" offsety="20"><objectgroup name="wet" visible="0">
            <object x="0" y="0" width="1000" height="1000"/>
            <object x="0" y="0"><polygon points="0,0 1000,0 1000,1000 0,1000"/></object>
            </objectgroup></group></map>
            """))).getDocumentElement();
        var water = CollisionMapLoader.load(root,"wet");
        check(water.shapes().size()==2,"hidden water rectangle and polygon both load");
        check(CollisionMapLoader.load(root).shapes().isEmpty(),"water is not solid collision");
        near(10,water.shapes().get(0).getBounds().getX(),"group x offset");
        near(20,water.shapes().get(0).getBounds().getY(),"group y offset");
        TerrainEffects terrain = new TerrainEffects(water.shapes());
        Racer racer = new Racer(100,100,96,65,new Stamina(GameConfig.MAX_STAMINA));
        racer.setTerrainEffects(terrain);
        racer.update(new RacerAction(1,0,false),.1);
        near(116,racer.getX(),"overlapping puddles slow once to eighty percent");
        racer.update(new RacerAction(1,0,true),.1);
        near(136,racer.getX(),"sprint also slowed twenty percent");
        racer.placeAt(1100,100);
        check(!racer.isInWater(),"leaving water clears effect immediately");
        racer.update(new RacerAction(1,0,false),.1);
        near(1120,racer.getX(),"dry ground restores full speed without timer");
        racer.placeAt(100,100);
        TerrainEffects headOnly = new TerrainEffects(List.of(rectangle(100,100,96,20)));
        racer.setTerrainEffects(headOnly);
        check(!racer.isInWater(),"sprite overlaps water but feet do not");
        racer.setTerrainEffects(terrain);
        CollisionSystem wall = new CollisionSystem(List.of(rectangle(180,20,10,1000)));
        racer.update(new RacerAction(1,0,false),.2,wall);
        check(racer.isSlowed(),"solid impact still triggers collision slow");
        double x = racer.getX();
        racer.update(new RacerAction(-1,0,false),.1,wall);
        near(8,x-racer.getX(),"water and collision slow multiply");
        racer.resetAt(100,100);
        racer.update(new RacerAction(1,0,true),.2,wall);
        check(racer.isStunned(),"sprint impact still stuns in water");
        x=racer.getX();
        racer.update(new RacerAction(-1,0,true),.1,wall);
        near(x,racer.getX(),"water cannot bypass stun");
        racer.resetAt(1100,100);
        check(!racer.isInWater() && !racer.isSlowed(),"reset has no lingering terrain or impact penalty");

        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        check(map.getWaterAreas().size()==3,"three saved wet polygons load");
        check(map.getWaterWarnings().isEmpty(),"saved water shapes valid");
        RaceWorld world = new RaceWorld(map.getRaceTrack(),new CollisionSystem(map.getCollisionShapes()),
            map.getPixelWidth(),map.getPixelHeight(),map.getStaminaPickups(),new TerrainEffects(map.getWaterAreas()));
        InputState input = new InputState();
        world.start(input);
        world.update(input,3);
        for (Racer actor : List.of(world.getPlayer(),world.getBot())) {
            actor.placeAt(1140-48,2310-58);
            check(actor.isInWater(),"shared water affects each competitor");
        }
        // Restart actors at authored spawns, then exercise the complete real route.
        world = new RaceWorld(map.getRaceTrack(),new CollisionSystem(map.getCollisionShapes()),
            map.getPixelWidth(),map.getPixelHeight(),map.getStaminaPickups(),new TerrainEffects(map.getWaterAreas()));
        world.start(input);
        world.update(input,3);
        for(int i=0;i<18000 && !world.isBotFinished();i++) world.update(input,.02);
        check(world.isBotFinished(),"bot completes saved route with water and pickups");
        System.out.println("Water map bot finish: "+world.getBotFinishSeconds());
        System.out.println("PASS: "+checks+" water checks.");
    }
    private static CollisionShape rectangle(double x,double y,double w,double h) {
        return new CollisionShape(List.of(new Point2D.Double(x,y),new Point2D.Double(x+w,y),
            new Point2D.Double(x+w,y+h),new Point2D.Double(x,y+h)));
    }
    private static void near(double expected,double actual,String label) {
        check(Math.abs(expected-actual)<1e-5,label+" ("+actual+")");
    }
    private static void check(boolean value,String label) {
        checks++;
        if(!value) throw new AssertionError(label);
    }
}
