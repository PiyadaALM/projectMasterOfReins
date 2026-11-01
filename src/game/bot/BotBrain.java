package game.bot;

import game.collision.*;
import game.items.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Observe the world, choose a useful goal, then use the shared movement controller. */
public final class BotBrain {
    public enum Decision { FOLLOW, COLLECT, OVERTAKE, AVOID_WATER, SAVE_ENERGY, FINISH_SPRINT, CHARGE, STUNNED }
    private static final int BYPASS_START_ORDER = 3;
    private static final double CARELESS_PERIOD = 1.5;
    private static final double CARELESS_WINDOW = .8;
    private final RaceTrack track;
    private BotPersonality personality;
    private final int index;
    private final boolean prefersBypass;
    private final BotBranchBehavior branchBehavior = new BotBranchBehavior();
    private final BotObstacleBehavior obstacleBehavior = new BotObstacleBehavior();
    private final BotItemBehavior itemBehavior;
    private final BotPassingBehavior passingBehavior;
    private final BotChargeBehavior chargeBehavior;
    private BotController routeController;
    private BotController tacticController;
    private BotRouteGraph.Plan plan;
    private StaminaPickup pickup;
    private Point2D.Double passingTarget;
    private Decision decision=Decision.FOLLOW;
    private boolean initialized;
    private int lastFork=-1;
    private double tacticSeconds;
    private double passingCooldown;
    private double carelessClock;

    public BotBrain(RaceTrack track,int index) {
        this(track,index,false);
    }

    public BotBrain(RaceTrack track,int index,boolean prefersBypass) {
        this.track=track;
        this.index=index;
        this.prefersBypass=prefersBypass;
        personality=BotPersonality.forIndex(index);
        itemBehavior=new BotItemBehavior(track);
        passingBehavior=new BotPassingBehavior(track,index);
        chargeBehavior=new BotChargeBehavior(index);
        reset();
    }
    public void setPersonality(BotPersonality personality) {
        this.personality=personality;
        reset();
    }
    public void reset() {
        routeController=new BotController(track.getBotRoute(),false);
        tacticController=null;
        pickup=null;
        passingTarget=null;
        plan=null;
        initialized=false;
        lastFork=-1;
        tacticSeconds=passingCooldown=0;
        carelessClock=1.2;
        chargeBehavior.reset();
        decision=Decision.FOLLOW;
    }
    public BotPersonality getPersonality() {return personality;}
    public Decision getDecision() {return decision;}
    public String getDecisionLabel() {
        return switch(decision) {
            case FOLLOW -> "วิ่ง";
            case COLLECT -> "เก็บของ";
            case OVERTAKE -> "แซง";
            case AVOID_WATER -> "หลบน้ำ";
            case SAVE_ENERGY -> "ออมแรง";
            case FINISH_SPRINT -> "เร่งท้าย";
            case CHARGE -> "ชาร์จพลัง";
            case STUNNED -> "สตั๊น";
        };
    }
    public StaminaPickup getPickupTarget() {return pickup;}
    public List<Integer> getChosenRoute() {return plan==null?List.of():plan.ids();}
    /** Kept for older callers; bots no longer rest to recover stamina. */
    public boolean isResting() {return false;}
    public boolean isCharging() { return chargeBehavior.isHolding(); }
    public void cancelCharge() { chargeBehavior.finishAttempt(); }

    public RacerAction decide(Racer racer,double seconds,CollisionSystem walls,CollisionSystem bodies,
                             int width,int height,TerrainEffects terrain,List<StaminaPickup> pickups,
                             List<Racer> racers,int passed) {
        if (racer.getCharge().isAwaitingRelease()) chargeBehavior.finishAttempt();
        if(racer.isStunned()) {decision=Decision.STUNNED;return new RacerAction(0,0,false);}
        updateGraph(racer,walls,terrain,racers,passed);
        Point2D.Double position=position(racer);
        Point2D.Double routeTarget=routeController.getTarget();
        if(routeTarget==null) {
            // A tactical detour must never leave a missed gate unrecoverable.
            RaceTrack.Gate gate=passed<track.getCheckpoints().size()?track.getCheckpoints().get(passed):track.getFinish();
            Rectangle2D area=gate.getArea();
            Point2D.Double before,after;
            if(area.getWidth()<=area.getHeight()) {
                before=new Point2D.Double(area.getMinX()-racer.getFeetBounds().getWidth()/2-16,area.getCenterY()+5);
                after=new Point2D.Double(area.getMaxX()+racer.getFeetBounds().getWidth()/2+16,area.getCenterY()+5);
            } else {
                before=new Point2D.Double(area.getCenterX(),area.getMinY()-16);
                after=new Point2D.Double(area.getCenterX(),area.getMaxY()+26);
            }
            routeController=controller(position,List.of(before,after));
            plan=null;
            routeTarget=before;
        }
        passingCooldown=Math.max(0,passingCooldown-seconds);
        tacticSeconds+=seconds;
        if(pickup!=null && (!pickup.isAvailable()
            || !itemBehavior.isUseful(racer,pickup,personality) || tacticSeconds>5)) {
            pickup=null;tacticController=null;
        }
        if(passingTarget!=null && (passingTarget.distance(position)<12 || tacticSeconds>1.5)) {
            passingTarget=null;tacticController=null;passingCooldown=.8;
        }
        if(pickup==null && passingTarget==null) {
            StaminaPickup choice=itemBehavior.choose(racer,routeTarget,pickups,walls,terrain,passed,personality);
            if(choice!=null) {
                pickup=choice;
                Rectangle2D area=choice.getArea();
                tacticController=controller(position,List.of(new Point2D.Double(area.getCenterX(),area.getCenterY()+5)));
                tacticSeconds=0;
            } else if(passingCooldown==0) {
                passingTarget=passingBehavior.choose(racer,routeTarget,racers,walls,terrain,width,height,passed,personality);
                if(passingTarget!=null) {
                    tacticController=controller(position,List.of(passingTarget));
                    tacticSeconds=0;
                }
            }
        }
        BotController steering=tacticController==null?routeController:tacticController;
        carelessClock+=seconds;
        boolean careless=(carelessClock+index*.73)%CARELESS_PERIOD<CARELESS_WINDOW;
        RacerAction motion=obstacleBehavior.steer(steering,racer,seconds,bodies,width,height,terrain,personality,careless);
        // Keep bots simple: they no longer charge or sprint for most of the race.
        // Only a short finish rush is allowed, so a player who sprints can compete.
        boolean charging=false;
        boolean moving=motion.getHorizontalDirection()!=0 || motion.getVerticalDirection()!=0;
        boolean finishRush=passed==track.getCheckpoints().size()
            && position.distance(track.getFinish().getArea().getCenterX(),track.getFinish().getArea().getCenterY()+5)<900;
        boolean sprint=moving && racer.getStamina().hasEnergy() && finishRush;
        if(charging) decision=Decision.CHARGE;
        else if(pickup!=null) decision=Decision.COLLECT;
        else if(passingTarget!=null) decision=Decision.OVERTAKE;
        else if(finishRush) decision=Decision.FINISH_SPRINT;
        else if(!sprint) decision=Decision.SAVE_ENERGY;
        else decision=Decision.FOLLOW;
        Point2D.Double target=steering.getTarget();
        if(tacticController==null && target!=null) {
            double dx=target.x-position.x,dy=target.y-position.y,length=Math.max(1,Math.hypot(dx,dy));
            double scale=Math.min(100,length)/length;
            if(terrain.travelCost(racer.getFeetBounds(),dx*scale,dy*scale,personality.waterAversion())>length*scale+.1)
                decision=Decision.AVOID_WATER;
        }
        // The course scrolls to the right; tactical choices (passing/items) must
        // never make a bot backtrack toward the left edge of the world.
        int horizontal = Math.max(0, motion.getHorizontalDirection());
        return new RacerAction(horizontal,motion.getVerticalDirection(),sprint);
    }

    private void updateGraph(Racer racer,CollisionSystem walls,TerrainEffects terrain,List<Racer> racers,int passed) {
        BotRouteGraph graph=track.getBotRoute().getGraph();
        if(graph==null) {initialized=true;return;}
        int source=0;
        if(initialized) {
            if(plan==null) {
                source=0;
            } else {
                int previous=routeController.getNextWaypoint()-1;
                if(previous<0 || previous>=plan.ids().size()) return;
                source=plan.ids().get(previous);
                if(!graph.isFork(source) || source==lastFork) return;
            }
        }
        List<Racer> traffic=racers.stream().filter(other->other!=racer).toList();
        boolean bypassAttempt=prefersBypass && (plan==null || !initialized);
        int planSource=bypassAttempt?BYPASS_START_ORDER:source;
        int planPassed=bypassAttempt?Math.max(passed,1):passed;
        BotRouteGraph.Plan selected=branchBehavior.choose(graph,position(racer),planSource,
            planPassed,track,walls,terrain,personality,traffic);
        // A clustered start can temporarily block the first edge. Retry without
        // bodies rather than leaving the bot on its stale linear waypoint list.
        if(selected.points().isEmpty()) {
            selected=branchBehavior.choose(graph,position(racer),planSource,
                planPassed,track,walls,terrain,personality,List.of());
        }
        if(bypassAttempt) {
            if(!selected.points().isEmpty()) {
                selected=prepend(graph,BYPASS_START_ORDER,selected);
            } else {
                // If the deep branch is unavailable, fall back to the normal graph route.
                selected=branchBehavior.choose(graph,position(racer),source,passed,
                    track,walls,terrain,personality,traffic);
                if(selected.points().isEmpty()) {
                    selected=branchBehavior.choose(graph,position(racer),source,passed,
                        track,walls,terrain,personality,List.of());
                }
            }
        }
        if(!selected.points().isEmpty()) {
            plan=selected;
            routeController=controller(position(racer),selected.points());
        }
        lastFork=source;
        initialized=true;
    }

    private static BotRouteGraph.Plan prepend(BotRouteGraph graph,int id,BotRouteGraph.Plan rest) {
        Point2D.Double point=graph.point(id);
        if(point==null) return rest;
        List<Integer> ids=new ArrayList<>();
        ids.add(id);
        ids.addAll(rest.ids());
        List<Point2D.Double> points=new ArrayList<>();
        points.add(point);
        points.addAll(rest.points());
        return new BotRouteGraph.Plan(List.copyOf(ids),List.copyOf(points));
    }

    private static Point2D.Double position(Racer racer) {return new Point2D.Double(racer.getFeetBounds().getCenterX(),racer.getGroundY());}
    private static BotController controller(Point2D.Double start,List<Point2D.Double> points) {
        return new BotController(new BotRoute(start,points),false);
    }
}
