// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// A stage value identified by its key and optional qualifier.
/// Each task declaring the stage opens a distinct [TaskStageInstance].
///
/// @param key the kind of work, such as `hmcl.install.forge`
/// @param qualifier a value such as a component version, or `null` for an unqualified stage
@NotNullByDefault
public record TaskStage(String key, @Nullable String qualifier) {
    /// Creates a stage with the supplied key and qualifier.
    public TaskStage {
        Objects.requireNonNull(key);
    }

    /// Creates an unqualified stage.
    public TaskStage(String key) {
        this(key, null);
    }
}
