// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ListView;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.TaskDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies queued task execution and explicit child tasks with asynchronous roots.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskManagerTest {

    /// Queued tasks run in order after the failed task's cleanup finishes.
    @Test
    void waitsForCleanupBeforeContinuingQueueAfterFailure() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        Submission first = submit(Task.runAsync(() -> {
            await(release);
            throw new IOException("Expected failure");
        }), new TaskListener() {
            /// Keeps the execution slot occupied until cleanup is explicitly released.
            @Override
            public void onExecutionFinished(Task<?> task, boolean success) {
                cleanupEntered.countDown();
                try {
                    await(releaseCleanup);
                    assertEquals(0, order.getAndIncrement());
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
        });
        Submission second = submit(Task.runAsync(() -> assertEquals(1, order.getAndIncrement())), null);
        Submission third = submit(Task.runAsync(() -> assertEquals(2, order.getAndIncrement())), null);
        try {
            onFxThread(() -> {
                assertTrue(second.pane().queuedProperty().get());
                assertTrue(third.pane().queuedProperty().get());
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                assertSame(second.completion(), second.pane().startTask());
                return true;
            });
            release.countDown();
            await(cleanupEntered);
            assertEquals(0, order.get());
            assertFalse(second.completion().isDone());
        } finally {
            release.countDown();
            releaseCleanup.countDown();
        }
        assertFalse(first.completion().get(10, TimeUnit.SECONDS));
        assertTrue(second.completion().get(10, TimeUnit.SECONDS));
        assertTrue(third.completion().get(10, TimeUnit.SECONDS));
        assertEquals(3, order.get());
    }

    /// Cancels queued work without running its body, while still delivering cleanup notifications.
    @Test
    void cancelledQueuedTaskDoesNotExecute() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger bodies = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        Submission first = submit(Task.runAsync(() -> await(release)), null);
        Submission second = submit(Task.runAsync(bodies::incrementAndGet).setSignificance(TaskSignificance.MAJOR), new TaskListener() {
            /// Records cleanup even when the task was cancelled before starting.
            @Override
            public void onExecutionFinished(Task<?> task, boolean success) {
                assertFalse(success);
                cleanups.incrementAndGet();
            }
        });
        try {
            onFxThread(() -> {
                assertTrue(second.pane().queuedProperty().get());
                @Nullable ButtonBase cancel = (ButtonBase) second.pane().lookup(".dialog-cancel");
                assertNotNull(cancel);
                assertFalse(cancel.isDisabled());
                cancel.fire();
                assertTrue(cancel.isDisabled());
                assertTrue(TaskManager.getInstance().getTasks().contains(second.pane()));
                return true;
            });
        } finally {
            release.countDown();
        }
        assertTrue(first.completion().get(10, TimeUnit.SECONDS));
        assertFalse(second.completion().get(10, TimeUnit.SECONDS));
        assertEquals(0, bodies.get());
        assertEquals(1, cleanups.get());
        assertTrue(onFxThread(() -> assertInstanceOf(ListView.class, second.pane().lookup(".list-view")).getItems().isEmpty()));
    }

    /// Keeps a cancelled running task's slot until its body actually returns.
    @Test
    void cancellationRequestDoesNotReleaseRunningSlot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<?> running = Task.runAsync(() -> {
            entered.countDown();
            await(release);
        });
        TaskManager manager = TaskManager.getInstance();
        CompletableFuture<Boolean> first = onFxThread(() -> manager.submit(running, null));
        CompletableFuture<Boolean> next = onFxThread(() -> manager.submit(
                Task.runAsync(() -> assertTrue(running.isCancelled())), null));
        try {
            await(entered);
            running.cancel();
            assertFalse(first.isDone());
        } finally {
            release.countDown();
        }
        assertFalse(first.get(10, TimeUnit.SECONDS));
        assertTrue(next.get(10, TimeUnit.SECONDS));
    }

    /// A required child starts ahead of queued jobs and keeps its detached parent's slot until it finishes.
    @Test
    void childRetainsDetachedParentsSlot() throws Exception {
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        Submission parent = submit(Task.runAsync(() -> await(releaseParent)), null);
        Task<?> childTask = Task.runAsync(() -> {
            childStarted.countDown();
            await(releaseChild);
        });
        Submission next = submit(Task.runAsync(() -> assertEquals(Task.State.SUCCEEDED, childTask.getState())), null);
        Submission child = onFxThread(() -> {
            TaskDialogPane pane = new TaskDialogPane(TaskCancellationAction.NORMAL);
            pane.setTask(childTask);
            return new Submission(pane, pane.startTask(parent.pane()));
        });
        try {
            await(childStarted);
            onFxThread(() -> {
                assertTrue(next.pane().queuedProperty().get());
                parent.pane().setTask(null);
                return true;
            });
            releaseParent.countDown();
            assertTrue(parent.completion().get(10, TimeUnit.SECONDS));
            assertFalse(next.completion().isDone());
        } finally {
            releaseParent.countDown();
            releaseChild.countDown();
        }
        assertTrue(child.completion().get(10, TimeUnit.SECONDS));
        assertTrue(next.completion().get(10, TimeUnit.SECONDS));
    }

    /// Rejects reused child and queued root tasks while preserving the parent's exclusive slot.
    @Test
    void rejectedStartsReleaseOnlyTheirOwnSlots() throws Exception {
        CountDownLatch releaseParent = new CountDownLatch(1);
        AtomicInteger bodies = new AtomicInteger();
        Task<?> used = Task.runAsync(bodies::incrementAndGet);
        assertTrue(used.test());
        Task<?> completed = Task.completed(0);
        assertTrue(Task.allOf(completed).test());

        TaskManager manager = TaskManager.getInstance();
        Task<?> parent = Task.runAsync(() -> await(releaseParent));
        CompletableFuture<Boolean> parentResult = onFxThread(() -> manager.submit(parent, null));
        CompletableFuture<Boolean> queued = onFxThread(() -> manager.submit(used, null));
        CompletableFuture<Boolean> next = onFxThread(() -> manager.submit(
                Task.runAsync(bodies::incrementAndGet), null));
        try {
            CompletableFuture<Boolean> child = onFxThread(() -> manager.submit(completed, parent));
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> child.get(10, TimeUnit.SECONDS)).getCause());
            assertFalse(queued.isDone());
            assertFalse(next.isDone());
        } finally {
            releaseParent.countDown();
        }
        assertTrue(parentResult.get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class,
                assertThrows(ExecutionException.class, () -> queued.get(10, TimeUnit.SECONDS)).getCause());
        assertTrue(next.get(10, TimeUnit.SECONDS));
        assertEquals(2, bodies.get());
    }

    /// Rejects an externally running root without making the queue wait for that root to finish.
    @Test
    void rejectsExternallyRunningRootWithoutBlockingNextTask() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<?> external = Task.runAsync(() -> {
            entered.countDown();
            await(release);
        });
        external.start();
        AtomicInteger closes = new AtomicInteger();
        CountDownLatch finished = new CountDownLatch(1);
        try {
            await(entered);
            TaskManager manager = TaskManager.getInstance();
            CompletableFuture<Boolean> rejected = onFxThread(() -> {
                TaskDialogPane pane = new TaskDialogPane(null);
                pane.setTask(external);
                pane.addEventHandler(DialogCloseEvent.CLOSE, event -> closes.incrementAndGet());
                return pane.startTask();
            });
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> rejected.get(10, TimeUnit.SECONDS)).getCause());
            CompletableFuture<Boolean> next = onFxThread(() -> manager.submit(Task.completed(0), null));
            assertTrue(next.get(10, TimeUnit.SECONDS));
            external.addTaskListener(new TaskListener() {
                /// Waits for the externally owned task's last notification to reach the dialog.
                @Override
                public void onExecutionFinished(Task<?> task, boolean success) {
                    finished.countDown();
                }
            });
        } finally {
            release.countDown();
        }
        await(finished);
        assertEquals(1, onFxThread(closes::get).intValue());
    }

    /// Attaches listeners before submitting an independent task on the JavaFX thread.
    private static Submission submit(Task<?> task, @Nullable TaskListener listener) throws Exception {
        return onFxThread(() -> {
            if (listener != null)
                task.addTaskListener(listener);
            TaskDialogPane pane = new TaskDialogPane(TaskCancellationAction.NORMAL);
            pane.setTask(task);
            return new Submission(pane, pane.startTask());
        });
    }

    /// Waits for a controlled phase, failing rather than hanging if scheduling stops progressing.
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    /// Executes an operation on the JavaFX thread and waits for its result.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        return future.get(15, TimeUnit.SECONDS);
    }

    /// Retains a dialog and its queue completion for assertions outside the JavaFX thread.
    ///
    /// @param pane the submitted dialog
    /// @param completion completion after the task's cleanup listeners
    @NotNullByDefault
    private record Submission(TaskDialogPane pane, CompletableFuture<Boolean> completion) {
    }
}
