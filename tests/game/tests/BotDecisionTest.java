package game.tests;

import game.app.*;
import game.bot.*;
import game.collision.*;
import game.items.*;
import game.racer.*;
import game.world.*;


import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class BotDecisionTest {
    private static int checks;
    private static final CollisionSystem EMPTY=new CollisionSystem(List.of());
    private static final String BASE="""
        <map><objectgroup name="race">
        <object name="spawn" x="100" y="100"><point/></object>
        <object name="bot_spawn" x="100" y="200"><point/></object>
        <object name="checkpoint" x="700" y="80" width="5" height="280">
          <properties><property name="order" type="int" value="1"/></properties></object>
        <object name="finish" x="900" y="80" width="5" height="280"/>
        %s</objectgroup></map>
        """;
    public static void main(String[] args) throws Exception {
        RaceTrack linear=parse(BASE.formatted(point(1,500,200,null)+point(2,950,200,null)));
        Racer collector=racer(100,200);
        collector.getStamina().consume(GameConfig.MAX_STAMINA*.65);
        BotBrain brain=new BotBrain(linear,2);
        StaminaPickup near=item(220,220);
        StaminaPickup far=item(600,420);
        decide(brain,collector,TerrainEffects.NONE,List.of(near,far),List.of(collector),0);
        check(brain.getPickupTarget()==near,"low energy chooses nearby useful pickup");
        check(brain.getDecision()==BotBrain.Decision.COLLECT,"pickup decision visible");
        near.collect(collector);
        decide(brain,collector,TerrainEffects.NONE,List.of(near,far),List.of(collector),0);
        check(brain.getPickupTarget()==null,"collected item is abandoned");
        brain.reset();
        collector.getStamina().reset();
        decide(brain,collector,TerrainEffects.NONE,List.of(far),List.of(collector),0);
        check(brain.getPickupTarget()==null,"full stamina does not seek pickups");
        collector.getStamina().consume(GameConfig.MAX_STAMINA*.8);
        brain.reset();
        decide(brain,collector,TerrainEffects.NONE,List.of(far),List.of(collector),0);
        check(brain.getPickupTarget()==null,"distant item is not worth chasing");

        Racer fast=racer(100,200),slow=racer(170,200);
        BotBrain passer=new BotBrain(linear,3);
        decide(passer,fast,TerrainEffects.NONE,List.of(),List.of(fast,slow),0);
        check(passer.getDecision()==BotBrain.Decision.OVERTAKE,"slower rider triggers proactive passing");
        for(int i=0;i<100;i++) {
            RacerAction action=decide(passer,fast,TerrainEffects.NONE,List.of(),List.of(fast,slow),0);
            fast.update(action,1.0/60,EMPTY,1100,500,EMPTY.withBodies(List.of(fast,slow),fast));
            check(!fast.getFeetBounds().intersects(slow.getFeetBounds()),"passing never overlaps rider");
        }
        check(fast.getFeetBounds().getCenterX()>slow.getFeetBounds().getCenterX()+30,"passes stationary rival");

        Racer low=racer(100,200);
        low.getStamina().consume(GameConfig.MAX_STAMINA*.7);
        BotBrain sprinter=new BotBrain(linear,0),finisher=new BotBrain(linear,5);
        check(!decide(sprinter,low,TerrainEffects.NONE,List.of(),List.of(low),0).isSprinting(),"bots run normally");
        check(!decide(finisher,low,TerrainEffects.NONE,List.of(),List.of(low),0).isSprinting(),"bots save sprint for finish");
        low.placeAt(800-48,200-63);
        check(decide(finisher,low,TerrainEffects.NONE,List.of(),List.of(low),1).isSprinting(),"saved energy used near finish");
        check(finisher.getDecision()==BotBrain.Decision.FINISH_SPRINT,"finish sprint decision");
        finisher.reset();
        check(finisher.getDecision()==BotBrain.Decision.FOLLOW && finisher.getPickupTarget()==null,"restart clears decisions and goals");

        TerrainEffects puddle=new TerrainEffects(List.of(rectangle(240,175,120,50)));
        Racer waterRacer=racer(200,200);
        waterRacer.setTerrainEffects(puddle);
        var dryPath=BotNavigation.find(waterRacer,new Point2D.Double(450,200),EMPTY,1100,500,puddle,1.8);
        check(!dryPath.isEmpty(),"weighted navigation finds water detour");
        double cost=pathCost(waterRacer,dryPath,puddle,1.8);
        double straight=puddle.travelCost(waterRacer.getFeetBounds(),250,0,1.8);
        check(cost<straight-1,"chooses detour only when its weighted travel cost is better");
        check(dryPath.stream().anyMatch(p->Math.abs(p.y-200)>25),"path moves around water");

        String network=point(1,180,200,"2,3")+point(2,450,200,"4")
            +point(3,450,270,"4")+point(4,750,200,"5")+point(5,950,200,"");
        RaceTrack graph=parse(BASE.formatted(network));
        graph.getBotRoute().validate(graph,EMPTY,1100,500);
        BotRouteGraph roads=graph.getBotRoute().getGraph();
        var dry=roads.plan(new Point2D.Double(100,200),0,0,graph,EMPTY,TerrainEffects.NONE,
            BotPersonality.BALANCED,List.of());
        check(dry.ids().contains(2) && !dry.ids().contains(3),"short dry branch chosen");
        TerrainEffects branchWater=new TerrainEffects(List.of(rectangle(260,175,340,42)));
        var wet=roads.plan(new Point2D.Double(100,200),0,0,graph,EMPTY,branchWater,
            BotPersonality.forIndex(1),List.of());
        check(wet.ids().contains(3) && !wet.ids().contains(2),"water changes branch choice");
        Racer traffic=racer(450,200);
        var busy=roads.plan(new Point2D.Double(180,200),1,0,graph,EMPTY,TerrainEffects.NONE,
            BotPersonality.forIndex(3),List.of(traffic));
        check(busy.ids().contains(3),"congestion changes choice at fork");
        BotBrain graphBrain=new BotBrain(graph,1);
        Racer graphRacer=racer(100,200);
        decide(graphBrain,graphRacer,branchWater,List.of(),List.of(graphRacer),0);
        check(graphBrain.getChosenRoute().contains(3),"brain uses graph planner");
        // Move to fork, advance its controller, then change conditions before re-selection.
        graphRacer.placeAt(180-48,200-63);
        decide(graphBrain,graphRacer,branchWater,List.of(),List.of(graphRacer),0);
        decide(graphBrain,graphRacer,TerrainEffects.NONE,List.of(),List.of(graphRacer),0);
        check(graphBrain.getChosenRoute().contains(2),"re-evaluates branch at junction");

        String riskNetwork=point(1,180,200,"2,3").replace("</properties>",
                "<property name=\"nextDifficulty\" type=\"string\" value=\"100,0\"/></properties>")
            +point(2,450,200,"4")+point(3,450,270,"4")+point(4,750,200,"5")+point(5,950,200,"");
        RaceTrack riskTrack=parse(BASE.formatted(riskNetwork));
        var daring=riskTrack.getBotRoute().getGraph().plan(new Point2D.Double(100,200),0,0,riskTrack,EMPTY,
            TerrainEffects.NONE,BotPersonality.forIndex(0),List.of());
        var cautious=riskTrack.getBotRoute().getGraph().plan(new Point2D.Double(100,200),0,0,riskTrack,EMPTY,
            TerrainEffects.NONE,BotPersonality.forIndex(1),List.of());
        check(daring.ids().contains(2) && cautious.ids().contains(3),"personality weighs a hard shortcut differently");
        reject(BASE.formatted(riskNetwork.replace("value=\"100,0\"","value=\"-1,0\"")));
        reject(BASE.formatted(riskNetwork.replace("value=\"100,0\"","value=\"100\"")));

        BotPersonalityPool pool=new BotPersonalityPool();
        List<BotPersonality> firstRound=pool.nextRound(6),secondRound=pool.nextRound(6);
        check(firstRound.stream().map(BotPersonality::name).distinct().count()==6,"round assigns unique bot personalities");
        for(int i=0;i<6;i++) check(!firstRound.get(i).equals(secondRound.get(i)),"same bot changes personality each round");

        RaceWorld world=new RaceWorld(graph,EMPTY,1100,500,List.of(),branchWater,1);
        InputState input=new InputState();
        world.start(input);world.update(input,3);
        for(int i=0;i<6000 && !world.isBotFinished();i++) world.update(input,1.0/60);
        check(world.isBotFinished() && world.getBotPassedCheckpoints()==1,"graph route finishes through mandatory checkpoint");
        // A tempting branch to finish that skips checkpoint must not be selected.
        String skip=point(1,180,200,"2,3")+point(2,750,100,"4")
            +point(3,750,200,"4")+point(4,950,200,"");
        String narrow=BASE.replace("x=\"700\" y=\"80\" width=\"5\" height=\"280\"",
            "x=\"700\" y=\"80\" width=\"5\" height=\"80\"");
        RaceTrack skipTrack=parse(narrow.formatted(skip));
        var safe=skipTrack.getBotRoute().getGraph().plan(new Point2D.Double(100,200),0,0,skipTrack,EMPTY,
            TerrainEffects.NONE,BotPersonality.BALANCED,List.of());
        check(safe.ids().contains(2) && !safe.ids().contains(3),"branch skipping mandatory checkpoint rejected");
        reject(BASE.formatted(network.replace("value=\"2,3\"","value=\"2,99\"")));
        reject(BASE.formatted(network.replace("value=\"2,3\"","value=\"2,,3\"")));
        reject(BASE.formatted(network.replace("name=\"next\" type=\"string\" value=\"2,3\"","name=\"unused\" type=\"string\" value=\"2,3\"")));
        String cycle=point(1,180,200,"2")+point(2,300,200,"1");
        try {
            RaceTrack invalid=parse(BASE.formatted(cycle));
            invalid.getBotRoute().validate(invalid,EMPTY,1100,500);
            throw new AssertionError("network without finish accepted");
        } catch(IllegalArgumentException expected) {checks++;}

        RaceWorld pack=new RaceWorld(graph,EMPTY,1100,500,List.of(),branchWater,6);
        pack.start(input);pack.update(input,3);
        for(int i=0;i<6000 && !pack.getBots().stream().allMatch(pack::isFinished);i++) {
            pack.update(input,1.0/60);
            for(int a=0;a<pack.getRacers().size();a++) for(int b=a+1;b<pack.getRacers().size();b++) {
                if (pack.isFinished(pack.getRacers().get(a)) || pack.isFinished(pack.getRacers().get(b))) continue;
                check(!pack.getRacers().get(a).getFeetBounds().intersects(pack.getRacers().get(b).getFeetBounds()),
                    "pack never overlaps on forked course");
            }
        }
        check(pack.getBots().stream().allMatch(pack::isFinished),"six autonomous bots finish forked course");
        System.out.println("PASS: "+checks+" decision/branch checks.");
    }
    private static RacerAction decide(BotBrain brain,Racer racer,TerrainEffects terrain,List<StaminaPickup> pickups,
                                       List<Racer> racers,int passed) {
        return brain.decide(racer,1.0/60,EMPTY,EMPTY.withBodies(racers,racer),1100,500,terrain,pickups,racers,passed);
    }
    private static double pathCost(Racer racer,List<Point2D.Double> path,TerrainEffects terrain,double aversion) {
        double x=racer.getFeetBounds().getCenterX(),y=racer.getGroundY(),cost=0;
        for(Point2D.Double point:path) {
            cost+=terrain.travelCost(new Rectangle2D.Double(x-racer.getFeetBounds().getWidth()/2,y-10,
                racer.getFeetBounds().getWidth(),10),point.x-x,point.y-y,aversion);
            x=point.x;y=point.y;
        }
        return cost;
    }
    private static String point(int order,double x,double y,String next) {
        return "<object name=\"waypoint\" x=\""+x+"\" y=\""+y+"\"><point/><properties><property name=\"order\" type=\"int\" value=\""+order+"\"/>"
            +(next==null?"":"<property name=\"next\" type=\"string\" value=\""+next+"\"/>")+"</properties></object>";
    }
    private static RaceTrack parse(String xml) throws Exception {
        return RaceTrack.load(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
            .getDocumentElement(),1100,500);
    }
    private static void reject(String xml) throws Exception {
        try {parse(xml);throw new AssertionError("invalid links accepted");} catch(IOException expected) {checks++;}
    }
    private static Racer racer(double x,double y) {return new Racer(x-48,y-63,96,65,new Stamina(GameConfig.MAX_STAMINA));}
    private static StaminaPickup item(int x,int y) {
        return new StaminaPickup(new StaminaPickup.Definition(new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB),x,y,1,5,10));
    }
    private static CollisionShape rectangle(double x,double y,double w,double h) {
        return new CollisionShape(List.of(new Point2D.Double(x,y),new Point2D.Double(x+w,y),
            new Point2D.Double(x+w,y+h),new Point2D.Double(x,y+h)));
    }
    private static void check(boolean value,String label) {checks++;if(!value)throw new AssertionError(label);}
}
