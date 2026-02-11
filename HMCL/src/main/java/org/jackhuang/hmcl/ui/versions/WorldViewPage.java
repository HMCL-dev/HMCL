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

import java.io.EOFException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

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
        private static final WVColor[] MISSING_CHUNK_PATTERNS = {
                WVColor.rgb(128, 0, 128),  // Purple
                WVColor.rgb(0, 0, 0)       // Black
        };

        private static final WVColor UNGENERATED_CHUNK_COLOR = WVColor.rgb(-1, -1, -1);

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

            setupMouseEvents();
        }

        // Set up mouse interaction handlers
        private void setupMouseEvents() {
            // Mouse press handler for dragging
            setOnMousePressed(event -> {
                dragStartX = event.getSceneX();
                dragStartY = event.getSceneY();
            });

            // Mouse drag handler for panning
            setOnMouseDragged(event -> {
                double deltaX = event.getSceneX() - dragStartX;
                double deltaY = event.getSceneY() - dragStartY;

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
                        if (c != UNGENERATED_CHUNK_COLOR) {
                            gc.setFill(c.get());
                        } else {
                            drawMissingChunkPattern(gc, screenX, screenY, chunkSize);
                            continue;
                        }
                    } else {
                        // Use missing chunk pattern and request loading
                        drawMissingChunkPattern(gc, screenX, screenY, chunkSize);
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

        private void drawMissingChunkPattern(GraphicsContext gc, double x, double y, double size) {
            gc.setFill(MISSING_CHUNK_PATTERNS[0].get()); // Purple
            gc.fillRect(x, y, size / 2, size / 2);
            gc.fillRect(x + size / 2, y + size / 2, size / 2, size / 2);

            gc.setFill(MISSING_CHUNK_PATTERNS[1].get()); // Black
            gc.fillRect(x + size / 2, y, size / 2, size / 2);
            gc.fillRect(x, y + size / 2, size / 2, size / 2);
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
                            Thread.sleep(10); // Avoid busy waiting
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
            return WVColor.fromColor(Arrays.stream(chunkColors)
                    .collect(Collectors.groupingBy(
                            c -> Arrays.asList(
                                    (int)(c.get().getRed() * 10),
                                    (int)(c.get().getGreen() * 10),
                                    (int)(c.get().getBlue() * 10)
                            ), Collectors.counting()
                    ))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(e -> Color.color(
                            e.getKey().get(0) / 10.0,
                            e.getKey().get(1) / 10.0,
                            e.getKey().get(2) / 10.0
                    ))
                    .orElse(Color.WHITE));
        }

        /**
         * Gets the color for a specific block type.
         * @param blockName The block identifier
         * @return The color representing the block
         */
        private WVColor getColor(@NotNull String blockName) {
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
                default -> Color.GRAY;
            });
        }

        private static final class WVColor {
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
            private static @NotNull WVColor fromColor(@NotNull Color color) {
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
        }
    }

    public static class Skin extends DecoratorAnimatedPageSkin<WorldViewPage> {
        public Skin(WorldViewPage page) {
            super(page);
            setCenter(page.viewer);
        }
    }
}
