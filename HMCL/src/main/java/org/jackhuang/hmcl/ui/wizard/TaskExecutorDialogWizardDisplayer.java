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
import org.jackhuang.hmcl.ui.task.TaskCenter;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.util.Queue;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class TaskExecutorDialogWizardDisplayer extends AbstractWizardDisplayer {

    public TaskExecutorDialogWizardDisplayer(Queue<Object> cancelQueue) {
        super(cancelQueue);
    }

    @Override
    public void handleTask(SettingsMap settings, Task<?> task) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(new TaskCancellationAction(it -> {
            it.fireEvent(new DialogCloseEvent());
            onEnd();
        }));

        pane.setTitle(i18n("message.doing"));
        if (settings.containsKey("title")) {
            Object title = settings.get("title");
            if (title instanceof StringProperty titleProperty)
                pane.titleProperty().bind(titleProperty);
            else if (title instanceof String titleMessage)
                pane.setTitle(titleMessage);
        }

        runInFX(() -> {
            boolean backgroundable = Boolean.TRUE.equals(settings.get("backgroundable"));

            // Track whether this task has been moved to background
            final boolean[] movedToBackground = {false};

            TaskExecutor executor = task.executor(new TaskListener() {
                @Override
                public void onStop(boolean success, TaskExecutor executor) {
                    runInFX(() -> {
                        // If task was moved to background, TaskCenter handles notifications
                        if (movedToBackground[0]) return;

                        if (success) {
                            if (settings.get("success_message") instanceof String successMessage)
                                Controllers.dialog(successMessage, null, MessageType.SUCCESS, () -> onEnd());
                            else if (!settings.containsKey("forbid_success_message"))
                                Controllers.dialog(i18n("message.success"), null, MessageType.SUCCESS, () -> onEnd());
                        } else {
                            if (executor.getException() == null)
                                return;

                            if (executor.getException() instanceof CancellationException) {
                                onEnd();
                                return;
                            }

                            String appendix = StringUtils.getStackTrace(executor.getException());
                            if (settings.get(WizardProvider.FailureCallback.KEY) != null)
                                settings.get(WizardProvider.FailureCallback.KEY).onFail(settings, executor.getException(), () -> onEnd());
                            else if (settings.get("failure_message") instanceof String failureMessage)
                                Controllers.dialog(appendix, failureMessage, MessageType.ERROR, () -> onEnd());
                            else if (!settings.containsKey("forbid_failure_message"))
                                Controllers.dialog(appendix, i18n("wizard.failed"), MessageType.ERROR, () -> onEnd());
                        }
                    });
                }
            });

            if (backgroundable) {
                Object detailObj = settings.get("task_detail");
                String detail = detailObj != null ? detailObj.toString() : pane.getTitle();

                TaskCenter.TaskKind kind = (TaskCenter.TaskKind) settings.get("task_kind");
                String taskName = (String) settings.get("task_name");

                if (config().isAutoBackgroundTask()) {
                    // Auto-background: enqueue directly without showing dialog
                    movedToBackground[0] = true;
                    TaskCenter.getInstance().enqueue(executor, pane.getTitle(), detail, kind, taskName);
                    Controllers.showToast(i18n("task.auto_background.enqueued", detail != null ? detail : pane.getTitle()));
                    onEnd();
                    return;
                }

                Runnable moveToBackground = () -> {
                    movedToBackground[0] = true;
                    TaskCenter.getInstance().enqueue(executor, pane.getTitle(), detail, kind, taskName);

                    boolean returnToDownloadList = Boolean.TRUE.equals(settings.get("return_to_download_list"));
                    onEnd();
                    if (returnToDownloadList) {
                        Controllers.getDownloadPage().showGameDownloads();
                        Controllers.navigate(Controllers.getDownloadPage());
                    }

                    pane.fireEvent(new DialogCloseEvent());
                };

                // Manual mode: check if background tasks are running, wait if so
                if (!TaskCenter.getInstance().getEntries().isEmpty()) {
                    pane.setWaitingForBackground(true);
                    TaskCenter.getInstance().getEntries().addListener(
                            (javafx.collections.ListChangeListener<TaskCenter.Entry>) change -> {
                                if (TaskCenter.getInstance().getEntries().isEmpty()) {
                                    pane.setWaitingForBackground(false);
                                    executor.start();
                                    pane.refreshTaskList();
                                }
                            });
                }
                pane.setBackgroundAction(moveToBackground);
            }

            pane.setExecutor(executor);

            pane.addEventHandler(DialogCloseEvent.CLOSE, event -> {
                boolean returnToDownloadList = Boolean.TRUE.equals(settings.get("return_to_download_list"));
                if (returnToDownloadList) {
                    onEnd();
                    Controllers.getDownloadPage().showGameDownloads();
                    Controllers.navigate(Controllers.getDownloadPage());
                }
            });

            Controllers.dialog(pane);

            if (!backgroundable || TaskCenter.getInstance().getEntries().isEmpty()) {
                executor.start();
                pane.refreshTaskList();
            }
        });
    }
}
