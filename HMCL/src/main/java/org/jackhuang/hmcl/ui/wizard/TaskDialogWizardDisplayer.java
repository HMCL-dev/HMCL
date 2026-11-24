// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskDialogPane;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a wizard's root task and reports its outcome through the dialog.
@NotNullByDefault
public abstract class TaskDialogWizardDisplayer extends AbstractWizardDisplayer {

    /// Tracks tasks and threads cancelled when this wizard closes.
    public TaskDialogWizardDisplayer(Queue<Object> cancelQueue) {
        super(cancelQueue);
    }

    /// Displays a task and reports its outcome after execution stops.
    @Override
    public void handleTask(SettingsMap settings, Task<?> task) {
        boolean waitForCancellation = Boolean.TRUE.equals(settings.remove(WizardProvider.WAIT_FOR_CANCELLATION));
        TaskDialogPane pane = new TaskDialogPane(it -> {
            if (waitForCancellation) {
                it.setCancel(null);
                return;
            }
            it.fireEvent(new DialogCloseEvent());
            onEnd();
        });
        pane.setOnBackground(this::onEnd);

        pane.setTitle(i18n("message.doing"));
        if (settings.containsKey("title")) {
            @Nullable Object title = settings.get("title");
            if (title instanceof StringProperty titleProperty)
                pane.titleProperty().bind(titleProperty);
            else if (title instanceof String titleMessage)
                pane.setTitle(titleMessage);
        }

        runInFX(() -> {
            task.addTaskListener(new TaskListener() {
                /// Reports the outcome on the JavaFX thread.
                @Override
                public void onExecutionFinished(Task<?> rootTask, boolean success) {
                    @Nullable Throwable completedFailure = rootTask.exceptionNowOrNull();
                    runInFX(() -> {
                        if (success) {
                            if (settings.get("success_message") instanceof String successMessage)
                                Controllers.dialog(successMessage, null, MessageType.SUCCESS, () -> onEnd());
                            else if (!settings.containsKey("forbid_success_message"))
                                Controllers.dialog(i18n("message.success"), null, MessageType.SUCCESS, () -> onEnd());
                        } else {
                            if (completedFailure == null) {
                                onEnd();
                                return;
                            }

                            if (completedFailure instanceof CancellationException) {
                                @Nullable Throwable cause = completedFailure.getCause();
                                if (completedFailure.getSuppressed().length > 0
                                        || cause instanceof InterruptedException && cause.getSuppressed().length > 0) {
                                    Controllers.dialog(StringUtils.getStackTrace(completedFailure),
                                            i18n("wizard.failed"), MessageType.ERROR, () -> onEnd());
                                } else {
                                    onEnd();
                                }
                                return;
                            }

                            @Nullable Throwable cause = completedFailure instanceof OutOfMemoryError
                                    ? completedFailure : completedFailure.getCause();
                            if (cause instanceof OutOfMemoryError outOfMemoryError) {
                                try {
                                    Controllers.dialog(StringUtils.getStackTrace(outOfMemoryError), null, MessageType.ERROR, () -> onEnd());
                                } catch (OutOfMemoryError ignored) {
                                    onEnd();
                                }
                                return;
                            }

                            String appendix = StringUtils.getStackTrace(completedFailure);
                            if (settings.get(WizardProvider.FailureCallback.KEY) != null)
                                settings.get(WizardProvider.FailureCallback.KEY).onFail(settings, completedFailure, () -> onEnd());
                            else if (settings.get("failure_message") instanceof String failureMessage)
                                Controllers.dialog(appendix, failureMessage, MessageType.ERROR, () -> onEnd());
                            else if (!settings.containsKey("forbid_failure_message"))
                                Controllers.dialog(appendix, i18n("wizard.failed"), MessageType.ERROR, () -> onEnd());
                        }

                    });
                }
            });
            pane.setTask(task);
            Controllers.dialog(pane);
            pane.startTask();
        });
    }
}
