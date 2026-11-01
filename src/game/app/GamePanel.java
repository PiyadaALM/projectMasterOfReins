package game.app;

import game.collision.*;
import game.combat.*;
import game.items.*;
import game.racer.*;
import game.world.*;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.GridLayout;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

@SuppressWarnings("serial")
public final class GamePanel extends JPanel {
    private final InputState inputState = new InputState();
    private final TiledMap tiledMap;
    private final String mapError;
    private final boolean cameraFollowActive;
    private final Racer racer;
    private final CollisionSystem collisions;
    private boolean showCollisionBounds;
    private final RaceWorld raceWorld;
    private final String raceError;
    private final Camera camera = new Camera();
    private final BufferedImage landingImage;
    private final BufferedImage riderImage;
    private final BufferedImage riderRunSheet;
    private final String imageError;
    private final String runSheetError;
    private final Map<String, List<BufferedImage>> riderAnimations;
    private final String animationError;
    private final JButton pauseButton = new PauseButton();
    private final JButton marathonButton = new RoundedButton("Marathon Race");
    private final JButton sprintRaceButton = new RoundedButton("Flat Sprint Race");
    private final JPanel stageMenu = new JPanel(new GridLayout(1, 2, 20, 0));
    private final RoundedPanel pauseMenu = new RoundedPanel(new GridLayout(3, 1, 0, 12));
    private final RoundedPanel finishMenu = new RoundedPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
    private final Map<Racer, RacerVisual> racerVisuals = new IdentityHashMap<>();
    private long lastUpdateNanos;
    private final SpacebarControl spacebarControl = new SpacebarControl();
    private boolean pauseKeyHeld;
    private int checkpointAnnouncement;
    private int checkpointAnnouncementRank;
    private double checkpointAnnouncementRemaining;

    public GamePanel() {
        setBackground(new Color(150, 169, 119));
        setFocusable(true);
        setDoubleBuffered(true);
        setLayout(null);

        styleBlueButton(pauseButton);
        pauseButton.setFont(new Font("Arial", Font.BOLD, 20));
        pauseButton.setToolTipText("หยุดชั่วคราว");
        pauseButton.setMargin(new Insets(0, 0, 2, 0));
        pauseButton.addActionListener(event -> togglePause());
        add(pauseButton);
        styleBlueButton(marathonButton);
        marathonButton.addActionListener(event -> beginRace());
        styleBlueButton(sprintRaceButton);
        sprintRaceButton.setToolTipText("รอเพิ่มแผนที่ Flat Sprint");
        sprintRaceButton.setEnabled(false);
        stageMenu.setOpaque(false);
        stageMenu.add(marathonButton);
        stageMenu.add(sprintRaceButton);
        add(stageMenu);
        createPauseMenu();
        add(pauseMenu);
        finishMenu.setBackground(new Color(20, 52, 112, 225));
        finishMenu.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JButton again = pauseMenuButton("Race Again");
        again.addActionListener(event -> beginRace());
        JButton quitToMenu = pauseMenuButton("Quit Game");
        quitToMenu.addActionListener(event -> returnToMenu());
        finishMenu.add(again);
        finishMenu.add(quitToMenu);
        add(finishMenu);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                positionControls();
            }
        });

        TiledMap loadedMap = null;
        String loadMapError = null;
        try {
            loadedMap = TiledMap.load(new File("assets/Map/Background.tmx"));
        } catch (IOException exception) {
            loadMapError = exception.getMessage();
        }
        tiledMap = loadedMap;
        mapError = loadMapError;
        collisions = new CollisionSystem(tiledMap == null ? java.util.List.of() : tiledMap.getCollisionShapes());
        if (tiledMap != null) {
            tiledMap.getCollisionWarnings().forEach(System.err::println);
            tiledMap.getWaterWarnings().forEach(System.err::println);
        }
        cameraFollowActive = tiledMap != null && GameConfig.CAMERA_FOLLOW_ENABLED;

        int worldWidth = tiledMap == null ? GameConfig.PANEL_WIDTH : tiledMap.getPixelWidth();
        int worldHeight = tiledMap == null ? GameConfig.PANEL_HEIGHT : tiledMap.getPixelHeight();
        double startX = Math.max(
            0,
            getCameraViewportWidth() * GameConfig.CAMERA_FOCUS_X_RATIO
                - GameConfig.RACER_WIDTH / 2.0);
        startX = Math.min(startX, Math.max(0, worldWidth - GameConfig.RACER_WIDTH));
        double startY = Math.max(0, (worldHeight - GameConfig.RACER_HEIGHT) / 2.0);
        RaceWorld loadedWorld = null;
        String loadRaceError = tiledMap == null ? loadMapError : tiledMap.getRaceError();
        if (tiledMap != null && tiledMap.getRaceTrack() != null) {
            try {
                loadedWorld = new RaceWorld(tiledMap.getRaceTrack(), collisions, worldWidth, worldHeight,
                    tiledMap.getItemPickups(), new TerrainEffects(tiledMap.getWaterAreas()));
            } catch (IllegalArgumentException exception) {
                loadRaceError = exception.getMessage();
            }
        }
        raceWorld = loadedWorld;
        raceError = loadRaceError;
        racer = raceWorld == null
            ? new Racer(startX, startY, GameConfig.RACER_WIDTH, GameConfig.RACER_HEIGHT,
                new Stamina(GameConfig.MAX_STAMINA)) : raceWorld.getPlayer();
        racer.setGradualStop(true);
        updateUiVisibility();

        BufferedImage loadedLandingImage = null;

        try {
            loadedLandingImage = ImageIO.read(
                new File("assets/LandingPage.png")
            );

            if (loadedLandingImage == null) {
                System.err.println("Cannot read LandingPage.png");
            }

        } catch (IOException exception) {
            System.err.println("Cannot load LandingPage.png: " + exception.getMessage());
        }

        landingImage = loadedLandingImage;

        BufferedImage loadedImage = null;
        String loadError = null;
        try {
            loadedImage = ImageIO.read(new File(
                "assets/Gothic Gunslinger on Galloping Steed.png"));
            if (loadedImage == null) {
                loadError = "The PNG image could not be read.";
            }
        } catch (IOException exception) {
            loadError = "Could not load the image from the assets folder.";
        }
        riderImage = loadedImage;
        imageError = loadError;

        BufferedImage loadedRunSheet = null;
        String loadRunSheetError = null;
        try {
            loadedRunSheet = ImageIO.read(new File("assets/ScaledHorseRun.png"));
            if (loadedRunSheet == null) {
                loadRunSheetError = "The run sprite sheet could not be decoded.";
            }
        } catch (IOException exception) {
            loadRunSheetError = "Could not load the run sprite sheet from assets/ScaledHorseRun.png.";
        }
        riderRunSheet = loadedRunSheet;
        runSheetError = loadRunSheetError;

        Map<String, List<BufferedImage>> loadedAnimations = new LinkedHashMap<>();
        List<String> animationErrors = new ArrayList<>();
        for (String tag : List.of("idle", "run", "charge", "RBattack", "explode", "block", "hitted")) {
            File animationFile = new File("assets/Animations/" + tag + ".png");
            try {
                BufferedImage sheet = ImageIO.read(animationFile);
                if (sheet == null || sheet.getHeight() <= 0 || sheet.getWidth() % sheet.getHeight() != 0) {
                    throw new IOException("Expected a horizontal sheet of square frames.");
                }
                List<BufferedImage> frames = new ArrayList<>();
                for (int x = 0; x < sheet.getWidth(); x += sheet.getHeight()) {
                    frames.add(sheet.getSubimage(x, 0, sheet.getHeight(), sheet.getHeight()));
                }
                loadedAnimations.put(tag, List.copyOf(frames));
            } catch (IOException exception) {
                animationErrors.add(tag + ": " + exception.getMessage());
            }
        }
        riderAnimations = Map.copyOf(loadedAnimations);
        animationError = animationErrors.isEmpty() ? null : String.join(" | ", animationErrors);

        bindGameKeys();
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                inputState.clear();
                spacebarControl.reset();
                pauseKeyHeld = false;
            }
        });
        if (cameraFollowActive) {
            camera.follow(
                racer,
                getCameraViewportWidth(),
                getCameraViewportHeight(),
                tiledMap.getPixelWidth(),
                tiledMap.getPixelHeight());
        }
        positionControls();
        lastUpdateNanos = System.nanoTime();
        new Timer(16, event -> updateGame()).start();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(GameConfig.PANEL_WIDTH, GameConfig.PANEL_HEIGHT);
    }

    private void bindGameKeys() {
        bindKey("W", "pressed", () -> inputState.setMovingUp(true));
        bindKey("W", "released", () -> inputState.setMovingUp(false));
        bindKey("UP", "pressed", () -> inputState.setMovingUp(true));
        bindKey("UP", "released", () -> inputState.setMovingUp(false));
        bindKey("S", "pressed", () -> inputState.setMovingDown(true));
        bindKey("S", "released", () -> inputState.setMovingDown(false));
        bindKey("DOWN", "pressed", () -> inputState.setMovingDown(true));
        bindKey("DOWN", "released", () -> inputState.setMovingDown(false));
        bindKey("SPACE", "pressed", () -> updateSpacebar(true));
        bindKey("SPACE", "released", () -> updateSpacebar(false));
        bindKey("J", "pressed", () -> inputState.setAttackPressed(true));
        bindKey("J", "released", () -> inputState.setAttackPressed(false));
        bindKey("K", "pressed", () -> inputState.setGuarding(true));
        bindKey("K", "released", () -> inputState.setGuarding(false));
        bindKey("O", "pressed", () -> inputState.setCharging(true));
        bindKey("O", "released", () -> inputState.setCharging(false));
        bindKey("P", "pressed", () -> {
            if (!pauseKeyHeld) togglePause();
            pauseKeyHeld = true;
        });
        bindKey("P", "released", () -> pauseKeyHeld = false);
        bindKey("ESCAPE", "pressed", this::handleEscape);
        bindKey("F3", "released", () -> showCollisionBounds = !showCollisionBounds);
    }

    private void bindKey(String key, String state, Runnable action) {
        String actionName = state + "-" + key;
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(state + " " + key), actionName);
        getActionMap().put(actionName, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    private void handleEscape() {
        if (raceWorld == null) return;
        if (raceWorld.getState() == GameState.PAUSED) {
            resumeRace();
        } else if (raceWorld.getState() == GameState.RACING
            || raceWorld.getState() == GameState.COUNTDOWN) {
            togglePause();
        }
    }

    private void updateSpacebar(boolean pressed) {
        if (pressed) {
            if (!spacebarControl.press(System.nanoTime())) return;
            inputState.setMovingRight(true);
            inputState.setSprinting(spacebarControl.isSprinting());
        } else {
            // Always clear the held run state, even if the control was reset when
            // a race began while Space was already down.
            spacebarControl.release();
            inputState.setMovingRight(false);
            inputState.setSprinting(false);
        }
    }

    private void positionControls() {
        double scale = Math.min(getWidth() / (double) GameConfig.PANEL_WIDTH,
            getHeight() / (double) GameConfig.PANEL_HEIGHT);
        int pauseSize = Math.max(34, (int) (42 * scale));
        pauseButton.setBounds((getWidth() - pauseSize) / 2,
            0, pauseSize, pauseSize);
        int buttonWidth = Math.max(180, (int) (220 * scale));
        int buttonHeight = Math.max(46, (int) (54 * scale));
        int buttonGap = Math.max(12, (int) (20 * scale));
        int buttonsWidth = buttonWidth * 2 + buttonGap;
        int centerX = (getWidth() - buttonsWidth) / 2;
        int centerY = getHeight() - buttonHeight - Math.max(26, (int) (44 * scale));
        stageMenu.setBounds(centerX, centerY, buttonsWidth, buttonHeight);
        Font buttonFont = new Font("Trebuchet MS", Font.BOLD, Math.max(14, (int) (17 * scale)));
        marathonButton.setFont(buttonFont);
        sprintRaceButton.setFont(buttonFont);
        marathonButton.setPreferredSize(new Dimension(buttonWidth, buttonHeight));
        sprintRaceButton.setPreferredSize(new Dimension(buttonWidth, buttonHeight));
        pauseButton.setFont(new Font("Trebuchet MS", Font.BOLD, Math.max(16, (int) (20 * scale))));
        int menuWidth = Math.max(290, (int) (360 * scale));
        int menuHeight = Math.max(220, (int) (250 * scale));
        pauseMenu.setBounds((getWidth() - menuWidth) / 2, (getHeight() - menuHeight) / 2,
            menuWidth, menuHeight);
        int finishWidth = Math.max(340, (int) (430 * scale));
        int finishHeight = Math.max(66, (int) (76 * scale));
        finishMenu.setBounds((getWidth() - finishWidth) / 2,
            (getHeight() - finishHeight) / 2 + Math.max(65, (int) (82 * scale)), finishWidth, finishHeight);
        for (int i = 0; i < pauseMenu.getComponentCount(); i++) {
            pauseMenu.getComponent(i).setFont(new Font("Trebuchet MS", Font.BOLD,
                Math.max(i == 0 ? 21 : 16, (int) ((i == 0 ? 25 : 18) * scale))));
        }
        for (int i = 0; i < finishMenu.getComponentCount(); i++) {
            finishMenu.getComponent(i).setFont(new Font("Trebuchet MS", Font.BOLD,
                Math.max(15, (int) (18 * scale))));
            finishMenu.getComponent(i).setPreferredSize(new Dimension(
                Math.max(125, (int) (150 * scale)), Math.max(42, (int) (48 * scale))));
        }
    }

    private void createPauseMenu() {
        pauseMenu.setBackground(new Color(20, 52, 112, 220));
        pauseMenu.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        JLabel title = new JLabel("PAUSED", JLabel.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Tahoma", Font.BOLD, 23));
        pauseMenu.add(title);
        JButton resume = new RoundedButton("Resume");
        styleBlueButton(resume);
        resume.addActionListener(event -> resumeRace());
        pauseMenu.add(resume);
        JButton quit = new RoundedButton("Quit Game");
        styleBlueButton(quit);
        quit.addActionListener(event -> returnToMenu());
        pauseMenu.add(quit);
        pauseMenu.setVisible(false);
        finishMenu.setVisible(false);
    }

    private static JButton pauseMenuButton(String text) {
        JButton button = new RoundedButton(text);
        styleBlueButton(button);
        button.setFont(new Font("Tahoma", Font.PLAIN, 17));
        return button;
    }

    private static void styleBlueButton(JButton button) {
        button.setFocusable(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(34, 104, 205));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
    }

    private void beginRace() {
        if (raceWorld == null || (raceWorld.getState() != GameState.MENU
            && raceWorld.getState() != GameState.FINISHED)) {
            return;
        }
        raceWorld.start(inputState);
        checkpointAnnouncementRemaining = 0;
        refreshAfterRaceTransition();
    }

    private void refreshAfterRaceTransition() {
        racerVisuals.clear();
        spacebarControl.reset();
        lastUpdateNanos = System.nanoTime();
        followPlayer();
        updateUiVisibility();
        requestFocusInWindow();
        repaint();
    }

    private void updateUiVisibility() {
        GameState state = raceWorld == null ? GameState.MENU : raceWorld.getState();
        boolean menu = raceWorld == null || state == GameState.MENU;
        stageMenu.setVisible(menu);
        pauseButton.setVisible(state == GameState.COUNTDOWN || state == GameState.RACING
            || state == GameState.PAUSED);
        pauseMenu.setVisible(state == GameState.PAUSED);
        finishMenu.setVisible(state == GameState.FINISHED);
        positionControls();
    }

    private void togglePause() {
        if (raceWorld == null) return;
        if (raceWorld.getState() == GameState.PAUSED) {
            resumeRace();
        } else if (raceWorld.pause(inputState)) {
            spacebarControl.reset();
            updateUiVisibility();
            repaint();
        }
    }

    private void resumeRace() {
        if (raceWorld == null || !raceWorld.resume(inputState)) return;
        spacebarControl.reset();
        lastUpdateNanos = System.nanoTime();
        updateUiVisibility();
        requestFocusInWindow();
        repaint();
    }

    private void returnToMenu() {
        if (raceWorld == null) return;
        raceWorld.returnToMenu(inputState);
        spacebarControl.reset();
        racerVisuals.clear();
        followPlayer();
        updateUiVisibility();
        requestFocusInWindow();
        repaint();
    }

    private void followPlayer() {
        if (cameraFollowActive) {
            camera.follow(racer, getCameraViewportWidth(), getCameraViewportHeight(),
                tiledMap.getPixelWidth(), tiledMap.getPixelHeight());
        }
    }

    private int getCameraViewportWidth() {
        return (int) Math.ceil(GameConfig.PANEL_WIDTH / GameConfig.CAMERA_ZOOM);
    }

    private int getCameraViewportHeight() {
        return (int) Math.ceil(GameConfig.PANEL_HEIGHT / GameConfig.CAMERA_ZOOM);
    }

    private void updateGame() {
        long currentTimeNanos = System.nanoTime();
        double elapsedSeconds = Math.min(
            (currentTimeNanos - lastUpdateNanos) / 1_000_000_000.0,
            0.05);
        lastUpdateNanos = currentTimeNanos;

        boolean paused = raceWorld != null && raceWorld.getState() == GameState.PAUSED;
        if (!paused && tiledMap != null) tiledMap.update(elapsedSeconds);

        if (raceWorld != null && !paused) {
            for (Racer actor : raceWorld.getRacers()) {
                racerVisuals.computeIfAbsent(actor, key -> new RacerVisual(actor.getX()));
            }
            int passedBeforeUpdate = raceWorld.getPassedCheckpoints();
            raceWorld.update(inputState, elapsedSeconds);
            if (raceWorld.getPassedCheckpoints() > passedBeforeUpdate) {
                checkpointAnnouncement = raceWorld.getPassedCheckpoints();
                checkpointAnnouncementRank = raceWorld.getPlayerRank();
                checkpointAnnouncementRemaining = 3.0;
            } else {
                checkpointAnnouncementRemaining = Math.max(0, checkpointAnnouncementRemaining - elapsedSeconds);
            }
            for (Racer actor : raceWorld.getRacers()) {
                RacerVisual visual = racerVisuals.get(actor);
                if (Math.abs(actor.getX() - visual.previousX) > 1e-6) {
                    visual.facingLeft = actor.getX() < visual.previousX;
                }
                visual.previousX = actor.getX();
                visual.update(actor, elapsedSeconds, riderAnimations);
            }
        }
        followPlayer();
        updateUiVisibility();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D screenDrawing = (Graphics2D) graphics.create();
        screenDrawing.setColor(Color.BLACK);
        screenDrawing.fillRect(0, 0, getWidth(), getHeight());

        double scale = Math.max(
            getWidth() / (double) GameConfig.PANEL_WIDTH,
            getHeight() / (double) GameConfig.PANEL_HEIGHT);
        int offsetX = (int) ((getWidth() - GameConfig.PANEL_WIDTH * scale) / 2.0);
        int offsetY = (int) ((getHeight() - GameConfig.PANEL_HEIGHT * scale) / 2.0);

        Graphics2D drawing = (Graphics2D) screenDrawing.create();
        drawing.translate(offsetX, offsetY);
        drawing.scale(scale, scale);

        int cameraX = cameraFollowActive ? camera.getX() : 0;
        int cameraY = cameraFollowActive ? camera.getY() : 0;
        Graphics2D worldDrawing = (Graphics2D) drawing.create();
        worldDrawing.scale(GameConfig.CAMERA_ZOOM, GameConfig.CAMERA_ZOOM);
        worldDrawing.translate(-cameraX, -cameraY);
        worldDrawing.setColor(getBackground());
        worldDrawing.fillRect(
            cameraX,
            cameraY,
            getCameraViewportWidth(),
            getCameraViewportHeight());

        boolean landing = raceWorld == null || raceWorld.getState() == GameState.MENU;
        if (!landing && tiledMap != null) {
            tiledMap.draw(
                worldDrawing,
                cameraX,
                cameraY,
                getCameraViewportWidth(),
                getCameraViewportHeight());
        }
        if (!landing && raceWorld != null) {
            for (StaminaPickup pickup : raceWorld.getItemPickups()) {
                pickup.draw(worldDrawing, cameraX, cameraY, getCameraViewportWidth(), getCameraViewportHeight());
            }
        }
        if (!landing) drawCompetitorsAndTrees(worldDrawing, cameraX, cameraY);
        if (!landing && showCollisionBounds) {
            worldDrawing.setColor(Color.CYAN);
            collisions.draw(worldDrawing);
            if (raceWorld != null) {
                worldDrawing.setColor(new Color(60, 130, 255));
                raceWorld.getTerrainEffects().draw(worldDrawing);
            }
            worldDrawing.setColor(Color.MAGENTA);
            for (Racer actor : raceWorld == null ? List.of(racer) : raceWorld.getRacers()) {
                worldDrawing.draw(actor.getFeetBounds());
            }
            if (raceWorld != null) {
                worldDrawing.setColor(Color.ORANGE);
                raceWorld.getItemPickups().forEach(pickup -> worldDrawing.draw(pickup.getArea()));
            }
            if (raceWorld != null) {
                worldDrawing.setColor(Color.YELLOW);
                for (RaceTrack.Gate checkpoint : raceWorld.getTrack().getCheckpoints()) {
                    worldDrawing.draw(checkpoint.getArea());
                    worldDrawing.drawString("CP " + checkpoint.getOrder(),
                        (int) checkpoint.getArea().getX(), (int) checkpoint.getArea().getY() - 5);
                }
                worldDrawing.setColor(Color.GREEN);
                worldDrawing.draw(raceWorld.getTrack().getFinish().getArea());
            }
        }
        worldDrawing.dispose();
        // Draw landing page background
        if (landing && landingImage != null) {
            drawing.drawImage(
                landingImage,
                0,
                0,
                GameConfig.PANEL_WIDTH,
                GameConfig.PANEL_HEIGHT,
                this
            );
        }        

        drawing.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (!landing && raceWorld != null && raceWorld.getState() != GameState.FINISHED
            && raceWorld.getState() != GameState.PAUSED) drawCompactHud(drawing);
        drawRaceInterface(drawing);

        drawing.dispose();
        screenDrawing.dispose();
    }

    private void drawCompactHud(Graphics2D drawing) {
        int x = GameConfig.PANEL_WIDTH - 214, y = GameConfig.PANEL_HEIGHT - 88;
        drawing.setColor(new Color(20, 82, 170, 155));
        drawing.fillRoundRect(x, y, 198, 72, 16, 16);
        drawing.setColor(new Color(130, 195, 255, 220));
        drawing.drawRoundRect(x, y, 198, 72, 16, 16);
        drawing.setColor(Color.WHITE);
        drawing.setFont(new Font("Tahoma", Font.BOLD, 14));
        drawing.drawString("STAMINA", x + 14, y + 21);
        drawStaminaBar(drawing, x + 14, y + 29, 170, 12);
        drawing.setFont(new Font("Tahoma", Font.PLAIN, 13));
        drawing.drawString("SPRINT  " + (racer.isSprinting() ? "ON" : "OFF"), x + 14, y + 61);
        String speedText = "SPEED " + (int) Math.round(racer.getSpeed());
        drawing.drawString(speedText,
            x + 184 - drawing.getFontMetrics().stringWidth(speedText), y + 61);
    }

    private void drawRaceInterface(Graphics2D drawing) {
        GameState state = raceWorld == null ? GameState.MENU : raceWorld.getState();
        if (state == GameState.MENU) return;
        if (state == GameState.RACING && checkpointAnnouncementRemaining > 0) {
            drawing.setColor(new Color(18, 55, 112, 195));
            drawing.fillRoundRect(330, 70, 300, 82, 20, 20);
            drawing.setColor(new Color(145, 205, 255));
            drawing.drawRoundRect(330, 70, 300, 82, 20, 20);
            drawing.setColor(Color.WHITE);
            drawing.setFont(new Font("Arial", Font.BOLD, 18));
            drawCentered(drawing, ordinal(checkpointAnnouncement) + " CHECKPOINT", 102);
            drawing.setFont(new Font("Arial", Font.BOLD, 30));
            drawCentered(drawing, ordinal(checkpointAnnouncementRank), 137);
            return;
        }
        if (state == GameState.RACING) return;
        if (state == GameState.PAUSED) {
            drawing.setColor(new Color(0, 0, 0, 150));
            drawing.fillRect(0, 0, GameConfig.PANEL_WIDTH, GameConfig.PANEL_HEIGHT);
            return;
        }
        drawing.setColor(new Color(0, 0, 0, 185));
        drawing.fillRoundRect(150, 130, 660, 280, 24, 24);
        drawing.setColor(new Color(120, 185, 255));
        drawing.drawRoundRect(150, 130, 660, 280, 24, 24);
        drawing.setColor(Color.WHITE);
        if (raceWorld == null) {
            drawing.setFont(new Font("Tahoma", Font.BOLD, 27));
            drawCentered(drawing, "ข้อมูลสนามยังไม่พร้อม", 205);
            drawing.setFont(new Font("Tahoma", Font.PLAIN, 16));
            drawCentered(drawing, raceError == null ? "ตรวจสอบไฟล์สนามใน Tiled" : raceError, 250);
        } else if (state == GameState.COUNTDOWN) {
            drawing.setFont(new Font("Arial", Font.BOLD, 32));
            drawCentered(drawing, "GET READY", 220);
            drawing.setFont(new Font("Arial", Font.BOLD, 88));
            drawCentered(drawing, Integer.toString(raceWorld.getCountdownNumber()), 335);
        } else if (state == GameState.FINISHED) {
            drawing.setFont(new Font("Arial", Font.BOLD, 32));
            drawCentered(drawing, ordinal(raceWorld.getPlayerRank()), 220);
            drawing.setFont(new Font("Arial", Font.PLAIN, 23));
            drawCentered(drawing, formatTime(raceWorld.getElapsedSeconds()), 275);
        }
    }

    private static String ordinal(int rank) {
        int lastTwo = rank % 100;
        if (lastTwo >= 11 && lastTwo <= 13) return rank + "th";
        return rank + switch (rank % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private static void drawCentered(Graphics2D graphics, String text, int y) {
        graphics.drawString(text, (GameConfig.PANEL_WIDTH - graphics.getFontMetrics().stringWidth(text)) / 2, y);
    }

    static String formatTime(double seconds) {
        long milliseconds = Math.round(seconds * 1000);
        return String.format("%02d:%02d.%03d", milliseconds / 60000, milliseconds / 1000 % 60,
            milliseconds % 1000);
    }

    private static class RoundedButton extends JButton {
        private static final long serialVersionUID = 1L;

        private RoundedButton(String text) {
            super(text);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = !isEnabled() ? new Color(80, 104, 137)
                : getModel().isPressed() ? new Color(22, 76, 160)
                : getModel().isRollover() ? new Color(67, 142, 235)
                : new Color(34, 104, 205);
            g.setColor(fill);
            g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            g.setColor(new Color(155, 207, 255));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 22, 22);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class PauseButton extends RoundedButton {
        private static final long serialVersionUID = 1L;

        private PauseButton() {
            super("");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int barWidth = Math.max(3, getWidth() / 9);
            int gap = Math.max(3, getWidth() / 10);
            int barHeight = Math.max(14, getHeight() / 2);
            int left = (getWidth() - (barWidth * 2 + gap)) / 2;
            int top = (getHeight() - barHeight) / 2;
            g.setColor(Color.WHITE);
            g.fillRoundRect(left, top, barWidth, barHeight, barWidth, barWidth);
            g.fillRoundRect(left + barWidth + gap, top, barWidth, barHeight, barWidth, barWidth);
            g.dispose();
        }
    }

    private static final class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);
            g.dispose();
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(145, 200, 255));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 28, 28);
            g.dispose();
        }
    }

    private void drawCompetitorsAndTrees(Graphics2D graphics, int cameraX, int cameraY) {
        List<Racer> actors = new ArrayList<>(raceWorld == null ? List.of(racer) : raceWorld.getRacers());
        List<TreeSprite> trees = tiledMap == null ? List.of() : tiledMap.getTrees();
        
        // สร้างรายการสำหรับ Render ร่วมกันทั้งหมด (ทั้งตัวละครและต้นไม้) แล้วเรียงลำดับตามความสูงของพื้น (Y-Sorting)
        List<Renderable> renderables = new ArrayList<>();
        for (Racer actor : actors) {
            renderables.add(new RacerRenderable(actor));
        }
        for (TreeSprite tree : trees) {
            renderables.add(new TreeRenderable(tree));
        }
        
        // เรียงลำดับจากค่า groundY น้อยไปมาก (ค่า Y น้อยอยู่ด้านหลัง, ค่า Y มากอยู่ด้านหน้า)
        renderables.sort(Comparator.comparingDouble(Renderable::getGroundY));

        Racer player = raceWorld == null ? racer : raceWorld.getPlayer();
        Rectangle2D playerBounds = new Rectangle2D.Double(player.getX(), player.getY(),
            player.getWidth(), player.getHeight());

        for (Renderable item : renderables) {
            item.draw(graphics, cameraX, cameraY, player, playerBounds);
        }
    }

    private interface Renderable {
        double getGroundY();
        void draw(Graphics2D graphics, int cameraX, int cameraY, Racer player, Rectangle2D playerBounds);
    }

    private final class RacerRenderable implements Renderable {
        private final Racer actor;

        private RacerRenderable(Racer actor) {
            this.actor = actor;
        }

        @Override
        public double getGroundY() {
            return actor.getGroundY();
        }

        @Override
        public void draw(Graphics2D graphics, int cameraX, int cameraY, Racer player, Rectangle2D playerBounds) {
            drawRacer(graphics, actor);
        }
    }

    private final class TreeRenderable implements Renderable {
        private final TreeSprite tree;

        private TreeRenderable(TreeSprite tree) {
            this.tree = tree;
        }

        @Override
        public double getGroundY() {
            return tree.getGroundY();
        }

        @Override
        public void draw(Graphics2D graphics, int cameraX, int cameraY, Racer player, Rectangle2D playerBounds) {
            float opacityScale = tree.covers(player.getGroundY(), playerBounds)
                ? GameConfig.TREE_OCCLUSION_OPACITY : 1f;
            tree.draw(graphics, cameraX, cameraY, getCameraViewportWidth(), getCameraViewportHeight(), opacityScale);
        }
    }

    private void drawRacer(Graphics2D graphics, Racer actor) {
        RacerVisual visual = racerVisuals.computeIfAbsent(actor, key -> new RacerVisual(actor.getX()));
        Graphics2D drawing = (Graphics2D) graphics.create();
        boolean bot = raceWorld != null && actor != raceWorld.getPlayer();
        if (bot) drawing.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, GameConfig.BOT_OPACITY));
        int x = (int) Math.floor(actor.getX()), y = (int) Math.floor(actor.getY());
        BufferedImage frame = visual.getCurrentFrame(riderAnimations);
        if (frame != null) {
            int drawWidth = GameConfig.RACER_ART_SIZE;
            int drawHeight = (int) Math.round(drawWidth * frame.getHeight() / (double) frame.getWidth());
            int drawX = x + (actor.getWidth() - drawWidth) / 2;
            int drawY = (int) Math.round(actor.getGroundY()) - drawHeight;
            if (visual.facingLeft) {
                drawing.drawImage(frame, drawX + drawWidth, drawY, drawX, drawY + drawHeight,
                    0, 0, frame.getWidth(), frame.getHeight(), this);
            } else {
                drawing.drawImage(frame, drawX, drawY, drawX + drawWidth, drawY + drawHeight,
                    0, 0, frame.getWidth(), frame.getHeight(), this);
            }
        } else if (visual.animation.isPlaying() && riderRunSheet != null) {
            drawRunFrame(drawing, actor, visual, x, y);
        } else if (riderImage != null) {
            drawing.drawImage(riderImage, x, y, actor.getWidth(), actor.getHeight(), this);
        } else {
            drawing.setColor(new Color(87, 48, 39));
            drawing.fillOval(x, y, actor.getWidth(), actor.getHeight());
        }
        if (bot) {
            drawing.setColor(Color.WHITE);
            drawing.drawString("BOT " + (raceWorld.getBots().indexOf(actor) + 1), x + actor.getWidth() / 2 - 18, y + 8);
        }
        CombatRenderer.draw(drawing,actor);
        drawing.dispose();
    }

    private static final class RacerVisual {
        private final SpriteAnimation animation = new SpriteAnimation(8, .10, .075);
        private boolean facingLeft;
        private double previousX;
        private String tag = "idle";
        private int frameIndex;
        private double frameElapsed;
        private boolean wasAttacking;
        private boolean attackPlaying;
        private int attackFrame;
        private double attackFrameElapsed;
        private List<BufferedImage> attackFrames = List.of();

        private RacerVisual(double x) { previousX = x; }

        private void update(Racer actor, double seconds, Map<String, List<BufferedImage>> sheets) {
            animation.update(seconds, actor.isMoving(), actor.isSprinting());
            boolean attacking = actor.getCombat().isAttacking();
            if (attacking && !wasAttacking) {
                List<BufferedImage> sequence = new ArrayList<>();
                sequence.addAll(sheets.getOrDefault("RBattack", List.of()));
                sequence.addAll(sheets.getOrDefault("explode", List.of()));
                attackFrames = List.copyOf(sequence);
                attackFrame = 0;
                attackFrameElapsed = 0;
                attackPlaying = !attackFrames.isEmpty();
            }
            wasAttacking = attacking;

            if (actor.isStunned()) {
                attackPlaying = false;
                wasAttacking = false;
                select("hitted");
                advance(seconds, .10, false, sheets.getOrDefault(tag, List.of()).size());
            } else if (attackPlaying) {
                attackFrameElapsed += seconds;
                while (attackPlaying) {
                    double frameDuration = attackFrame < sheets.getOrDefault("RBattack", List.of()).size()
                        ? GameConfig.ATTACK_ANIMATION_FRAME_SECONDS / GameConfig.RBATTACK_ANIMATION_SPEED
                        : GameConfig.ATTACK_ANIMATION_FRAME_SECONDS;
                    if (attackFrameElapsed < frameDuration) break;
                    attackFrameElapsed -= frameDuration;
                    attackFrame++;
                    if (attackFrame >= attackFrames.size()) attackPlaying = false;
                }
                if (attackPlaying) {
                    tag = "attack";
                    frameIndex = attackFrame;
                } else {
                    select(actor.getCombat().isGuarding() ? "block"
                        : actor.getCharge().isCharging() ? "charge"
                        : actor.isMoving() ? "run" : "idle");
                    advance(seconds, actor.isSprinting() ? .075 : .10, true,
                        sheets.getOrDefault(tag, List.of()).size());
                }
            } else {
                String desired = actor.getCombat().isGuarding() ? "block"
                    : actor.getCharge().isCharging() ? "charge"
                    : actor.isMoving() ? "run" : "idle";
                select(desired);
                advance(seconds, desired.equals("run") && actor.isSprinting() ? .075 : .10,
                    !desired.equals("hitted"), sheets.getOrDefault(tag, List.of()).size());
            }
        }

        private void select(String nextTag) {
            if (tag.equals(nextTag)) return;
            tag = nextTag;
            frameIndex = 0;
            frameElapsed = 0;
        }

        private void advance(double seconds, double frameDuration, boolean loop, int frameCount) {
            if (frameCount <= 1) { frameIndex = 0; return; }
            frameElapsed += seconds;
            while (frameElapsed >= frameDuration) {
                frameElapsed -= frameDuration;
                if (frameIndex + 1 < frameCount) frameIndex++;
                else if (loop) frameIndex = 0;
            }
        }

        private BufferedImage getCurrentFrame(Map<String, List<BufferedImage>> sheets) {
            if (tag.equals("attack")) {
                return attackFrame < attackFrames.size() ? attackFrames.get(attackFrame) : null;
            }
            List<BufferedImage> frames = sheets.getOrDefault(tag, List.of());
            return frameIndex < frames.size() ? frames.get(frameIndex) : null;
        }
    }

    private void drawRunFrame(Graphics2D drawing, Racer actor, RacerVisual visual, int x, int y) {
        int columns = 4;
        int frame = visual.animation.getCurrentFrame();
        int column = frame % columns;
        int row = frame / columns;
        int sourceX = column * riderRunSheet.getWidth() / columns;
        int sourceRight = (column + 1) * riderRunSheet.getWidth() / columns;
        int sourceY = row * riderRunSheet.getHeight() / 2;
        int sourceBottom = (row + 1) * riderRunSheet.getHeight() / 2;
        int frameWidth = (int) Math.round(actor.getWidth() * GameConfig.RUN_SPRITE_SCALE);
        int frameHeight = (int) Math.round(actor.getHeight() * GameConfig.RUN_SPRITE_SCALE);
        int frameX = x + (actor.getWidth() - frameWidth) / 2;
        int frameY = y + actor.getHeight() - frameHeight;
        int destinationLeft = visual.facingLeft ? frameX + frameWidth : frameX;
        int destinationRight = visual.facingLeft ? frameX : frameX + frameWidth;

        drawing.drawImage(
            riderRunSheet,
            destinationLeft,
            frameY,
            destinationRight,
            frameY + frameHeight,
            sourceX,
            sourceY,
            sourceRight,
            sourceBottom,
            this);
    }

    private void drawStaminaBar(Graphics2D drawing, int x, int y, int width, int height) {
        int filledWidth = (int) (width * racer.getStamina().getRatio());
        drawing.setColor(new Color(8, 30, 68, 210));
        drawing.fillRoundRect(x, y, width, height, height, height);
        drawing.setColor(new Color(112, 195, 255));
        drawing.fillRoundRect(x, y, filledWidth, height, height, height);
        drawing.setColor(new Color(205, 232, 255));
        drawing.drawRoundRect(x, y, width, height, height, height);
    }
}