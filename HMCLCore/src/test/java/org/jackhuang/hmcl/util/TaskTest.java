// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {
    /// A nested task's error reaches both its completion callback and the default uncaught exception handler.
    @Test
    public void expectErrorUncaught() {
        Error error = new Error();
        AtomicReference<@Nullable Throwable> observed = new AtomicReference<>();
        AtomicReference<@Nullable Throwable> uncaught = new AtomicReference<>();
        Thread.@Nullable UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.set(failure));
            assertFalse(Task.composeAsync(() -> Task.allOf(Task.allOf(Task.failed(error))))
                    .whenComplete(observed::set).test());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        assertSame(error, observed.get());
        assertSame(error, uncaught.get());
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

        task.addTaskListener(new TaskListener() {
            /// Records the original error after the task has published its failure.
            @Override
            public void onTaskFinished(Task<?> failedTask, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                assertSame(task, failedTask);
                assertEquals(Task.TaskState.FAILED, failedTask.getState());
                assertSame(error, failedTask.exceptionNow());
                failedCount.incrementAndGet();
                failedThrowable.set(failure);
            }

            /// Records the final outcome of the root task.
            @Override
            public void onExecutionFinished(Task<?> root, boolean success) {
                stopCount.incrementAndGet();
                stopSuccess.set(success);
            }
        });

        @Nullable Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> uncaught.set(throwable));
            assertFalse(task.test());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        }

        assertEquals(1, failedCount.get());
        assertSame(error, failedThrowable.get());
        assertEquals(1, stopCount.get());
        assertEquals(Boolean.FALSE, stopSuccess.get());
        assertEquals(Task.TaskState.FAILED, task.getState());
        assertSame(error, task.exceptionNowOrNull());
        assertSame(error, uncaught.get());
    }

    /// A callback failure is suppressed when the source failed and propagates when the source succeeded.
    @Test
    public void testWhenComplete() {
        AssertionError source = new AssertionError("source");
        IOException callback = new IOException("callback");
        Task<?> observed = Task.failed(source).whenComplete(null, (value, failure) -> {
            assertNull(value);
            assertSame(source, failure);
            throw callback;
        });
        assertSame(source, assertThrows(AssertionError.class, observed::run));
        assertArrayEquals(new Throwable[]{callback}, source.getSuppressed());

        IOException sourceException = new IOException("source");
        AssertionError callbackError = new AssertionError("callback");
        Task<?> failedCleanup = Task.failed(sourceException).whenComplete(failure -> { throw callbackError; });
        assertSame(sourceException, assertThrows(IOException.class, failedCleanup::run));
        assertArrayEquals(new Throwable[]{callbackError}, sourceException.getSuppressed());

        Task<?> rethrown = Task.failed(callback).whenComplete(failure -> { throw callback; });
        assertSame(callback, assertThrows(IOException.class, rethrown::run));
        assertEquals(0, callback.getSuppressed().length);

        Task<?> successfulSource = Task.completed(1).whenComplete(null, (value, failure) -> {
            assertEquals(1, value);
            assertNull(failure);
            throw callback;
        });
        assertSame(callback, assertThrows(IOException.class, successfulSource::run));

        AtomicReference<@Nullable Throwable> observedFailure = new AtomicReference<>();
        AssertionError successCallback = new AssertionError("success callback");
        Task<?> paired = Task.completed(1).whenComplete(null, value -> {
            throw successCallback;
        }, observedFailure::set);
        assertSame(successCallback, assertThrows(AssertionError.class, paired::run));
        assertSame(successCallback, observedFailure.get());
    }

    /// Wrapping a failed task preserves the original throwable.
    @Test
    public void testWrapResultPreservesFailure() throws Exception {
        for (Throwable source : List.of(new IOException(), new AssertionError(), new CancellationException(), new Throwable())) {
            assertSame(source, Task.failed(source).wrapResult().run().getException());
        }
    }

    /// Implicit continuations run after ordinary exceptions but propagate cancellation and errors.
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
        for (Throwable source : List.of(new CancellationException(), new AssertionError())) {
            Task<?> run = Task.failed(source).withRunAsync(() -> fail("Unexpected continuation"));
            Task<?> compose = Task.failed(source).withComposeAsync(() -> fail("Unexpected continuation"));
            assertSame(source, assertThrows(source.getClass(), run::run));
            assertSame(source, assertThrows(source.getClass(), compose::run));
        }
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
        task.addTaskListener(new TaskListener() {
            /// Publishes the execution result to the waiting test thread.
            @Override
            public void onExecutionFinished(Task<?> root, boolean success) {
                stopped.complete(success);
            }
        });
        try {
            task.start();
            assertTrue(taskStarted.await(30, TimeUnit.SECONDS), "Task should start before cancellation");
            task.cancel();
        } finally {
            // Keep the task running until cancellation is requested, and release it even if setup fails.
            releaseTask.countDown();
        }

        assertFalse(stopped.get(30, TimeUnit.SECONDS), "Task should fail because we have cancelled it");
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
