package game.world;

import game.app.*;
import game.collision.*;
import game.items.*;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class TiledMap {
    private static final long TILE_ID_MASK = 0x0FFFFFFFL;
    private static final long TRANSFORM_MASK = 0xF0000000L;

    private final int width;
    private final int height;
    private final int tileWidth;
    private final int tileHeight;
    private final List<TileSet> tileSets;
    private final List<TileLayer> layers;
    private final List<TreeSprite> trees;
    private final List<TileObject> animatedTileObjects;
    private final List<StaminaArt> staminaArt = new ArrayList<>();
    private final List<PickupArt> pickupArt = new ArrayList<>();
    private double animationSeconds;
    private final CollisionMapLoader.Result collisionData;
    private final RaceTrack raceTrack;
    private final String raceError;
    private final List<StaminaPickup.Definition> staminaPickups;
    private final List<StaminaPickup.Definition> itemPickups;
    private final CollisionMapLoader.Result waterData;

    private TiledMap(
        int width,
        int height,
        int tileWidth,
        int tileHeight,
        List<TileSet> tileSets,
        List<TileLayer> layers,
        CollisionMapLoader.Result collisionData, RaceTrack raceTrack, String raceError,
        CollisionMapLoader.Result waterData, Element mapElement) throws IOException {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.tileSets = tileSets;
        this.layers = layers;
        this.collisionData = collisionData;
        this.raceTrack = raceTrack;
        this.raceError = raceError;
        this.waterData = waterData;
        validateTileIds();
        trees = loadTrees();
        animatedTileObjects = loadAnimatedTileObjects(mapElement);
        boolean staminaObjectLayerExists = childElements(mapElement, "objectgroup").stream()
            .anyMatch(layer -> layer.getAttribute("name").equalsIgnoreCase("stamina"));
        List<StaminaPickup.Definition> loadedPickups = new ArrayList<>();
        if (!staminaObjectLayerExists) loadedPickups.addAll(loadStaminaPickups());
        loadedPickups.addAll(loadObjectPickups(mapElement));
        itemPickups = List.copyOf(loadedPickups);
        staminaPickups = itemPickups.stream()
            .filter(definition -> definition.type() == StaminaPickup.Type.STAMINA).toList();
    }

    public static TiledMap load(File mapFile) throws IOException {
        Document mapDocument = parseXml(mapFile);
        Element mapElement = mapDocument.getDocumentElement();

        if (!"orthogonal".equals(mapElement.getAttribute("orientation"))) {
            throw new IOException("Only orthogonal Tiled maps are supported.");
        }
        if ("1".equals(mapElement.getAttribute("infinite"))) {
            throw new IOException("The map must use a fixed size, not infinite mode.");
        }

        int mapWidth = integerAttribute(mapElement, "width");
        int mapHeight = integerAttribute(mapElement, "height");
        int mapTileWidth = integerAttribute(mapElement, "tilewidth");
        int mapTileHeight = integerAttribute(mapElement, "tileheight");
        if (mapWidth <= 0 || mapHeight <= 0 || mapTileWidth <= 0 || mapTileHeight <= 0) {
            throw new IOException("Map and tile dimensions must be positive.");
        }

        List<TileSet> tileSets = loadTileSets(mapElement, mapFile);
        List<TileLayer> layers = loadLayers(mapElement, mapWidth, mapHeight);

        if (tileSets.isEmpty()) {
            throw new IOException("The map does not contain any tilesets.");
        }
        tileSets.sort(Comparator.comparingLong(tileSet -> tileSet.firstGlobalId));
        RaceTrack raceTrack = null;
        String raceError = null;
        try {
            raceTrack = RaceTrack.load(mapElement, mapWidth * mapTileWidth, mapHeight * mapTileHeight);
        } catch (IOException exception) {
            raceError = exception.getMessage();
        }
        TiledMap tiledMap = new TiledMap(
            mapWidth,
            mapHeight,
            mapTileWidth,
            mapTileHeight,
            tileSets,
            layers,
            CollisionMapLoader.load(mapElement), raceTrack, raceError, CollisionMapLoader.load(mapElement, "wet"),
            mapElement);
        return tiledMap;
    }

    public void draw(
        Graphics2D graphics,
        double cameraX,
        double cameraY,
        int viewportWidth,
        int viewportHeight) {
        drawTiles(graphics, cameraX, cameraY, viewportWidth, viewportHeight);
        drawAnimatedTileObjects(graphics, cameraX, cameraY, viewportWidth, viewportHeight);
    }

    public void update(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return;
        animationSeconds = (animationSeconds + seconds) % 3600.0;
        for (StaminaArt art : staminaArt) art.render(animationSeconds, this);
        for (PickupArt art : pickupArt) art.render(animationSeconds, this);
    }

    private void drawTiles(
        Graphics2D graphics,
        double cameraX,
        double cameraY,
        int viewportWidth,
        int viewportHeight) {
        int firstColumn = Math.max(0, (int) Math.floor(cameraX / tileWidth));
        int lastColumn = Math.min(
            width,
            (int) Math.ceil((cameraX + viewportWidth) / tileWidth));
        int firstRow = Math.max(0, (int) Math.floor(cameraY / tileHeight));
        int lastRow = Math.min(
            height,
            (int) Math.ceil((cameraY + viewportHeight) / tileHeight));

        for (TileLayer layer : layers) {
            if (!layer.visible || layer.opacity <= 0 || layer.name.equalsIgnoreCase("stamina")) {
                continue;
            }

            java.awt.Composite oldComposite = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                layer.opacity));

            for (int row = firstRow; row < lastRow; row++) {
                for (int column = firstColumn; column < lastColumn; column++) {
                    int layerColumn = column - layer.x;
                    int layerRow = row - layer.y;
                    if (layerColumn < 0 || layerColumn >= layer.width
                        || layerRow < 0 || layerRow >= layer.height) {
                        continue;
                    }

                    long encodedId = layer.globalIds[layerRow * layer.width + layerColumn];
                    long globalId = encodedId & TILE_ID_MASK;
                    if (globalId == 0) {
                        continue;
                    }
                    long transformFlags = encodedId & TRANSFORM_MASK;

                    TileSet tileSet = findTileSet(globalId);
                    if (tileSet == null) {
                        continue;
                    }
                    if (tileSet.treeBase != null) {
                        continue;
                    }

                    long localId = globalId - tileSet.firstGlobalId;
                    int destinationX = column * tileWidth;
                    int destinationY = row * tileHeight;
                    int frameId = tileSet.frameFor((int) (globalId - tileSet.firstGlobalId), animationSeconds);
                    int frameX = tileSet.margin + frameId % tileSet.columns * (tileSet.tileWidth + tileSet.spacing);
                    int frameY = tileSet.margin + frameId / tileSet.columns * (tileSet.tileHeight + tileSet.spacing);
                    drawTile(graphics, tileSet, destinationX, destinationY, frameX, frameY, transformFlags);
                }
            }

            graphics.setComposite(oldComposite);
        }
    }

    private List<TileObject> loadAnimatedTileObjects(Element mapElement) throws IOException {
        List<TileObject> result = new ArrayList<>();
        for (Element layer : childElements(mapElement, "objectgroup")) {
            if (isPickupLayer(layer.getAttribute("name"))) continue;
            if ("0".equals(layer.getAttribute("visible"))) continue;
            float opacity = optionalFloatAttribute(layer, "opacity", 1);
            if (opacity <= 0) continue;
            double offsetX = optionalDoubleAttribute(layer, "offsetx", 0);
            double offsetY = optionalDoubleAttribute(layer, "offsety", 0);
            for (Element object : childElements(layer, "object")) {
                if ("0".equals(object.getAttribute("visible")) || object.getAttribute("gid").isEmpty()) continue;
                long encodedId;
                try {
                    encodedId = Long.parseLong(object.getAttribute("gid"));
                } catch (NumberFormatException exception) {
                    throw new IOException("Tile object has an invalid global tile ID.", exception);
                }
                long globalId = encodedId & TILE_ID_MASK;
                TileSet set = findTileSet(globalId);
                if (set == null) continue;
                int localId = (int) (globalId - set.firstGlobalId);
                if (!set.animations.containsKey(localId)) continue;
                double x = optionalDoubleAttribute(object, "x", 0) + offsetX;
                double y = optionalDoubleAttribute(object, "y", 0) + offsetY;
                double objectWidth = optionalDoubleAttribute(object, "width", set.tileWidth);
                double objectHeight = optionalDoubleAttribute(object, "height", set.tileHeight);
                if (objectWidth <= 0 || objectHeight <= 0) continue;
                result.add(new TileObject(set, encodedId, x, y, objectWidth, objectHeight,
                    optionalDoubleAttribute(object, "rotation", 0), opacity));
            }
        }
        return List.copyOf(result);
    }

    private void drawAnimatedTileObjects(Graphics2D graphics, double cameraX, double cameraY,
                                         int viewportWidth, int viewportHeight) {
        Rectangle2D.Double viewport = new Rectangle2D.Double(cameraX, cameraY, viewportWidth, viewportHeight);
        for (TileObject object : animatedTileObjects) {
            double drawY = object.y - object.height;
            if (!viewport.intersects(object.x, drawY, object.width, object.height)) continue;
            TileSet set = object.tileSet;
            long globalId = object.encodedId & TILE_ID_MASK;
            int localId = (int) (globalId - set.firstGlobalId);
            int frameId = set.frameFor(localId, animationSeconds);
            int sourceX = set.margin + frameId % set.columns * (set.tileWidth + set.spacing);
            int sourceY = set.margin + frameId / set.columns * (set.tileHeight + set.spacing);
            if (sourceX + set.tileWidth > set.image.getWidth() || sourceY + set.tileHeight > set.image.getHeight()) continue;
            Graphics2D item = (Graphics2D) graphics.create();
            item.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, object.opacity));
            double centerX = object.x + object.width / 2;
            double centerY = drawY + object.height / 2;
            item.translate(centerX, centerY);
            item.rotate(Math.toRadians(object.rotation));
            item.drawImage(set.image, (int) -object.width / 2, (int) -object.height / 2,
                (int) object.width / 2, (int) object.height / 2,
                sourceX, sourceY, sourceX + set.tileWidth, sourceY + set.tileHeight, null);
            item.dispose();
        }
    }

    private void drawTile(
        Graphics2D graphics,
        TileSet tileSet,
        int destinationX,
        int destinationY,
        int sourceX,
        int sourceY,
        long transformFlags) {
        graphics.drawImage(
            tileSet.image,
            destinationX,
            destinationY,
            destinationX + tileWidth,
            destinationY + tileHeight,
            sourceX,
            sourceY,
            sourceX + tileSet.tileWidth,
            sourceY + tileSet.tileHeight,
            null);
    }

    private List<TreeSprite> loadTrees() {
        List<TreeSprite> result = new ArrayList<>();
        for (TileLayer layer : layers) {
            if (!layer.visible || layer.opacity <= 0 || layer.name.equalsIgnoreCase("stamina")) {
                continue;
            }
            Map<String, TreeBuilder> placedTrees = new LinkedHashMap<>();
            for (int index = 0; index < layer.globalIds.length; index++) {
                long encodedId = layer.globalIds[index];
                long id = encodedId & TILE_ID_MASK;
                TileSet set = id == 0 ? null : findTileSet(id);
                if (set == null || set.treeBase == null || id - set.firstGlobalId >= set.tileCount) {
                    continue;
                }
                long flags = encodedId & TRANSFORM_MASK;
                int localId = (int) (id - set.firstGlobalId);
                int column = localId % set.columns;
                int row = localId / set.columns;
                int columns = set.columns;
                int rows = (set.tileCount + columns - 1) / columns;
                Point2D.Double base = (Point2D.Double) set.treeBase.clone();
                base.x *= tileWidth / (double) set.tileWidth;
                base.y *= tileHeight / (double) set.tileHeight;
                int originX = (index % layer.width + layer.x - column) * tileWidth;
                int originY = (index / layer.width + layer.y - row) * tileHeight;
                String key = set.firstGlobalId + ":" + flags + ":" + originX + ":" + originY;
                TreeBuilder builder = placedTrees.get(key);
                if (builder == null) {
                    builder = new TreeBuilder(originX, originY,
                        columns * tileWidth, rows * tileHeight, base.y);
                    placedTrees.put(key, builder);
                }
                Graphics2D graphics = builder.image.createGraphics();
                drawTile(graphics, set, column * tileWidth, row * tileHeight,
                    set.margin + localId % set.columns * (set.tileWidth + set.spacing),
                    set.margin + localId / set.columns * (set.tileHeight + set.spacing), flags);
                graphics.dispose();
            }
            for (TreeBuilder builder : placedTrees.values()) {
                result.add(new TreeSprite(builder.image, builder.x, builder.y,
                    layer.opacity, builder.baseY));
            }
        }
        result.sort(Comparator.comparingDouble(TreeSprite::getGroundY));
        return List.copyOf(result);
    }

    private List<StaminaPickup.Definition> loadStaminaPickups() {
        List<StaminaPickup.Definition> result = new ArrayList<>();
        for (TileLayer layer : layers) {
            if (!layer.name.equalsIgnoreCase("stamina") || !layer.visible || layer.opacity <= 0) continue;
            boolean[] visited = new boolean[layer.globalIds.length];
            for (int start = 0; start < layer.globalIds.length; start++) {
                if (visited[start] || (layer.globalIds[start] & TILE_ID_MASK) == 0) continue;
                List<Integer> component = new ArrayList<>();
                java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
                pending.add(start);
                visited[start] = true;
                int minX = layer.width, minY = layer.height, maxX = 0, maxY = 0;
                while (!pending.isEmpty()) {
                    int cell = pending.removeFirst();
                    component.add(cell);
                    int x = cell % layer.width, y = cell / layer.width;
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                    int[][] neighbours = {{x-1,y},{x+1,y},{x,y-1},{x,y+1}};
                    for (int[] neighbour : neighbours) {
                        int nx = neighbour[0], ny = neighbour[1];
                        if (nx < 0 || nx >= layer.width || ny < 0 || ny >= layer.height) continue;
                        int next = ny * layer.width + nx;
                        if (!visited[next] && (layer.globalIds[next] & TILE_ID_MASK) != 0) {
                            visited[next] = true;
                            pending.add(next);
                        }
                    }
                }
                BufferedImage art = new BufferedImage((maxX-minX+1)*tileWidth,
                    (maxY-minY+1)*tileHeight, BufferedImage.TYPE_INT_ARGB);
                List<StaminaTile> tiles = new ArrayList<>();
                for (int cell : component) {
                    long encoded = layer.globalIds[cell];
                    long id = encoded & TILE_ID_MASK;
                    TileSet set = findTileSet(id);
                    int local = (int) (id - set.firstGlobalId);
                    tiles.add(new StaminaTile(set, local, (cell%layer.width-minX)*tileWidth,
                        (cell/layer.width-minY)*tileHeight, encoded & TRANSFORM_MASK));
                }
                StaminaArt composedArt = new StaminaArt(art, tiles);
                composedArt.render(animationSeconds, this);
                staminaArt.add(composedArt);
                result.add(new StaminaPickup.Definition(art, (minX+layer.x)*tileWidth,
                    (minY+layer.y)*tileHeight, layer.opacity, GameConfig.STAMINA_PICKUP_AMOUNT,
                    GameConfig.STAMINA_PICKUP_RESPAWN_SECONDS,
                    new Rectangle2D.Double(0, 0, art.getWidth(), art.getHeight())));
            }
        }
        return List.copyOf(result);
    }

    private List<StaminaPickup.Definition> loadObjectPickups(Element mapElement) throws IOException {
        List<StaminaPickup.Definition> result = new ArrayList<>();
        for (Element layer : childElements(mapElement, "objectgroup")) {
            StaminaPickup.Type type = pickupType(layer.getAttribute("name"));
            if (type == null || "0".equals(layer.getAttribute("visible"))) continue;
            float opacity = optionalFloatAttribute(layer, "opacity", 1);
            if (opacity <= 0) continue;
            double offsetX = optionalDoubleAttribute(layer, "offsetx", 0);
            double offsetY = optionalDoubleAttribute(layer, "offsety", 0);
            for (Element object : childElements(layer, "object")) {
                if ("0".equals(object.getAttribute("visible")) || object.getAttribute("gid").isEmpty()) continue;
                long encoded;
                try {
                    encoded = Long.parseLong(object.getAttribute("gid"));
                } catch (NumberFormatException exception) {
                    throw new IOException("Pickup tile object has an invalid global tile ID.", exception);
                }
                long globalId = encoded & TILE_ID_MASK;
                TileSet set = findTileSet(globalId);
                if (set == null || globalId - set.firstGlobalId >= set.tileCount) continue;
                int localId = (int) (globalId - set.firstGlobalId);
                double objectWidth = optionalDoubleAttribute(object, "width", set.tileWidth);
                double objectHeight = optionalDoubleAttribute(object, "height", set.tileHeight);
                if (objectWidth <= 0 || objectHeight <= 0) continue;
                double objectX = optionalDoubleAttribute(object, "x", 0) + offsetX;
                double objectY = optionalDoubleAttribute(object, "y", 0) + offsetY;
                BufferedImage art = new BufferedImage(Math.max(1, (int) Math.round(objectWidth)),
                    Math.max(1, (int) Math.round(objectHeight)), BufferedImage.TYPE_INT_ARGB);
                int x = (int) Math.floor(objectX);
                int y = (int) Math.floor(objectY - objectHeight);
                PickupArt dynamicArt = new PickupArt(art, set, localId, encoded & TRANSFORM_MASK,
                    x, y, optionalDoubleAttribute(object, "rotation", 0));
                dynamicArt.render(animationSeconds, this);
                pickupArt.add(dynamicArt);
                double amount = switch (type) {
                    case STAMINA -> GameConfig.STAMINA_PICKUP_AMOUNT;
                    case SPEED -> 0;
                    case SHIELD -> GameConfig.SHIELD_PICKUP_CHARGES;
                };
                double duration = type == StaminaPickup.Type.SPEED
                    ? GameConfig.SPEED_PICKUP_DURATION_SECONDS : 0;
                result.add(new StaminaPickup.Definition(art, x, y, opacity, amount,
                    GameConfig.ITEM_PICKUP_RESPAWN_SECONDS, type, duration,
                    new Rectangle2D.Double(0, 0, art.getWidth(), art.getHeight())));
            }
        }
        return List.copyOf(result);
    }

    private static StaminaPickup.Type pickupType(String layerName) {
        return switch (layerName.toLowerCase(java.util.Locale.ROOT)) {
            case "stamina" -> StaminaPickup.Type.STAMINA;
            case "speed" -> StaminaPickup.Type.SPEED;
            case "shield" -> StaminaPickup.Type.SHIELD;
            default -> null;
        };
    }

    private static boolean isPickupLayer(String layerName) { return pickupType(layerName) != null; }

    private static final class PickupArt {
        private final BufferedImage image;
        private final TileSet set;
        private final int localId;
        private final long flags;
        private final int x, y;
        private final double rotation;

        private PickupArt(BufferedImage image, TileSet set, int localId, long flags,
                          int x, int y, double rotation) {
            this.image = image; this.set = set; this.localId = localId;
            this.flags = flags; this.x = x; this.y = y; this.rotation = rotation;
        }

        private void render(double seconds, TiledMap map) {
            int frameId = set.frameFor(localId, seconds);
            int sourceX = set.margin + frameId % set.columns * (set.tileWidth + set.spacing);
            int sourceY = set.margin + frameId / set.columns * (set.tileHeight + set.spacing);
            BufferedImage frame = new BufferedImage(set.tileWidth, set.tileHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tile = frame.createGraphics();
            map.drawTile(tile, set, 0, 0, sourceX, sourceY, flags);
            tile.dispose();
            Graphics2D graphics = image.createGraphics();
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.translate(image.getWidth() / 2.0, image.getHeight() / 2.0);
            graphics.rotate(Math.toRadians(rotation));
            graphics.drawImage(frame, -image.getWidth() / 2, -image.getHeight() / 2,
                image.getWidth(), image.getHeight(), null);
            graphics.dispose();
        }
    }

    private record StaminaTile(TileSet set, int localId, int x, int y, long transformFlags) {}

    private static final class StaminaArt {
        private final BufferedImage image;
        private final List<StaminaTile> tiles;

        private StaminaArt(BufferedImage image, List<StaminaTile> tiles) {
            this.image = image;
            this.tiles = List.copyOf(tiles);
        }

        private void render(double seconds, TiledMap map) {
            Graphics2D graphics = image.createGraphics();
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            for (StaminaTile tile : tiles) {
                int frameId = tile.set().frameFor(tile.localId(), seconds);
                int sourceX = tile.set().margin + frameId % tile.set().columns
                    * (tile.set().tileWidth + tile.set().spacing);
                int sourceY = tile.set().margin + frameId / tile.set().columns
                    * (tile.set().tileHeight + tile.set().spacing);
                map.drawTile(graphics, tile.set(), tile.x(), tile.y(), sourceX, sourceY,
                    tile.transformFlags());
            }
            graphics.dispose();
        }
    }

    public List<TreeSprite> getTrees() {
        return trees;
    }

    public List<CollisionShape> getCollisionShapes() {
        return collisionData.shapes();
    }

    public List<String> getCollisionWarnings() {
        return collisionData.warnings();
    }

    public RaceTrack getRaceTrack() { return raceTrack; }
    public String getRaceError() { return raceError; }
    public List<StaminaPickup.Definition> getStaminaPickups() { return staminaPickups; }
    public List<StaminaPickup.Definition> getItemPickups() { return itemPickups; }
    public List<CollisionShape> getWaterAreas() { return waterData.shapes(); }
    public List<String> getWaterWarnings() { return waterData.warnings(); }

    private TileSet findTileSet(long globalId) {
        return findTileSet(tileSets, globalId);
    }

    private static TileSet findTileSet(List<TileSet> tileSets, long globalId) {
        for (int index = tileSets.size() - 1; index >= 0; index--) {
            TileSet tileSet = tileSets.get(index);
            if (globalId >= tileSet.firstGlobalId) {
                return tileSet;
            }
        }
        return null;
    }

    private void validateTileIds() throws IOException {
        for (TileLayer layer : layers) {
            for (long encodedId : layer.globalIds) {
                long globalId = encodedId & TILE_ID_MASK;
                if (globalId == 0) {
                    continue;
                }
                TileSet tileSet = findTileSet(globalId);
                if (tileSet == null || globalId - tileSet.firstGlobalId >= tileSet.tileCount) {
                    throw new IOException(
                        "Layer '" + layer.name + "' references an unknown tile ID: " + globalId);
                }
            }
        }
    }

    private static List<TileSet> loadTileSets(Element mapElement, File mapFile)
        throws IOException {
        List<TileSet> result = new ArrayList<>();
        for (Element mapTileSet : childElements(mapElement, "tileset")) {
            String source = mapTileSet.getAttribute("source");
            if (source.isEmpty()) {
                throw new IOException("Embedded tilesets are not supported yet.");
            }

            File tileSetFile = new File(mapFile.getParentFile(), source);
            if (!tileSetFile.isFile()) {
                throw new IOException("Tileset file not found: " + tileSetFile);
            }

            Document tileSetDocument = parseXml(tileSetFile);
            Element tileSetElement = tileSetDocument.getDocumentElement();
            Element imageElement = firstChild(tileSetElement, "image");
            if (imageElement == null) {
                throw new IOException("Tileset has no image element: " + tileSetFile);
            }

            File imageFile = new File(tileSetFile.getParentFile(), imageElement.getAttribute("source"));
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new IOException("No supported image reader for tileset: " + imageFile);
            }

            int tileWidth = integerAttribute(tileSetElement, "tilewidth");
            int tileHeight = integerAttribute(tileSetElement, "tileheight");
            int columns = integerAttribute(tileSetElement, "columns");
            int tileCount = integerAttribute(tileSetElement, "tilecount");
            int margin = optionalIntegerAttribute(tileSetElement, "margin", 0);
            int spacing = optionalIntegerAttribute(tileSetElement, "spacing", 0);
            if (tileWidth <= 0 || tileHeight <= 0 || columns <= 0 || tileCount <= 0
                || margin < 0 || spacing < 0) {
                throw new IOException("Tileset dimensions are invalid: " + tileSetFile);
            }
            int rows = (tileCount + columns - 1) / columns;
            int requiredWidth = margin * 2 + columns * tileWidth + (columns - 1) * spacing;
            int requiredHeight = margin * 2 + rows * tileHeight + (rows - 1) * spacing;
            if (requiredWidth > image.getWidth() || requiredHeight > image.getHeight()) {
                throw new IOException("Tileset image is smaller than its declared grid: " + imageFile);
            }

            result.add(new TileSet(
                longAttribute(mapTileSet, "firstgid"),
                tileWidth,
                tileHeight,
                columns,
                tileCount,
                margin,
                spacing,
                image,
                loadTileAnimations(tileSetElement, tileCount),
                loadTreeBase(tileSetElement, columns * tileWidth, rows * tileHeight)));
        }
        return result;
    }

    private static Point2D.Double loadTreeBase(Element tileSet, int width, int height)
        throws IOException {
        Element properties = firstChild(tileSet, "properties");
        if (properties == null) {
            return null;
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Element property : childElements(properties, "property")) {
            String name = property.getAttribute("name");
            if (List.of("treeBaseX", "treeBaseY").contains(name)) {
                values.put(name, integerAttribute(property, "value"));
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() != 2) {
            throw new IOException("Tree tileset needs treeBaseX and treeBaseY drawing anchors.");
        }
        Point2D.Double base = new Point2D.Double(values.get("treeBaseX"), values.get("treeBaseY"));
        if (base.x < 0 || base.y < 0 || base.x > width || base.y > height) {
            throw new IOException("Tree drawing anchor must be inside the tileset grid.");
        }
        return base;
    }

    private static Map<Integer, TileAnimation> loadTileAnimations(Element tileSet, int tileCount)
        throws IOException {
        Map<Integer, TileAnimation> result = new LinkedHashMap<>();
        for (Element tile : childElements(tileSet, "tile")) {
            Element animation = firstChild(tile, "animation");
            if (animation == null) continue;
            int tileId = integerAttribute(tile, "id");
            if (tileId < 0 || tileId >= tileCount) {
                throw new IOException("Animation references an unknown tile ID: " + tileId);
            }
            List<AnimationFrame> frames = new ArrayList<>();
            long totalDuration = 0;
            for (Element frame : childElements(animation, "frame")) {
                int frameId = integerAttribute(frame, "tileid");
                int duration = integerAttribute(frame, "duration");
                if (frameId < 0 || frameId >= tileCount || duration <= 0) {
                    throw new IOException("Tile animation contains an invalid frame or duration.");
                }
                frames.add(new AnimationFrame(frameId, duration));
                totalDuration += duration;
            }
            if (frames.isEmpty()) throw new IOException("Tile animation has no frames for tile " + tileId);
            if (totalDuration > Integer.MAX_VALUE) throw new IOException("Tile animation duration is too long.");
            result.put(tileId, new TileAnimation(List.copyOf(frames), (int) totalDuration));
        }
        return Map.copyOf(result);
    }

    private static List<TileLayer> loadLayers(
        Element mapElement, int mapWidth, int mapHeight) throws IOException {
        List<TileLayer> result = new ArrayList<>();
        for (Element layerElement : childElements(mapElement, "layer")) {
            Element dataElement = firstChild(layerElement, "data");
            if (dataElement == null || !"csv".equals(dataElement.getAttribute("encoding"))) {
                throw new IOException("Every tile layer must use CSV encoding.");
            }

            int layerWidth = optionalIntegerAttribute(layerElement, "width", mapWidth);
            int layerHeight = optionalIntegerAttribute(layerElement, "height", mapHeight);
            int layerX = optionalIntegerAttribute(layerElement, "x", 0);
            int layerY = optionalIntegerAttribute(layerElement, "y", 0);
            if (layerWidth <= 0 || layerHeight <= 0) {
                throw new IOException("Layer dimensions must be positive.");
            }
            String csv = dataElement.getTextContent().trim();
            String[] values = csv.isEmpty() ? new String[0] : csv.split("[,\\s]+");
            int expectedTileCount = layerWidth * layerHeight;
            if (values.length != expectedTileCount) {
                throw new IOException(
                    "Layer '" + layerElement.getAttribute("name") + "' has "
                        + values.length + " tiles; expected " + expectedTileCount + ".");
            }

            long[] globalIds = new long[values.length];
            try {
                for (int index = 0; index < values.length; index++) {
                    globalIds[index] = Long.parseLong(values[index]);
                }
            } catch (NumberFormatException exception) {
                throw new IOException(
                    "Layer '" + layerElement.getAttribute("name") + "' contains an invalid tile ID.",
                    exception);
            }

            boolean visible = !"0".equals(layerElement.getAttribute("visible"));
            float opacity = optionalFloatAttribute(layerElement, "opacity", 1.0f);
            if (opacity < 0 || opacity > 1) {
                throw new IOException("Layer opacity must be between 0 and 1.");
            }
            result.add(new TileLayer(
                layerElement.getAttribute("name"),
                layerX,
                layerY,
                layerWidth,
                layerHeight,
                globalIds,
                visible,
                opacity));
        }
        return result;
    }

    private static Document parseXml(File file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(file);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IOException("Could not parse Tiled XML file: " + file, exception);
        }
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element && tagName.equals(child.getNodeName())) {
                result.add((Element) child);
            }
        }
        return result;
    }

    private static Element firstChild(Element parent, String tagName) {
        List<Element> children = childElements(parent, tagName);
        return children.isEmpty() ? null : children.get(0);
    }

    private static int integerAttribute(Element element, String name) throws IOException {
        return optionalIntegerAttribute(element, name, -1);
    }

    private static long longAttribute(Element element, String name) throws IOException {
        String value = element.getAttribute(name);
        if (value.isEmpty()) {
            throw new IOException("Missing required XML attribute: " + name);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid XML long attribute '" + name + "'.", exception);
        }
    }

    private static int optionalIntegerAttribute(Element element, String name, int fallback)
        throws IOException {
        String value = element.getAttribute(name);
        if (value.isEmpty()) {
            if (fallback < 0) {
                throw new IOException("Missing required XML attribute: " + name);
            }
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid XML integer attribute '" + name + "'.", exception);
        }
    }

    private static float optionalFloatAttribute(Element element, String name, float fallback)
        throws IOException {
        String value = element.getAttribute(name);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid XML decimal attribute '" + name + "'.", exception);
        }
    }

    private static double optionalDoubleAttribute(Element element, String name, double fallback)
        throws IOException {
        String value = element.getAttribute(name);
        if (value.isEmpty()) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid XML decimal attribute '" + name + "'.", exception);
        }
    }


    public int getPixelWidth() {
        return width * tileWidth;
    }

    public int getPixelHeight() {
        return height * tileHeight;
    }

    private static class TileSet {
        private final long firstGlobalId;
        private final int tileWidth;
        private final int tileHeight;
        private final int columns;
        private final int tileCount;
        private final int margin;
        private final int spacing;
        private final BufferedImage image;
        private final Map<Integer, TileAnimation> animations;
        private final Point2D.Double treeBase;

        private TileSet(
            long firstGlobalId,
            int tileWidth,
            int tileHeight,
            int columns,
            int tileCount,
            int margin,
            int spacing,
            BufferedImage image,
            Map<Integer, TileAnimation> animations,
            Point2D.Double treeBase) {
            this.firstGlobalId = firstGlobalId;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.columns = columns;
            this.tileCount = tileCount;
            this.margin = margin;
            this.spacing = spacing;
            this.image = image;
            this.animations = animations;
            this.treeBase = treeBase;
        }

        private int frameFor(int tileId, double seconds) {
            TileAnimation animation = animations.get(tileId);
            return animation == null ? tileId : animation.frameFor(seconds);
        }
    }

    private record AnimationFrame(int tileId, int durationMillis) {}

    private static class TileAnimation {
        private final List<AnimationFrame> frames;
        private final int totalDurationMillis;

        private TileAnimation(List<AnimationFrame> frames, int totalDurationMillis) {
            this.frames = frames;
            this.totalDurationMillis = totalDurationMillis;
        }

        private int frameFor(double seconds) {
            int elapsedMillis = (int) Math.floor(Math.max(0, seconds) * 1000) % totalDurationMillis;
            for (AnimationFrame frame : frames) {
                if (elapsedMillis < frame.durationMillis()) return frame.tileId();
                elapsedMillis -= frame.durationMillis();
            }
            return frames.get(frames.size() - 1).tileId();
        }
    }

    private static class TileObject {
        private final TileSet tileSet;
        private final long encodedId;
        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final double rotation;
        private final float opacity;

        private TileObject(TileSet tileSet, long encodedId, double x, double y,
                           double width, double height, double rotation, float opacity) {
            this.tileSet = tileSet;
            this.encodedId = encodedId;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.opacity = opacity;
        }
    }

    private static class TreeBuilder {
        private final int x;
        private final int y;
        private final BufferedImage image;
        private final double baseY;

        private TreeBuilder(int x, int y, int width, int height, double baseY) {
            this.x = x;
            this.y = y;
            this.baseY = baseY;
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
    }

    private static class TileLayer {
        private final String name;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final long[] globalIds;
        private final boolean visible;
        private final float opacity;

        private TileLayer(
            String name,
            int x,
            int y,
            int width,
            int height,
            long[] globalIds,
            boolean visible,
            float opacity) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.globalIds = globalIds;
            this.visible = visible;
            this.opacity = opacity;
        }
    }
}