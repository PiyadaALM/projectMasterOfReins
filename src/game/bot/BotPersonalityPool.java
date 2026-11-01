package game.bot;



import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Gives every bot a fresh, unique personality assignment at each race start. */
public final class BotPersonalityPool {
    private final Random random;
    private int previousOffset = -1;

    public BotPersonalityPool() { this(new Random()); }
    BotPersonalityPool(Random random) { this.random = random; }

    public List<BotPersonality> nextRound(int botCount) {
        if (botCount < 0 || botCount > 6) throw new IllegalArgumentException("Personality pool supports zero to six bots.");
        List<BotPersonality> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) pool.add(BotPersonality.forIndex(i));
        int offset = random.nextInt(1, 6);
        if (previousOffset >= 0 && offset == previousOffset) offset = offset % 5 + 1;
        previousOffset = offset;
        List<BotPersonality> round = new ArrayList<>();
        for (int i = 0; i < botCount; i++) round.add(pool.get((i + offset) % pool.size()));
        return List.copyOf(round);
    }
}
