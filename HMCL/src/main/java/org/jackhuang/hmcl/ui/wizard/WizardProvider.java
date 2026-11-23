// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.Node;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.NotNullByDefault;

public interface WizardProvider {

    void start(SettingsMap settings);

    Object finish(SettingsMap settings);

    Node createPage(WizardController controller, int step, SettingsMap settings);

    boolean cancel();

    default boolean cancelIfCannotGoBack() {
        return false;
    }

    /// Reports a failed wizard task and continues after the failure is acknowledged.
    interface FailureCallback {
        /// Optional callback consumed by the wizard's task dialog.
        SettingsMap.Key<FailureCallback> KEY = new SettingsMap.Key<>("failure_callback");

        /// Reports the task failure and invokes `next` when finished.
        void onFail(SettingsMap settings, Throwable exception, Runnable next);
    }

    /// Releases resources owned by a task displayed by [TaskDialogWizardDisplayer].
    ///
    /// Cleanup runs on the root task's completion thread after execution stops, including
    /// cancellation before the first task starts, and before the dialog reports its outcome.
    @NotNullByDefault
    @FunctionalInterface
    interface TaskCleanup {
        /// Optional cleanup consumed by the task dialog when it accepts the task.
        SettingsMap.Key<TaskCleanup> KEY = new SettingsMap.Key<>("task_cleanup");

        /// Releases the completed task's resources.
        ///
        /// @throws Exception if cleanup fails; the task dialog reports the failure
        void cleanup() throws Exception;
    }
}
