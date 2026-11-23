// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskHandle;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {
    /// A nested task's error reaches the default uncaught exception handler.
    @Test
    public void expectErrorUncaught() {
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> throwable.set(e));
        assertFalse(Task.composeAsync(() -> Task.allOf(
                Task.allOf(Task.runAsync(() -> {
                    throw new Error();
                }))
        )).whenComplete(Assertions::assertNull).test());

        assertInstanceOf(Error.class, throwable.get(), "Error has not been thrown to uncaught exception handler");
    }

    /// Verifies that OOM completes the task lifecycle and still reaches the global handler.
    @Test
    public void testOutOfMemoryErrorLifecycle() {
        OutOfMemoryError normalTaskError = new OutOfMemoryError("normal task");
        assertOutOfMemoryErrorLifecycle(Task.runAsync(() -> {
            throw normalTaskError;
        }), normalTaskError);

        OutOfMemoryError futureTaskError = new OutOfMemoryError("completable future task");
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(futureTaskError);
        assertOutOfMemoryErrorLifecycle(Task.fromCompletableFuture(future), futureTaskError);
    }

    /// Checks the lifecycle events and recorded exceptions for one OOM task.
    private void assertOutOfMemoryErrorLifecycle(Task<?> task, OutOfMemoryError error) {
        AtomicInteger failedCount = new AtomicInteger();
        AtomicReference<@Nullable Throwable> failedThrowable = new AtomicReference<>();
        AtomicInteger stopCount = new AtomicInteger();
        AtomicReference<@Nullable Boolean> stopSuccess = new AtomicReference<>();
        AtomicReference<@Nullable Throwable> uncaught = new AtomicReference<>();

        TaskHandle handle = task.newHandle(new TaskListener() {
            /// Records the original error after the task has published its checked failure.
            @Override
            public void onTaskFinished(Task<?> failedTask, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                assertSame(task, failedTask);
                assertEquals(Task.TaskState.FAILED, failedTask.getState());
                assertSame(error, failedTask.exceptionNow().getCause());
                failedCount.incrementAndGet();
                failedThrowable.set(failure);
            }

            /// Records the final outcome reported by the handle.
            @Override
            public void onExecutionFinished(TaskHandle handle, boolean success) {
                stopCount.incrementAndGet();
                stopSuccess.set(success);
            }
        });

        @Nullable Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.set(throwable));
            assertFalse(handle.test());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }

        assertEquals(1, failedCount.get());
        assertSame(error, failedThrowable.get());
        assertEquals(1, stopCount.get());
        assertEquals(Boolean.FALSE, stopSuccess.get());
        assertEquals(Task.TaskState.FAILED, task.getState());
        assertSame(error, task.exceptionNow().getCause());
        assertNotNull(handle.getException());
        assertSame(error, handle.getException().getCause());
        assertSame(error, uncaught.get());
    }

    /**
     *
     */
    @Test
    public void testWhenComplete() {
        boolean result = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).whenComplete(exception -> {
            assertInstanceOf(IllegalStateException.class, exception);
        }).test();

        assertFalse(result, "Task should fail at this case");
    }

    @Test
    public void testWithCompose() {
        AtomicBoolean bool = new AtomicBoolean();
        boolean success = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).withRunAsync(() -> {
            bool.set(true);
        }).test();

        assertTrue(success, "Task should success because withRunAsync will ignore previous exception");
        assertTrue(bool.get(), "withRunAsync should be executed");
    }

    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testThenAccept() {
        AtomicBoolean flag = new AtomicBoolean();
        Object obj = new Object();
        boolean result = Task.supplyAsync(() -> obj)
                .thenAcceptAsync(Schedulers.io(), o -> {
                    flag.set(true);
                    assertSame(obj, o);
                })
                .test();

        assertTrue(result, "Task does not succeed");
        assertTrue(flag.get(), "ThenAccept has not been executed");
    }

    /// Verifies that cancellation lets a running task finish but prevents its continuation from running.
    @Test
    public void testCancellation() throws InterruptedException, ExecutionException, TimeoutException {
        AtomicBoolean taskCompleted = new AtomicBoolean();
        AtomicBoolean continuationExecuted = new AtomicBoolean();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CompletableFuture<Boolean> stopped = new CompletableFuture<>();
        Task<?> task = Task.runAsync(() -> {
            taskStarted.countDown();
            releaseTask.await();
            taskCompleted.set(true);
        }).thenRunAsync(() -> continuationExecuted.set(true));
        TaskHandle handle = task.newHandle(new TaskListener() {
            /// Publishes the execution result to the waiting test thread.
            @Override
            public void onExecutionFinished(TaskHandle handle, boolean success) {
                stopped.complete(success);
            }
        });
        try {
            handle.start();
            assertTrue(taskStarted.await(30, TimeUnit.SECONDS), "Task should start before cancellation");
            handle.cancel();
        } finally {
            // Keep the task running until cancellation is requested, and release it even if setup fails.
            releaseTask.countDown();
        }

        assertFalse(stopped.get(30, TimeUnit.SECONDS), "Task should fail because we have cancelled it");
        assertInstanceOf(CancellationException.class, handle.getException(), "CancellationException should be recorded");
        assertInstanceOf(CancellationException.class, task.exceptionNow(), "CancellationException should be recorded");
        assertTrue(taskCompleted.get(), "Cancellation should not interrupt the running task");
        assertFalse(continuationExecuted.get(), "Continuation should not run after cancellation");
    }

    @Test
    public void testCompletableFutureCancellation() throws Throwable {
        AtomicBoolean flag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
            latch.countDown();
            System.out.println("Sleep");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // default executor cannot interrupt task.
            flag.getAndSet(true);
            System.out.println("End");
        }).thenComposeAsync(non -> {
            System.out.println("compose");
            return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
                System.out.println("No way!");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                fail("Cannot reach here");
            }));
        });
        Lang.thread(() -> {
            try {
                latch.await();
                System.out.println("Main thread start waiting");
                Thread.sleep(100);
                System.out.println("Cancel");
                task.cancel(true);
            } catch (InterruptedException e) {
                fail(e);
            }
        });
        System.out.println("Start");
        try {
            task.get();
        } catch (CancellationException e) {
            System.out.println("Successfully cancelled");
        }
        //Assert.assertFalse("Task should fail because we have cancelled it", );
        Thread.sleep(4000);
        //Assert.assertNull("CancellationException should not be recorded.", executor.getException());
        //Assert.assertTrue("Thread.sleep cannot be interrupted", flag.get());
    }
}
