// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskName;
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
    /// Structured and literal names can be assigned and cleared through either overload.
    @Test
    public void testCustomTaskName() {
        Task<?> task = Task.completed(42);
        String className = task.getClass().getName();
        assertNull(task.getName());
        String unnamedDescription = task.toString();
        assertFalse(unnamedDescription.isBlank());

        TaskName name = () -> "custom";
        task.setName(name);
        assertSame(name, task.getName());
        assertEquals(className + "[custom]", task.toString());

        task.setName((TaskName) null);
        assertNull(task.getName());
        assertEquals(unnamedDescription, task.toString());

        assertSame(task, task.setName("literal"));
        assertEquals(new TaskName.Simple("literal"), task.getName());
        assertEquals(className + "[literal]", task.toString());

        task.setName((String) null);
        assertNull(task.getName());
        assertEquals(unnamedDescription, task.toString());
    }

    /// A factory captures its caller before the task name is read.
    @Test
    public void testCallSiteTaskName() {
        Task<?> task = createTaskWithCallSiteName();
        TaskName.CallSite name = assertInstanceOf(TaskName.CallSite.class, task.getName());
        StackTraceElement frame = name.frame();
        assertEquals(TaskTest.class.getName(), frame.getClassName());
        assertEquals("createTaskWithCallSiteName", frame.getMethodName());
        assertEquals(TaskTest.class.getName() + ".createTaskWithCallSiteName(TaskTest.java:" + frame.getLineNumber() + ")", name.text());
    }

    /// Creates a task whose factory call can be distinguished from the code reading its name.
    private static Task<Integer> createTaskWithCallSiteName() {
        return Task.supplyAsync(() -> 42);
    }

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
                assertEquals(Task.State.FAILED, failedTask.getState());
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
        assertEquals(Task.State.FAILED, task.getState());
        assertSame(error, task.exceptionNowOrNull());
        assertSame(error, uncaught.get());
    }

    /// Completion observers preserve the source value for downstream continuations, including null.
    @Test
    public void testWhenCompletePreservesResult() throws Exception {
        StringBuilder value = new StringBuilder("result");
        Task<StringBuilder> observed = Task.completed(value)
                .whenComplete(failure -> assertNull(failure))
                .whenComplete(null, failure -> assertNull(failure))
                .whenComplete((result, failure) -> {
                    assertSame(value, result);
                    assertNull(failure);
                    value.append('s');
                })
                .whenComplete(null, (result, failure) -> {
                    assertSame(value, result);
                    assertNull(failure);
                })
                .whenComplete(null, () -> value.append('!'), null)
                .whenComplete(null, result -> assertSame(value, result), null);
        assertEquals("results!", observed.thenApply(StringBuilder::toString).run());
        assertSame(value, observed.resultNow());

        Task<@Nullable String> nullable = Task.<@Nullable String>completed(null).whenComplete(null, (result, failure) -> {
            assertNull(result);
            assertNull(failure);
        });
        assertNull(nullable.run());
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

    /// A handler can transform a success, return null, or recover from a child failure.
    @Test
    public void testHandleOutcome() throws Exception {
        assertEquals("42", Task.completed(42).handle((value, failure) -> {
            assertNull(failure);
            return String.valueOf(value);
        }).run());
        assertEquals("empty", Task.completed(null).handle((value, failure) -> {
            assertNull(value);
            assertNull(failure);
            return "empty";
        }).run());
        assertNull(Task.completed(42).handle((value, failure) -> null).run());

        for (Throwable cause : List.of(new IOException(), new AssertionError(), new CancellationException())) {
            assertEquals("recovered", Task.failed(cause).handle((value, failure) -> {
                assertNull(value);
                assertSame(cause, failure);
                return "recovered";
            }).run());
        }
    }

    /// A throwing handler determines the failure and is never called again with its own exception.
    @Test
    public void testHandleFailure() {
        IOException cause = new IOException("Source failed");
        IOException callbackFailure = new IOException("Handler failed");
        for (Task<Integer> source : List.of(Task.completed(42), Task.<Integer>failed(cause))) {
            AtomicInteger calls = new AtomicInteger();
            Task<?> handled = source.handle((value, failure) -> {
                calls.incrementAndGet();
                throw callbackFailure;
            });
            assertSame(callbackFailure, assertThrows(IOException.class, handled::run));
            assertEquals(1, calls.get());
        }
    }

    /// A handler cannot recover from root cancellation, and a pre-cancelled root skips the handler.
    @Test
    public void testHandleRootCancellation() {
        Task<?> skipped = Task.completed(1).handle((value, failure) -> fail("Unexpected handler"));
        skipped.cancel();
        assertThrows(CancellationException.class, skipped::run);

        Task<Integer> source = Task.completed(1);
        Task<Integer> root = source.handle((value, failure) -> 2);
        root.addTaskListener(new TaskListener() {
            /// Cancels the whole tree while the source is running.
            @Override
            public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
                if (task == source)
                    root.cancel();
            }
        });
        assertThrows(CancellationException.class, root::run);
        assertTrue(source.isCancelled());
        assertTrue(root.isCancelled());
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
