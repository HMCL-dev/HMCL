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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@NotNullByDefault
public interface GameRepository2 {
    GameInstanceManifest resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException;

    boolean hasInstance(GameInstanceID instanceId);

    GameInstanceManifest getInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    GameInstanceManifest getResolvedInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException;

    default GameInstanceManifest getResolvedPreservingPatchesInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        throw new UnsupportedOperationException(); // TODO
    }

    int getInstanceCount();

    Collection<GameInstanceManifest> getInstanceManifests();

    void refresh();

    default Task<Void> refreshAsync() {
        return Task.runAsync(this::refresh);
    }

    Path getInstanceRoot(GameInstanceID instanceId);

    Path getRunDirectory(GameInstanceID instanceId);

    Path getLibrariesDirectory(GameInstanceManifest manifest);

    Path getLibraryFile(GameInstanceManifest manifest, Library lib);

    Path getNativeDirectory(GameInstanceID instanceId, Platform platform);

    Path getModsDirectory(GameInstanceID instanceId);

    Path getResourcePackDirectory(GameInstanceID instanceId);

    Path getInstanceJar(GameInstanceManifest manifest);

    Optional<String> getGameVersion(GameInstanceManifest manifest);

    default Optional<String> getGameVersion(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getGameVersion(getInstanceManifest(instanceId));
    }

    default Path getInstanceJar(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstanceJar(resolve(getInstanceManifest(instanceId)));
    }

    boolean renameInstance(GameInstanceID from, GameInstanceID to);

    Path getActualAssetDirectory(GameInstanceID instanceId, String assetId);

    Path getAssetDirectory(GameInstanceID instanceId, String assetId);

    Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException;

    Path getAssetObject(GameInstanceID instanceId, String assetId, AssetObject obj);

    AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException;

    Path getIndexFile(GameInstanceID instanceId, String assetId);

    Path getLoggingObject(GameInstanceID instanceId, String assetId, LoggingInfo loggingInfo);

    default Set<String> getClasspath(GameInstanceManifest manifest) {
        Set<String> classpath = new LinkedHashSet<>();
        if (manifest.libraries() != null) {
            for (Library library : manifest.libraries())
                if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                    Path f = getLibraryFile(manifest, library);
                    if (Files.isRegularFile(f))
                        classpath.add(FileUtils.getAbsolutePath(f));
                }
        }

        return classpath;
    }

}
