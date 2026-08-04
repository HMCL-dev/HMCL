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

import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/// Provides a view of a game instance and its instance-specific paths within a
/// [GameRepositorySnapshot].
///
/// Core repository implementations publish instances as values belonging to a sealed snapshot.
/// When the repository publishes a newer snapshot, previously obtained instances may be stale.
/// Callers that need a long-lived identity should retain a [GameInstanceID] (or a higher-level
/// handle) and resolve it again from [GameRepository#getSnapshot()].
@NotNullByDefault
public interface GameInstance {

    GameRepository getRepository();

    GameRepositoryLayout getLayout();

    /// Returns the instance ID.
    ///
    /// @return the instance ID
    GameInstanceID getId();

    /// Returns the manifest read from this instance's manifest file.
    ///
    /// @return the unresolved stored manifest
    GameInstanceManifest getManifest();

    /// Returns the eagerly resolved manifest views captured with this instance.
    ///
    /// @return the resolved manifest views
    GameInstanceManifest.Resolved getResolvedManifest();

    /// Returns the manifest used by launch-time consumers.
    ///
    /// @return the launch manifest
    default GameInstanceManifest getLaunchManifest() {
        return getResolvedManifest().launchManifest();
    }

    GameVersionNumber getVersion();

    /// Returns the directory containing files owned by this instance.
    ///
    /// @return the instance root directory
    Path getInstanceRoot();

    /// Returns the stored instance manifest file for this instance.
    ///
    /// @return the manifest JSON path
    Path getManifestFile();

    /// Returns the launcher-specific modpack configuration file for this instance.
    ///
    /// @return the modpack configuration path in the instance root
    Path getModpackConfigurationFile();

    /// Returns the primary client jar selected by the resolved launch manifest.
    ///
    /// @return the primary client jar path
    Path getInstanceJarFile();

    /// Returns the working directory used to run this instance.
    ///
    /// @return the run directory
    Path getRunDirectory();

    /// Reads an asset index used by this instance.
    ///
    /// @param assetId the asset index ID
    /// @return the parsed asset index
    /// @throws IOException if the asset index cannot be read
    AssetIndex getAssetIndex(String assetId) throws IOException;

    /// Returns the asset directory that should be supplied when launching this instance.
    ///
    /// Implementations may reconstruct virtual or legacy resource layouts before returning.
    ///
    /// @param assetId the asset index ID
    /// @return the launch-time asset directory
    Path getActualAssetDirectory(String assetId);

    /// Returns an existing asset object by its logical name.
    ///
    /// @param assetId the asset index ID
    /// @param name    the logical asset name
    /// @return the asset object path, or empty when the index has no such object
    /// @throws IOException if the asset index cannot be read
    Optional<Path> getAssetObject(String assetId, String name) throws IOException;

    /// Returns the directory containing mods used by this instance.
    ///
    /// @return the mods directory below the run directory
    default Path getModsDirectory() {
        return getRunDirectory().resolve("mods");
    }

    /// Returns the directory containing resource packs used by this instance.
    ///
    /// @return the resource pack directory below the run directory
    default Path getResourcePackDirectory() {
        return getRunDirectory().resolve("resourcepacks");
    }

    /// Returns the directory containing saved worlds used by this instance.
    ///
    /// @return the saves directory below the run directory
    default Path getSavesDirectory() {
        return getRunDirectory().resolve("saves");
    }

    /// Returns the directory containing world backups used by this instance.
    ///
    /// @return the backups directory below the run directory
    default Path getBackupsDirectory() {
        return getRunDirectory().resolve("backups");
    }

    /// Returns the directory containing schematics used by this instance.
    ///
    /// @return the schematics directory below the run directory
    default Path getSchematicsDirectory() {
        return getRunDirectory().resolve("schematics");
    }

    /// Returns the directory used for extracted native libraries for a platform.
    ///
    /// @param platform the target platform
    /// @return the platform-specific native directory below the instance root
    default Path getNativeDirectory(Platform platform) {
        return getInstanceRoot().resolve("natives-" + platform);
    }
}
