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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Tests task names, continuations, and failure reporting.
public class TaskTest {
    /// Structured and literal names can be assigned and cleared through either overload.
    @Test
    public void testCustomTaskName() {
        Task<?> task = Task.completed(42);
        String className = task.getClass().getName();
        assertNull(task.getName());
        String unnamedDescription = task.toString();

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

    /// A fatal failure from an adapted future reaches the root, its observers, and the uncaught handler.
    @Test
    public void reportsFatalChildFailure() {
        OutOfMemoryError error = new OutOfMemoryError("Child failed");
        Task<?> child = Task.fromCompletableFuture(CompletableFuture.failedFuture(error));
        List<@Nullable Throwable> observed = new ArrayList<>();
        Task<?> root = Task.allOf(child).whenComplete((result, failure) -> observed.add(failure));
        List<Boolean> finished = new ArrayList<>();
        root.addTaskListener(new TaskListener() {
            /// Records the root outcome delivered to listeners.
            @Override
            public void onExecutionFinished(Task<?> task, boolean success) {
                finished.add(success);
            }
        });

        AtomicReference<@Nullable Throwable> uncaught = new AtomicReference<>();
        Thread.@Nullable UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        try {
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught.set(failure));
            assertFalse(root.test());
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
        assertEquals(List.of(error), observed);
        assertEquals(List.of(false), finished);
        assertSame(error, root.exceptionNow());
        assertSame(error, uncaught.get());
    }

    /// Completion observers preserve the source value for downstream continuations, including null.
    @Test
    public void testWhenCompletePreservesResult() throws Exception {
        StringBuilder value = new StringBuilder("result");
        Task<StringBuilder> observed = Task.completed(value).whenComplete((result, failure) -> {
            assertSame(value, result);
            assertNull(failure);
            value.append('!');
        });
        assertEquals("result!", observed.thenApply(StringBuilder::toString).run());
        assertSame(value, observed.resultNow());

        Task<@Nullable String> nullable = Task.<@Nullable String>completed(null).whenComplete((result, failure) -> {
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
        Task<?> failedCallback = Task.completed(1).thenAcceptAsync(Schedulers.io(), value -> { throw source; });
        Task<?> observed = failedCallback.whenCompleteAsync(Schedulers.io(), (value, failure) -> {
            assertNull(value);
            assertSame(source, failure);
            throw callback;
        });
        assertSame(source, assertThrows(AssertionError.class, observed::run));
        assertArrayEquals(new Throwable[]{callback}, source.getSuppressed());

        IOException sourceException = new IOException("source");
        AssertionError callbackError = new AssertionError("callback");
        Task<?> failedCleanup = Task.failed(sourceException).whenComplete((value, failure) -> { throw callbackError; });
        assertSame(sourceException, assertThrows(IOException.class, failedCleanup::run));
        assertArrayEquals(new Throwable[]{callbackError}, sourceException.getSuppressed());

        Task<?> rethrown = Task.failed(callback).whenComplete((value, failure) -> { throw callback; });
        assertSame(callback, assertThrows(IOException.class, rethrown::run));
        assertEquals(0, callback.getSuppressed().length);

        Task<?> successfulSource = Task.completed(1).whenComplete((value, failure) -> {
            assertEquals(1, value);
            assertNull(failure);
            throw callback;
        });
        assertSame(callback, assertThrows(IOException.class, successfulSource::run));
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
}
