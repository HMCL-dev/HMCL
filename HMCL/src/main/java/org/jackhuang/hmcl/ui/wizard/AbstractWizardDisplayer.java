/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.wizard;

import com.jfoenix.controls.JFXProgressBar;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.construct.TaskListPane;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public interface AbstractWizardDisplayer extends WizardDisplayer {
    WizardController getWizardController();

    Queue<Object> getCancelQueue();

    @Override
    default void handleTask(Map<String, Object> settings, Task task) {
        TaskExecutor executor = task.with(Task.of(Schedulers.javafx(), this::navigateToSuccess)).executor();
        TaskListPane pane = new TaskListPane();
        pane.setExecutor(executor);
        navigateTo(pane, Navigation.NavigationDirection.FINISH);
        getCancelQueue().add(executor);
        executor.start();
    }

    @Override
    default void onCancel() {
        while (!getCancelQueue().isEmpty()) {
            Object x = getCancelQueue().poll();
            if (x instanceof TaskExecutor) ((TaskExecutor) x).cancel();
            else if (x instanceof Thread) ((Thread) x).interrupt();
            else throw new IllegalStateException("Unrecognized cancel queue element: " + x);
        }
    }

    default void navigateToSuccess() {
        navigateTo(new Label("Successful"), Navigation.NavigationDirection.FINISH);
    }
}
