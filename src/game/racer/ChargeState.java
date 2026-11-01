package game.racer;

import game.app.GameConfig;

/** Tracks one press-and-hold charge attempt for a racer. */
public final class ChargeState {
    public enum Outcome { NONE, REWARDED, FAILED }
    public record Result(Outcome outcome, double staminaReward) {
        private static final Result NONE = new Result(Outcome.NONE, 0);
    }

    private double seconds;
    private boolean charging;
    private boolean awaitingRelease;

    public Result update(boolean held, double elapsedSeconds, boolean unsafe) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Charge update time must be finite and non-negative.");
        }
        if (awaitingRelease) {
            if (!held) awaitingRelease = false;
            return Result.NONE;
        }

        if (unsafe && (charging || held)) {
            charging = false;
            seconds = 0;
            awaitingRelease = held;
            return new Result(Outcome.FAILED, 0);
        }

        if (!held) {
            if (!charging) return Result.NONE;
            double reward = seconds + 1e-9 >= GameConfig.CHARGE_MIN_SECONDS ? seconds : 0;
            charging = false;
            seconds = 0;
            return reward > 0 ? new Result(Outcome.REWARDED, reward) : Result.NONE;
        }

        if (!charging) {
            charging = true;
            seconds = 0;
        }
        seconds = Math.min(GameConfig.CHARGE_MAX_SECONDS, seconds + elapsedSeconds);
        if (seconds >= GameConfig.CHARGE_MAX_SECONDS - 1e-9) {
            charging = false;
            seconds = 0;
            awaitingRelease = true;
            return new Result(Outcome.REWARDED, GameConfig.CHARGE_MAX_REWARD);
        }
        return Result.NONE;
    }

    public boolean isCharging() { return charging; }
    public boolean isAwaitingRelease() { return awaitingRelease; }
    public double getSeconds() { return seconds; }
    public double getProgress() { return seconds / GameConfig.CHARGE_MAX_SECONDS; }

    public void reset() {
        seconds = 0;
        charging = false;
        awaitingRelease = false;
    }

    public void cancel() { reset(); }
}
