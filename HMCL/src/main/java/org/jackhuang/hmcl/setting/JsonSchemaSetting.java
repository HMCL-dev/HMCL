// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jetbrains.annotations.NotNullByDefault;

/// Marks a detached settings object that stores its own JSON schema URL.
///
/// @author Glavo
@NotNullByDefault
interface JsonSchemaSetting {
    /// Returns the schema used by this JSON settings file.
    JsonSchema getSchema();

    /// Sets the schema used by this JSON settings file.
    ///
    /// @param schema the schema to store
    void setSchema(JsonSchema schema);

    /// Returns whether this settings object may be saved back to its JSON file.
    boolean isSavable();

    /// Sets whether this settings object may be saved back to its JSON file.
    ///
    /// @param savable whether this settings object may be saved
    void setSavable(boolean savable);

    /// Returns whether the next successful save should back up the current on-disk file first.
    boolean isBackupOnNextSave();

    /// Sets whether the next successful save should back up the current on-disk file first.
    ///
    /// @param backupOnNextSave whether the next successful save should back up the current on-disk file first
    void setBackupOnNextSave(boolean backupOnNextSave);
}
