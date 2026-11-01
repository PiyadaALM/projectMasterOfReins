package game.app;



public class InputState {
    private boolean movingUp;
    private boolean movingDown;
    private boolean movingLeft;
    private boolean movingRight;
    private boolean sprinting;
    private boolean attackPressed;
    private boolean attackRequested;
    private boolean guarding;
    private boolean charging;

    public void setAttackPressed(boolean pressed) {
        if(pressed && !attackPressed) attackRequested=true;
        attackPressed=pressed;
    }
    public boolean consumeAttackRequest() {
        boolean requested=attackRequested;
        attackRequested=false;
        return requested;
    }
    public boolean isGuarding() {return guarding;}
    public void setGuarding(boolean guarding) {this.guarding=guarding;}
    public boolean isCharging() { return charging; }
    public void setCharging(boolean charging) { this.charging = charging; }

    public boolean isMovingUp() {
        return movingUp;
    }

    public void setMovingUp(boolean movingUp) {
        this.movingUp = movingUp;
    }

    public boolean isMovingDown() {
        return movingDown;
    }

    public void setMovingDown(boolean movingDown) {
        this.movingDown = movingDown;
    }

    public boolean isMovingLeft() {
        return movingLeft;
    }

    public void setMovingLeft(boolean movingLeft) {
        this.movingLeft = movingLeft;
    }

    public boolean isMovingRight() {
        return movingRight;
    }

    public void setMovingRight(boolean movingRight) {
        this.movingRight = movingRight;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public void clear() {
        movingUp = false;
        movingDown = false;
        movingLeft = false;
        movingRight = false;
        sprinting = false;
        attackPressed=attackRequested=guarding=charging=false;
    }

    /**
     * Clears one-shot actions without dropping keys the player is still holding.
     * Movement, sprint, guard, and charge are held states; only attack is buffered.
     */
    public void clearBufferedActions() {
        attackPressed = false;
        attackRequested = false;
    }
}
