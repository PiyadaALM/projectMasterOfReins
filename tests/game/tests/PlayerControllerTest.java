package game.tests;

import game.app.*;
import game.racer.*;


public final class PlayerControllerTest {
    public static void main(String[] args) {
        PlayerController controller = new PlayerController();
        InputState input = new InputState();
        Racer player = racer();
        input.setMovingRight(true); // Space runs to the right.
        input.setMovingUp(true);
        input.setMovingLeft(true);
        RacerAction diagonal = controller.createAction(input, player, 0);
        near(1, diagonal.getHorizontalDirection(), "Space moves right and A has no movement binding");
        near(-1, diagonal.getVerticalDirection(), "W moves upward while horizontal run is active");

        input.setMovingUp(false);
        input.setMovingLeft(false);
        input.setMovingDown(true);
        RacerAction down = controller.createAction(input, player, 0);
        near(1, down.getVerticalDirection(), "S moves downward");
        input.setMovingDown(false);
        input.setMovingUp(false);

        input.setMovingUp(true);
        Racer straightRacer = racer();
        straightRacer.update(new RacerAction(1, 0, false), .1);
        Racer diagonalRacer = racer();
        diagonalRacer.update(diagonal, .1);
        near(20, Math.hypot(straightRacer.getX() - 100, straightRacer.getY() - 100),
            "straight movement distance");
        near(20, Math.hypot(diagonalRacer.getX() - 100, diagonalRacer.getY() - 100),
            "diagonal movement keeps the same total speed");

        input.setSprinting(true);
        check(controller.createAction(input, player, 0).isSprinting(), "sprint input is passed to racer action");

        SpacebarControl space = new SpacebarControl();
        check(space.press(1_000_000_000L) && !space.isSprinting(), "first Space press starts normal run");
        check(!space.press(1_050_000_000L), "held-key repeats do not count as another tap");
        check(space.release(), "Space release ends first run press");
        check(space.press(1_200_000_000L) && space.isSprinting(), "second quick press starts sprint immediately");
        check(space.release() && !space.isSprinting(), "release ends sprint");
        check(space.press(2_000_000_000L) && !space.isSprinting(), "a late press starts a new normal run");

        Racer coasting = racer();
        coasting.setGradualStop(true);
        coasting.update(new RacerAction(1, 0, false), .1);
        double releaseX = coasting.getX();
        coasting.update(new RacerAction(0, 0, false), .1);
        check(coasting.isMoving() && coasting.getX() > releaseX,
            "player continues moving while easing to a stop");
        coasting.update(new RacerAction(0, 0, false), .2);
        check(coasting.getX() > releaseX, "player keeps coasting through the deceleration interval");
        coasting.update(new RacerAction(0, 0, false), .1);
        check(!coasting.isMoving(), "player finishes coasting after the configured stop time");
        double stoppedX = coasting.getX();
        coasting.update(new RacerAction(0, 0, false), .1);
        near(stoppedX, coasting.getX(), "player stays still after coasting ends");
        System.out.println("PlayerControllerTest passed");
    }

    private static Racer racer() {
        return new Racer(100, 100, 40, 40, new Stamina(8));
    }

    private static void near(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
