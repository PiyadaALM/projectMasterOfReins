package game.app;



public final class GameConfig {
    public static final int PANEL_WIDTH = 960;
    public static final int PANEL_HEIGHT = 540;
    public static final double CAMERA_FOCUS_X_RATIO = 0.28;
    public static final double CAMERA_ZOOM = 1.2;
    // ขยายเฉพาะภาพไอเทมในเลเยอร์ stamina/speed/shield โดยไม่ขยายพื้นที่เก็บ
    public static final double ITEM_ART_SCALE = 2.5;
    public static final boolean CAMERA_FOLLOW_ENABLED = true;
    public static final int RACER_WIDTH = 96;
    public static final int RACER_HEIGHT = 65;
    public static final double RUN_SPRITE_SCALE = 0.30;
    public static final int RACER_ART_SIZE = 96;
    public static final double NORMAL_SPEED = 200.0;
    public static final double SPRINT_MULTIPLIER = 1.25;
    public static final double PLAYER_STOP_DURATION = 0.30;
    public static final double MAX_STAMINA = 8.0;
    public static final double STAMINA_DRAIN_PER_SECOND = 0.22;
    public static final double STAMINA_RECOVERY_PER_SECOND = 0.15;
    public static final double COLLISION_SLOW_DURATION = 1.0;
    public static final double COLLISION_STUN_DURATION = 0.3;
    public static final double COLLISION_SLOW_MULTIPLIER = 0.5;
    public static final double RACE_COUNTDOWN_SECONDS = 3.0;
    public static final float BOT_OPACITY = 0.80f;
    public static final float TREE_OCCLUSION_OPACITY = 0.35f;
    public static final int BOT_COUNT = 5;
    public static final int BOT_BYPASS_COUNT = 2;
    public static final double STAMINA_PICKUP_AMOUNT = 5.0;
    public static final double STAMINA_PICKUP_RESPAWN_SECONDS = 2.5;
    public static final double SPEED_PICKUP_DURATION_SECONDS = 2.0;
    public static final double ITEM_SPEED_MULTIPLIER = 2.0;
    public static final int SHIELD_PICKUP_CHARGES = 1;
    public static final double ITEM_PICKUP_RESPAWN_SECONDS = 1.0;
    // ความเร็วที่เหลือเมื่อเหยียบน้ำ: 0.80 = ช้าลง 20%, 0.60 = ช้าลง 40%
    public static final double WATER_SPEED_MULTIPLIER = 0.80;
    // ระยะโจมตีวัดจากจุดกึ่งกลางเท้ารอบตัวทุกทิศ ไม่จำเป็นต้องชนกัน
    public static final double ATTACK_RANGE = 90.0;
    // RBattack มี 4 เฟรม เฟรมละ .10 วินาที ก่อนเริ่ม explode
    public static final double ATTACK_WINDUP = .40;
    public static final double ATTACK_COOLDOWN = 1.0;
    public static final double ATTACK_STAMINA_COST = .30;
    public static final double GUARD_STAMINA_PER_SECOND = .70;
    public static final double CHARGE_MIN_SECONDS = 3.0;
    public static final double CHARGE_MAX_SECONDS = 5.0;
    public static final double CHARGE_MAX_REWARD = 5.0;
    public static final double CHARGE_FAILURE_STUN_SECONDS = .30;
    public static final double COMBAT_SLOW_MULTIPLIER = .70;
    public static final double COMBAT_SLOW_DURATION = 1.0;
    public static final double COMBAT_HIT_STUN_DURATION = 0.80;
    public static final double COMBAT_SPEED_MULTIPLIER = 1.20;
    public static final double HIT_SPEED_DURATION = .50;
    public static final double BLOCK_SPEED_DURATION = 1.0;
    public static final double HIT_IMMUNITY_DURATION = 1.25;
    public static final double MAX_NORMAL_SPEED = 320.0;
    public static final double MAX_SPRINT_SPEED = 410.0;
    public static final double NORMAL_ACCELERATION = 40.0;
    public static final double SPRINT_ACCELERATION = 55.0;
    public static final double MAX_EFFECTIVE_SPEED = 480.0;
    public static final double FINISH_EXIT_GRACE_SECONDS = 1.5;
    public static final double BOT_FINISH_RUNOUT_SECONDS = 2.0;
    public static final double ATTACK_ANIMATION_FRAME_SECONDS = 0.1;
    public static final double RBATTACK_ANIMATION_SPEED = 1.5;

    private GameConfig() {
    }
}
