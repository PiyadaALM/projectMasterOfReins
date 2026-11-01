package game.tests;

import game.app.*;
import game.items.*;
import game.world.*;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.EnumSet;
import java.util.Set;

/** Pickup contact must use one fixed box, not the visible bounds of the current frame. */
public final class PickupHitboxTest {
    public static void main(String[] args) throws Exception {
        TiledMap map = TiledMap.load(new File("assets/Map/Background.tmx"));
        Set<StaminaPickup.Type> covered = EnumSet.noneOf(StaminaPickup.Type.class);
        int checked = 0;
        for (StaminaPickup.Definition definition : map.getItemPickups()) {
            StaminaPickup pickup = new StaminaPickup(definition);
            Rectangle2D fixed = pickup.getArea();
            check(!fixed.isEmpty(), "pickup has a fixed contact area");
            check(Math.abs(fixed.getWidth()
                    - definition.image().getWidth() * GameConfig.ITEM_ART_SCALE) < 1e-6,
                "contact width uses the largest artwork extent");
            check(Math.abs(fixed.getHeight()
                    - definition.image().getHeight() * GameConfig.ITEM_ART_SCALE) < 1e-6,
                "contact height uses the largest artwork extent");
            covered.add(definition.type());
            checked++;
        }
        check(covered.equals(EnumSet.allOf(StaminaPickup.Type.class)),
            "stamina, speed, and shield pickups are covered");
        check(checked == map.getItemPickups().size(), "every loaded pickup is checked");

        for (StaminaPickup.Type type : StaminaPickup.Type.values()) {
            StaminaPickup.Definition definition = map.getItemPickups().stream()
                .filter(pickup -> pickup.type() == type).findFirst().orElseThrow();
            StaminaPickup pickup = new StaminaPickup(definition);
            Rectangle2D first = pickup.getArea();
            for (int frame = 0; frame < 20; frame++) {
                map.update(.1);
                check(first.equals(pickup.getArea()),
                    "contact area stays fixed through " + type + " animation");
            }
        }
        System.out.println("PASS: " + checked + " pickup hitboxes stay fixed across animation frames.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
