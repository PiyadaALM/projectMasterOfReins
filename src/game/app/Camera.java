package game.app;

import game.racer.*;
public class Camera {
    private int x;
    private int y;

    public void follow(
        Racer racer,
        int viewportWidth,
        int viewportHeight,
        int worldWidth,
        int worldHeight) {
        int screenX = (int) Math.round(
            viewportWidth * GameConfig.CAMERA_FOCUS_X_RATIO - racer.getWidth() / 2.0);
        int screenY = (viewportHeight - racer.getHeight()) / 2;
        int targetX = (int) Math.floor(racer.getX()) - screenX;
        int targetY = (int) Math.floor(racer.getY()) - screenY;

        x = clamp(targetX, 0, Math.max(0, worldWidth - viewportWidth));
        y = clamp(targetY, 0, Math.max(0, worldHeight - viewportHeight));
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}