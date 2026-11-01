package game.racer;



public class RacerAction {
    private final int horizontalDirection;
    private final int verticalDirection;
    private final boolean sprinting;

    public RacerAction(int horizontalDirection, int verticalDirection, boolean sprinting) {
        this.horizontalDirection = Integer.compare(horizontalDirection, 0);
        this.verticalDirection = Integer.compare(verticalDirection, 0);
        this.sprinting = sprinting;
    }

    public int getHorizontalDirection() {
        return horizontalDirection;
    }

    public int getVerticalDirection() {
        return verticalDirection;
    }

    public boolean isSprinting() {
        return sprinting;
    }
}
