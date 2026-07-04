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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;
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

    private volatile Status status;

    public DefaultGameRepository2(Path baseDirectory) {
        this.status = new Status(baseDirectory);
    }

    public Lock readLock() {
        return lock.readLock();
    }

    public Lock writeLock() {
        return lock.writeLock();
    }

    public Path getBaseDirectory() {
        return status.baseDirectory;
    }

    private static boolean hasClassicVersion(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }


    @Override
    public void refresh() {
        Status newStatus = new Status(status.baseDirectory);

        if (hasClassicVersion(newStatus.baseDirectory)) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newStatus.instances.put(id, new InstanceHolder(newStatus, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newStatus.baseDirectory.resolve("versions");
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
                        manifest = JsonUtils.fromJsonFile(json, GameInstanceManifest.class);
                        if (manifest == null)
                            throw new JsonParseException("Manifest is null");
                    } catch (Exception e) {
                        LOG.warning("Malformed version json " + id, e);
                        return Stream.empty();
                    }

                    if (!id.equals(manifest.id())) {
                        // TODO
                        return Stream.empty();
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newStatus.instances.put(
                        it.id(),
                        new InstanceHolder(newStatus, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }
    }

    @Override
    public Path getInstanceRoot(GameInstanceID instanceId) {
        return getBaseDirectory().resolve("versions").resolve(instanceId.id());
    }


    @Override
    public ResolvedGameInstanceManifest resolve(GameInstanceManifest manifest) {
        throw new UnsupportedOperationException("TODO");
    }

    protected static class Status {
        private final Path baseDirectory;
        private final Map<GameInstanceID, InstanceHolder> instances = new TreeMap<>();

        protected Status(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        private GameInstanceManifest resolve(GameInstanceManifest manifest,
                                             Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
            GameInstanceManifest currentManifest;

            if (manifest.inheritsFrom() == null) {
                if (manifest.isRoot()) {
                    // TODO: Breaking change, require much testing on versions installed with external installer, other launchers, and all kinds of versions.
                    currentManifest = manifest.patches() != null ? new GameInstanceManifest(manifest.id()).withPatches(manifest.patches()) : manifest;
                } else {
                    currentManifest = manifest;
                }
                currentManifest = manifest.jar() == null ? manifest.withJar(manifest.id().id()) : currentManifest.withJar(manifest.jar());
            } else {
                // To maximize the compatibility.
                if (!resolvedSoFar.add(manifest.id())) {
                    LOG.warning("Found circular dependency versions: " + resolvedSoFar);
                    currentManifest = manifest.jar() == null ? manifest.withJar(manifest.id().id()) : manifest;
                } else {
                    InstanceHolder parentInstance = instances.get(manifest.inheritsFrom());
                    if (parentInstance == null) {
                        throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                    }

                    // It is supposed to auto-install a version in getVersion.
                    currentManifest = manifest.merge(resolve(parentInstance.manifest, resolvedSoFar));
                }
            }

            if (manifest.patches() == null) {
                // This is a version from external launcher. NO need to resolve the patches.
                return currentManifest;
            } else if (!manifest.patches().isEmpty()) {
                // Assume patches themselves do not have patches recursively.
                List<GameInstancePatch> sortedPatches = manifest.patches().stream()
                        .sorted(Comparator.comparing(GameInstancePatch::priority))
                        .toList();
                for (GameInstancePatch patch : sortedPatches) {
                    currentManifest = patch.withJar(null).merge(currentManifest);
                }
            }

            return currentManifest.setId(id);
        }

    }

    protected static class InstanceHolder {
        protected final Status status;
        protected final GameInstanceID id;
        protected final GameInstanceManifest manifest;

        protected @Nullable GameVersionNumber version;

        protected InstanceHolder(Status status, GameInstanceID id, GameInstanceManifest manifest) {
            this.status = status;
            this.id = id;
            this.manifest = manifest;
        }
    }
}
