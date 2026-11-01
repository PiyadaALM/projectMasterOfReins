package game.app;



/** Turns a held Space key into rightward running and a double-tap sprint. */
public final class SpacebarControl {
    private static final long DOUBLE_TAP_WINDOW_NANOS = 300_000_000L;
    private long lastPressNanos;
    private boolean keyDown;
    private boolean sprinting;

    /** Returns true only for a fresh key-down, so operating-system repeats are ignored. */
    public boolean press(long nowNanos) {
        if (keyDown) return false;
        keyDown = true;
        sprinting = lastPressNanos != 0 && nowNanos - lastPressNanos <= DOUBLE_TAP_WINDOW_NANOS;
        lastPressNanos = sprinting ? 0 : nowNanos;
        return true;
    }

    /** Returns true if a held Space key was released. */
    public boolean release() {
        if (!keyDown) return false;
        keyDown = false;
        sprinting = false;
        return true;
    }

    public boolean isSprinting() { return sprinting; }

    public void reset() {
        lastPressNanos = 0;
        keyDown = false;
        sprinting = false;
    }
}
