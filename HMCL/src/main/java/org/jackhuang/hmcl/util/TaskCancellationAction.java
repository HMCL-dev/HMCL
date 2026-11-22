// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.ui.construct.TaskHandleDialogPane;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

/// Handles a cancellation request from a task dialog.
@NotNullByDefault
@FunctionalInterface
public interface TaskCancellationAction extends Consumer<TaskHandleDialogPane> {

    /// Disables cancellation from the dialog.
    TaskCancellationAction NO_CANCEL = new TaskCancellationAction() {
        /// {@inheritDoc}
        @Override
        public boolean isEnabled() {
            return false;
        }

        /// {@inheritDoc}
        @Override
        public void accept(TaskHandleDialogPane pane) {
        }
    };

    /// Allows the dialog to cancel the task without a follow-up action.
    TaskCancellationAction NORMAL = ignored -> {
    };

    /// Returns whether the dialog may request cancellation.
    default boolean isEnabled() {
        return true;
    }

}
