// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// A stage value identified by its key and optional qualifier.
/// Each task declaring the stage opens a distinct [Scope].
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

    /// Declares one row in the stage progress display. Each stage keeps its first
    /// assigned row for the entire execution. To group stages across children, declare the
    /// hint on their common parent before it dispatches them.
    ///
    /// @param stage the stage used to find or create the row
    /// @param groupedStages additional stages to display in the same row
    public record Hint(TaskStage stage, @Unmodifiable List<TaskStage> groupedStages) {
        /// Creates a hint with an immutable snapshot of the grouped stages.
        public Hint {
            Objects.requireNonNull(stage);
            groupedStages = List.copyOf(groupedStages);
        }

        /// Creates a hint for a single stage.
        public Hint(TaskStage stage) {
            this(stage, List.of());
        }
    }

    /// A stage scope opened by one task. Children inherit it until they declare a stage of their own.
    public static final class Scope {
        /// The stage declared by the task.
        private final TaskStage stage;

        /// The task whose outcome determines the outcome of this scope.
        private final Task<?> task;

        /// The enclosing scope, or `null` at the top level.
        private final @Nullable Scope parent;

        /// Opens a scope for the stage declared by the task.
        Scope(TaskStage stage, Task<?> task, @Nullable Scope parent) {
            this.stage = stage;
            this.task = task;
            this.parent = parent;
        }

        /// Returns the stage declared by [#task()].
        public TaskStage stage() {
            return stage;
        }

        /// Returns the task that opened this scope.
        public Task<?> task() {
            return task;
        }

        /// Returns the enclosing scope, or `null` at the top level.
        public @Nullable Scope parent() {
            return parent;
        }
    }
}
