package game.bot;

import game.app.*;
import game.collision.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Directed road network. Search state includes ordered checkpoint progress. */
public final class BotRouteGraph {
    private final Map<Integer,Point2D.Double> points;
    private final Map<Integer,List<Integer>> links;
    private final Map<Integer,Map<Integer,Double>> difficulties;
    private final int entry;
    public record Plan(List<Integer> ids,List<Point2D.Double> points) {}
    private record State(int id,int passed,boolean finished) {}
    private record Visit(State state,double cost) implements Comparable<Visit> {
        public int compareTo(Visit other) {return Double.compare(cost,other.cost);}
    }
    public BotRouteGraph(Map<Integer,Point2D.Double> points,Map<Integer,List<Integer>> links) {
        this(points, links, Map.of());
    }
    public BotRouteGraph(Map<Integer,Point2D.Double> points,Map<Integer,List<Integer>> links,
                         Map<Integer,Map<Integer,Double>> difficulties) {
        this.points=new HashMap<>();
        points.forEach((id,point)->this.points.put(id,(Point2D.Double)point.clone()));
        this.links=new HashMap<>();
        links.forEach((id,next)->this.links.put(id,List.copyOf(next)));
        this.difficulties=new HashMap<>();
        difficulties.forEach((from,targets)->this.difficulties.put(from,Map.copyOf(targets)));
        entry=points.keySet().stream().mapToInt(Integer::intValue).min().orElseThrow();
        for(int id:points.keySet()) {
            if(!links.containsKey(id)) throw new IllegalArgumentException("Every graph waypoint needs a string property next (empty at the end).");
            for(int next:links.get(id)) if(!points.containsKey(next))
                throw new IllegalArgumentException("waypoint "+id+" links to missing waypoint "+next);
            for(var edge:this.difficulties.getOrDefault(id,Map.of()).entrySet()) {
                if(!links.get(id).contains(edge.getKey()) || !Double.isFinite(edge.getValue()) || edge.getValue()<0)
                    throw new IllegalArgumentException("waypoint difficulty must be a non-negative value for an existing next link.");
            }
        }
    }
    public boolean isFork(int id) {return links.getOrDefault(id,List.of()).size()>1;}
    public Point2D.Double point(int id) {
        Point2D.Double point=points.get(id);
        return point==null?null:(Point2D.Double)point.clone();
    }

    public void validate(Point2D.Double spawn,RaceTrack track,CollisionSystem collisions,int width,int height) {
        for(var item:points.entrySet()) {
            Point2D.Double point=item.getValue();
            if(point.x<GameConfig.RACER_WIDTH/2.0 || point.x>width-GameConfig.RACER_WIDTH/2.0
                || point.y<GameConfig.RACER_HEIGHT-2 || point.y>height-2 || collisions.overlaps(feet(point)))
                throw new IllegalArgumentException("waypoint "+item.getKey()+" must fit on clear ground inside the map.");
        }
        Point2D.Double first=points.get(entry);
        if(collisions.overlaps(feet(spawn)) || collisions.moveWithContact(feet(spawn),first.x-spawn.x,first.y-spawn.y,null).hit())
            throw new IllegalArgumentException("bot_spawn cannot reach the first graph waypoint.");
        for(RaceTrack.Gate gate:track.getCheckpoints()) {
            boolean crosses=crosses(gate,spawn,first);
            for(int id:points.keySet()) for(int next:links.get(id))
                crosses|=crosses(gate,points.get(id),points.get(next));
            if(!crosses) throw new IllegalArgumentException("No waypoint connection crosses checkpoint "+gate.getOrder()
                +". Add approach points so the route crosses its rectangle.");
        }
        if(plan(spawn,0,0,track,collisions,TerrainEffects.NONE,BotPersonality.BALANCED,List.of()).points().isEmpty())
            throw new IllegalArgumentException("Waypoint network must have a route through all checkpoints in order, then finish.");
    }

    private static boolean crosses(RaceTrack.Gate gate,Point2D.Double from,Point2D.Double to) {
        return !Double.isNaN(gate.entryFraction(from.x,from.y-5,to.x,to.y-5,GameConfig.RACER_WIDTH*.36,10));
    }

    public Plan plan(Point2D.Double start,int source,int passed,RaceTrack track,CollisionSystem collisions,
                     TerrainEffects terrain,BotPersonality personality,List<Racer> traffic) {
        State initial=new State(source,passed,false);
        Map<State,Double> costs=new HashMap<>();
        Map<State,State> previous=new HashMap<>();
        PriorityQueue<Visit> open=new PriorityQueue<>();
        costs.put(initial,0.0);
        open.add(new Visit(initial,0));
        while(!open.isEmpty()) {
            Visit visit=open.remove();
            if(visit.cost()>costs.getOrDefault(visit.state(),Double.POSITIVE_INFINITY)+1e-6) continue;
            State state=visit.state();
            if(state.finished()) {
                List<Integer> ids=new ArrayList<>();
                for(State cursor=state;!cursor.equals(initial);cursor=previous.get(cursor)) ids.add(cursor.id());
                Collections.reverse(ids);
                return new Plan(List.copyOf(ids),ids.stream().map(id->(Point2D.Double)points.get(id).clone()).toList());
            }
            Point2D.Double from=state.id()==0?start:points.get(state.id());
            for(int id:state.id()==0?List.of(entry):links.getOrDefault(state.id(),List.of())) {
                Point2D.Double to=points.get(id);
                if(collisions.moveWithContact(feet(from),to.x-from.x,to.y-from.y,null).hit()) continue;
                int nextPassed=state.passed();
                double cursor=0;
                while(nextPassed<track.getCheckpoints().size()) {
                    double t=track.getCheckpoints().get(nextPassed).entryFraction(from.x,from.y-5,to.x,to.y-5,
                        GameConfig.RACER_WIDTH*.36,10);
                    if(Double.isNaN(t) || t<cursor-1e-9) break;
                    nextPassed++;
                    cursor=t;
                }
                double finish=track.getFinish().entryFraction(from.x,from.y-5,to.x,to.y-5,GameConfig.RACER_WIDTH*.36,10);
                boolean done=nextPassed==track.getCheckpoints().size() && !Double.isNaN(finish) && finish>=cursor-1e-9;
                State next=new State(id,nextPassed,done);
                double distance=Math.max(.001,terrain.travelCost(feet(from),to.x-from.x,to.y-from.y,personality.waterAversion()));
                for(Racer other:traffic) {
                    if(Line2D.ptSegDist(from.x,from.y,to.x,to.y,other.getFeetBounds().getCenterX(),other.getGroundY())<40)
                        distance+=personality.passingDistance()*.35;
                }
                double risk=difficulties.getOrDefault(state.id(),Map.of()).getOrDefault(id,0.0)
                    * personality.routeDifficultyAversion();
                double cost=visit.cost()+distance+risk;
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY)-1e-6) continue;
                costs.put(next,cost);
                previous.put(next,state);
                open.add(new Visit(next,cost));
            }
        }
        return new Plan(List.of(),List.of());
    }
    private static Rectangle2D feet(Point2D.Double point) {
        return new Rectangle2D.Double(point.x-GameConfig.RACER_WIDTH*.18,point.y-10,GameConfig.RACER_WIDTH*.36,10);
    }
}
