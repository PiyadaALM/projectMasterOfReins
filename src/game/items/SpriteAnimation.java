package game.items;



public class SpriteAnimation {
    private final int frameCount;
    private final double normalFrameDuration;
    private final double sprintFrameDuration;
    private int currentFrame;
    private double elapsedOnFrame;
    private boolean playing;

    public SpriteAnimation(
        int frameCount,
        double normalFrameDuration,
        double sprintFrameDuration) {
        if (frameCount <= 0 || normalFrameDuration <= 0 || sprintFrameDuration <= 0) {
            throw new IllegalArgumentException("Animation settings must be positive.");
        }
        this.frameCount = frameCount;
        this.normalFrameDuration = normalFrameDuration;
        this.sprintFrameDuration = sprintFrameDuration;
    }

    public void update(double elapsedSeconds, boolean moving, boolean sprinting) {
        playing = moving;
        if (!moving) {
            currentFrame = 0;
            elapsedOnFrame = 0;
            return;
        }

        elapsedOnFrame += Math.max(0, elapsedSeconds);
        double frameDuration = sprinting ? sprintFrameDuration : normalFrameDuration;
        while (elapsedOnFrame >= frameDuration) {
            elapsedOnFrame -= frameDuration;
            currentFrame = (currentFrame + 1) % frameCount;
        }
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void reset() {
        currentFrame = 0;
        elapsedOnFrame = 0;
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }
}
