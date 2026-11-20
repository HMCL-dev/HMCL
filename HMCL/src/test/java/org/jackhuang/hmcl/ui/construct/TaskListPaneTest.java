// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
// SPDX-FileContributor: ganganjue
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies lazy materialization of task details inside collapsible stages.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskListPaneTest {

    /// Verifies an expanded download stage creates detail rows only on demand.
    @Test
    void downloadStageDetailsAreMaterializedOnlyWhenExpanded() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);

        Task<Void> resourceTask = Task.runAsync("resource.jar", () -> {
                    running.countDown();
                    release.await(10, TimeUnit.SECONDS);
                })
                .setSignificance(Task.TaskSignificance.MAJOR)
                .withStage("hmcl.install.libraries")
                .withStagesHints(new Task.StagesHint("hmcl.install.libraries"));

        TaskExecutor executor = resourceTask.executor();
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                stopped.countDown();
            }
        });

        TaskListPane pane = onFxThread(() -> {
            TaskListPane result = new TaskListPane();
            result.setExecutor(executor);
            return result;
        });

        executor.start();
        try {
            assertTrue(running.await(10, TimeUnit.SECONDS));
            drainFxQueue();

            assertEquals(1, itemCount(pane));

            @Nullable Object stageNode = stageNode(pane, "hmcl.install.libraries");
            assertNotNull(stageNode);

            onFxThread(() -> {
                invokeToggleStage(pane, stageNode);
                return null;
            });
            assertEquals(2, itemCount(pane));

            onFxThread(() -> {
                invokeToggleStage(pane, stageNode);
                return null;
            });
            assertEquals(1, itemCount(pane));
        } finally {
            release.countDown();
            stopped.await(10, TimeUnit.SECONDS);
            drainFxQueue();
        }

        assertEquals(1, itemCount(pane));
    }

    /// Returns the number of rows currently attached to the task list.
    private static int itemCount(TaskListPane pane) throws Exception {
        Field field = TaskListPane.class.getDeclaredField("listView");
        field.setAccessible(true);
        ListView<?> listView = (ListView<?>) field.get(pane);
        return listView.getItems().size();
    }

    /// Returns the stage node registered under the supplied stage name.
    private static @Nullable Object stageNode(TaskListPane pane, String stage) throws Exception {
        Field field = TaskListPane.class.getDeclaredField("stageNodes");
        field.setAccessible(true);
        Map<?, ?> stageNodes = (Map<?, ?>) field.get(pane);
        return stageNodes.get(stage);
    }

    /// Invokes the pane's stage toggle without requiring a rendered list cell.
    private static void invokeToggleStage(TaskListPane pane, Object stageNode) throws Exception {
        Method method = TaskListPane.class.getDeclaredMethod("toggleStage", stageNode.getClass());
        method.setAccessible(true);
        method.invoke(pane, stageNode);
    }

    /// Waits for all JavaFX updates queued before this call to complete.
    private static void drainFxQueue() throws Exception {
        onFxThread(() -> null);
    }

    /// Executes an operation on the JavaFX thread and waits for its result.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        return future.get(30, TimeUnit.SECONDS);
    }
}
