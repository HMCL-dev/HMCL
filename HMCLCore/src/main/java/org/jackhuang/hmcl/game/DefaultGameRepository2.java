/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public class DefaultGameRepository2 implements GameRepository2 {

    private static final GameInstanceManifest CLASSIC_MANIFEST = new GameInstanceManifest(
            new GameInstanceID("Classic"),
            "${auth_player_name} ${auth_session} --workDir ${game_directory}",
            null,
            "net.minecraft.client.Minecraft",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                    classicLibrary("lwjgl"),
                    classicLibrary("jinput"),
                    classicLibrary("lwjgl_util")),
            null,
            null,
            null,
            ReleaseType.UNKNOWN,
            null,
            null,
            0,
            null,
            null,
            null,
            null
    );

    private static Library classicLibrary(String name) {
        return new Library(new Artifact("", "", ""), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo("bin/" + name + ".jar"), null),
                null, null, null, null, null, null);
    }

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private Path baseDirectory;
    private volatile @Unmodifiable Snapshot snapshot;

    public DefaultGameRepository2(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
        this.snapshot = new Snapshot(baseDirectory);
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    private static boolean hasClassicVersion(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }

    public GameInstanceManifest readInstanceManifest(Path file) throws IOException, JsonParseException {
        JsonObject json = JsonUtils.fromJsonFile(file, JsonObject.class);
        try {
            // Try TLauncher version json format
            // return JsonUtils.GSON.fromJson(json, TLauncherVersion.class).toVersion();
            throw new UnsupportedOperationException("TODO");
        } catch (JsonParseException ignored) {
        }

        try {
            // Try official version json format
            return JsonUtils.GSON.fromJson(json, GameInstanceManifest.class); // TODO
        } catch (JsonParseException ignored) {
        }

        LOG.warning("Cannot parse game instance manifest json: " + file + "\n" + json);
        throw new JsonParseException("Game instance manifest incorrect");
    }

    @Override
    public void refresh() {
        Snapshot newSnapshot = new Snapshot(baseDirectory);

        if (hasClassicVersion(newSnapshot.baseDirectory)) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newSnapshot.instances.put(id, new InstanceHolder(newSnapshot, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newSnapshot.baseDirectory.resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (Stream<Path> stream = Files.list(versionsDir)) {
                stream.parallel().filter(Files::isDirectory).flatMap(dir -> {
                    GameInstanceID id = new GameInstanceID(FileUtils.getName(dir));
                    Path json = dir.resolve(id + ".json");

                    if (Files.notExists(json)) {
                        return Stream.empty();
                    }

                    GameInstanceManifest manifest;
                    try {
                        manifest = readInstanceManifest(json);
                    } catch (Exception e) {
                        LOG.warning("Malformed version json " + id, e);
                        return Stream.empty();
                    }

                    if (!id.equals(manifest.id())) {
                        // TODO
                        return Stream.empty();
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newSnapshot.instances.put(
                        it.id(),
                        new InstanceHolder(newSnapshot, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }
    }

    @Override
    public GameVersionNumber getGameVersion(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        InstanceHolder instance = snapshot.instances.get(instanceId);
        if (instance == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Path getInstanceRoot(GameInstanceID instanceId) {
        return getBaseDirectory().resolve("versions").resolve(instanceId.id());
    }

    @Override
    public ResolvedGameInstanceManifest resolve(GameInstanceManifest manifest) {
        throw new UnsupportedOperationException("TODO");
    }

    protected static class Snapshot {
        private final Path baseDirectory;
        private final Map<GameInstanceID, InstanceHolder> instances = new TreeMap<>();

        protected Snapshot(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
        }
    }

    protected static class InstanceHolder {
        protected final Snapshot snapshot;
        protected final GameInstanceID id;
        protected final GameInstanceManifest manifest;

        protected @Nullable GameVersionNumber version;

        protected InstanceHolder(Snapshot snapshot, GameInstanceID id, GameInstanceManifest manifest) {
            this.snapshot = snapshot;
            this.id = id;
            this.manifest = manifest;
        }
    }
}
