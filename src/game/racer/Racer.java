package game.racer;

import game.app.*;
import game.collision.*;
import game.combat.*;
import game.world.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

public class Racer {
    private final int width;
    private final int height;
    private final Stamina stamina;
    private final RacerStatus status = new RacerStatus();
    private double x;
    private double y;
    private boolean sprinting;
    private boolean moving;
    private boolean gradualStop;
    private double velocityX;
    private double velocityY;
    private double currentSpeed = GameConfig.NORMAL_SPEED;
    private double coastStartVelocityX;
    private double coastStartVelocityY;
    private double coastElapsed;
    private boolean coasting;
    private boolean collisionLatched;
    private boolean environmentContactThisUpdate;
    private boolean shieldProtectedThisUpdate;
    private double stunRemaining;
    private double slowRemaining;
    private List<CollisionSystem.MotionSegment> movementPath = List.of();
    private TerrainEffects terrain = TerrainEffects.NONE;
    private final CombatState combat=new CombatState();
    private final ChargeState charge = new ChargeState();
    private double facingX=1,facingY;
    public CombatState getCombat() {return combat;}
    public ChargeState getCharge() {return charge;}
    public double getFacingX() {return facingX;}
    public double getFacingY() {return facingY;}
    public void face(RacerAction action) {
        double length=Math.hypot(action.getHorizontalDirection(),action.getVerticalDirection());
        if(length>0) {facingX=action.getHorizontalDirection()/length;facingY=action.getVerticalDirection()/length;}
    }

    public Racer(double x, double y, int width, int height, Stamina stamina) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.stamina = stamina;
    }

    public void update(RacerAction action, double elapsedSeconds) {
        update(action, elapsedSeconds, null);
    }

    public void update(RacerAction action, double elapsedSeconds, CollisionSystem collisions) {
        update(action, elapsedSeconds, collisions, 0, 0);
    }

    public void update(RacerAction action, double elapsedSeconds, CollisionSystem collisions,
                       int worldWidth, int worldHeight) {
        update(action, elapsedSeconds, collisions, worldWidth, worldHeight, collisions);
    }

    public void update(RacerAction action, double elapsedSeconds, CollisionSystem collisions,
                       int worldWidth, int worldHeight, CollisionSystem movementCollisions) {
        environmentContactThisUpdate = false;
        shieldProtectedThisUpdate = false;
        int horizontalDirection = action.getHorizontalDirection();
        int verticalDirection = action.getVerticalDirection();
        boolean wantsToMove = horizontalDirection != 0 || verticalDirection != 0;
        face(action);
        moving = false;
        movementPath = List.of();
        Rectangle2D.Double feet = getFeetBounds();
        Rectangle2D centreLimits = worldWidth > 0 && worldHeight > 0
            ? new Rectangle2D.Double(feet.getCenterX() - x, feet.getCenterY() - y,
                Math.max(0, worldWidth - width), Math.max(0, worldHeight - height)) : null;
        if (collisions == null || !collisions.isTouching(feet, centreLimits)) {
            collisionLatched = false;
        }

        // The one-second slow begins after the stun, rather than expiring during it.
        double blockedTime = Math.min(elapsedSeconds, stunRemaining);
        stunRemaining = remaining(stunRemaining - blockedTime);
        double movementTime = Math.max(0, elapsedSeconds - blockedTime);
        double slowedTime = Math.min(movementTime, slowRemaining);
        slowRemaining = remaining(slowRemaining - slowedTime);

        sprinting = wantsToMove && movementTime > 1e-9 && action.isSprinting() && stamina.hasEnergy();
        if (sprinting) {
            stamina.consume(GameConfig.STAMINA_DRAIN_PER_SECOND * movementTime);
        }

        double baseSpeed = sprinting
            ? GameConfig.NORMAL_SPEED * GameConfig.SPRINT_MULTIPLIER
            : GameConfig.NORMAL_SPEED;
        double maximumModeSpeed = sprinting
            ? GameConfig.MAX_SPRINT_SPEED : GameConfig.MAX_NORMAL_SPEED;
        if (wantsToMove && movementTime > 1e-9) {
            currentSpeed = Math.max(baseSpeed, Math.min(currentSpeed, maximumModeSpeed));
            double acceleration = sprinting
                ? GameConfig.SPRINT_ACCELERATION : GameConfig.NORMAL_ACCELERATION;
            currentSpeed = Math.min(maximumModeSpeed, currentSpeed + acceleration * movementTime);
        } else if (!wantsToMove && !gradualStop) {
            currentSpeed = GameConfig.NORMAL_SPEED;
        }

        double speed = Math.min(currentSpeed * status.getSpeedMultiplier(), GameConfig.MAX_EFFECTIVE_SPEED);
        if (isInWater()) speed *= GameConfig.WATER_SPEED_MULTIPLIER;
        double directionLength = Math.sqrt(
            horizontalDirection * horizontalDirection
                + verticalDirection * verticalDirection);

        double effectiveMovementTime = combat.movementTime(elapsedSeconds, blockedTime, slowedTime);
        double dx = 0;
        double dy = 0;
        if (directionLength > 0 && movementTime > 1e-9) {
            velocityX = horizontalDirection / directionLength * speed;
            velocityY = verticalDirection / directionLength * speed;
            coasting = false;
            dx = velocityX * effectiveMovementTime;
            dy = velocityY * effectiveMovementTime;
        } else if (!wantsToMove && gradualStop && effectiveMovementTime > 1e-9) {
            if (!coasting) {
                coasting = true;
                coastStartVelocityX = velocityX;
                coastStartVelocityY = velocityY;
                coastElapsed = 0;
            }
            double end = Math.min(GameConfig.PLAYER_STOP_DURATION, coastElapsed + effectiveMovementTime);
            double coastDistanceTime = (end - coastElapsed)
                - (end * end - coastElapsed * coastElapsed) / (2 * GameConfig.PLAYER_STOP_DURATION);
            dx = coastStartVelocityX * coastDistanceTime;
            dy = coastStartVelocityY * coastDistanceTime;
            coastElapsed = end;
            velocityX = coastStartVelocityX * (1 - end / GameConfig.PLAYER_STOP_DURATION);
            velocityY = coastStartVelocityY * (1 - end / GameConfig.PLAYER_STOP_DURATION);
            if (end >= GameConfig.PLAYER_STOP_DURATION) {
                velocityX = velocityY = 0;
                coasting = false;
                currentSpeed = GameConfig.NORMAL_SPEED;
            }
        } else if (!wantsToMove) {
            velocityX = velocityY = 0;
            coasting = false;
        }

        if (Math.hypot(dx, dy) > 1e-9) {
            CollisionSystem.Movement allowed = movementCollisions == null
                ? new CollisionSystem.Movement(dx, dy, false)
                : movementCollisions.moveWithContact(feet, dx, dy, centreLimits);
            x += allowed.dx();
            y += allowed.dy();
            movementPath = allowed.path();
            if (movementCollisions == null) {
                movementPath = List.of(new CollisionSystem.MotionSegment(feet.getCenterX(), feet.getCenterY(),
                    feet.getCenterX() + allowed.dx(), feet.getCenterY() + allowed.dy(), 0, 1));
            }
            if (blockedTime > 0) {
                double startFraction = blockedTime / elapsedSeconds;
                movementPath = movementPath.stream().map(segment -> new CollisionSystem.MotionSegment(
                    segment.x1(), segment.y1(), segment.x2(), segment.y2(),
                    startFraction + segment.startFraction() * (1 - startFraction),
                    startFraction + segment.endFraction() * (1 - startFraction))).toList();
            }
            moving = Math.hypot(allowed.dx(), allowed.dy()) > 1e-9;
            // Bumping another rider blocks motion without repeatedly stunning a pack.
            boolean terrainContact = collisions == movementCollisions
                || collisions != null && collisions.isTouching(getFeetBounds(), centreLimits);
            environmentContactThisUpdate = allowed.hit() && terrainContact;
            if (allowed.hit() && terrainContact && !collisionLatched) {
                collisionLatched = true;
                if (status.consumeShield()) {
                    shieldProtectedThisUpdate = true;
                } else {
                    stunRemaining = action.isSprinting() ? GameConfig.COLLISION_STUN_DURATION : 0;
                    slowRemaining = GameConfig.COLLISION_SLOW_DURATION;
                }
                if (isStunned()) {
                    sprinting = false;
                    moving = false;
                }
            }
            if (allowed.hit() && terrainContact) {
                // Hitting map geometry drops built-up momentum; racer contact does not.
                currentSpeed = baseSpeed;
            }
            if (allowed.hit()) {
                velocityX = velocityY = 0;
                coasting = false;
            }
        }
        combat.advanceMovement(elapsedSeconds);
        status.update(elapsedSeconds);
    }

    private static double remaining(double time) {
        return time < 1e-9 ? 0 : time;
    }

    public boolean isStunned() {
        return stunRemaining > 0;
    }

    public boolean isSlowed() {
        return !isStunned() && slowRemaining > 0;
    }

    public double getStunRemaining() {
        return stunRemaining;
    }

    public double getSlowRemaining() {
        return slowRemaining;
    }

    public boolean isMoving() {
        return moving;
    }

    public void setTerrainEffects(TerrainEffects terrain) {
        this.terrain = java.util.Objects.requireNonNull(terrain);
    }

    public boolean isInWater() { return terrain.isWet(getFeetBounds()); }

    public boolean hadEnvironmentContactThisUpdate() { return environmentContactThisUpdate; }

    /** Charge failures stun unless a shield already absorbed this frame's hazard. */
    public boolean applyChargeFailureStun() {
        if (!shieldProtectedThisUpdate && !status.consumeShield()) {
            stunRemaining = Math.max(stunRemaining, GameConfig.CHARGE_FAILURE_STUN_SECONDS);
            moving = sprinting = false;
            velocityX = velocityY = 0;
            coasting = false;
            combat.stopActions();
            return true;
        }
        return false;
    }

    /** A successful opponent attack interrupts the rider briefly, then its slow effect continues. */
    public void applyCombatHitStun() {
        stunRemaining = Math.max(stunRemaining, GameConfig.COMBAT_HIT_STUN_DURATION);
        moving = sprinting = false;
        velocityX = velocityY = 0;
        coasting = false;
        combat.stopActions();
    }

    public List<CollisionSystem.MotionSegment> getMovementPath() { return movementPath; }

    public double getGroundY() {
        return y + height - 2;
    }

    public Rectangle2D.Double getFeetBounds() {
        return new Rectangle2D.Double(x + width * 0.32, y + height - 12, width * 0.36, 10);
    }

    public void placeAt(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void resetAt(double x, double y) {
        placeAt(x, y);
        stamina.reset();
        stunRemaining = slowRemaining = 0;
        collisionLatched = false;
        environmentContactThisUpdate = shieldProtectedThisUpdate = false;
        charge.reset();
        combat.reset();
        status.reset();
        facingX=1;facingY=0;
        stopMotion();
    }

    public void stopMotion() {
        combat.stopActions();
        moving = false;
        sprinting = false;
        velocityX = velocityY = 0;
        currentSpeed = GameConfig.NORMAL_SPEED;
        coasting = false;
        movementPath = List.of();
    }

    public void setGradualStop(boolean gradualStop) {
        this.gradualStop = gradualStop;
    }

    public void keepInside(int panelWidth, int panelHeight) {
        x = Math.max(0, Math.min(x, panelWidth - width));
        y = Math.max(0, Math.min(y, panelHeight - height));
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Stamina getStamina() {
        return stamina;
    }

    public RacerStatus getStatus() { return status; }

    public boolean isSprinting() {
        return sprinting;
    }

    /** Current world-space speed in pixels per second, including buffs and water. */
    public double getSpeed() {
        return Math.hypot(velocityX, velocityY);
    }
}
