// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

@NotNullByDefault
@FunctionalInterface
public interface TaskCancellationAction extends Consumer<TaskExecutorDialogPane> {

    TaskCancellationAction NO_CANCEL = new TaskCancellationAction() {
        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void accept(TaskExecutorDialogPane taskExecutorDialogPane) {
        }
    };

    TaskCancellationAction NORMAL = ignored -> {
    };

    default boolean isEnabled() {
        return true;
    }

}
