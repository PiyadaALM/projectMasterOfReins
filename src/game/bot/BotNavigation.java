package game.bot;

import game.collision.*;
import game.racer.*;
import game.world.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Temporary local detours; authored waypoints still define race order. */
public final class BotNavigation {
    private static final int STEP = 16;
    private BotNavigation() {}
    private record Cell(int x,int y) {}
    private record Node(Cell cell,double cost,double estimate) implements Comparable<Node> {
        public int compareTo(Node other) { return Double.compare(estimate,other.estimate); }
    }
    public static List<Point2D.Double> find(Racer racer, Point2D.Double target,
                                           CollisionSystem obstacles,int width,int height) {
        return find(racer,target,obstacles,width,height,TerrainEffects.NONE,0);
    }
    public static List<Point2D.Double> find(Racer racer, Point2D.Double target,
                                           CollisionSystem obstacles,int width,int height,
                                           TerrainEffects terrain,double waterAversion) {
        Rectangle2D feet=racer.getFeetBounds();
        double originX=feet.getCenterX(), originY=racer.getGroundY();
        double minX=Math.max(racer.getWidth()/2.0,Math.min(originX,target.x)-160);
        double maxX=Math.min(width-racer.getWidth()/2.0,Math.max(originX,target.x)+160);
        double minY=Math.max(racer.getHeight()-2,Math.min(originY,target.y)-160);
        double maxY=Math.min(height-2,Math.max(originY,target.y)+160);
        Cell start=new Cell(0,0);
        Map<Cell,Double> costs=new HashMap<>();
        Map<Cell,Cell> previous=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>();
        costs.put(start,0.0);
        open.add(new Node(start,0,Point2D.distance(originX,originY,target.x,target.y)));
        int explored=0;
        Cell goal=null;
        double goalCost=Double.POSITIVE_INFINITY;
        while(!open.isEmpty() && explored++<16000 && open.peek().estimate()<goalCost) {
            Node node=open.remove();
            if(node.cost()>costs.getOrDefault(node.cell(),Double.POSITIVE_INFINITY)+1e-6) continue;
            double x=originX+node.cell().x()*STEP, y=originY+node.cell().y()*STEP;
            Rectangle2D probe=new Rectangle2D.Double(x-feet.getWidth()/2,y-10,feet.getWidth(),10);
            double remaining=Point2D.distance(x,y,target.x,target.y);
            if(remaining<=24 && !obstacles.moveWithContact(probe,target.x-x,target.y-y,null).hit()) {
                double finishCost=node.cost()+terrain.travelCost(probe,target.x-x,target.y-y,waterAversion);
                if(finishCost<goalCost) {
                    goalCost=finishCost;
                    goal=node.cell();
                }
            }
            for(int h=-1;h<=1;h++) for(int v=-1;v<=1;v++) {
                if(h==0 && v==0) continue;
                double nx=x+h*STEP,ny=y+v*STEP;
                if(nx<minX || nx>maxX || ny<minY || ny>maxY) continue;
                Cell next=new Cell(node.cell().x()+h,node.cell().y()+v);
                double cost=node.cost()+terrain.travelCost(probe,h*STEP,v*STEP,waterAversion);
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY)-1e-6) continue;
                if(obstacles.moveWithContact(probe,h*STEP,v*STEP,null).hit()) continue;
                costs.put(next,cost);
                previous.put(next,node.cell());
                open.add(new Node(next,cost,cost+Point2D.distance(nx,ny,target.x,target.y)));
            }
        }
        if(goal==null) return List.of();
        List<Point2D.Double> path=new ArrayList<>();
        path.add(new Point2D.Double(target.x,target.y));
        for(Cell cell=goal;!cell.equals(start);cell=previous.get(cell))
            path.add(new Point2D.Double(originX+cell.x()*STEP,originY+cell.y()*STEP));
        Collections.reverse(path);
        return path;
    }
}
