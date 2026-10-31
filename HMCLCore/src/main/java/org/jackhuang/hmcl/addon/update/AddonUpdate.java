// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
