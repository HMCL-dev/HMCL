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
import org.jackhuang.hmcl.ui.task.TaskCenter;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;

import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class TaskExecutorDialogWizardDisplayer extends AbstractWizardDisplayer {

    public TaskExecutorDialogWizardDisplayer(Queue<Object> cancelQueue) {
        super(cancelQueue);
    }

    @Override
    public void handleTask(SettingsMap settings, Task<?> task) {
        runInFX(() -> {
            // onEnd() performs wizard teardown + navigation; it must run exactly once across all
            // the paths below (cancel / background / terminal / auto-background).
            AtomicBoolean ended = new AtomicBoolean(false);
            Runnable endOnce = () -> {
                if (ended.compareAndSet(false, true))
                    onEnd();
            };

            String titleText = settings.get("title") instanceof String s ? s : i18n("message.doing");
            boolean backgroundable = Boolean.TRUE.equals(settings.get("backgroundable"));

            if (!backgroundable) {
                handleUnmanaged(settings, task, titleText, endOnce);
                return;
            }

            TaskCenter.TaskKind kind = settings.get("task_kind") instanceof TaskCenter.TaskKind k
                    ? k : TaskCenter.TaskKind.OTHER;
            String taskName = settings.get("task_name") instanceof String n ? n : null;
            String detail = settings.get("task_detail") != null ? settings.get("task_detail").toString() : titleText;

            TaskExecutor executor = task.executor();
            TaskCenter.Entry entry = TaskCenter.getInstance().submit(executor, titleText, detail, kind, taskName);

            // Terminal handling: a foreground completion shows the wizard's rich success/failure
            // message; a background completion shows a lightweight toast. The two are mutually
            // exclusive via foregroundShown, so the user is notified exactly once.
            entry.statusProperty().addListener((obs, old, now) -> {
                if (!now.isTerminal())
                    return;
                if (entry.isForegroundShown())
                    showResult(settings, entry.getExecutor(), endOnce);
                else
                    showBackgroundToast(entry);
            });

            // Auto-background: never show a dialog, just enqueue and leave the wizard.
            if (settings().isAutoBackgroundTask()) {
                entry.setForegroundShown(false);
                Controllers.showToast(i18n("task.auto_background.enqueued", entry.getDisplayText()));
                endOnce.run();
                return;
            }

            presentForeground(settings, entry, titleText, endOnce);
        });
    }

    /// Non-backgroundable task: runs immediately as an unmanaged modal dialog (legacy behaviour).
    private void handleUnmanaged(SettingsMap settings, Task<?> task, String titleText, Runnable endOnce) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(new TaskCancellationAction(it -> {
            it.fireEvent(new DialogCloseEvent());
            endOnce.run();
        }));
        bindTitle(pane, settings, titleText);

        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                runInFX(() -> showResult(settings, executor, endOnce));
            }
        });

        pane.setExecutor(executor);
        Controllers.dialog(pane);
        executor.start();
        pane.refreshTaskList();
    }

    /// Foreground presentation of a managed entry.
    private void presentForeground(SettingsMap settings, TaskCenter.Entry entry, String titleText, Runnable endOnce) {
        TaskExecutorDialogPane pane = new TaskExecutorDialogPane(new TaskCancellationAction(it -> {
            TaskCenter.getInstance().cancel(entry);
            it.fireEvent(new DialogCloseEvent());
            endOnce.run();
        }));
        bindTitle(pane, settings, titleText);
        pane.setExecutor(entry.getExecutor());

        pane.setBackgroundAction(() -> {
            entry.setForegroundShown(false);
            endOnce.run();
            pane.fireEvent(new DialogCloseEvent());
        });

        pane.setWaitingForBackground(entry.getStatus() == TaskCenter.Status.QUEUED);
        entry.statusProperty().addListener((obs, old, now) ->
                pane.setWaitingForBackground(now == TaskCenter.Status.QUEUED));

        entry.setForegroundShown(true);

        // Single close handler: detach the foreground flag and, for download-page installs, return
        // to the download list (fires once per close — background, cancel or completion).
        pane.addEventHandler(DialogCloseEvent.CLOSE, event -> {
            entry.setForegroundShown(false);
            if (Boolean.TRUE.equals(settings.get("return_to_download_list"))) {
                Controllers.getDownloadPage().showGameDownloads();
                Controllers.navigate(Controllers.getDownloadPage());
            }
        });

        Controllers.dialog(pane);
        pane.refreshTaskList();
    }

    private void bindTitle(TaskExecutorDialogPane pane, SettingsMap settings, String fallback) {
        Object title = settings.get("title");
        if (title instanceof StringProperty titleProperty)
            pane.titleProperty().bind(titleProperty);
        else
            pane.setTitle(fallback);
    }

    private void showBackgroundToast(TaskCenter.Entry entry) {
        String text = entry.getDisplayText();
        switch (entry.getStatus()) {
            case SUCCEEDED:
                Controllers.showToast(i18n("task.toast.success", text));
                break;
            case CANCELLED:
                Controllers.showToast(i18n("task.toast.cancelled", text));
                break;
            case FAILED:
                Controllers.showToast(i18n("task.toast.failed", text));
                break;
            default:
                break;
        }
    }

    private void showResult(SettingsMap settings, TaskExecutor executor, Runnable endOnce) {
        Exception exception = executor.getException();
        if (exception == null) {
            if (settings.get("success_message") instanceof String successMessage)
                Controllers.dialog(successMessage, null, MessageType.SUCCESS, endOnce);
            else if (!settings.containsKey("forbid_success_message"))
                Controllers.dialog(i18n("message.success"), null, MessageType.SUCCESS, endOnce);
        } else {
            if (exception instanceof CancellationException) {
                endOnce.run();
                return;
            }

            String appendix = StringUtils.getStackTrace(exception);
            WizardProvider.FailureCallback failureCallback = settings.get(WizardProvider.FailureCallback.KEY);
            if (failureCallback != null)
                failureCallback.onFail(settings, exception, endOnce);
            else if (settings.get("failure_message") instanceof String failureMessage)
                Controllers.dialog(appendix, failureMessage, MessageType.ERROR, endOnce);
            else if (!settings.containsKey("forbid_failure_message"))
                Controllers.dialog(appendix, i18n("wizard.failed"), MessageType.ERROR, endOnce);
        }
    }
}
