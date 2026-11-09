// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.Objects;

/// Implements the conventional official Minecraft launcher repository directory layout.
///
/// Instance definitions are stored as `versions/<id>/<id>.json`, client jars as
/// `versions/<id>/<id>.jar`, with shared `libraries/` and `assets/` directories under the base
/// directory.
@NotNullByDefault
public class DefaultGameRepositoryLayout implements GameRepositoryLayout {
    private final Path baseDirectory;

    /// Creates a layout rooted at the given directory.
    ///
    /// The path is retained as supplied and is not normalized or converted to an absolute path.
    ///
    /// @param baseDirectory the repository base directory
    public DefaultGameRepositoryLayout(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory);
    }

    /// {@inheritDoc}
    @Override
    public Path getBaseDirectory() {
        return baseDirectory;
    }

    /// {@inheritDoc}
    ///
    /// Official layout path: `versions/<id>/` below the base directory.
    @Override
    public Path getInstanceRoot(GameInstanceID instanceId) {
        return getBaseDirectory().resolve("versions").resolve(instanceId.id());
    }

    /// Returns the official version manifest file for an instance.
    ///
    /// @param instanceId the instance ID
    /// @return the path `versions/<id>/<id>.json` below the base directory
    public Path getInstanceJson(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(instanceId.id() + ".json");
    }

    /// Returns the conventional client jar file for an instance under the official layout.
    ///
    /// @param instanceId the instance ID
    /// @return the path `versions/<id>/<id>.jar` below the base directory
    public Path getInstanceJarFile(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(instanceId.id() + ".jar");
    }

    public Path getModpackConfigurationFile(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve("modpack.cfg");
    }

    /// {@inheritDoc}
    ///
    /// Official layout path: `libraries/` below the base directory.
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
    ///
    /// Official layout path: `assets/` below the base directory.
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
    /// The official layout stores logging configurations in a shared directory, so `assetId` does
    /// not alter the returned path.
    @Override
    public Path getLoggingObject(String assetId, LoggingInfo loggingInfo) {
        return getAssetDirectory().resolve("log_configs").resolve(loggingInfo.file().getId());
    }
}
