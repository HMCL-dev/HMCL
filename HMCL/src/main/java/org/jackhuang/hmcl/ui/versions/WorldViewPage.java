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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Xiaotian
 */
public class WorldViewPage extends DecoratorAnimatedPage implements DecoratorPage {

    private final ObjectProperty<State> state;
    private final WorldViewer viewer;

    public WorldViewPage(@NotNull World world) {
        this.state = new SimpleObjectProperty<>(new State(i18n("world.view.title", StringUtils.parseColorEscapes(world.getWorldName())), null, true, true, true));
        this.viewer = new WorldViewer(world, this.getWidth(), this.getHeight(), Math.max((Runtime.getRuntime().availableProcessors() / 2), 1)); // 使用一半的CPU核心数进行区块取色，至少使用一个核心
        this.viewer.render();
        this.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) { // page is closed
                viewer.stopRender();
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public static class WorldViewer extends Canvas {

        World.WorldParser worldParser;
        final Set<CacheChunkColorTask> tasks = new HashSet<>();
        final ConcurrentHashMap<World.WorldParser.Chunk, Color> chunkColorMap = new ConcurrentHashMap<>();

        final RenderTask renderTask = new RenderTask();
        final AsyncTaskExecutor render = new AsyncTaskExecutor(renderTask);

        public WorldViewer(@NotNull World world, double width, double height, int asyncTaskCount) {
            super(width, height);
            LOG.info("Initializing world view: %s [async: %d]".formatted(world.getWorldName(), asyncTaskCount));
            this.worldParser = new World.WorldParser(world);
            if (asyncTaskCount <= 0) {
                throw new IllegalArgumentException("Thread count must be greater than 0");
            }
            for (int i = 0; i < asyncTaskCount; i++) {
                var task = new CacheChunkColorTask();
                tasks.add(task);
            }
        }

        public void render() {
            LOG.info("Start rendering world view: %s".formatted(worldParser.toString()));
            CacheChunkColorTask.executeAll(tasks);
            // preload the (0, 0) chunk of the overworld
            CacheChunkColorTask.sendRequestAll(tasks, new World.WorldParser.Chunk[] {new World.WorldParser.Chunk(0, 0, worldParser.overworld)});
            render.start();
        }

        public void stopRender() {
            tasks.forEach(CacheChunkColorTask::stop);
            renderTask.stop();
            chunkColorMap.clear();
            LOG.info("Stopped rendering world view: %s".formatted(worldParser.toString()));
        }

        public class RenderTask extends Task<Void> {

            Thread thread = null;

            @Override
            public void execute() {
                thread = Thread.currentThread();
                while (! isCancelled()) {
                    if (chunkColorMap.isEmpty()) continue;
                    GraphicsContext gc = getGraphicsContext2D();
                    // TODO: 区块渲染
                }
            }

            public void stop() {
                thread.interrupt();
            }
        }

        public class CacheChunkColorTask extends Task<Void> {

            Thread thread = null;

            public CacheChunkColorTask() {
                super();
                setSignificance(TaskSignificance.MINOR);
            }

            @Override
            public void execute() {
                thread = Thread.currentThread();
                while (!isCancelled()) {
                    Map<String, Object> properties = this.getProperties();
                    if (properties.isEmpty()) continue;
                    properties.keySet().forEach(
                            key -> {
                                if (properties.get(key) instanceof World.WorldParser.Chunk chunk) {
                                    if (!chunkColorMap.containsKey(chunk)) {
                                        Color[] chunkColors = new Color[256];
                                        for (int x = 0; x < 16; x++) {
                                            for (int z = 0; z < 16; z++) {
                                                chunkColors[x * 16 + z] = getColor(worldParser.parseBlockFromChunkData(chunk, x, 255, z));
                                            }
                                        }
                                        chunkColorMap.put(chunk, evaluateColor(chunkColors));
                                    }
                                }
                            }
                    );
                }
            }

            public synchronized void sendRequest(World.WorldParser.Chunk chunk) {
                this.getProperties().put(chunk.toString(), chunk);
            }

            public void stop() {
                thread.interrupt();
            }

            static void executeAll(@NotNull Set<CacheChunkColorTask> tasks) {
                tasks.forEach(
                        task -> {
                            (new AsyncTaskExecutor(task)).start();
                        }
                );
            }

            static void sendRequestAll(@NotNull Set<CacheChunkColorTask> tasks, World.WorldParser.Chunk @NotNull [] chunks) {
                // 平均分配请求
                int taskCount = tasks.size();
                for (int i = 0; i < chunks.length; i++) {
                    CacheChunkColorTask task = tasks.stream().skip(i % taskCount).findFirst().orElseThrow();
                    task.sendRequest(chunks[i]);
                }
            }
        }

        private @NotNull Color evaluateColor(Color @NotNull [] chunkColors) {
            return Arrays.stream(chunkColors)
                    .collect(Collectors.groupingBy(
                            c -> Arrays.asList(
                                    (int)(c.getRed() * 10),
                                    (int)(c.getGreen() * 10),
                                    (int)(c.getBlue() * 10)
                            ), Collectors.counting()
                    ))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(e -> Color.color(
                            e.getKey().get(0) / 10.0,
                            e.getKey().get(1) / 10.0,
                            e.getKey().get(2) / 10.0
                    ))
                    .orElse(Color.WHITE);
        }

        private Color getColor(@NotNull String blockName) {
            return switch (blockName) {
                case "minecraft:air" -> Color.rgb(0, 0, 0, 0);
                case "minecraft:water" -> Color.rgb(64, 164, 223);
                case "minecraft:lava" -> Color.rgb(240, 88, 17);
                case "minecraft:bedrock" -> Color.rgb(54, 54, 54);
                case "minecraft:grass_block" -> Color.rgb(127, 178, 56);
                case "minecraft:dirt" -> Color.rgb(134, 96, 67);

                default -> Color.GRAY;
            };
        }
    }
}
