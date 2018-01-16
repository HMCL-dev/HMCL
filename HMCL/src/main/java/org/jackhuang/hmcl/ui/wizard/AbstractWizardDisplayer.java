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
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public interface AbstractWizardDisplayer extends WizardDisplayer {
    WizardController getWizardController();

    Queue<Object> getCancelQueue();

    @Override
    default void handleDeferredWizardResult(Map<String, Object> settings, DeferredWizardResult deferredWizardResult) {
        VBox vbox = new VBox();
        JFXProgressBar progressBar = new JFXProgressBar();
        Label label = new Label();
        progressBar.setMaxHeight(10);
        vbox.getChildren().addAll(progressBar, label);

        StackPane root = new StackPane();
        root.getChildren().add(vbox);
        navigateTo(root, Navigation.NavigationDirection.FINISH);

        getCancelQueue().add(Lang.thread(() -> {
            deferredWizardResult.start(settings, new ResultProgressHandle() {
                private boolean running = true;

                @Override
                public void setProgress(int currentStep, int totalSteps) {
                    progressBar.setProgress(1.0 * currentStep / totalSteps);
                }

                @Override
                public void setProgress(String description, int currentStep, int totalSteps) {
                    label.setText(description);
                    progressBar.setProgress(1.0 * currentStep / totalSteps);
                }

                @Override
                public void setBusy(String description) {
                    progressBar.setProgress(JFXProgressBar.INDETERMINATE_PROGRESS);
                }

                @Override
                public void finished(Object result) {
                    running = false;
                }

                @Override
                public void failed(String message, boolean canNavigateBack) {
                    running = false;
                }

                @Override
                public boolean isRunning() {
                    return running;
                }
            });

            Platform.runLater(this::navigateToSuccess);
        }));
    }

    @Override
    default void handleTask(Map<String, Object> settings, Task task) {
        VBox vbox = new VBox();
        JFXProgressBar progressBar = new JFXProgressBar();
        Label label = new Label();
        progressBar.setMaxHeight(10);
        vbox.getChildren().addAll(progressBar, label);

        StackPane root = new StackPane();
        root.getChildren().add(vbox);
        navigateTo(root, Navigation.NavigationDirection.FINISH);

        AtomicInteger finishedTasks = new AtomicInteger(0);

        TaskExecutor executor = task.with(Task.of(Schedulers.javafx(), this::navigateToSuccess)).executor(e -> new TaskListener() {
            @Override
            public void onReady(Task task) {
                Platform.runLater(() -> progressBar.setProgress(finishedTasks.get() * 1.0 / e.getRunningTasks()));
            }

            @Override
            public void onFinished(Task task) {
                Platform.runLater(() -> {
                    label.setText(task.getName());
                    progressBar.setProgress(finishedTasks.incrementAndGet() * 1.0 / e.getRunningTasks());
                });
            }

            @Override
            public void onFailed(Task task, Throwable throwable) {
                Platform.runLater(() -> {
                    label.setText(task.getName());
                    progressBar.setProgress(finishedTasks.incrementAndGet() * 1.0 / e.getRunningTasks());
                });
            }

            @Override
            public void onTerminate() {
                Platform.runLater(AbstractWizardDisplayer.this::navigateToSuccess);
            }
        });
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
