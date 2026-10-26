// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A modpack file entry that may be marked optional by the pack author.
@NotNullByDefault
public interface ModpackFile {

    /// Returns a stable identity for this file used when persisting exclusion choices.
    ///
    /// @return the exclusion key
    String key();

    /// Returns the file name, or `null` when it has not been resolved yet.
    ///
    /// @return the file name, or `null`
    @Nullable
    String fileName();

    /// Returns whether this file is optional on the client side.
    ///
    /// @return `true` when the client may skip this file
    boolean optional();

    /// Returns the path of the file relative to the instance run directory, or `null` when unknown.
    ///
    /// @return the relative path, or `null`
    @Nullable
    String path();

    /// Returns the remote addon metadata for this file when [#addonQueried()] is `true`.
    ///
    /// @return the remote addon, or `null` when not found or not yet queried
    @Nullable
    RemoteAddon remoteAddon();

    /// Returns whether remote addon metadata has been queried for this file.
    ///
    /// When `false`, [#remoteAddon()] is unset and a query has not been attempted yet.
    /// When `true`, [#remoteAddon()] is the queried result (`null` means not found).
    ///
    /// @return `true` when remote addon metadata has been queried
    boolean addonQueried();
}
