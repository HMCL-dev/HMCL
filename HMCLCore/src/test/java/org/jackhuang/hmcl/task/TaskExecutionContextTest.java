// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
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

    /// A cancellation request reaches parent and child contexts and permits cleanup on the callback executor.
    @Test
    void cleanupAfterCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        CompletableFuture<Boolean> stopped = new CompletableFuture<>();
        Task<@Nullable Void> child = task(Schedulers.io(), context -> {
            assertFalse(context.isCancelled());
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            assertTrue(context.isCancelled());
            return null;
        });
        Task<?> root = task(parent -> {
            assertFalse(parent.isCancelled());
            try {
                return parent.run(child);
            } finally {
                assertTrue(parent.isCancelled());
            }
        }).whenComplete(cause -> {
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

    /// Querying cancellation observes the worker's interrupt status and leaves it set.
    @Test
    void observesInterruptionWithoutClearingIt() throws Exception {
        task(context -> {
            Thread.currentThread().interrupt();
            try {
                assertTrue(context.isCancelled());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            return null;
        }).run();
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
        Task<List<@Nullable String>> batch = Task.allOf(first, second);
        assertTrue(batch.executor(new TaskListener() {
            /// Releases the first child after the second child's result has been published.
            @Override
            public void onFinished(Task<?> task) {
                if (task == second)
                    released.countDown();
            }
        }).test());
        assertEquals(Arrays.asList("first", null), batch.getResult());
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
        assertTrue(root.executor(new TaskListener() {
            /// Observes the published state and result, then exercises listener failure isolation.
            @Override
            public void onFinished(Task<?> task) {
                assertSame(root, task);
                assertEquals(Task.TaskState.SUCCEEDED, task.getState());
                assertEquals(42, task.getResult());
                completions.incrementAndGet();
                throw new IllegalStateException("Observer failed");
            }
        }).test());
        assertEquals(1, completions.get());
        assertNull(root.getException());
    }

    /// A task owns its declared stage from the running notification onward. Children on other
    /// workers inherit it, even when an enclosing task uses the same identifier.
    @Test
    void stagesBelongToTasksAcrossWorkerPools() {
        Task<Thread> leaf = Task.supplyAsync(Schedulers.io(), Thread::currentThread);
        Task<Thread> counted = leaf.withCounter();
        Task<Thread> nested = task(null, "download", context -> {
            context.addStageItems(2);
            IOException failure = new IOException("Download failed");
            assertSame(failure, assertThrows(IOException.class, () -> context.run(Task.failed(failure).withCounter())));
            return context.runAll(List.of(counted)).get(0);
        });
        Task<Integer> sibling = Task.completed(7);
        Task<Integer> outer = task(context -> {
            assertNotSame(Thread.currentThread(), context.run(nested));
            return context.run(sibling);
        }).withStage("download");
        Task<Integer> root = task(context -> {
            context.addStageItems(1);
            context.completeStageItem();
            return context.run(outer);
        });
        Map<Task<?>, @Nullable TaskStage> running = new HashMap<>();
        List<StageProgress> progress = new ArrayList<>();
        StageEvents events = new StageEvents() {
            /// Captures the stage delivered with the running task itself.
            @Override
            public void onRunning(Task<?> task, @Nullable TaskStage stage) {
                running.put(task, stage);
            }

            /// Records the effective stage and both increments in dispatch order.
            @Override
            public void onStageProgress(TaskStage stage, int completedDelta, int totalDelta) {
                progress.add(new StageProgress(stage, completedDelta, totalDelta));
            }
        };

        assertTrue(root.executor(events).test());
        assertEquals(7, root.getResult());
        assertEquals(2, events.started.size());
        TaskStage first = events.started.get(0);
        TaskStage second = events.started.get(1);
        assertSame(outer, first.task());
        assertSame(nested, second.task());
        assertEquals("download", first.id());
        assertEquals("download", second.id());
        assertNull(first.parent());
        assertSame(first, second.parent());
        assertSame(first, running.get(outer));
        assertSame(second, running.get(nested));
        assertSame(second, running.get(leaf));
        assertSame(first, running.get(sibling));
        assertTrue(running.containsKey(root));
        assertNull(running.get(root));
        assertNull(leaf.getStage());
        assertNull(sibling.getStage());
        assertEquals(List.of(new StageProgress(second, 0, 2), new StageProgress(second, 1, 0)), progress);
        assertEquals(List.of(second, first), events.finished);
        assertEquals(Arrays.asList(null, null), events.failures);
    }

    /// A parent that handles a child's exception, error or scheduling rejection can finish
    /// its own stage successfully. Each child stage reports the original failure once.
    @Test
    void recoversFromFailedStages() {
        IOException exception = new IOException("Download failed");
        AssertionError error = new AssertionError("Unexpected failure");
        RejectedExecutionException rejection = new RejectedExecutionException("Worker unavailable");
        Task<?> failed = Task.failed(exception).withStage("checked");
        Task<?> broken = task(null, "error", context -> {
            throw error;
        });
        Task<?> rejected = task(command -> {
            throw rejection;
        }, "rejected", context -> fail("Unexpected execution"));
        Task<Integer> root = task(null, "outer", context -> {
            assertSame(exception, assertThrows(IOException.class, () -> context.run(failed)));
            assertSame(error, assertThrows(AssertionError.class, () -> context.run(broken)));
            assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> context.runAll(List.of(rejected))));
            return 42;
        });

        StageEvents events = new StageEvents();
        assertTrue(root.executor(events).test());
        assertEquals(42, root.getResult());
        assertEquals(List.of(root, failed, broken, rejected), events.started.stream().map(TaskStage::task).toList());
        TaskStage outer = events.started.get(0);
        assertEquals(List.of(events.started.get(1), events.started.get(2), events.started.get(3), outer), events.finished);
        assertEquals(Arrays.asList(exception, error, rejection, null), events.failures);
    }

    /// A root cancelled before dispatch still reports the start and cancellation of its stage.
    @Test
    void closesStageOfPreCancelledRoot() {
        StageEvents events = new StageEvents();
        Task<?> root = task(null, "cancelled", context -> fail("Unexpected execution"));
        TaskExecutor executor = root.executor(events);
        executor.cancel();
        assertFalse(executor.test());
        assertEquals(1, events.started.size());
        assertSame(root, events.started.get(0).task());
        assertEquals(events.started, events.finished);
        assertEquals(List.of(assertInstanceOf(CancellationException.class, root.getException())), events.failures);
    }

    /// Hints declared during execution follow root hints and precede the staged task they describe.
    @Test
    void announcesNestedHintsWhenDispatched() {
        Task.StagesHint check = new Task.StagesHint("check");
        Task.StagesHint install = new Task.StagesHint("install", List.of("download"));
        Task.StagesHint runtime = new Task.StagesHint("runtime");
        Task<Integer> root = task(context ->
                context.run(Task.completed(42).withStage("runtime").withStagesHints(runtime)))
                .withStagesHints(check, install);
        List<Task.StagesHint> declarations = new ArrayList<>();
        List<Task.StagesHint> atStageStart = new ArrayList<>();
        assertTrue(root.executor(new TaskListener() {
            /// Preserves declaration order across callbacks.
            @Override
            public void onStagesDeclared(@Unmodifiable List<Task.StagesHint> hints) {
                declarations.addAll(hints);
            }

            /// Records which declarations are already visible at stage entry.
            @Override
            public void onStageStarted(TaskStage stage) {
                atStageStart.addAll(declarations);
            }
        }).test());
        assertEquals(List.of(check, install, runtime), declarations);
        assertEquals(declarations, atStageStart);
    }

    /// One incremental progress notification for an executing stage.
    private record StageProgress(TaskStage stage, int completedDelta, int totalDelta) {}

    /// Records stage events from tests that dispatch one child at a time.
    private static class StageEvents extends TaskListener {
        /// Stages in dispatch order.
        private final List<TaskStage> started = new ArrayList<>();

        /// Stages in completion order.
        private final List<TaskStage> finished = new ArrayList<>();

        /// Outcomes in completion order, with null denoting success.
        private final List<@Nullable Throwable> failures = new ArrayList<>();

        /// Records the identity of the new stage.
        @Override
        public void onStageStarted(TaskStage stage) {
            started.add(stage);
        }

        /// Records the stage identity and cause from the terminal notification.
        @Override
        public void onStageFinished(TaskStage stage, @Nullable Throwable failure) {
            finished.add(stage);
            failures.add(failure);
        }
    }

    /// Creates a task exposing its context to a test body.
    private static <T extends @Nullable Object> Task<T> task(Body<T> body) {
        return task(null, body);
    }

    /// Creates a task with an optional executor preference for its body.
    private static <T extends @Nullable Object> Task<T> task(@Nullable Executor executor, Body<T> body) {
        return task(executor, null, body);
    }

    /// Creates a task declaring the given stage, or inheriting its parent's when null.
    private static <T extends @Nullable Object> Task<T> task(@Nullable Executor executor, @Nullable String stage, Body<T> body) {
        return new Task<>() {
            /// Returns the test task's stage declaration.
            @Override
            public @Nullable String getStage() {
                return stage;
            }

            /// Returns the test task's executor preference.
            @Override
            public @Nullable Executor getExecutor() {
                return executor;
            }

            /// Invokes the test body on the selected worker.
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
