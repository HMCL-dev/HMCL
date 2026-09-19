/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

    interface FailureCallback {
        SettingsMap.Key<FailureCallback> KEY = new SettingsMap.Key<>("failure_callback");

        void onFail(SettingsMap settings, Exception exception, Runnable next);
    }

    /// Releases resources owned by a task displayed by [TaskExecutorDialogWizardDisplayer].
    ///
    /// Cleanup runs on the executor's completion thread after task execution stops, including
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
