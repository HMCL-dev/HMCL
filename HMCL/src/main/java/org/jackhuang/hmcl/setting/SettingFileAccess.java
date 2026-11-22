// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes whether a settings file can be read and safely overwritten by this HMCL version.
@NotNullByDefault
public enum SettingFileAccess {
    /// The file can be read and overwritten safely.
    READ_WRITE,

    /// The file can be read but must not be overwritten automatically.
    READ_ONLY,

    /// The file cannot be read safely and must not be overwritten automatically.
    UNREADABLE;

    /// Returns whether this access mode permits saving the file.
    public boolean canSave() {
        return this == READ_WRITE;
    }

    /// Returns whether this access mode blocks normal editing of the owning feature.
    public boolean blocksEditing() {
        return this != READ_WRITE;
    }
}
