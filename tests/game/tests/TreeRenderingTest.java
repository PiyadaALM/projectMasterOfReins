package game.tests;

import game.app.*;
import game.racer.*;
import game.world.*;


import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

public final class TreeRenderingTest {
    public static void main(String[] args) throws Exception {
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        double previousY = Double.NEGATIVE_INFINITY;
        for (TreeSprite tree : map.getTrees()) {
            if (tree.getGroundY() < previousY) {
                throw new AssertionError("Tree depth order changed");
            }
            previousY = tree.getGroundY();
        }
        BufferedImage art = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = art.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 10, 10);
        g.dispose();
        TreeSprite tree = new TreeSprite(art, 0, 0, 1, 9);
        BufferedImage frame = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        g = frame.createGraphics();
        tree.draw(g, 0, 0, 10, 10);
        g.dispose();
        if (frame.getRGB(5, 5) != Color.GREEN.getRGB() || tree.getGroundY() != 9) {
            throw new AssertionError("Tree artwork or drawing anchor changed");
        }
        Rectangle2D riderBehindTree = new Rectangle2D.Double(2, 2, 5, 5);
        if (!tree.covers(5, riderBehindTree) || tree.covers(10, riderBehindTree)) {
            throw new AssertionError("Tree should cover the rider only when the rider is behind it");
        }
        frame = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        g = frame.createGraphics();
        tree.draw(g, 0, 0, 10, 10, GameConfig.TREE_OCCLUSION_OPACITY);
        g.dispose();
        int fadedAlpha = frame.getRGB(5, 5) >>> 24;
        if (fadedAlpha < 85 || fadedAlpha > 95) {
            throw new AssertionError("Tree opacity should fade while it covers the rider");
        }
        Racer racer = new Racer(0, 140, 96, 65, new Stamina(GameConfig.MAX_STAMINA));
        for (int i = 0; i < 100; i++) {
            racer.update(new RacerAction(1, 0, false), .05);
        }
        if (Math.abs(racer.getX() - 1000) > .00001) {
            throw new AssertionError("Unexpected movement restriction");
        }
        System.out.println("PASS: map loaded, " + map.getTrees().size()
            + " tree sprites sorted; drawing and unrestricted movement verified.");
    }
}
