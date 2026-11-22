// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.application.Platform;
import javafx.scene.control.ButtonBase;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies serial business execution with real asynchronous executors and explicit child tasks.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskManagerTest {

    /// Keeps a following task queued until work and blocking stop-listener cleanup both finish.
    @Test
    void waitsForWorkAndCleanupBeforeStartingNext() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        Submission first = submit(Task.runAsync(() -> {
            entered.countDown();
            await(release);
        }), new TaskListener() {
            /// Keeps the execution slot occupied until cleanup is explicitly released.
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
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
            await(entered);
            onFxThread(() -> {
                assertTrue(second.pane().queuedProperty().get());
                assertTrue(third.pane().queuedProperty().get());
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                assertSame(second.completion(), second.pane().startExecutor());
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
        assertTrue(first.completion().get(10, TimeUnit.SECONDS));
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
        Submission second = submit(Task.runAsync(bodies::incrementAndGet), new TaskListener() {
            /// Records cleanup even when cancellation precedes executor start.
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
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
            assertEquals(0, bodies.get());
        } finally {
            release.countDown();
        }
        assertTrue(first.completion().get(10, TimeUnit.SECONDS));
        assertFalse(second.completion().get(10, TimeUnit.SECONDS));
        assertEquals(0, bodies.get());
        assertEquals(1, cleanups.get());
    }

    /// Keeps a cancelled running task's slot until its body actually returns.
    @Test
    void cancellationRequestDoesNotReleaseRunningSlot() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger nextBodies = new AtomicInteger();
        Submission first = submit(Task.runAsync(() -> {
            entered.countDown();
            await(release);
        }), null);
        Submission next = submit(Task.runAsync(nextBodies::incrementAndGet), null);
        try {
            await(entered);
            onFxThread(() -> {
                @Nullable ButtonBase cancel = (ButtonBase) first.pane().lookup(".dialog-cancel");
                assertNotNull(cancel);
                cancel.fire();
                assertTrue(next.pane().queuedProperty().get());
                assertTrue(TaskManager.getInstance().getTasks().contains(first.pane()));
                return true;
            });
            assertEquals(0, nextBodies.get());
            assertFalse(first.completion().isDone());
        } finally {
            release.countDown();
        }
        assertFalse(first.completion().get(10, TimeUnit.SECONDS));
        assertTrue(next.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, nextBodies.get());
    }

    /// Allows a required child to run ahead of queued independent work without deadlocking its parent.
    @Test
    void childSharesParentsSlot() throws Exception {
        CountDownLatch parentEntered = new CountDownLatch(1);
        CountDownLatch childFinished = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        AtomicInteger order = new AtomicInteger();
        Submission parent = submit(Task.runAsync(() -> {
            parentEntered.countDown();
            await(childFinished);
            assertEquals(1, order.getAndIncrement());
        }), null);
        Submission next = submit(Task.runAsync(() -> assertEquals(2, order.getAndIncrement())), null);
        await(parentEntered);
        Submission child = onFxThread(() -> {
            TaskExecutorDialogPane pane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            pane.setExecutor(Task.runAsync(() -> {
                await(releaseChild);
                assertEquals(0, order.getAndIncrement());
                childFinished.countDown();
            }).executor());
            return new Submission(pane, pane.startExecutor(parent.pane()));
        });
        try {
            onFxThread(() -> {
                assertFalse(child.pane().queuedProperty().get());
                assertTrue(next.pane().queuedProperty().get());
                return true;
            });
        } finally {
            releaseChild.countDown();
        }
        assertTrue(child.completion().get(10, TimeUnit.SECONDS));
        assertTrue(parent.completion().get(10, TimeUnit.SECONDS));
        assertTrue(next.completion().get(10, TimeUnit.SECONDS));
        assertEquals(3, order.get());
    }

    /// Keeps a detached parent's child in the slot until that child also finishes.
    @Test
    void detachingDoesNotReleaseExecutionSlot() throws Exception {
        CountDownLatch releaseParent = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);
        AtomicInteger nextBodies = new AtomicInteger();
        Submission parent = submit(Task.runAsync(() -> await(releaseParent)), null);
        Submission child = onFxThread(() -> {
            TaskExecutorDialogPane pane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            pane.setExecutor(Task.runAsync(() -> await(releaseChild)).executor());
            return new Submission(pane, pane.startExecutor(parent.pane()));
        });
        Submission next = submit(Task.runAsync(nextBodies::incrementAndGet), null);
        try {
            onFxThread(() -> {
                parent.pane().setExecutor(null);
                return true;
            });
            releaseParent.countDown();
            assertTrue(parent.completion().get(10, TimeUnit.SECONDS));
            assertFalse(next.completion().isDone());
            assertEquals(0, nextBodies.get());
        } finally {
            releaseParent.countDown();
            releaseChild.countDown();
        }
        assertTrue(child.completion().get(10, TimeUnit.SECONDS));
        assertTrue(next.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, nextBodies.get());
    }

    /// Continues the queue after an execution fails.
    @Test
    void failureReleasesSlot() throws Exception {
        Submission failed = submit(Task.runAsync(() -> {
            throw new java.io.IOException("Expected failure");
        }), null);
        AtomicInteger bodies = new AtomicInteger();
        Submission next = submit(Task.runAsync(bodies::incrementAndGet), null);
        assertFalse(failed.completion().get(10, TimeUnit.SECONDS));
        assertTrue(next.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, bodies.get());
    }

    /// Attaches listeners before submitting an independent task on the JavaFX thread.
    private static Submission submit(Task<?> task, @Nullable TaskListener listener) throws Exception {
        return onFxThread(() -> {
            TaskExecutor executor = task.executor();
            if (listener != null)
                executor.addTaskListener(listener);
            TaskExecutorDialogPane pane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);
            pane.setExecutor(executor);
            return new Submission(pane, pane.startExecutor());
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
    /// @param completion completion after the executor's cleanup listeners
    @NotNullByDefault
    private record Submission(TaskExecutorDialogPane pane, CompletableFuture<Boolean> completion) {
    }
}
