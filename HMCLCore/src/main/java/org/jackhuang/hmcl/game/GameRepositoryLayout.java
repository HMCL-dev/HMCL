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
/// The methods on this interface describe path concepts that are common across repository
/// layouts used by Minecraft launchers, including the official/vanilla layout and MultiMC-family
/// layouts: a repository base directory, per-instance roots, shared libraries, and shared assets.
///
/// Layout-specific storage for instance definitions (for example official `versions/<id>/<id>.json`
/// files, or MultiMC `mmc-pack.json` / `patches/`) is not part of this interface.
///
/// Implementations must be immutable. Returned paths are derived solely from the layout's base
/// directory and the supplied arguments, so callers may safely share a layout between threads.
@NotNullByDefault
public interface GameRepositoryLayout {
    /// Returns the repository base directory.
    ///
    /// Shared libraries, assets, and layout-specific instance storage are resolved relative to this
    /// directory unless a method documents otherwise.
    ///
    /// @return the repository base directory
    Path getBaseDirectory();

    /// Returns the directory containing the files owned by an instance.
    ///
    /// This is the instance's private storage root (for example official `versions/<id>/`, or a
    /// MultiMC `instances/<name>/` directory). It is not necessarily the launch working directory.
    ///
    /// @param instanceId the instance ID
    /// @return the instance root directory
    Path getInstanceRoot(GameInstanceID instanceId);

    /// Returns the shared libraries directory.
    ///
    /// @return the libraries directory below the base directory
    Path getLibrariesDirectory();

    /// Returns the shared library file for a Maven artifact coordinate.
    ///
    /// Unlike [#getLibraryFile], this always resolves under [#getLibrariesDirectory] and does not
    /// consult instance-local library storage.
    ///
    /// @param artifact the Maven artifact coordinate
    /// @return the artifact file path below the shared libraries directory
    default Path getArtifactFile(Artifact artifact) {
        return artifact.getPath(getLibrariesDirectory());
    }

    /// Returns the file used for a library referenced by an instance.
    ///
    /// Libraries with the `local` hint are resolved below the owning instance's private libraries
    /// storage. Other libraries are resolved below the shared libraries directory.
    ///
    /// @param owner   the ID of the instance that owns the library reference
    /// @param library the library descriptor
    /// @return the library file path
    Path getLibraryFile(GameInstanceID owner, Library library);

    /// Returns the shared asset directory.
    ///
    /// @return the assets directory below the base directory
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
