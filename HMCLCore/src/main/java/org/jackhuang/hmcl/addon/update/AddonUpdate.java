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
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// An addon that has at least one remote version available for update.
///
/// @param localAddonFile the local addon file that will be replaced
/// @param currentVersion the version currently installed on the local machine
/// @param targetVersion the version that will be installed, which is always one of [#availableVersions()]
/// @param availableVersions all remote versions the addon can be updated to, sorted by publish date in descending order
@NotNullByDefault
public record AddonUpdate(
        LocalAddonFile<?> localAddonFile,
        RemoteAddon.Version currentVersion,
        RemoteAddon.Version targetVersion,
        @Unmodifiable List<RemoteAddon.Version> availableVersions
) {
    /// @throws IllegalArgumentException if [#availableVersions()] is empty or does not contain [#targetVersion()]
    public AddonUpdate {
        availableVersions = List.copyOf(availableVersions);
        if (availableVersions.isEmpty())
            throw new IllegalArgumentException("availableVersions must not be empty");
        if (!availableVersions.contains(targetVersion))
            throw new IllegalArgumentException("targetVersion must be one of availableVersions");
    }

    /// Returns a copy of this update with [#targetVersion()] replaced by `targetVersion`.
    public AddonUpdate withTargetVersion(RemoteAddon.Version targetVersion) {
        return new AddonUpdate(localAddonFile, currentVersion, targetVersion, availableVersions);
    }
}
