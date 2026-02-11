/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.versions.WorldViewPage.WorldViewer.UNKNOWN_CHUNK_COLOR;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * World view page that displays a preview of Minecraft world chunks.
 * @author Xiaotian
 */
public class WorldViewPage extends DecoratorAnimatedPage implements DecoratorPage {

    // Current state of the page
    private final ObjectProperty<State> state;
    // The world viewer component
    private final WorldViewer viewer;

    /**
     * Creates a new world view page for the specified world.
     * @param world The world to view
     */
    public WorldViewPage(@NotNull World world, double width, double height) {
        // Initialize page state with world name as title
        this.state = new SimpleObjectProperty<>(new State(i18n("world.view.title", StringUtils.parseColorEscapes(world.getWorldName())), null, true, true, true));
        // Create viewer using half of available CPU cores (minimum 1)
        this.viewer = new WorldViewer(world, this.getWidth(), this.getHeight(), Math.max((Runtime.getRuntime().availableProcessors() / 2), 1));

        this.setWidth(width);
        this.setHeight(height);
        this.viewer.setWidth(width);
        this.viewer.setHeight(height);

        LOG.debug("%f, %f".formatted(this.getWidth(), this.getHeight()));
        this.viewer.render();
        // Stop rendering when page is closed
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                viewer.stopRender();
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @Override
    protected @NotNull Skin createDefaultSkin() {
        return new Skin(this);
    }

    /**
     * Canvas component that displays and interacts with the world chunks.
     */
    public static class WorldViewer extends Canvas {
        // Color patterns for unloaded chunks (purple/black alternating)
        public static final WVColor[] UNKNOWN_CHUNK_COLORSET = {
                WVColor.rgb(128, 0, 128),  // Purple
                WVColor.rgb(0, 0, 0)       // Black
        };

        public static final WVColor UNKNOWN_CHUNK_COLOR = WVColor.rgb(-1, 0, 0);
        public static final WVColor UNLOADED_CHUNK_COLOR = WVColor.rgb(-1, -1, 0);
        public static final WVColor UNGENERATED_CHUNK_COLOR = WVColor.rgb(-1, -1, -1);

        private double dragStartX, dragStartY; // Drag start coordinates
        private int centerChunkX = 0, centerChunkZ = 0; // Center chunk coordinates
        private int mouseChunkX = 0, mouseChunkZ = 0; // Mouse position in chunk coordinates
        private final Label coordinateLabel = new Label("chunk(0,0)"); // Coordinate display label

        World.WorldParser worldParser; // World data parser
        final Set<CacheChunkColorTask> tasks = new HashSet<>(); // Async chunk processing tasks
        final RenderTask renderTask = new RenderTask(); // Main render loop task
        final ConcurrentHashMap<World.WorldParser.Chunk, WVColor> chunkColorMap = new ConcurrentHashMap<>(); // Chunk color cache

        /**
         * Creates a new world viewer.
         * @param world The world to display
         * @param width Initial width
         * @param height Initial height
         * @param asyncTaskCount Number of async tasks for chunk processing
         */
        public WorldViewer(@NotNull World world, double width, double height, int asyncTaskCount) {
            super(width, height);
            LOG.info("Initializing world view: %s [async: %d]".formatted(world.getWorldName(), asyncTaskCount));
            this.worldParser = new World.WorldParser(world);
            if (asyncTaskCount <= 0) {
                throw new IllegalArgumentException("Thread count must be greater than 0");
            }
            // Create async tasks for chunk processing
            for (int i = 0; i < asyncTaskCount; i++) {
                var task = new CacheChunkColorTask();
                tasks.add(task);
            }

            // Configure coordinate label style
            coordinateLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-background-color: rgba(0,0,0,0.7);");
            coordinateLabel.setPadding(new Insets(2, 5, 2, 5));

            Platform.runLater(() -> setupMouseEvents(1.3 * (600 / getWidth()))); // 这样可以根据画布大小自动调整鼠标拖动的灵敏度，保持在不同分辨率下都有良好的体验
        }

        // Set up mouse interaction handlers
        private void setupMouseEvents(double sensitivity) {
            // Mouse press handler for dragging
            setOnMousePressed(event -> {
                dragStartX = event.getSceneX();
                dragStartY = event.getSceneY();
            });

            // Mouse drag handler for panning
            setOnMouseDragged(event -> {
                double deltaX = (event.getSceneX() - dragStartX) * sensitivity;
                double deltaY = (event.getSceneY() - dragStartY) * sensitivity;

                double chunkSize = getChunkSize();
                centerChunkX -= (int)(deltaX / chunkSize);
                centerChunkZ -= (int)(deltaY / chunkSize);

                dragStartX = event.getSceneX();
                dragStartY = event.getSceneY();

                requestChunksAroundCenter();
            });

            // Mouse move handler for coordinate display
            setOnMouseMoved(event -> {
                double chunkSize = getChunkSize();
                mouseChunkX = centerChunkX + (int)((event.getX() - getWidth() / 2) / chunkSize);
                mouseChunkZ = centerChunkZ + (int)((event.getY() - getHeight() / 2) / chunkSize);
                coordinateLabel.setText(String.format("chunk(%d,%d)", mouseChunkX, mouseChunkZ));
                renderMainLoop();
            });
        }

        // Calculate chunk size in pixels based on canvas dimensions
        private double getChunkSize() {
            return Math.min(getWidth(), getHeight()) / 20.0;
        }

        // Request loading of chunks around current center
        private void requestChunksAroundCenter() {
            int visibleRadius = (int) (Math.max(getWidth(), getHeight()) / getChunkSize() / 2) + 1;
            List<World.WorldParser.Chunk> chunksToLoad = new ArrayList<>();

            // Generate chunk coordinates in visible area
            for (int x = centerChunkX - visibleRadius; x <= centerChunkX + visibleRadius; x++) {
                for (int z = centerChunkZ - visibleRadius; z <= centerChunkZ + visibleRadius; z++) {
                    chunksToLoad.add(new World.WorldParser.Chunk(x, z, worldParser.overworld));
                }
            }

            // Send chunk load requests to async tasks
            CacheChunkColorTask.sendRequestAll(tasks, chunksToLoad.toArray(new World.WorldParser.Chunk[0]));
        }

        /**
         * Starts rendering the world view.
         */
        public void render() {
            // Ensure coordinate label is properly parented
            if (getParent() == null) {
                StackPane root = new StackPane(this);
                StackPane.setAlignment(coordinateLabel, Pos.BOTTOM_RIGHT);
                root.getChildren().add(coordinateLabel);
            } else if (getParent() instanceof StackPane root) {
                if (!root.getChildren().contains(coordinateLabel)) {
                    StackPane.setAlignment(coordinateLabel, Pos.BOTTOM_RIGHT);
                    root.getChildren().add(coordinateLabel);
                }
            } else {
                StackPane newRoot = new StackPane(getParent(), this);
                StackPane.setAlignment(coordinateLabel, Pos.BOTTOM_RIGHT);
                newRoot.getChildren().add(coordinateLabel);
                getParent().getChildrenUnmodifiable().stream()
                        .filter(node -> node != this)
                        .forEach(newRoot.getChildren()::add);
                ((Pane)getParent()).getChildren().setAll(newRoot);
            }

            (new AsyncTaskExecutor(renderTask)).start();

            LOG.info("Start rendering world view(%fx%f): %s".formatted(this.getHeight(), this.getHeight(), worldParser.toString()));
            CacheChunkColorTask.executeAll(tasks);
            requestChunksAroundCenter();
            Platform.runLater(this::renderMainLoop);
        }

        /**
         * Stops rendering and cleans up resources.
         */
        public void stopRender() {
            tasks.forEach(CacheChunkColorTask::stop);
            renderTask.stop();
            chunkColorMap.clear();
            LOG.info("Stopped rendering world view: %s".formatted(worldParser.toString()));
        }

        public void renderMainLoop() {
            GraphicsContext gc = getGraphicsContext2D();
            gc.clearRect(0, 0, getWidth(), getHeight());

            double chunkSize = getChunkSize();
            int visibleRadius = (int) (Math.max(getWidth(), getHeight()) / chunkSize / 2) + 1;

            // Render all visible chunks
            for (int x = centerChunkX - visibleRadius; x <= centerChunkX + visibleRadius; x++) {
                for (int z = centerChunkZ - visibleRadius; z <= centerChunkZ + visibleRadius; z++) {
                    World.WorldParser.Chunk chunk = new World.WorldParser.Chunk(x, z, worldParser.overworld);
                    double screenX = getWidth() / 2 + (x - centerChunkX) * chunkSize;
                    double screenY = getHeight() / 2 + (z - centerChunkZ) * chunkSize;

                    if (chunkColorMap.containsKey(chunk)) {
                        // Use cached color if available
                        WVColor c = chunkColorMap.get(chunk);
                        if (c == UNGENERATED_CHUNK_COLOR) {
                            drawUngeneratedChunkPattern(gc, screenX, screenY, chunkSize);
                            continue;
                        } else if (c == UNLOADED_CHUNK_COLOR) {
                            drawUnloadedChunkPattern(gc, screenX, screenY, chunkSize);
                            continue;
                        } else if (c == UNKNOWN_CHUNK_COLOR) {
                            drawUnknownChunkPattern(gc, screenX, screenY, chunkSize);
                            continue;
                        } else {
                            gc.setFill(c.get());
                        }
                    } else {
                        // Use missing chunk pattern and request loading
                        drawUnloadedChunkPattern(gc, screenX, screenY, chunkSize);
                        CacheChunkColorTask.sendRequestAll(tasks, new World.WorldParser.Chunk[]{chunk});
                        gc.setStroke(Color.BLACK);
                        gc.setLineWidth(0.5);
                        gc.strokeRect(screenX, screenY, chunkSize, chunkSize);
                        continue;
                    }

                    // Draw chunk rectangle
                    gc.fillRect(screenX, screenY, chunkSize, chunkSize);
                    gc.setStroke(Color.BLACK);
                    gc.setLineWidth(0.5);
                    gc.strokeRect(screenX, screenY, chunkSize, chunkSize);
                }
            }
        }

        private void drawUngeneratedChunkPattern(GraphicsContext gc, double screenX, double screenY, double chunkSize) {
            gc.setStroke(Color.DARKRED);
            gc.setLineWidth(1);
            gc.strokeLine(screenX, screenY, screenX + chunkSize, screenY + chunkSize);
            gc.strokeLine(screenX + chunkSize, screenY, screenX, screenY + chunkSize);
        }

        private void drawUnknownChunkPattern(@NotNull GraphicsContext gc, double x, double y, double size) {
            gc.setFill(UNKNOWN_CHUNK_COLORSET[0].get()); // Purple
            gc.fillRect(x, y, size / 2, size / 2);
            gc.fillRect(x + size / 2, y + size / 2, size / 2, size / 2);

            gc.setFill(UNKNOWN_CHUNK_COLORSET[1].get()); // Black
            gc.fillRect(x + size / 2, y, size / 2, size / 2);
            gc.fillRect(x, y + size / 2, size / 2, size / 2);
        }

        private void drawUnloadedChunkPattern(@NotNull GraphicsContext gc, double x, double y, double size) {
            gc.setFill(Color.LIGHTGRAY);
            gc.fillRect(x, y, size, size);
            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(0.5);
            gc.strokeRect(x, y, size, size);
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(1);
            gc.strokeLine(x, y, x + size, y + size);
            gc.strokeLine(x + size, y, x, y + size);
        }

        /**
         * Async task that processes chunk data to determine their colors.
         */
        public class CacheChunkColorTask extends Task<Void> {

            Thread thread = null;
            private final ConcurrentLinkedQueue<World.WorldParser.Chunk> pendingChunks = new ConcurrentLinkedQueue<>();

            public CacheChunkColorTask() {
                super();
                setSignificance(TaskSignificance.MINOR);
            }

            @Override
            public void execute() {
                thread = Thread.currentThread();
                while (!isCancelled()) {
                    World.WorldParser.Chunk chunk = pendingChunks.poll();
                    if (chunk == null) {
                        try {
                            Thread.sleep(20 / tasks.size()); // Avoid busy waiting
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }

                    // Process chunk if not already cached
                    if (!chunkColorMap.containsKey(chunk)) {
                        WVColor[] chunkColors = new WVColor[256];
                        // Sample block colors at top layer (y=255)
                        try {
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    int y = worldParser.getTheHighestNonAirBlock(chunk, x, z);
                                    chunkColors[x * 16 + z] = getColor(worldParser.parseBlockFromChunkData(chunk, x, y != Integer.MIN_VALUE ? y : 64, z));
                                }
                            }
                            // Determine dominant color for the chunk
                            chunkColorMap.put(chunk, evaluateColor(chunkColors));
                        } catch (RuntimeException e) {
                            if (e.getCause() instanceof EOFException) {
                                LOG.warning("Chunk data not fully generated yet: %s".formatted(chunk));
                                chunkColorMap.put(chunk, UNGENERATED_CHUNK_COLOR);
                            } else if (e.getCause() instanceof RuntimeException runtimeException) { // ignore known exceptions related to missing or incomplete chunk data
                                if (! runtimeException.getMessage().equals("Broken file head.")
                                    && ! runtimeException.getMessage().equals("Region file does not exists.")) {
                                    LOG.warning("An unexpected exception occurred while parsing chunk data", e);
                                }
                                chunkColorMap.put(chunk, UNGENERATED_CHUNK_COLOR);
                            }
                        }
                    }
                }
            }

            /**
             * Adds a chunk to the processing queue.
             * @param chunk The chunk to process
             */
            public void sendRequest(World.WorldParser.Chunk chunk) {
                pendingChunks.offer(chunk);
            }

            public void stop() {
                thread.interrupt();
            }

            /**
             * Starts all chunk processing tasks.
             * @param tasks The tasks to start
             */
            static void executeAll(@NotNull Set<CacheChunkColorTask> tasks) {
                tasks.forEach(task -> (new AsyncTaskExecutor(task)).start());
            }

            /**
             * Distributes chunk processing requests across all tasks.
             * @param tasks Available tasks
             * @param chunks Chunks to process
             */
            static void sendRequestAll(@NotNull Set<CacheChunkColorTask> tasks, World.WorldParser.Chunk @NotNull [] chunks) {
                int taskCount = tasks.size();
                for (int i = 0; i < chunks.length; i++) {
                    CacheChunkColorTask task = tasks.stream().skip(i % taskCount).findFirst().orElseThrow();
                    task.sendRequest(chunks[i]);
                }
            }
        }

        public class RenderTask extends Task<Void> {
            Thread thread = null;

            @Override
            public void execute() {
                thread = Thread.currentThread();
                while (! isCancelled()) {
                    Platform.runLater(WorldViewer.this::renderMainLoop);
                    try {
                        Thread.sleep(100 / tasks.size()); // Adjust render frequency as needed
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }

            public void stop() {
                thread.interrupt();
            }
        }

        /**
         * Determines the dominant color from an array of colors.
         * @param chunkColors Array of colors to analyze
         * @return The most frequently occurring color
         */
        private @NotNull WVColor evaluateColor(WVColor @NotNull [] chunkColors) {
            if (Arrays.stream(chunkColors).allMatch(WVColor::isNormalColor)) {
                return WVColor.fromColor(Arrays.stream(chunkColors)
                        .collect(Collectors.groupingBy(
                                c -> Arrays.asList(
                                        (int) (c.get().getRed() * 10),
                                        (int) (c.get().getGreen() * 10),
                                        (int) (c.get().getBlue() * 10)
                                ), Collectors.counting()
                        ))
                        .entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(e -> Color.color(
                                e.getKey().get(0) / 10.0,
                                e.getKey().get(1) / 10.0,
                                e.getKey().get(2) / 10.0
                        ))
                        .orElse(null));
            } else {
                return UNKNOWN_CHUNK_COLOR;
            }
        }
    }

    /**
     * Gets the color for a specific block type.
     * @param blockName The block identifier
     * @return The color representing the block
     */
    @Contract("_ -> new")
    private static @NotNull WVColor getColor(@NotNull String blockName) {
        return WVColor.fromColor(switch (blockName) {
            case "minecraft:air" -> Color.rgb(0, 0, 0, 0);
            case "minecraft:water" -> Color.rgb(64, 164, 223);
            case "minecraft:ice" -> Color.rgb(131, 190, 223);
            case "minecraft:lava" -> Color.rgb(240, 88, 17);
            case "minecraft:bedrock" -> Color.rgb(54, 54, 54);
            case "minecraft:grass_block" -> Color.rgb(127, 178, 56);
            case "minecraft:dirt" -> Color.rgb(134, 96, 67);
            case "minecraft:stone" -> Color.rgb(112, 112, 112);
            case "minecraft:sand" -> Color.rgb(218, 210, 158);
            case "minecraft:gravel" -> Color.rgb(136, 126, 126);
            default -> BlockColorFilter.getColorByFilter(blockName);
        });
    }

    public static final class WVColor {
        private final Color color;

        private WVColor(int r, int g, int b, int a) {
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255 || a < 0 || a > 1) {
                color = null;
            } else {
                color = Color.rgb(r, g, b, a);
            }
        }

        @Contract(value = "_, _, _ -> new", pure = true)
        private static @NotNull WVColor rgb(int r, int g, int b) {
            return new WVColor(r, g, b, 1);
        }

        @Contract(value = "_, _, _, _ -> new", pure = true)
        private static @NotNull WVColor rgb(int r, int g, int b, int a) {
            return new WVColor(r, g, b, a);
        }

        @Contract("_ -> new")
        private static @NotNull WVColor fromColor(Color color) {
            if (color == null) return UNKNOWN_CHUNK_COLOR;
            return new WVColor(
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255),
                    (int)(color.getOpacity())
            );
        }

        private Color get() {
            return color;
        }

        public boolean isNormalColor() {
            return color != null;
        }
    }

    public static class Skin extends DecoratorAnimatedPageSkin<WorldViewPage> {
        public Skin(WorldViewPage page) {
            super(page);
            setCenter(page.viewer);
        }
    }

    public static final class BlockColorFilter {
        public static final BlockColorFilter GRASS = new BlockColorFilter(new String[] {"minecraft:grass_block", "minecraft:tall_grass", "minecraft:fern"});
        public static final BlockColorFilter LEAVES = new BlockColorFilter(new String[] {
                "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
                "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves"
        });

        public static final BlockColorFilter WHITE_COLOR_BLOCKS = new BlockColorFilter(":.*white");
        public static final BlockColorFilter ORANGE_COLOR_BLOCKS = new BlockColorFilter(":.*orange");
        public static final BlockColorFilter MAGENTA_COLOR_BLOCKS = new BlockColorFilter(":.*magenta");
        public static final BlockColorFilter LIGHT_BLUE_COLOR_BLOCKS = new BlockColorFilter(":.*light_blue");
        public static final BlockColorFilter YELLOW_COLOR_BLOCKS = new BlockColorFilter(":.*yellow");
        public static final BlockColorFilter LIME_COLOR_BLOCKS = new BlockColorFilter(":.*lime");
        public static final BlockColorFilter PINK_COLOR_BLOCKS = new BlockColorFilter(":.*pink");
        public static final BlockColorFilter GRAY_COLOR_BLOCKS = new BlockColorFilter(":.*gray");
        public static final BlockColorFilter LIGHT_GRAY_COLOR_BLOCKS = new BlockColorFilter(":.*light_gray");
        public static final BlockColorFilter CYAN_COLOR_BLOCKS = new BlockColorFilter(":.*cyan");
        public static final BlockColorFilter PURPLE_COLOR_BLOCKS = new BlockColorFilter(":.*purple");
        public static final BlockColorFilter BLUE_COLOR_BLOCKS = new BlockColorFilter(":.*blue");
        public static final BlockColorFilter BROWN_COLOR_BLOCKS = new BlockColorFilter(":.*brown");
        public static final BlockColorFilter GREEN_COLOR_BLOCKS = new BlockColorFilter(":.*green");
        public static final BlockColorFilter RED_COLOR_BLOCKS = new BlockColorFilter(":.*red");
        public static final BlockColorFilter BLACK_COLOR_BLOCKS = new BlockColorFilter(":.*black");

        final String[] blocks;
        final String regex;

        public BlockColorFilter(String[] blocks) {
            this.blocks = blocks;
            this.regex = null;
        }

        public BlockColorFilter(String regex) {
            this.blocks = null;
            this.regex = regex;
        }

        public boolean matches(String blockName) {
            if (regex == null && blocks != null) {
                for (String block : blocks) {
                    if (block.equals(blockName)) {
                        return true;
                    }
                }
            } else if (regex != null) {
                return blockName.matches(regex);
            }
            return false;
        }

        private static @Nullable Color getColorByFilter(String blockName) {
            if (BlockColorFilter.WHITE_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(240, 240, 240);
            } else if (BlockColorFilter.ORANGE_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(216, 127, 51);
            } else if (BlockColorFilter.MAGENTA_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(178, 76, 216);
            } else if (BlockColorFilter.LIGHT_BLUE_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(102, 153, 216);
            } else if (BlockColorFilter.YELLOW_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(229, 229, 51);
            } else if (BlockColorFilter.LIME_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(127, 204, 25);
            } else if (BlockColorFilter.PINK_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(242, 127, 165);
            } else if (BlockColorFilter.GRAY_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(76, 76, 76);
            } else if (BlockColorFilter.LIGHT_GRAY_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(153, 153, 153);
            } else if (BlockColorFilter.CYAN_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(76, 127, 153);
            } else if (BlockColorFilter.PURPLE_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(127, 63, 178);
            } else if (BlockColorFilter.BLUE_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(51, 76, 178);
            } else if (BlockColorFilter.BROWN_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(102, 76, 51);
            } else if (BlockColorFilter.GREEN_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(102, 127, 51);
            } else if (BlockColorFilter.RED_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(153, 51, 51);
            } else if (BlockColorFilter.BLACK_COLOR_BLOCKS.matches(blockName)) {
                return Color.rgb(25, 25, 25);
            } else if (BlockColorFilter.GRASS.matches(blockName)) {
                return Color.rgb(127, 178, 56);
            } else if (BlockColorFilter.LEAVES.matches(blockName)) {
                return Color.rgb(63, 179, 63);
            }
            return null;
        }
    }
}
