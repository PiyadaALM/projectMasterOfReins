package game.tests;

import game.app.*;
import game.bot.*;
import game.collision.*;
import game.items.*;
import game.racer.*;
import game.world.*;


import java.io.File;
import java.util.List;

public final class MultiBotRaceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        CollisionSystem solid = new CollisionSystem(map.getCollisionShapes());
        BotRouteGraph graph=map.getRaceTrack().getBotRoute().getGraph();
        var mainRoute=graph.plan(map.getRaceTrack().getBotRoute().getSpawn(),0,0,map.getRaceTrack(),solid,
            new TerrainEffects(map.getWaterAreas()),BotPersonality.BALANCED,List.of());
        var bypassRoute=graph.plan(map.getRaceTrack().getBotRoute().getSpawn(),3,1,map.getRaceTrack(),solid,
            new TerrainEffects(map.getWaterAreas()),BotPersonality.BALANCED,List.of());
        check(mainRoute.ids().contains(7) && !mainRoute.ids().contains(3),"normal route uses the main branch");
        check(bypassRoute.ids().contains(4) && bypassRoute.ids().contains(6)
            && bypassRoute.ids().contains(39),"deep bypass is reachable after checkpoint 1");
        List<StaminaPickup.Definition> pickups = map.getStaminaPickups();
        if (pickups.isEmpty()) pickups = List.of(new StaminaPickup.Definition(
            new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB),
            1100,2295,1,GameConfig.STAMINA_PICKUP_AMOUNT,GameConfig.STAMINA_PICKUP_RESPAWN_SECONDS));
        RaceWorld world = new RaceWorld(map.getRaceTrack(), solid, map.getPixelWidth(),map.getPixelHeight(),
            pickups,new TerrainEffects(map.getWaterAreas()));
        check(world.getBots().size()==5 && world.getRacers().size()==6,"five bots plus human");
        clearBodies(world);
        InputState input = new InputState();
        world.start(input);
        double startX=world.getBots().get(0).getFeetBounds().getCenterX();
        for(Racer bot:world.getBots()) near(startX,bot.getFeetBounds().getCenterX(),"bots share one starting X");
        double[] starts = world.getBots().stream().mapToDouble(Racer::getY).toArray();
        world.update(input,2.9);
        for(int i=0;i<world.getBots().size();i++) near(starts[i],world.getBots().get(i).getY(),"countdown locks every bot");
        world.update(input,.2);
        long bypassBots=world.getBots().stream()
            .filter(bot->world.getBotBrain(bot).getChosenRoute().contains(3)).count();
        check(bypassBots==2,"exactly two bots choose the deep bypass");
        for(int i=0;i<18000 && !world.getBots().stream().allMatch(world::isFinished);i++) {
            world.update(input,1.0/60);
            clearBodies(world);
            for(Racer actor:world.getRacers()) check(!solid.overlaps(actor.getFeetBounds()),"pack never enters solid map");
        }
        for(Racer bot:world.getBots()) {
            System.out.println("BOT "+(world.getBots().indexOf(bot)+1)+": finish="+world.getFinishSeconds(bot)
                +" cp="+world.getPassedCheckpoints(bot)+" xy="+bot.getFeetBounds().getCenterX()+","+bot.getGroundY());
        }
        for(Racer bot:world.getBots()) {
            check(world.isFinished(bot),"every bot finishes real map");
            check(world.getPassedCheckpoints(bot)==map.getRaceTrack().getCheckpoints().size(),"individual checkpoint progress");
        }
        check(world.getState()==GameState.RACING,"bots finishing do not finish idle human");

        // Head-on contact and sliding use a continuous sweep, even for a long update.
        Racer a = new Racer(100,100,96,65,new Stamina(8));
        Racer b = new Racer(200,100,96,65,new Stamina(8));
        CollisionSystem empty = new CollisionSystem(List.of());
        a.update(new RacerAction(1,0,true),1,empty,1000,500,empty.withBodies(List.of(a,b),a));
        check(!a.getFeetBounds().intersects(b.getFeetBounds()),"sprint cannot tunnel through rider");
        check(a.getFeetBounds().getMaxX()<=b.getFeetBounds().getMinX(),"head-on stop before body");
        check(!a.isStunned() && !a.isSlowed(),"body contact does not cause repeated terrain penalties");
        double y=a.getY();
        a.update(new RacerAction(1,1,false),.05,empty,1000,500,empty.withBodies(List.of(a,b),a));
        check(a.getY()>y && !a.getFeetBounds().intersects(b.getFeetBounds()),"slide around body");

        // Move human through both real gates to restart and validate all runtime state.
        for(RaceTrack.Gate gate:map.getRaceTrack().getCheckpoints()) cross(world,input,gate);
        cross(world,input,map.getRaceTrack().getFinish());
        world.update(input,GameConfig.FINISH_EXIT_GRACE_SECONDS+.1);
        check(world.getState()==GameState.FINISHED,"player can still finish after bots");
        check(world.getPlayerRank()==6 && world.getCompetitorCount()==6,"player result ranks after five bots");
        List<String> previousPersonalities=world.getBots().stream().map(bot->world.getBotBrain(bot).getPersonality().name()).toList();
        world.start(input);
        clearBodies(world);
        for(int i=0;i<world.getBots().size();i++) {
            Racer bot=world.getBots().get(i);
            check(!previousPersonalities.get(i).equals(world.getBotBrain(bot).getPersonality().name()),
                "same bot gets a different personality after restart");
            check(!world.isFinished(bot) && world.getPassedCheckpoints(bot)==0,"restart resets each progress");
            near(GameConfig.MAX_STAMINA,bot.getStamina().getCurrent(),"restart refills each bot");
            near(starts[i],bot.getY(),"restart restores spaced spawns");
        }
        world.update(input,3);
        BotRoute botRoute=map.getRaceTrack().getBotRoute();
        List<java.awt.geom.Point2D.Double> driverPoints=botRoute.getGraph()==null?botRoute.getWaypoints()
            :botRoute.getGraph().plan(botRoute.getSpawn(),0,0,map.getRaceTrack(),solid,
                new TerrainEffects(map.getWaterAreas()),BotPersonality.BALANCED,List.of()).points();
        BotController driver = new BotController(new BotRoute(map.getRaceTrack().getSpawn(),driverPoints));
        for(int i=0;i<18000 && !world.getRacers().stream().allMatch(world::isFinished);i++) {
            if (!world.isFinished(world.getPlayer())) {
                RacerAction action=driver.createActionAvoiding(world.getPlayer(),1.0/60,
                    solid.withBodies(world.getRacers(),world.getPlayer()),map.getPixelWidth(),map.getPixelHeight());
                input.setMovingRight(action.getHorizontalDirection()>0);
                input.setMovingLeft(action.getHorizontalDirection()<0);
                input.setMovingDown(action.getVerticalDirection()>0);
                input.setMovingUp(action.getVerticalDirection()<0);
                input.setSprinting(action.isSprinting());
            }
            world.update(input,1.0/60);
            clearBodies(world);
        }
        check(world.getRacers().stream().allMatch(world::isFinished),"moving human and five bots all finish together");
        System.out.println("PASS: "+checks+" multi-bot checks.");
    }
    private static void cross(RaceWorld world,InputState input,RaceTrack.Gate gate) {
        var area=gate.getArea();
        // Finished riders remain solid at the centre of the finish: approach in a clear lane.
        world.getPlayer().placeAt(area.getX()-48-25,area.getCenterY()-58);
        input.setMovingRight(true);
        world.update(input,.3);
        input.clear();
    }
    private static void clearBodies(RaceWorld world) {
        List<Racer> racers=world.getRacers();
        for(int i=0;i<racers.size();i++) for(int j=i+1;j<racers.size();j++) {
            if (world.isFinished(racers.get(i)) || world.isFinished(racers.get(j))) continue;
            check(!racers.get(i).getFeetBounds().intersects(racers.get(j).getFeetBounds()),
                "racing competitors never overlap feet");
        }
    }
    private static void near(double expected,double actual,String label) {check(Math.abs(expected-actual)<1e-5,label);}
    private static void check(boolean value,String label) {checks++;if(!value) throw new AssertionError(label);}
}
