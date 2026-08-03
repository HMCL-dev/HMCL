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

import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackManager;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default snapshot member for an official-layout game instance.
///
/// Index fields (`id`, `manifest`, layout binding) belong to a
/// [DefaultGameRepositorySnapshot]. Session services such as [#getModManager()] and
/// [#getResourcePackManager()] are lazy and are shared across [#withNewSnapshot] /
/// [#withManifest] copies so caches survive COW publishes.
@NotNullByDefault
public abstract class DefaultGameInstance implements GameInstance {

    protected final DefaultGameRepositorySnapshot snapshot;
    protected final DefaultGameRepository repository;
    protected final DefaultGameRepositoryLayout layout;
    protected final GameInstanceID id;
    protected final GameInstanceManifest manifest;
    protected GameInstanceManifest.@Nullable Resolved resolvedManifest;

    /// Cached Minecraft game version detected from this instance's primary jar.
    ///
    /// `null` means detection has not been attempted yet. After detection, unknown results are
    /// stored as [GameVersionNumber#unknown()] rather than left null.
    protected @Nullable GameVersionNumber version;

    /// Lazily created mod manager shared across snapshot wrappers for this instance id.
    private @Nullable ModManager modManager;

    /// Lazily created resource-pack manager shared across snapshot wrappers for this instance id.
    private @Nullable ResourcePackManager resourcePackManager;

    protected DefaultGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest) {
        this.snapshot = snapshot;
        this.repository = snapshot.getRepository();
        this.layout = snapshot.getLayout();
        this.id = id;
        this.manifest = manifest;
    }

    /// Creates an instance that reuses session state from another wrapper of the same logical
    /// instance.
    ///
    /// @param snapshot     the snapshot that will own the copy
    /// @param id           the instance id
    /// @param manifest     the stored instance manifest
    /// @param shareSession the instance whose session services and caches should be shared
    protected DefaultGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            DefaultGameInstance shareSession) {
        this(snapshot, id, manifest);
        this.version = shareSession.version;
        this.modManager = shareSession.modManager;
        this.resourcePackManager = shareSession.resourcePackManager;
    }

    protected abstract DefaultGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot);

    /// Returns a copy of this instance bound to a new snapshot and stored manifest.
    ///
    /// @param newSnapshot the snapshot that will own the copy
    /// @param manifest    the stored instance manifest
    /// @return the updated instance
    protected abstract DefaultGameInstance withManifest(DefaultGameRepositorySnapshot newSnapshot, GameInstanceManifest manifest);

    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return layout;
    }

    @Override
    public GameInstanceID getId() {
        return id;
    }

    /// Returns whether this instance is only a provisional placeholder.
    ///
    /// Provisional instances may appear in the current [DefaultGameRepositorySnapshot] so that
    /// instance-local state (for example install-time settings) can be tracked before a real
    /// manifest is saved. They must not be treated as indexed repository members.
    ///
    /// @return `false` by default
    public boolean isProvisional() {
        return false;
    }

    @Override
    public GameInstanceManifest getManifest() {
        return manifest;
    }

    @Override
    public GameInstanceManifest.Resolved getResolvedManifest() {
        if (resolvedManifest == null) {
            resolvedManifest = snapshot.resolve(manifest);
        }
        return resolvedManifest;
    }

    /// {@inheritDoc}
    ///
    /// The detected version is cached on this instance. When the primary jar cannot be resolved or
    /// its Minecraft version cannot be recognized, [GameVersionNumber#unknown()] is cached and
    /// returned.
    @Override
    public GameVersionNumber getVersion() {
        if (version == null) {
            version = detectVersion();
        }
        return version;
    }

    /// Returns the mod manager for this instance.
    ///
    /// The manager is created on first use and shared across snapshot wrappers produced by
    /// [#withNewSnapshot] / [#withManifest].
    ///
    /// @return the mod manager
    public ModManager getModManager() {
        if (modManager == null) {
            modManager = new ModManager(repository, id);
        }
        return modManager;
    }

    /// Returns the resource-pack manager for this instance.
    ///
    /// The manager is created on first use and shared across snapshot wrappers produced by
    /// [#withNewSnapshot] / [#withManifest].
    ///
    /// @return the resource-pack manager
    public ResourcePackManager getResourcePackManager() {
        if (resourcePackManager == null) {
            resourcePackManager = new ResourcePackManager(repository, id);
        }
        return resourcePackManager;
    }

    /// Detects the Minecraft game version from this instance's primary client jar.
    ///
    /// @return the detected version, or [GameVersionNumber#unknown()] when detection fails
    private GameVersionNumber detectVersion() {
        try {
            GameInstanceManifest launchManifest = getResolvedManifest().launchManifest();
            Path jar = repository.getInstanceJar(launchManifest);
            Optional<String> detected = GameVersion.minecraftVersion(jar);
            if (detected.isEmpty()) {
                LOG.warning("Cannot find out game version of " + id
                        + ", primary jar: " + jar
                        + ", jar exists: " + Files.exists(jar));
                return GameVersionNumber.unknown();
            }
            return GameVersionNumber.asGameVersion(detected.get());
        } catch (NoSuchGameInstanceException e) {
            LOG.warning("Cannot resolve game version of " + id, e);
            return GameVersionNumber.unknown();
        }
    }

    @Override
    public Path getInstanceRoot() {
        return layout.getInstanceRoot(id);
    }

    @Override
    public Path getInstanceJarFile() {
        return layout.getInstanceJarFile(id);
    }

    @Override
    public Path getRunDirectory() {
        return getRepository().getRunDirectory(id);
    }
}
