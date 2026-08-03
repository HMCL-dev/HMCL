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

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public abstract class DefaultGameInstance implements GameInstance {

    protected final DefaultGameRepository.Status status;
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

    protected DefaultGameInstance(
            DefaultGameRepository.Status status,
            GameInstanceID id,
            GameInstanceManifest manifest) {
        this.status = status;
        this.repository = status.repository;
        this.layout = status.layout;
        this.id = id;
        this.manifest = manifest;
    }

    protected abstract DefaultGameInstance withNewStatus(DefaultGameRepository.Status newStatus);

    /// Returns a copy of this instance bound to a new status and stored manifest.
    ///
    /// @param newStatus the status that will own the copy
    /// @param manifest  the stored instance manifest
    /// @return the updated instance
    protected abstract DefaultGameInstance withManifest(DefaultGameRepository.Status newStatus, GameInstanceManifest manifest);

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
    /// Provisional instances may appear in the current [DefaultGameRepository.Status] so that
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
            resolvedManifest = status.resolve(manifest, new HashSet<>());
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
