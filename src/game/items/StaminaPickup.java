package game.items;

import game.app.*;
import game.collision.*;
import game.racer.*;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** One shared pickup, its cooldown, and the original artwork painted in Tiled. */
public final class StaminaPickup {
    public enum Type { STAMINA, SPEED, SHIELD }

    public record Definition(BufferedImage image, int x, int y, float opacity,
                             double amount, double respawnSeconds, Type type, double durationSeconds,
                             Rectangle2D hitbox) {
        public Definition(BufferedImage image, int x, int y, float opacity,
                          double amount, double respawnSeconds, Type type, double durationSeconds) {
            this(image, x, y, opacity, amount, respawnSeconds, type, durationSeconds, null);
        }

        public Definition(BufferedImage image, int x, int y, float opacity,
                          double amount, double respawnSeconds) {
            this(image, x, y, opacity, amount, respawnSeconds, Type.STAMINA, 0, null);
        }

        public Definition(BufferedImage image, int x, int y, float opacity,
                          double amount, double respawnSeconds, Rectangle2D hitbox) {
            this(image, x, y, opacity, amount, respawnSeconds, Type.STAMINA, 0, hitbox);
        }

        public Definition {
            if (image == null || type == null || opacity < 0 || opacity > 1
                || !Double.isFinite(respawnSeconds) || respawnSeconds <= 0
                || type == Type.STAMINA && (!Double.isFinite(amount) || amount <= 0)
                || type == Type.SPEED && (!Double.isFinite(durationSeconds) || durationSeconds <= 0)
                || type == Type.SHIELD && (!Double.isFinite(amount) || amount <= 0)) {
                throw new IllegalArgumentException("Invalid stamina pickup definition.");
            }
            hitbox = normalizeHitbox(image, hitbox);
        }

        @Override
        public Rectangle2D hitbox() {
            return (Rectangle2D) hitbox.clone();
        }

        /**
         * Pickup contact must not follow the current animation frame. The loader supplies
         * one box covering the largest frame; the fallback uses the full image when it has
         * visible pixels. Either way the contact box stays fixed for the whole animation.
         */
        private static Rectangle2D normalizeHitbox(BufferedImage image, Rectangle2D requested) {
            Rectangle2D imageBounds = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
            if (requested == null) {
                Rectangle2D visible = visiblePixels(image);
                if (visible.isEmpty()) return new Rectangle2D.Double();
                return imageBounds;
            }
            if (!Double.isFinite(requested.getX()) || !Double.isFinite(requested.getY())
                || !Double.isFinite(requested.getWidth()) || !Double.isFinite(requested.getHeight())
                || requested.getWidth() < 0 || requested.getHeight() < 0) {
                throw new IllegalArgumentException("Invalid pickup hitbox.");
            }
            Rectangle2D clipped = requested.createIntersection(imageBounds);
            return clipped.isEmpty() ? new Rectangle2D.Double() : clipped;
        }

        private static Rectangle2D visiblePixels(BufferedImage image) {
            int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) == 0) continue;
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
            if (maxX < minX) return new Rectangle2D.Double();
            return new Rectangle2D.Double(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }
    }

    private final Definition definition;
    private double cooldown;

    public StaminaPickup(Definition definition) {
        this.definition = definition;
    }

    public void update(double seconds) {
        cooldown = Math.max(0, cooldown - seconds);
        if (cooldown < 1e-9) cooldown = 0;
    }

    public void reset() { cooldown = 0; }
    public boolean isAvailable() { return cooldown == 0; }
    public double getCooldownRemaining() { return cooldown; }
    /** Fixed pickup zone covering the largest extent of the animation artwork. */
    public Rectangle2D getArea() {
        Rectangle2D hitbox = definition.hitbox();
        if (hitbox.isEmpty()) return new Rectangle2D.Double(definition.x(), definition.y(), 0, 0);
        int width = Math.max(1, (int) Math.round(definition.image().getWidth() * GameConfig.ITEM_ART_SCALE));
        int height = Math.max(1, (int) Math.round(definition.image().getHeight() * GameConfig.ITEM_ART_SCALE));
        int drawX = (int) Math.round(definition.x() + (definition.image().getWidth() - width) / 2.0);
        int drawY = (int) Math.round(definition.y() + (definition.image().getHeight() - height) / 2.0);
        double scaleX = width / (double) definition.image().getWidth();
        double scaleY = height / (double) definition.image().getHeight();
        return new Rectangle2D.Double(drawX + hitbox.getX() * scaleX, drawY + hitbox.getY() * scaleY,
            hitbox.getWidth() * scaleX, hitbox.getHeight() * scaleY);
    }
    public double getAmount() { return definition.amount(); }
    public Type getType() { return definition.type(); }
    public boolean isType(Type type) { return definition.type() == type; }

    public double contactFraction(Racer racer) {
        if (!isAvailable() || definition.type() == Type.STAMINA
            && racer.getStamina().getCurrent() >= racer.getStamina().getMaximum() - 1e-9) {
            return Double.NaN;
        }
        Rectangle2D feet = racer.getFeetBounds();
        Rectangle2D area = getArea();
        double first = Double.NaN;
        for (CollisionSystem.MotionSegment segment : racer.getMovementPath()) {
            double local = SweptTrigger.entryFraction(area, segment.x1(), segment.y1(), segment.x2(), segment.y2(),
                feet.getWidth(), feet.getHeight(), true);
            if (!Double.isNaN(local)) {
                double time = segment.startFraction() + local * (segment.endFraction() - segment.startFraction());
                if (Double.isNaN(first) || time < first) first = time;
            }
        }
        if (racer.getMovementPath().isEmpty() && getArea().intersects(feet)) return 0;
        return first;
    }

    public double collect(Racer racer) {
        if (!isAvailable() || definition.type() != Type.STAMINA) return 0;
        double before = racer.getStamina().getCurrent();
        if (before >= racer.getStamina().getMaximum() - 1e-9) return 0;
        racer.getStatus().restoreStamina(racer.getStamina(), definition.amount());
        double restored = racer.getStamina().getCurrent() - before;
        if (restored > 0) cooldown = definition.respawnSeconds();
        return restored;
    }

    public boolean collectItem(Racer racer) {
        if (!isAvailable()) return false;
        switch (definition.type()) {
            case STAMINA -> {
                if (collect(racer) <= 0) return false;
            }
            case SPEED -> {
                racer.getStatus().addSpeedBoost(definition.durationSeconds());
                cooldown = definition.respawnSeconds();
            }
            case SHIELD -> {
                racer.getStatus().addShield((int) Math.floor(definition.amount()));
                cooldown = definition.respawnSeconds();
            }
        }
        return true;
    }

    public void draw(Graphics2D graphics, int cameraX, int cameraY, int viewportWidth, int viewportHeight) {
        Rectangle2D area = getArea();
        if (!isAvailable() || !area.intersects(cameraX, cameraY, viewportWidth, viewportHeight)) return;
        java.awt.Composite previous = graphics.getComposite();
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, definition.opacity()));
        int width = Math.max(1, (int) Math.round(definition.image().getWidth() * GameConfig.ITEM_ART_SCALE));
        int height = Math.max(1, (int) Math.round(definition.image().getHeight() * GameConfig.ITEM_ART_SCALE));
        int x = (int) Math.round(definition.x() + (definition.image().getWidth() - width) / 2.0);
        int y = (int) Math.round(definition.y() + (definition.image().getHeight() - height) / 2.0);
        graphics.drawImage(definition.image(), x, y, width, height, null);
        graphics.setComposite(previous);
    }
}
