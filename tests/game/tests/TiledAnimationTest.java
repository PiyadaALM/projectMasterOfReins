package game.tests;

import game.items.*;
import game.world.*;


import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

public final class TiledAnimationTest {
    public static void main(String[] args) throws Exception {
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        BufferedImage first = render(map);
        map.update(.11);
        BufferedImage second = render(map);
        int differentPixels = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) differentPixels++;
            }
        }
        if (differentPixels == 0) throw new AssertionError("Tiled tile-object animation did not advance.");
        System.out.println("PASS: Tiled animated tile object advances between frames ("
            + differentPixels + " pixels changed).");

    }

    private static BufferedImage render(TiledMap map) {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.translate(-1530, -2290);
        map.draw(graphics, 1530, 2290, image.getWidth(), image.getHeight());
        for (StaminaPickup.Definition definition : map.getItemPickups()) {
            new StaminaPickup(definition).draw(graphics, 1530, 2290, image.getWidth(), image.getHeight());
        }
        graphics.dispose();
        return image;
    }

}
