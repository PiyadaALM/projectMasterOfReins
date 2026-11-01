package game.world;



import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** Tree artwork and its ground anchor are used only for drawing depth. */
public final class TreeSprite {
    private final BufferedImage image;
    private final int x;
    private final int y;
    private final float opacity;
    private final double groundY;

    public TreeSprite(BufferedImage image, int x, int y, float opacity, double baseY) {
        this.image = image;
        this.x = x;
        this.y = y;
        this.opacity = opacity;
        groundY = y + baseY;
    }

    public double getGroundY() {
        return groundY;
    }

    public boolean covers(double actorGroundY, Rectangle2D actorBounds) {
        return groundY > actorGroundY
            && new Rectangle2D.Double(x, y, image.getWidth(), image.getHeight()).intersects(actorBounds);
    }

    public void draw(Graphics2D graphics, double cameraX, double cameraY,
                     int viewportWidth, int viewportHeight) {
        draw(graphics, cameraX, cameraY, viewportWidth, viewportHeight, 1f);
    }

    public void draw(Graphics2D graphics, double cameraX, double cameraY,
                     int viewportWidth, int viewportHeight, float opacityScale) {
        if (x + image.getWidth() <= cameraX || y + image.getHeight() <= cameraY
            || x >= cameraX + viewportWidth || y >= cameraY + viewportHeight) {
            return;
        }
        java.awt.Composite previous = graphics.getComposite();
        float effectiveOpacity = Math.max(0, Math.min(1, opacity * opacityScale));
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, effectiveOpacity));
        graphics.drawImage(image, x, y, null);
        graphics.setComposite(previous);
    }
}
