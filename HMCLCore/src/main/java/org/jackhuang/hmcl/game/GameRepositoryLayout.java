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

/// Computes repository paths without performing filesystem I/O.
///
/// Implementations must be immutable. Returned paths are derived solely from the layout's base
/// directory and the supplied arguments, so callers may safely share a layout between threads.
@NotNullByDefault
public interface GameRepositoryLayout {
    /// Returns the directory containing the files owned by an instance.
    ///
    /// @param instanceId the instance ID
    /// @return the instance root directory
    Path getInstanceRoot(GameInstanceID instanceId);

    /// Returns the manifest file for an instance.
    ///
    /// @param instanceId the instance ID
    /// @return the path `versions/<id>/<id>.json` below the base directory
    Path getInstanceJson(GameInstanceID instanceId);

    /// Returns the conventional client jar file for an instance.
    ///
    /// @param instanceId the instance ID
    /// @return the path `versions/<id>/<id>.jar` below the base directory
    Path getInstanceJarFile(GameInstanceID instanceId);

    /// Returns the shared libraries directory.
    ///
    /// @return the path `libraries` below the base directory
    Path getLibrariesDirectory();

    /// Returns the file used for a library referenced by an instance.
    ///
    /// Libraries with the `local` hint are resolved below the owning instance's `libraries`
    /// directory. Other libraries are resolved below the shared libraries directory.
    ///
    /// @param owner   the ID of the instance that owns the library reference
    /// @param library the library descriptor
    /// @return the library file path
    Path getLibraryFile(GameInstanceID owner, Library library);

    /// Returns the shared asset directory.
    ///
    /// @return the path `assets` below the base directory
    Path getAssetDirectory();

    /// Returns the file containing an asset index.
    ///
    /// @param assetId the asset index ID
    /// @return the asset index file path
    Path getAssetIndexFile(String assetId);

    /// Returns the content-addressed file for an asset object.
    ///
    /// @param object the asset object descriptor
    /// @return the asset object file path
    Path getAssetObject(AssetObject object);

    /// Returns the file containing a logging configuration object.
    ///
    /// @param assetId     the asset index ID associated with the launch manifest
    /// @param loggingInfo the logging configuration descriptor
    /// @return the logging configuration file path
    Path getLoggingObject(String assetId, LoggingInfo loggingInfo);
}
