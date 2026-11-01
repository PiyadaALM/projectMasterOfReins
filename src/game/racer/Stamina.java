package game.racer;



public class Stamina {
    private final double maximum;
    private double current;

    public Stamina(double maximum) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("Maximum stamina must be positive.");
        }
        this.maximum = maximum;
        current = maximum;
    }

    public double getCurrent() {
        return current;
    }

    public double getMaximum() {
        return maximum;
    }

    public double getRatio() {
        return current / maximum;
    }

    public boolean hasEnergy() {
        return current > 0;
    }

    public void reset() {
        current = maximum;
    }

    public void consume(double amount) {
        if (amount > 0) {
            current = Math.max(0, current - amount);
        }
    }

    public void recover(double amount) {
        if (amount > 0) {
            current = Math.min(maximum, current + amount);
        }
    }
}
