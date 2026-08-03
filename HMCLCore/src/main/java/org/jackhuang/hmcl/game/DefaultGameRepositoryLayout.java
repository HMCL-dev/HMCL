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

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Objects;

/// Implements the conventional Minecraft repository directory layout.
@NotNullByDefault
public abstract class DefaultGameRepositoryLayout implements GameRepositoryLayout {
    private final Path baseDirectory;

    /// Creates a layout rooted at the given directory.
    ///
    /// The path is retained as supplied and is not normalized or converted to an absolute path.
    ///
    /// @param baseDirectory the repository base directory
    public DefaultGameRepositoryLayout(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory);
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceRoot(GameInstanceID instanceId) {
        return getBaseDirectory().resolve("versions").resolve(instanceId.id());
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceJson(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(instanceId.id() + ".json");
    }

    /// {@inheritDoc}
    @Override
    public Path getInstanceJarFile(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(instanceId.id() + ".jar");
    }

    /// {@inheritDoc}
    @Override
    public Path getLibrariesDirectory() {
        return getBaseDirectory().resolve("libraries");
    }

    /// {@inheritDoc}
    @Override
    public Path getLibraryFile(GameInstanceID owner, Library library) {
        if ("local".equals(library.hint())) {
            if (library.filename() != null) {
                return getInstanceRoot(owner).resolve("libraries").resolve(library.filename());
            }

            return getInstanceRoot(owner).resolve("libraries").resolve(library.artifact().getFileName());
        }

        return getLibrariesDirectory().resolve(library.getPath());
    }

    /// {@inheritDoc}
    @Override
    public Path getAssetDirectory() {
        return getBaseDirectory().resolve("assets");
    }

    /// {@inheritDoc}
    @Override
    public Path getAssetIndexFile(String assetId) {
        return getAssetDirectory().resolve("indexes").resolve(assetId + ".json");
    }

    /// {@inheritDoc}
    @Override
    public Path getAssetObject(AssetObject object) {
        return getAssetDirectory().resolve("objects").resolve(object.getLocation());
    }

    /// {@inheritDoc}
    ///
    /// The conventional layout stores logging configurations in a shared directory, so
    /// `assetId` does not alter the returned path.
    @Override
    public Path getLoggingObject(String assetId, LoggingInfo loggingInfo) {
        return getAssetDirectory().resolve("log_configs").resolve(loggingInfo.file().getId());
    }
}
