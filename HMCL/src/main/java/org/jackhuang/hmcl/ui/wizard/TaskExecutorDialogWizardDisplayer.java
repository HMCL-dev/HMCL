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

import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public interface TaskExecutorDialogWizardDisplayer extends AbstractWizardDisplayer {

    @Override
    default void handleTask(Map<String, Object> settings, Task<?> task) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(it -> {
            it.fireEvent(new DialogCloseEvent());
            onEnd();
        });

        pane.setTitle(i18n("message.doing"));
        pane.setProgress(Double.MAX_VALUE);
        if (settings.containsKey("title")) {
            Object title = settings.get("title");
            if (title instanceof StringProperty)
                pane.titleProperty().bind((StringProperty) title);
            else if (title instanceof String)
                pane.setTitle((String) title);
        }

        if (settings.containsKey("subtitle")) {
            Object subtitle = settings.get("subtitle");
            if (subtitle instanceof StringProperty)
                pane.subtitleProperty().bind((StringProperty) subtitle);
            else if (subtitle instanceof String)
                pane.setSubtitle((String) subtitle);
        }

        runInFX(() -> {
            TaskExecutor executor = task.executor(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    runInFX(() -> {
                        if (success) {
                            if (settings.containsKey("success_message") && settings.get("success_message") instanceof String)
                                Controllers.dialog((String) settings.get("success_message"), null, MessageType.FINE, () -> onEnd());
                            else if (!settings.containsKey("forbid_success_message"))
                                Controllers.dialog(i18n("message.success"), null, MessageType.FINE, () -> onEnd());
                        } else {
                            if (executor.getException() == null)
                                return;
                            String appendix = StringUtils.getStackTrace(executor.getException());
                            if (settings.get("failure_callback") instanceof WizardProvider.FailureCallback)
                                ((WizardProvider.FailureCallback)settings.get("failure_callback")).onFail(settings, executor.getException(), () -> onEnd());
                            else if (settings.get("failure_message") instanceof String)
                                Controllers.dialog(appendix, (String) settings.get("failure_message"), MessageType.ERROR, () -> onEnd());
                            else if (!settings.containsKey("forbid_failure_message"))
                                Controllers.dialog(appendix, i18n("wizard.failed"), MessageType.ERROR, () -> onEnd());
                        }

                    });
                }
            });
            pane.setExecutor(executor);
            Controllers.dialog(pane);
            executor.start();
        });
    }
}
