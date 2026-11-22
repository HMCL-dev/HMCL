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
import java.util.concurrent.CopyOnWriteArrayList;
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
        TaskHandle handle = root.newHandle(new TaskListener() {
            /// Signals the test thread after cancellation has finished propagating.
            @Override
            public void onExecutionFinished(TaskHandle handle, boolean success) {
                stopped.complete(success);
            }
        });
        handle.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, child::resultNow);
            assertThrows(IllegalStateException.class, child::exceptionNow);
            assertNull(child.resultNowOrNull());
            assertNull(child.exceptionNowOrNull());
            handle.cancel();
        } finally {
            release.countDown();
        }
        assertFalse(stopped.get(10, TimeUnit.SECONDS));
        assertEquals(1, cleanups.get());
        CancellationException cancellation = assertInstanceOf(CancellationException.class, root.exceptionNow());
        assertSame(cancellation, root.exceptionNowOrNull());
        assertNull(root.resultNowOrNull());
        assertThrows(IllegalStateException.class, root::resultNow);
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
            assertEquals(1, second.resultNow());
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
            assertSame(failure, broken.exceptionNow());
            assertSame(failure, broken.exceptionNowOrNull());
            assertNull(broken.resultNowOrNull());
            assertEquals(Task.TaskState.FAILED, broken.getState());
            assertEquals(1, first.resultNow());
            assertEquals(2, last.resultNow());
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
        assertTrue(batch.newHandle(new TaskListener() {
            /// Releases the first child after the second child's result has been published.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                if (task == second)
                    released.countDown();
            }
        }).test());
        assertEquals(Arrays.asList("first", null), batch.resultNow());
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

    /// A null result is available after success and distinguishable from a task that has yet to run.
    @Test
    void resultNowReturnsNullOnlyAfterSuccess() throws Exception {
        Task<@Nullable String> root = Task.completed(null);
        assertThrows(IllegalStateException.class, root::resultNow);
        assertNull(root.run());
        assertNull(root.resultNow());
        assertNull(root.resultNowOrNull());
        assertThrows(IllegalStateException.class, root::exceptionNow);
        assertNull(root.exceptionNowOrNull());
    }

    /// A successful task publishes its result before observers, even when an observer throws.
    @Test
    void observersSeeTerminalStateOnce() {
        Task<Integer> root = task(context -> 42);
        AtomicInteger completions = new AtomicInteger();
        assertTrue(root.newHandle(new TaskListener() {
            /// Observes the published state and result, then exercises listener failure isolation.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                assertSame(root, task);
                assertNull(failure);
                assertEquals(Task.TaskState.SUCCEEDED, task.getState());
                assertEquals(42, task.resultNow());
                assertEquals(42, task.resultNowOrNull());
                completions.incrementAndGet();
                throw new IllegalStateException("Observer failed");
            }
        }).test());
        assertEquals(1, completions.get());
        assertNull(root.exceptionNowOrNull());
    }

    /// Running a root directly consumes the same execution that its handles would start.
    @Test
    void rejectsAnotherRootExecutionAfterRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> root = Task.supplyAsync(calls::incrementAndGet);
        TaskHandle handle = root.newHandle();
        assertEquals(1, root.run());
        assertThrows(IllegalStateException.class, root::run);
        assertThrows(IllegalStateException.class, handle::test);
        assertThrows(IllegalStateException.class, root::start);
        assertEquals(1, calls.get());
        assertEquals(Task.TaskState.SUCCEEDED, root.getState());
        assertEquals(1, root.resultNow());
        assertNull(root.exceptionNowOrNull());
    }

    /// Competing callers are rejected while the first handle is still announcing its start.
    @Test
    void claimsRootBeforeNotifyingStartListeners() throws Exception {
        AtomicInteger bodies = new AtomicInteger();
        Task<Integer> root = task(null, new TaskStage("claimed"), context -> bodies.incrementAndGet());
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<@Nullable Void> release = new CompletableFuture<>();
        CompletableFuture<Boolean> stopped = new CompletableFuture<>();
        List<TaskHandle> starts = new CopyOnWriteArrayList<>();
        List<TaskHandle> stops = new CopyOnWriteArrayList<>();
        StageEvents events = new StageEvents() {
            /// Pauses the winning start call before it can submit the root body.
            @Override
            public void onExecutionStarted(TaskHandle handle) {
                starts.add(handle);
                if (starts.size() == 1) {
                    entered.countDown();
                    release.orTimeout(10, TimeUnit.SECONDS).join();
                }
            }

            /// Records each handle stop and releases the test thread.
            @Override
            public void onExecutionFinished(TaskHandle handle, boolean success) {
                stops.add(handle);
                stopped.complete(success);
            }
        };
        TaskHandle first = root.newHandle(events);
        TaskHandle second = root.newHandle(events);
        assertEquals(Task.TaskState.NEW, root.getState());
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            var start = caller.submit(first::start);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(Task.TaskState.READY, root.getState());
                assertThrows(IllegalStateException.class, root::resultNow);
                assertThrows(IllegalStateException.class, second::start);
                assertThrows(IllegalStateException.class, first::test);
                assertThrows(IllegalStateException.class, root::run);
                assertFalse(release.isDone());
                assertEquals(0, bodies.get());
            } finally {
                release.complete(null);
            }
            assertSame(first, start.get(10, TimeUnit.SECONDS));
            assertTrue(stopped.get(10, TimeUnit.SECONDS));
        } finally {
            caller.shutdownNow();
        }
        assertThrows(IllegalStateException.class, second::test);
        assertEquals(List.of(first), starts);
        assertEquals(List.of(first), stops);
        assertEquals(1, events.started.size());
        assertEquals(events.started, events.finished);
        assertEquals(1, bodies.get());
        assertEquals(1, root.resultNow());
        assertEquals(Task.TaskState.SUCCEEDED, root.getState());
        assertNull(root.exceptionNowOrNull());
        assertNull(second.getException());
    }

    /// A repeated child fails the batch once its siblings finish and retains its own successful result.
    @Test
    void rejectsRepeatedChildWithoutFailingItsFirstExecution() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> shared = Task.supplyAsync(calls::incrementAndGet);
        Task<Integer> first = Task.completed(11);
        Task<Integer> last = Task.completed(22);
        task(context -> {
            assertThrows(IllegalStateException.class, () -> context.runAll(List.of(first, shared, shared, last)));
            assertEquals(11, first.resultNow());
            assertEquals(22, last.resultNow());
            assertThrows(IllegalStateException.class, () -> context.run(shared));
            return null;
        }).run();
        assertEquals(1, calls.get());
        assertEquals(1, shared.resultNow());
        assertEquals(Task.TaskState.SUCCEEDED, shared.getState());
        assertNull(shared.exceptionNowOrNull());
    }

    /// A task owns its declared stage from the running notification onward. Children on other
    /// workers inherit it, even when an enclosing task uses the same identifier.
    @Test
    void stagesBelongToTasksAcrossWorkerPools() {
        Task<Thread> leaf = Task.supplyAsync(Schedulers.io(), Thread::currentThread);
        Task<Thread> counted = leaf.withCounter();
        Task<Thread> nested = task(null, new TaskStage("download"), context -> {
            context.addStageItems(2);
            IOException failure = new IOException("Download failed");
            assertSame(failure, assertThrows(IOException.class, () -> context.run(Task.failed(failure).withCounter())));
            return context.runAll(List.of(counted)).get(0);
        });
        Task<Integer> sibling = Task.completed(7);
        Task<Integer> outer = task(context -> {
            assertNotSame(Thread.currentThread(), context.run(nested));
            return context.run(sibling);
        }).withStage(new TaskStage("download"));
        Task<Integer> root = task(context -> {
            context.addStageItems(1);
            context.completeStageItem();
            return context.run(outer);
        });
        List<StageProgress> progress = new ArrayList<>();
        StageEvents events = new StageEvents() {
            /// Records the effective stage and both increments in dispatch order.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                progress.add(new StageProgress(scope, completedDelta, totalDelta));
            }
        };

        assertTrue(root.newHandle(events).test());
        assertEquals(7, root.resultNow());
        assertEquals(2, events.started.size());
        TaskStage.Scope first = events.started.get(0);
        TaskStage.Scope second = events.started.get(1);
        assertNotSame(first, second);
        assertSame(outer, first.task());
        assertSame(nested, second.task());
        assertSame(outer.getStage(), first.stage());
        assertSame(nested.getStage(), second.stage());
        assertEquals(first.stage(), second.stage());
        assertNull(first.parent());
        assertSame(first, second.parent());
        assertSame(first, events.running.get(outer));
        assertSame(second, events.running.get(nested));
        assertSame(second, events.running.get(leaf));
        assertSame(first, events.running.get(sibling));
        assertTrue(events.running.containsKey(root));
        assertNull(events.running.get(root));
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
        Task<?> source = Task.failed(exception);
        Task<?> failed = source.withStage(new TaskStage("checked"));
        Task<?> broken = task(null, new TaskStage("error"), context -> {
            throw error;
        });
        Task<?> rejected = task(command -> {
            throw rejection;
        }, new TaskStage("rejected"), context -> fail("Unexpected execution"));
        Task<Integer> root = task(null, new TaskStage("outer"), context -> {
            assertSame(exception, assertThrows(IOException.class, () -> context.run(failed)));
            assertSame(error, assertThrows(AssertionError.class, () -> context.run(broken)));
            assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> context.runAll(List.of(rejected))));
            return 42;
        });

        StageEvents events = new StageEvents();
        assertTrue(root.newHandle(events).test());
        assertEquals(42, root.resultNow());
        assertEquals(List.of(root, failed, broken, rejected), events.started.stream().map(TaskStage.Scope::task).toList());
        TaskStage.Scope outer = events.started.get(0);
        TaskStage.Scope checked = events.started.get(1);
        assertFalse(events.running.containsKey(rejected));
        assertEquals(List.of(
                new TaskCompletion(source, checked, exception),
                new TaskCompletion(failed, checked, exception),
                new TaskCompletion(broken, events.started.get(2), error),
                new TaskCompletion(rejected, events.started.get(3), rejection),
                new TaskCompletion(root, outer, null)), events.completions);
        assertEquals(List.of(events.started.get(1), events.started.get(2), events.started.get(3), outer), events.finished);
        assertEquals(Arrays.asList(exception, error, rejection, null), events.failures);
    }

    /// A root cancelled before dispatch still reports the start and cancellation of its stage.
    @Test
    void closesStageOfPreCancelledRoot() {
        StageEvents events = new StageEvents();
        Task<?> root = task(null, new TaskStage("cancelled"), context -> fail("Unexpected execution"));
        TaskHandle handle = root.newHandle(events);
        handle.cancel();
        assertFalse(handle.test());
        CancellationException failure = assertInstanceOf(CancellationException.class, root.exceptionNow());
        assertThrows(IllegalStateException.class, handle::test);
        assertThrows(IllegalStateException.class, root.newHandle(events)::start);
        assertThrows(IllegalStateException.class, root::run);
        assertSame(failure, root.exceptionNow());
        assertSame(failure, handle.getException());
        assertEquals(Task.TaskState.FAILED, root.getState());
        assertEquals(1, events.started.size());
        assertSame(root, events.started.get(0).task());
        assertTrue(events.running.isEmpty());
        assertEquals(List.of(new TaskCompletion(root, events.started.get(0), failure)), events.completions);
        assertEquals(events.started, events.finished);
        assertEquals(List.of(failure), events.failures);
    }

    /// Hints declared during execution follow root hints and precede the staged task they describe.
    @Test
    void announcesNestedHintsWhenDispatched() {
        Task.StageHint check = new Task.StageHint(new TaskStage("check"));
        Task.StageHint install = new Task.StageHint(new TaskStage("install"), List.of(new TaskStage("download")));
        Task.StageHint runtime = new Task.StageHint(new TaskStage("runtime"));
        Task<Integer> root = task(context ->
                context.run(Task.completed(42).withStage(runtime.stage()).withStageHints(runtime)))
                .withStageHints(check, install);
        List<Task.StageHint> declarations = new ArrayList<>();
        List<Task.StageHint> atStageStart = new ArrayList<>();
        assertTrue(root.newHandle(new TaskListener() {
            /// Preserves declaration order across callbacks.
            @Override
            public void onStagesDeclared(@Unmodifiable List<Task.StageHint> hints) {
                declarations.addAll(hints);
            }

            /// Records which declarations are already visible at stage entry.
            @Override
            public void onStageStarted(TaskStage.Scope scope) {
                atStageStart.addAll(declarations);
            }
        }).test());
        assertEquals(List.of(check, install, runtime), declarations);
        assertEquals(declarations, atStageStart);
    }

    /// One incremental progress notification for an executing stage.
    private record StageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {}

    /// The task, effective stage and outcome delivered together at completion.
    private record TaskCompletion(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {}

    /// Records stage events from tests that dispatch one child at a time.
    private static class StageEvents extends TaskListener {
        /// The effective stage reported when each task body starts.
        private final Map<Task<?>, TaskStage.@Nullable Scope> running = new HashMap<>();

        /// Task outcomes in completion order, including failures before body entry.
        private final List<TaskCompletion> completions = new ArrayList<>();

        /// Stages in dispatch order.
        private final List<TaskStage.Scope> started = new ArrayList<>();

        /// Stages in completion order.
        private final List<TaskStage.Scope> finished = new ArrayList<>();

        /// Outcomes in completion order, with null denoting success.
        private final List<@Nullable Throwable> failures = new ArrayList<>();

        /// Records the stage associated with the task's running body.
        @Override
        public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
            running.put(task, scope);
        }

        /// Records the terminal notification and the original failure.
        @Override
        public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
            completions.add(new TaskCompletion(task, scope, failure));
        }

        /// Records the identity of the new stage.
        @Override
        public void onStageStarted(TaskStage.Scope scope) {
            started.add(scope);
        }

        /// Records the stage identity and cause from the terminal notification.
        @Override
        public void onStageFinished(TaskStage.Scope scope, @Nullable Throwable failure) {
            finished.add(scope);
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
    private static <T extends @Nullable Object> Task<T> task(@Nullable Executor executor, @Nullable TaskStage stage, Body<T> body) {
        return new Task<>() {
            /// Returns the test task's stage declaration.
            @Override
            public @Nullable TaskStage getStage() {
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
