// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Identifies a task stage by its key and optional qualifier. Stages with the same key
/// and different qualifiers have distinct identifiers.
///
/// @param key the kind of work, such as `hmcl.install.forge`
/// @param qualifier a value such as a component version, or `null` for an unqualified stage
@NotNullByDefault
public record StageId(String key, @Nullable String qualifier) {
    /// Creates an identifier with the supplied key and qualifier.
    public StageId {
        Objects.requireNonNull(key);
    }

    /// Creates an unqualified stage identifier.
    public StageId(String key) {
        this(key, null);
    }
}
