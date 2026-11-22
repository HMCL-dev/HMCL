// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises synchronous child execution across worker pools and lifecycle boundaries.
@NotNullByDefault
final class TaskExecutionContextTest {
    /// A child on another single-thread pool can return to its parent's worker.
    @Test
    void returnsToWaitingWorker() {
        ExecutorService parentPool = Executors.newSingleThreadExecutor();
        ExecutorService childPool = Executors.newSingleThreadExecutor();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                Task<Integer> root = task(parentPool, parent -> {
                    Thread parentThread = Thread.currentThread();
                    Task<Integer> child = task(childPool, context -> {
                        assertNotSame(parentThread, Thread.currentThread());
                        return context.call(parentPool, () -> {
                            assertSame(parentThread, Thread.currentThread());
                            return 17;
                        });
                    });
                    return parent.run(child);
                });
                assertEquals(17, root.run());
            });
        } finally {
            parentPool.shutdownNow();
            childPool.shutdownNow();
        }
    }

    /// A root's executor preference is resolved once, independently of its caller's executor.
    @Test
    void defaultsRootToTaskScheduler() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicInteger resolutions = new AtomicInteger();
        Task<Thread> root = new Task<>() {
            @Override
            public @Nullable Executor getExecutor() {
                resolutions.incrementAndGet();
                return null;
            }

            @Override
            public Thread execute(TaskExecutionContext context) throws Exception {
                Thread worker = Thread.currentThread();
                assertSame(worker, context.call(Schedulers.tasks(), Thread::currentThread));
                return worker;
            }
        };
        assertNotSame(caller, task(Runnable::run, context -> root.run()).run());
        assertEquals(1, resolutions.get());
    }

    /// Inherited batches preserve executor selection through nested tasks and explicit overrides.
    @Test
    void inheritsAcrossNestedTasksAndBatches() throws Exception {
        Thread caller = Thread.currentThread();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Thread worker = other.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            Task<List<Thread>> branch = task(context -> {
                context.run(Task.runAsync(() -> assertSame(caller, Thread.currentThread())));
                return context.runAll(List.of(
                        Task.supplyAsync(Thread::currentThread),
                        task(other, child -> child.run(Task.supplyAsync(Thread::currentThread))),
                        Task.supplyAsync("last", Thread::currentThread)));
            });
            assertEquals(List.of(caller, worker, caller),
                    task(Runnable::run, context -> context.run(branch)).run());
        } finally {
            other.shutdownNow();
        }
    }

    /// A synchronous caller on the JavaFX thread can service a callback from the awaited task.
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    void returnsToJavaFxCaller() throws Exception {
        FutureTask<Boolean> check = new FutureTask<>(() -> task(context ->
                context.call(Schedulers.javafx(), Platform::isFxApplicationThread)).run());
        Platform.runLater(check);
        assertTrue(check.get(10, TimeUnit.SECONDS));
    }

    /// The JavaFX callback leaves the event loop available and its child inherits the task body's executor.
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    void keepsJavaFxAvailableWhileWaitingForSource() throws Exception {
        Task<Boolean> source = Task.supplyAsync(() -> {
            FutureTask<Boolean> event = new FutureTask<>(Platform::isFxApplicationThread);
            Platform.runLater(event);
            return event.get(10, TimeUnit.SECONDS);
        });
        Task<@Nullable Thread> composed = source.thenComposeAsync(Schedulers.javafx(), processed -> {
            assertTrue(processed);
            assertTrue(Platform.isFxApplicationThread());
            return Task.supplyAsync(Thread::currentThread);
        });
        assertSame(Thread.currentThread(), task(Runnable::run, context -> context.run(composed)).run());
    }

    /// Cancellation permits a running wrapper to clean up on the requested callback executor.
    @Test
    void cleanupAfterCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        CompletableFuture<Boolean> stopped = new CompletableFuture<>();
        Task<@Nullable Void> child = task(context -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return null;
        });
        Task<?> root = child.whenComplete(cause -> {
            assertInstanceOf(CancellationException.class, cause);
            cleanups.incrementAndGet();
        });
        TaskExecutor executor = root.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                stopped.complete(success);
            }
        });
        executor.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            executor.cancel();
        } finally {
            release.countDown();
        }
        assertFalse(stopped.get(10, TimeUnit.SECONDS));
        assertEquals(1, cleanups.get());
        assertInstanceOf(CancellationException.class, root.getException());
    }

    /// A failed sibling does not prevent concurrent work from completing before the batch returns.
    @Test
    void drainsConcurrentSiblingsOnFailure() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        AtomicInteger finished = new AtomicInteger();
        IOException failure = new IOException("Child failed");
        Task<@Nullable Void> first = task(context -> {
            entered.countDown();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            throw failure;
        });
        Task<Integer> second = task(context -> {
            entered.countDown();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            return finished.incrementAndGet();
        });
        task(context -> {
            assertSame(failure, assertThrows(IOException.class, () -> context.runAll(List.of(first, second))));
            assertEquals(1, finished.get());
            assertEquals(1, second.getResult());
            assertEquals(Task.TaskState.FAILED, first.getState());
            return null;
        }).run();
    }

    /// Failure to resolve a child's executor fails that child and allows every sibling to finish.
    @Test
    void drainsSiblingsWhenExecutorSelectionFails() {
        IllegalStateException failure = new IllegalStateException("No executor available");
        Task<Integer> broken = new Task<>() {
            @Override
            public Executor getExecutor() {
                throw failure;
            }

            @Override
            public Integer execute(TaskExecutionContext context) {
                return fail("A task with a failed executor selection should not start");
            }
        };
        CountDownLatch release = new CountDownLatch(1);
        Task<Integer> first = Task.supplyAsync(() -> {
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return 1;
        });
        Task<Integer> last = Task.supplyAsync(() -> {
            release.countDown();
            return 2;
        });
        try {
            assertSame(failure, assertThrows(IllegalStateException.class, Task.allOf(first, broken, last)::run));
            assertSame(failure, broken.getException());
            assertEquals(Task.TaskState.FAILED, broken.getState());
            assertEquals(1, first.getResult());
            assertEquals(2, last.getResult());
        } finally {
            release.countDown();
        }
    }

    /// A batch retains null results and input order even when the last child finishes first.
    @Test
    void returnsResultsInInputOrder() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        Task<String> first = task(context -> {
            assertTrue(released.await(10, TimeUnit.SECONDS));
            return "first";
        });
        Task<@Nullable String> second = Task.completed(null);
        second.onDone().register(event -> released.countDown());
        assertEquals(Arrays.asList("first", null), Task.allOf(first, second).run());
        assertTrue(Task.allOf().run().isEmpty());
    }

    /// A continuation's body inherits its parent's executor and dispatches the callback on the selected executor.
    @Test
    void separatesContinuationBodyAndCallbackExecutors() throws Exception {
        Thread caller = Thread.currentThread();
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        try {
            Thread worker = callbacks.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            Task<Thread> source = Task.supplyAsync(Thread::currentThread);
            Task<Thread> continuation = source.thenApplyAsync(callbacks, dispatchThread -> {
                assertSame(caller, dispatchThread);
                return Thread.currentThread();
            });
            Thread actual = task(Runnable::run, context -> context.run(continuation)).run();
            assertSame(worker, actual);
        } finally {
            callbacks.shutdownNow();
        }
    }

    /// Thousands of wrappers around download-like children can complete on bounded executors.
    @Test
    void largeBatchWithNestedChildren() {
        ExecutorService leaves = Executors.newFixedThreadPool(2);
        ExecutorService wrappers = Executors.newSingleThreadExecutor();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
                List<Task<?>> children = new ArrayList<>();
                AtomicInteger runs = new AtomicInteger();
                for (int i = 0; i < 4096; i++) {
                    Task<Integer> leaf = Task.supplyAsync(leaves, runs::incrementAndGet);
                    children.add(task(i % 2 == 0 ? null : wrappers, context -> context.run(leaf)));
                }
                task(context -> {
                    context.runAll(children);
                    return null;
                }).run();
                assertEquals(children.size(), runs.get());
            });
        } finally {
            leaves.shutdownNow();
            wrappers.shutdownNow();
        }
    }

    /// A successful task publishes its result before observers, even when an observer throws.
    @Test
    void observersSeeTerminalStateOnce() {
        Task<Integer> root = task(context -> 42);
        AtomicInteger completions = new AtomicInteger();
        root.onDone().register(event -> {
            assertFalse(event.isFailed());
            assertEquals(Task.TaskState.SUCCEEDED, root.getState());
            assertEquals(42, root.getResult());
            completions.incrementAndGet();
            throw new IllegalStateException("Observer failed");
        });
        assertTrue(root.test());
        assertEquals(1, completions.get());
        assertNull(root.getException());
    }

    /// Creates a task exposing its context to a test body.
    private static <T extends @Nullable Object> Task<T> task(Body<T> body) {
        return task(null, body);
    }

    /// Creates a task with an optional executor preference for its body.
    private static <T extends @Nullable Object> Task<T> task(@Nullable Executor executor, Body<T> body) {
        return new Task<>() {
            @Override
            public @Nullable Executor getExecutor() {
                return executor;
            }

            @Override
            public T execute(TaskExecutionContext context) throws Exception {
                return body.execute(context);
            }
        };
    }

    /// A checked operation performed in a task's context.
    @FunctionalInterface
    @NotNullByDefault
    private interface Body<T extends @Nullable Object> {
        /// Returns the test task's result.
        T execute(TaskExecutionContext context) throws Exception;
    }
}
