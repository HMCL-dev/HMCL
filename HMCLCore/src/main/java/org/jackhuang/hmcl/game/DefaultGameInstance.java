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
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default snapshot member for an official-layout game instance.
///
/// Index fields (`id`, `manifest`, layout binding, and optional non-conventional file paths) belong
/// to a [DefaultGameRepositorySnapshot]. Lazy services such as [#getModManager()] and
/// [#getResourcePackManager()] belong to this snapshot member only: copies produced by
/// [#withNewSnapshot] / [#withManifest] do not inherit them, so a repository refresh or COW publish
/// does not keep a long-lived addon-manager session.
@NotNullByDefault
public abstract class DefaultGameInstance implements GameInstance {

    protected final DefaultGameRepositorySnapshot snapshot;
    protected final DefaultGameRepository repository;
    protected final DefaultGameRepositoryLayout layout;
    protected final GameInstanceID id;
    protected final GameInstanceManifest manifest;

    /// Non-conventional manifest file path discovered at load time, or `null` for the layout default.
    ///
    /// When set, this instance's own primary jar is the sibling path with the same base name and a
    /// `.jar` extension.
    protected final @Nullable Path manifestFile;

    protected GameInstanceManifest.@Nullable Resolved resolvedManifest;

    /// Cached Minecraft game version detected from this instance's primary jar.
    ///
    /// `null` means detection has not been attempted yet. After detection, unknown results are
    /// stored as [GameVersionNumber#unknown()] rather than left null.
    protected @Nullable GameVersionNumber version;

    /// Lazily created mod manager for this snapshot member only.
    private @Nullable ModManager modManager;

    /// Lazily created resource-pack manager for this snapshot member only.
    private @Nullable ResourcePackManager resourcePackManager;

    protected DefaultGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest) {
        this(snapshot, id, manifest, (Path) null);
    }

    /// Creates an instance with an optional non-conventional manifest path.
    ///
    /// @param snapshot     the snapshot that owns this instance
    /// @param id           the instance id (directory name under the official layout)
    /// @param manifest     the stored instance manifest
    /// @param manifestFile the actual manifest JSON path, or `null` for [DefaultGameRepositoryLayout#getInstanceJson]
    protected DefaultGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            @Nullable Path manifestFile) {
        this.snapshot = snapshot;
        this.repository = snapshot.getRepository();
        this.layout = snapshot.getLayout();
        this.id = id;
        this.manifest = manifest;
        this.manifestFile = manifestFile;
    }

    /// Creates an instance that may reuse storage paths and version cache from another snapshot wrapper.
    ///
    /// The manifest path is copied when `id` equals that of `shareSession`. The cached game version is
    /// copied only when `id` and `manifest` also equal those of `shareSession`. Addon managers are
    /// never shared: each snapshot member creates its own managers on first use.
    ///
    /// @param snapshot     the snapshot that will own the copy
    /// @param id           the instance id
    /// @param manifest     the stored instance manifest
    /// @param shareSession the instance whose stable path/version state may be reused
    protected DefaultGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            DefaultGameInstance shareSession) {
        this(
                snapshot,
                id,
                manifest,
                Objects.equals(id, shareSession.id) ? shareSession.manifestFile : null);
        if (Objects.equals(id, shareSession.id) && Objects.equals(manifest, shareSession.manifest)) {
            this.version = shareSession.version;
        }
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

    /// Returns the mod manager for this snapshot member.
    ///
    /// The manager is created on first use and is not shared with other snapshot wrappers. After a
    /// repository refresh or COW publish, callers should obtain the manager from the current
    /// instance again.
    ///
    /// @return the mod manager
    public ModManager getModManager() {
        if (modManager == null) {
            modManager = new ModManager(this);
        }
        return modManager;
    }

    /// Returns the resource-pack manager for this snapshot member.
    ///
    /// The manager is created on first use and is not shared with other snapshot wrappers. After a
    /// repository refresh or COW publish, callers should obtain the manager from the current
    /// instance again.
    ///
    /// @return the resource-pack manager
    public ResourcePackManager getResourcePackManager() {
        if (resourcePackManager == null) {
            resourcePackManager = new ResourcePackManager(this);
        }
        return resourcePackManager;
    }

    /// Detects the Minecraft game version from this instance's primary client jar.
    ///
    /// @return the detected version, or [GameVersionNumber#unknown()] when detection fails
    private GameVersionNumber detectVersion() {
        try {
            Path jar = getInstanceJarFile();
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

    /// {@inheritDoc}
    ///
    /// When a non-conventional path was discovered while loading this instance, that path is
    /// returned; otherwise the layout default `versions/<id>/<id>.json` is used.
    @Override
    public Path getManifestFile() {
        return manifestFile != null ? manifestFile : layout.getInstanceJson(id);
    }

    /// {@inheritDoc}
    ///
    /// When the launch manifest redirects to another version via [GameInstanceManifest#jar()], the
    /// jar is resolved through the layout (or that other instance when present). Otherwise this
    /// instance's own jar is returned from [#getOwnJarFile()].
    @Override
    public Path getInstanceJarFile() {
        GameInstanceManifest launchManifest = getResolvedManifest().launchManifest();
        GameInstanceID jarId = Optional.ofNullable(launchManifest.jar()).orElse(launchManifest.id());
        if (!jarId.equals(id)) {
            DefaultGameInstance other = snapshot.findInstance(jarId);
            if (other != null) {
                return other.getOwnJarFile();
            }
            return layout.getInstanceJarFile(jarId);
        }
        return getOwnJarFile();
    }

    /// Returns this instance's own primary jar without following `jar` inheritance.
    ///
    /// When a non-conventional manifest path is recorded, the jar is the sibling path with the same
    /// base name. Otherwise the layout default `versions/<id>/<id>.jar` is used.
    ///
    /// @return the jar path derived from the manifest file or the layout default
    Path getOwnJarFile() {
        if (manifestFile != null) {
            return manifestFile.resolveSibling(FileUtils.getNameWithoutExtension(manifestFile) + ".jar");
        }
        return layout.getInstanceJarFile(id);
    }

    @Override
    public Path getRunDirectory() {
        return getRepository().getRunDirectory(id);
    }
}
