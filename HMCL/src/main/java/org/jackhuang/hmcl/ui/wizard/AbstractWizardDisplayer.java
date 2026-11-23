// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.control.Label;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.TaskListPane;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.TaskManager;

import java.util.Queue;

public abstract class AbstractWizardDisplayer implements WizardDisplayer {
    private final Queue<Object> cancelQueue;

    public AbstractWizardDisplayer(Queue<Object> cancelQueue) {
        this.cancelQueue = cancelQueue;
    }

    /// Displays task progress and submits the wizard's execution to the serial business-task queue.
    @Override
    public void handleTask(SettingsMap settings, Task<?> task) {
        Task<?> rootTask = task.withRunAsync(Schedulers.javafx(), this::navigateToSuccess);
        TaskListPane pane = new TaskListPane();
        pane.setTask(rootTask);
        navigateTo(pane, Navigation.NavigationDirection.FINISH);
        cancelQueue.add(rootTask);
        TaskManager.getInstance().submit(rootTask, null);
    }

    @Override
    public void onCancel() {
        while (!cancelQueue.isEmpty()) {
            Object x = cancelQueue.poll();
            if (x instanceof Task<?> task) task.cancel();
            else if (x instanceof Thread) ((Thread) x).interrupt();
            else throw new IllegalStateException("Unrecognized cancel queue element: " + x);
        }
    }

    void navigateToSuccess() {
        navigateTo(new Label("Successful"), Navigation.NavigationDirection.FINISH);
    }
}
