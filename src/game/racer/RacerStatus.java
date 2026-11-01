package game.racer;

import game.app.*;
/** Temporary power-up effects and accumulated defenses for one racer. */
public final class RacerStatus {
    private double speedBoostRemaining;
    private int shieldCharges;

    public void restoreStamina(Stamina stamina, double amount) {
        stamina.recover(amount);
    }

    public void addSpeedBoost(double seconds) {
        if (Double.isFinite(seconds) && seconds > 0) speedBoostRemaining += seconds;
    }

    public void update(double seconds) {
        speedBoostRemaining = Math.max(0, speedBoostRemaining - seconds);
        if (speedBoostRemaining < 1e-9) speedBoostRemaining = 0;
    }

    public double getSpeedBoostRemaining() { return speedBoostRemaining; }
    public double getSpeedMultiplier() {
        return speedBoostRemaining > 0 ? GameConfig.ITEM_SPEED_MULTIPLIER : 1;
    }

    public void addShield(int charges) {
        if (charges > 0) shieldCharges = Math.addExact(shieldCharges, charges);
    }

    public boolean consumeShield() {
        if (shieldCharges <= 0) return false;
        shieldCharges--;
        return true;
    }

    public int getShieldCharges() { return shieldCharges; }
    public void reset() { speedBoostRemaining = 0; shieldCharges = 0; }
}
