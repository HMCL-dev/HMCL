// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskDialogPane;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.ui.DialogUtils.shouldReportCancellation;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a wizard's root task and reports its outcome through the dialog.
@NotNullByDefault
public abstract class TaskDialogWizardDisplayer extends AbstractWizardDisplayer {

    /// Tracks tasks and threads cancelled when this wizard closes.
    public TaskDialogWizardDisplayer(Queue<Object> cancelQueue) {
        super(cancelQueue);
    }

    /// {@inheritDoc}
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
            pane.setTask(task);
            Controllers.dialog(pane);
            pane.startTask().handleAsync((success, rejected) -> {
                showResult(settings, Boolean.TRUE.equals(success),
                        rejected != null ? Lang.resolveException(rejected) : task.exceptionNowOrNull());
                return null;
            }, Schedulers.javafx()).exceptionally(Lang::handleUncaughtException);
        });
    }

    /// Reports the outcome after the progress dialog closes, including rejection before execution.
    private void showResult(SettingsMap settings, boolean success, @Nullable Throwable failure) {
        if (success) {
            if (settings.get("success_message") instanceof String message)
                Controllers.dialog(message, null, MessageType.SUCCESS, this::onEnd);
            else if (!settings.containsKey("forbid_success_message"))
                Controllers.dialog(i18n("message.success"), null, MessageType.SUCCESS, this::onEnd);
            return;
        }

        if (failure == null) {
            onEnd();
            return;
        }
        if (failure instanceof CancellationException cancellation) {
            if (shouldReportCancellation(cancellation))
                Controllers.dialog(StringUtils.getStackTrace(cancellation), i18n("wizard.failed"), MessageType.ERROR, this::onEnd);
            else
                onEnd();
            return;
        }
        @Nullable Throwable cause = failure instanceof OutOfMemoryError ? failure : failure.getCause();
        if (cause instanceof OutOfMemoryError error) {
            try {
                Controllers.dialog(StringUtils.getStackTrace(error), null, MessageType.ERROR, this::onEnd);
            } catch (OutOfMemoryError ignored) {
                onEnd();
            }
            return;
        }

        WizardProvider.@Nullable FailureCallback callback = settings.get(WizardProvider.FailureCallback.KEY);
        if (callback != null)
            callback.onFail(settings, failure, this::onEnd);
        else if (settings.get("failure_message") instanceof String message)
            Controllers.dialog(StringUtils.getStackTrace(failure), message, MessageType.ERROR, this::onEnd);
        else if (!settings.containsKey("forbid_failure_message"))
            Controllers.dialog(StringUtils.getStackTrace(failure), i18n("wizard.failed"), MessageType.ERROR, this::onEnd);
    }
}
