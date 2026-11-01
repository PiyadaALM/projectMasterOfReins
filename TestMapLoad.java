import game.world.*;
import game.collision.*;
import game.racer.*;
import game.app.*;
import game.items.*;
import java.io.File;
import java.util.List;

public class TestMapLoad {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless","true");
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        if (map == null) { System.out.println("FAIL: TiledMap.load returned null"); return; }
        System.out.println("TiledMap loaded OK");
        map.getCollisionWarnings().forEach(w -> System.err.println("COLLISION WARN: " + w));

        if (map.getRaceError() != null) { System.out.println("RACE ERROR: " + map.getRaceError()); return; }
        if (map.getRaceTrack() == null) { System.out.println("FAIL: no race track"); return; }
        System.out.println("RaceTrack OK, checkpoints: " + map.getRaceTrack().getCheckpoints().size());

        CollisionSystem collisions = new CollisionSystem(map.getCollisionShapes());
        TerrainEffects terrain = new TerrainEffects(map.getWaterAreas());
        try {
            RaceWorld world = new RaceWorld(
                map.getRaceTrack(), collisions, map.getPixelWidth(), map.getPixelHeight(),
                map.getItemPickups(), terrain);
            System.out.println("RaceWorld state: " + world.getState());
            System.out.println("SUCCESS! เกมจะโหลดได้ปกติ");
        } catch (IllegalArgumentException e) {
            System.out.println("RaceWorld ERROR: " + e.getMessage());
        }
    }
}

