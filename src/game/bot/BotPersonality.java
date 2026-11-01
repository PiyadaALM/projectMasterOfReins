package game.bot;



/** Preferences change decisions, never the racer's speed or stamina rules. */
public record BotPersonality(String name, double sprintReserve, double waterAversion,
                             double pickupThreshold, double pickupRadius, double passingDistance,
                             double routeDifficultyAversion, double combatAggression) {
    public static final BotPersonality BALANCED = new BotPersonality("Balanced", .28, 1.1, .60, 240, 85, .25, .55);
    public static BotPersonality forIndex(int index) {
        return switch (Math.floorMod(index,6)) {
            case 0 -> new BotPersonality("Sprinter", .16, .8, .50, 200, 100, .005, .85);
            case 1 -> new BotPersonality("Careful", .38, 1.8, .65, 230, 65, .80, .20);
            case 2 -> new BotPersonality("Collector", .30, 1.2, .80, 330, 75, .45, .35);
            case 3 -> new BotPersonality("Overtaker", .22, 1.0, .55, 220, 125, .02, .95);
            case 4 -> BALANCED;
            default -> new BotPersonality("Finisher", .45, 1.4, .70, 270, 80, .08, .60);
        };
    }
}
