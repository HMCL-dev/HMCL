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
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

/// Provides an immutable view of a game instance and its instance-specific paths.
///
/// Core repository implementations replace instances as complete values when repository state
/// changes. Callers that need a long-lived identity must use a higher-level implementation that
/// explicitly provides that guarantee.
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

    /// Returns the directory containing files owned by this instance.
    ///
    /// @return the instance root directory
    Path getInstanceRoot();

    /// Returns the primary client jar selected by the resolved launch manifest.
    ///
    /// @return the primary client jar path
    Path getInstanceJarFile();

    /// Returns the working directory used to run this instance.
    ///
    /// @return the run directory
    Path getRunDirectory();

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
