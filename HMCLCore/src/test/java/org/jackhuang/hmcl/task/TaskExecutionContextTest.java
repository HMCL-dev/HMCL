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
                Task<Integer> root = task(parent -> {
                    Thread parentThread = Thread.currentThread();
                    Task<Integer> child = task(context -> {
                        assertNotSame(parentThread, Thread.currentThread());
                        return context.call(parentPool, () -> {
                            assertSame(parentThread, Thread.currentThread());
                            return 17;
                        });
                    });
                    return parent.run(child.setExecutor(childPool));
                });
                assertEquals(17, root.setExecutor(parentPool).run());
            });
        } finally {
            parentPool.shutdownNow();
            childPool.shutdownNow();
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

    /// Reconfiguring a continuation's executor moves its callback without moving its source.
    @Test
    void honorsReconfiguredCallbackExecutor() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Thread worker = pool.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            Thread actual = Task.supplyAsync(Thread::currentThread).thenApplyAsync(source -> {
                assertNotSame(worker, source);
                return Thread.currentThread();
            }).setExecutor(pool).run();
            assertSame(worker, actual);
        } finally {
            pool.shutdownNow();
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
                    Task<Integer> leaf = task(context -> runs.incrementAndGet());
                    Task<Integer> wrapper = task(context -> context.run(leaf.setExecutor(leaves)));
                    children.add(i % 2 == 0 ? wrapper : wrapper.setExecutor(wrappers));
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
        return new Task<>() {
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
