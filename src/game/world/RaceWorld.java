package game.world;

import game.app.*;
import game.bot.*;
import game.collision.*;
import game.combat.*;
import game.items.*;
import game.racer.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Owns competitors, rules and time; Swing supplies input and renders state. */
public final class RaceWorld {
    private final RaceTrack track;
    private final CollisionSystem collisions;
    private final int width;
    private final int height;
    private final Racer player;
    private final List<Racer> bots;
    private final List<Racer> racers;
    private final Map<Racer, Point2D.Double> botSpawns = new IdentityHashMap<>();
    private final Map<Racer, BotBrain> botBrains = new IdentityHashMap<>();
    private final Map<Racer, BotCombatController> botCombatControllers = new IdentityHashMap<>();
    private final BotPersonalityPool personalityPool = new BotPersonalityPool();
    private final CombatSystem combatSystem=new CombatSystem();
    private final Map<Racer, RaceProgress> botProgresses = new IdentityHashMap<>();
    private final Map<Racer, Double> botFinishRunout = new IdentityHashMap<>();
    private final List<StaminaPickup> itemPickups;
    private final TerrainEffects terrain;
    private final RacerController playerController = new PlayerController();
    private final RaceProgress playerProgress = new RaceProgress();
    private GameState state = GameState.MENU;
    private double countdown;
    private double elapsed;
    private double finishExitRemaining;
    private GameState pausedFromState;
    private String message = "";
    private double messageRemaining;

    public RaceWorld(RaceTrack track, CollisionSystem collisions, int width, int height) {
        this(track, collisions, width, height, List.of());
    }

    public RaceWorld(RaceTrack track, CollisionSystem collisions, int width, int height,
                     List<StaminaPickup.Definition> pickupDefinitions) {
        this(track, collisions, width, height, pickupDefinitions, TerrainEffects.NONE);
    }

    public RaceWorld(RaceTrack track, CollisionSystem collisions, int width, int height,
                     List<StaminaPickup.Definition> pickupDefinitions, TerrainEffects terrain) {
        this(track, collisions, width, height, pickupDefinitions, terrain, GameConfig.BOT_COUNT);
    }

    public RaceWorld(RaceTrack track, CollisionSystem collisions, int width, int height,
                     List<StaminaPickup.Definition> pickupDefinitions, TerrainEffects terrain, int botCount) {
        if (track == null) throw new IllegalArgumentException("Race track data is missing.");
        if (botCount < 0) throw new IllegalArgumentException("Bot count must be non-negative.");
        this.track = track;
        this.collisions = collisions;
        this.width = width;
        this.height = height;
        this.terrain = terrain;
        itemPickups = pickupDefinitions.stream().map(StaminaPickup::new).toList();
        player = createRacer();
        player.setGradualStop(true);
        resetRacer(player, track.getSpawn(), "spawn");
        List<Racer> competitors = new ArrayList<>();
        List<Racer> createdBots = new ArrayList<>();
        competitors.add(player);
        if (track.getBotRoute() != null && botCount > 0) {
            track.getBotRoute().validate(track, collisions, width, height);
            for (int i = 0; i < botCount; i++) {
                Racer bot = createRacer();
                Point2D.Double spawn = findBotSpawn(bot, competitors, i);
                botSpawns.put(bot, spawn);
                botBrains.put(bot, new BotBrain(track,i,i<GameConfig.BOT_BYPASS_COUNT));
                botCombatControllers.put(bot,new BotCombatController(i));
                botProgresses.put(bot, new RaceProgress());
                createdBots.add(bot);
                competitors.add(bot);
            }
        }
        bots = List.copyOf(createdBots);
        racers = List.copyOf(competitors);
    }

    private Point2D.Double findBotSpawn(Racer bot, List<Racer> placed, int index) {
        Point2D.Double base = track.getBotRoute().getSpawn();
        // Every bot starts on the same vertical line as bot_spawn. Only Y changes,
        // so no bot gets a free head start to the right.
        int[] offsets = {0, -22, 22, -44, 44, -66, 66, -88, 88, -110, 110, -132, 132,
            -154, 154, -176, 176};
        for (int offset : offsets) {
            Point2D.Double point = new Point2D.Double(base.x, base.y + offset);
            try {
                resetRacer(bot, point, "bot_spawn " + (index + 1));
                if (placed.stream().anyMatch(actor -> actor.getFeetBounds().intersects(bot.getFeetBounds()))) continue;
                // A same-column grid can put the straight line to the first waypoint
                // through scenery even when the slot itself is clear. Route steering
                // handles that locally, so only validate the actual spawn slot here.
                return point;
            } catch (IllegalArgumentException blocked) {
                // Try the next vertical slot, never spawn inside a wall or gate.
            }
        }
        throw new IllegalArgumentException("Not enough clear starting space for bots on the bot_spawn column.");
    }

    private Racer createRacer() {
        Racer racer = new Racer(0, 0, GameConfig.RACER_WIDTH, GameConfig.RACER_HEIGHT,
            new Stamina(GameConfig.MAX_STAMINA));
        racer.setTerrainEffects(terrain);
        return racer;
    }

    private void resetRacer(Racer racer, Point2D.Double spawn, String label) {
        double x = spawn.x - racer.getWidth() / 2.0;
        double y = spawn.y - racer.getHeight() + 2;
        if (x < 0 || y < 0 || x + racer.getWidth() > width || y + racer.getHeight() > height) {
            throw new IllegalArgumentException("Move " + label + " farther inside the map to fit the rider.");
        }
        racer.resetAt(x, y);
        Rectangle2D feet = racer.getFeetBounds();
        if (collisions.overlaps(feet)) {
            throw new IllegalArgumentException(label + " overlaps collision. Move it to clear ground in Tiled.");
        }
        if (track.getFinish().getArea().intersects(feet)
            || track.getCheckpoints().stream().anyMatch(gate -> gate.getArea().intersects(feet))) {
            throw new IllegalArgumentException("Move " + label + " away from checkpoints and finish.");
        }
    }

    public void start(InputState input) {
        if (state != GameState.MENU && state != GameState.FINISHED && state != GameState.PAUSED) return;
        resetRacer(player, track.getSpawn(), "spawn");
        List<BotPersonality> personalities=personalityPool.nextRound(bots.size());
        int botIndex=0;
        for (Racer bot : bots) {
            resetRacer(bot, botSpawns.get(bot), "bot_spawn");
            botBrains.get(bot).setPersonality(personalities.get(botIndex));
            botCombatControllers.get(bot).setPersonality(personalities.get(botIndex));
            botCombatControllers.get(bot).reset();
            botProgresses.get(bot).reset();
            botFinishRunout.remove(bot);
            botIndex++;
        }
        playerProgress.reset();
        combatSystem.reset();
        itemPickups.forEach(StaminaPickup::reset);
        // Keep keys the player is already holding. Movement is locked during the
        // countdown, so they become active only at GO.
        input.clearBufferedActions();
        elapsed = 0;
        botFinishRunout.clear();
        message = "";
        messageRemaining = 0;
        countdown = GameConfig.RACE_COUNTDOWN_SECONDS;
        finishExitRemaining = 0;
        pausedFromState = null;
        state = GameState.COUNTDOWN;
    }

    public boolean pause(InputState input) {
        if (state != GameState.COUNTDOWN && state != GameState.RACING) return false;
        pausedFromState = state;
        state = GameState.PAUSED;
        racers.forEach(racer -> racer.getCharge().cancel());
        botBrains.values().forEach(BotBrain::cancelCharge);
        input.clear();
        return true;
    }

    public boolean resume(InputState input) {
        if (state != GameState.PAUSED || pausedFromState == null) return false;
        state = pausedFromState;
        pausedFromState = null;
        input.clear();
        return true;
    }

    public void returnToMenu(InputState input) {
        if (state != GameState.PAUSED && state != GameState.FINISHED) return;
        resetRacer(player, track.getSpawn(), "spawn");
        for (Racer bot : bots) {
            resetRacer(bot, botSpawns.get(bot), "bot_spawn");
            botBrains.get(bot).reset();
            botCombatControllers.get(bot).reset();
            botProgresses.get(bot).reset();
        }
        botFinishRunout.clear();
        playerProgress.reset();
        combatSystem.reset();
        itemPickups.forEach(StaminaPickup::reset);
        input.clear();
        elapsed = countdown = finishExitRemaining = messageRemaining = 0;
        message = "";
        pausedFromState = null;
        state = GameState.MENU;
    }

    public void update(InputState input, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Race update time must be finite and non-negative.");
        }
        if (state == GameState.COUNTDOWN) {
            double used = Math.min(seconds, countdown);
            countdown = Math.max(0, countdown - used);
            seconds -= used;
            if (countdown < 1e-9) {
                countdown = 0;
                state = GameState.RACING;
                // Keep movement/charge keys that were already held during the countdown.
                input.clearBufferedActions();
            }
        }
        while ((state == GameState.RACING || state == GameState.FINISHED && bots.stream().anyMatch(bot ->
                !isFinished(bot) || botFinishRunout.getOrDefault(bot, 0.0) > 0))
            && seconds > 1e-9) {
            double step = Math.min(seconds, 1.0 / 60);
            boolean playerFinishedAtStepStart = playerProgress.isFinished();
            messageRemaining = Math.max(0, messageRemaining - step);
            itemPickups.forEach(pickup -> pickup.update(step));
            boolean playerCanMove = !playerProgress.isFinished() || finishExitRemaining > 0;
            List<Racer> active=racers.stream().filter(actor->!isFinished(actor)).toList();
            List<Racer> movementActors = new ArrayList<>(active);
            if (playerCanMove && !movementActors.contains(player)) movementActors.add(player);
            for (Racer bot : bots) {
                if (botFinishRunout.getOrDefault(bot, 0.0) > 0 && !movementActors.contains(bot)) {
                    movementActors.add(bot);
                }
            }
            Map<Racer,RacerAction> movement=new IdentityHashMap<>();
            Map<Racer,CombatAction> combatCommands=new IdentityHashMap<>();
            if (playerCanMove) {
                RacerAction action=playerController.createAction(input,player,step);
                player.face(action);
                movement.put(player,action);
                if (!playerProgress.isFinished()) {
                    combatCommands.put(player,new CombatAction(input.consumeAttackRequest(),input.isGuarding()));
                }
            }
            for (Racer bot : bots) {
                if (isFinished(bot)) {
                    if (botFinishRunout.getOrDefault(bot, 0.0) > 0) {
                        movement.put(bot, finishRunoutAction(bot));
                    }
                    continue;
                }
                CollisionSystem bodies = collisions.withBodies(racers, bot);
                RacerAction action = botBrains.get(bot).decide(bot,step,collisions,bodies,width,height,
                    terrain,itemPickups,racers,botProgresses.get(bot).getPassed());
                bot.face(action);
                movement.put(bot,action);
            }
            for(Racer bot:bots) if(!isFinished(bot)) {
                BotBrain brain=botBrains.get(bot);
                combatCommands.put(bot,botCombatControllers.get(bot).decide(bot,active,collisions,step));
            }
            combatSystem.step(active,combatCommands,collisions,step,bots);
            for(CombatSystem.Event event:combatSystem.getEvents()) {
                if(event.target()==player || event.attacker()==player) {
                    message=event.outcome()==CombatSystem.Outcome.BLOCK
                        ? (event.target()==player?"ป้องกันสำเร็จ! เร็วขึ้น 1 วินาที":"ถูกป้องกัน")
                        : event.outcome()==CombatSystem.Outcome.SHIELD
                            ? (event.target()==player?"โล่ป้องกันการโจมตีแล้ว":"โล่ของคู่แข่งป้องกันการโจมตี")
                            : (event.attacker()==player?"ตีโดน! เร็วขึ้น 0.5 วินาที":"โดนโจมตี! ช้าลง 1 วินาที");
                    messageRemaining=1.2;
                }
            }
            for(Racer actor:movementActors) {
                // Racers block one another physically, while Racer keeps body contact separate
                // from map hazards so bumping another rider does not apply stun or slow.
                // Racers who already finished are moving clear of the line and no longer
                // take part in body collisions, so they cannot block the pack behind them.
                CollisionSystem bodies = collisions.withBodies(active, actor);
                actor.update(movement.get(actor),step,collisions,width,height,bodies);
                if (actor == player && playerProgress.isFinished()) continue;
                boolean chargeHeld = actor == player ? input.isCharging() : botBrains.get(actor).isCharging();
                ChargeState.Result charge = actor.getCharge().update(chargeHeld,step,
                    actor.hadEnvironmentContactThisUpdate() || actor.isInWater()
                        || terrain.crossesWater(actor.getFeetBounds(),actor.getMovementPath()));
                if (charge.outcome() == ChargeState.Outcome.FAILED) {
                    boolean stunned = actor.applyChargeFailureStun();
                    if (actor == player) {
                        message = stunned ? "ชาร์จพลาด! ติดสตั๊น" : "โล่ป้องกันสตั๊นแล้ว แต่ชาร์จถูกยกเลิก";
                        messageRemaining = 1.5;
                    }
                } else if (charge.outcome() == ChargeState.Outcome.REWARDED) {
                    double before = actor.getStamina().getCurrent();
                    actor.getStamina().recover(charge.staminaReward());
                    double gained = actor.getStamina().getCurrent() - before;
                    if (actor == player && gained > 0) {
                        message = String.format("ชาร์จสำเร็จ! +%.1f สตามินา",gained);
                        messageRemaining = 1.5;
                    }
                }
            }
            collectPickups();
            if (!playerProgress.isFinished()) {
                int before = playerProgress.getPassed();
                boolean earlyFinish = crossesFinish(player);
                double finish = playerProgress.check(track, player);
                if (!Double.isNaN(finish)) {
                    playerProgress.finishAt(elapsed + step * finish);
                    player.getCharge().cancel();
                    finishExitRemaining = GameConfig.FINISH_EXIT_GRACE_SECONDS;
                } else if (earlyFinish) {
                    message = "ต้องผ่านจุดตรวจให้ครบตามลำดับก่อนเข้าเส้นชัย";
                    messageRemaining = 2.5;
                } else if (playerProgress.getPassed() != before) {
                    message = "ผ่านจุดตรวจ " + playerProgress.getPassed() + "/" + track.getCheckpoints().size();
                    messageRemaining = 2.5;
                }
            }
            for (Racer bot : bots) {
                RaceProgress progress = botProgresses.get(bot);
                if (progress.isFinished()) continue;
                double finish = progress.check(track, bot);
                if (!Double.isNaN(finish)) {
                    progress.finishAt(elapsed + step * finish);
                    bot.getCharge().cancel();
                    botBrains.get(bot).cancelCharge();
                    botFinishRunout.put(bot, GameConfig.BOT_FINISH_RUNOUT_SECONDS);
                }
            }
            elapsed += step;
            seconds -= step;
            if (playerFinishedAtStepStart && finishExitRemaining > 0) {
                finishExitRemaining = Math.max(0, finishExitRemaining - step);
                if (finishExitRemaining == 0) {
                    state = GameState.FINISHED;
                    player.stopMotion();
                    input.clear();
                }
            }
            for (Racer bot : bots) {
                double remaining = botFinishRunout.getOrDefault(bot, 0.0);
                if (remaining > 0) {
                    remaining = Math.max(0, remaining - step);
                    botFinishRunout.put(bot, remaining);
                    if (remaining == 0) bot.stopMotion();
                }
            }
        }
    }

    private static RacerAction finishRunoutAction(Racer bot) {
        int horizontal = Math.max(0, (int) Math.signum(bot.getFacingX()));
        int vertical = (int) Math.signum(bot.getFacingY());
        if (horizontal == 0 && vertical == 0) horizontal = 1;
        return new RacerAction(horizontal, vertical, true);
    }

    private void collectPickups() {
        for (StaminaPickup pickup : itemPickups) {
            if (!pickup.isAvailable()) continue;
            Racer winner = null;
            double firstContact = Double.POSITIVE_INFINITY;
            for (Racer racer : racers) {
                if (isFinished(racer)) continue;
                double contact = pickup.contactFraction(racer);
                if (!Double.isNaN(contact) && contact < firstContact) {
                    winner = racer;
                    firstContact = contact;
                }
            }
            if (winner != null) {
                double staminaBefore = winner.getStamina().getCurrent();
                boolean collected = pickup.collectItem(winner);
                if (winner == player && collected) {
                    message = switch (pickup.getType()) {
                        case STAMINA -> String.format("เก็บสตามินา +%.1f",
                            winner.getStamina().getCurrent() - staminaBefore);
                        case SPEED -> "สปีดพุ่ง! เร็วขึ้น 2 วินาที";
                        case SHIELD -> "เก็บโล่! ป้องกันได้ " + player.getStatus().getShieldCharges() + " ครั้ง";
                    };
                    messageRemaining = 2.5;
                }
            }
        }
    }

    private boolean crossesFinish(Racer racer) {
        Rectangle2D feet = racer.getFeetBounds();
        return racer.getMovementPath().stream().anyMatch(segment -> !Double.isNaN(track.getFinish().entryFraction(
            segment.x1(), segment.y1(), segment.x2(), segment.y2(), feet.getWidth(), feet.getHeight())));
    }

    public GameState getState() { return state; }
    public Racer getPlayer() { return player; }
    /** First-bot accessors retained for existing callers. */
    public Racer getBot() { return bots.isEmpty() ? null : bots.get(0); }
    public List<Racer> getBots() { return bots; }
    public BotBrain getBotBrain(Racer bot) {return botBrains.get(bot);}
    public CombatSystem getCombatSystem() {return combatSystem;}
    public boolean isFinished(Racer racer) { return progressOf(racer).isFinished(); }
    public int getPassedCheckpoints(Racer racer) { return progressOf(racer).getPassed(); }
    public double getFinishSeconds(Racer racer) { return progressOf(racer).getFinishTime(); }
    /** Ranks racers by completed checkpoints, then forward screen position on the right-scrolling course. */
    public int getPlayerRank() {
        int rank = 1;
        if (playerProgress.isFinished()) {
            double playerTime = playerProgress.getFinishTime();
            return 1 + (int) bots.stream().filter(bot -> isFinished(bot)
                && getFinishSeconds(bot) < playerTime - 1e-9).count();
        }
        int playerCheckpoints = playerProgress.getPassed();
        double playerForwardPosition = player.getFeetBounds().getCenterX();
        for (Racer bot : bots) {
            RaceProgress progress = botProgresses.get(bot);
            if (progress.isFinished() || progress.getPassed() > playerCheckpoints
                || progress.getPassed() == playerCheckpoints
                    && bot.getFeetBounds().getCenterX() > playerForwardPosition + 1e-9) {
                rank++;
            }
        }
        return rank;
    }
    public int getCompetitorCount() { return racers.size(); }
    private RaceProgress progressOf(Racer racer) {
        if (racer == player) return playerProgress;
        RaceProgress progress = botProgresses.get(racer);
        if (progress == null) throw new IllegalArgumentException("Unknown competitor.");
        return progress;
    }
    public List<Racer> getRacers() { return racers; }
    public List<StaminaPickup> getStaminaPickups() {
        return itemPickups.stream().filter(pickup -> pickup.isType(StaminaPickup.Type.STAMINA)).toList();
    }
    public List<StaminaPickup> getItemPickups() { return itemPickups; }
    public TerrainEffects getTerrainEffects() { return terrain; }
    public RaceTrack getTrack() { return track; }
    public double getElapsedSeconds() { return playerProgress.isFinished() ? playerProgress.getFinishTime() : elapsed; }
    public int getCountdownNumber() { return (int) Math.ceil(countdown); }
    public int getPassedCheckpoints() { return playerProgress.getPassed(); }
    public int getBotPassedCheckpoints() { return getBot() == null ? 0 : getPassedCheckpoints(getBot()); }
    public boolean isBotFinished() { return getBot() != null && isFinished(getBot()); }
    public double getBotFinishSeconds() { return getBot() == null ? Double.NaN : getFinishSeconds(getBot()); }
    public String getMessage() { return messageRemaining > 0 ? message : ""; }
}
