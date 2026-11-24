// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /// Children inherit their executor through batches. A separately submitted root uses the task scheduler.
    @Test
    void distinguishesInheritedAndRootExecutors() throws Exception {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            Thread otherWorker = other.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            task(parent, context -> {
                Thread parentWorker = Thread.currentThread();
                assertNotSame(parentWorker, Task.supplyAsync(Thread::currentThread).run());

                Task<List<Thread>> branch = task(children -> children.runAll(List.of(
                        Task.supplyAsync(Thread::currentThread),
                        task(other, nested -> nested.run(Task.supplyAsync(Thread::currentThread))))));
                assertEquals(List.of(parentWorker, otherWorker), context.run(branch));
                return null;
            }).run();
        } finally {
            parent.shutdownNow();
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
        Task<Boolean> composed = source.thenComposeAsync(Schedulers.javafx(), processed -> {
            assertTrue(processed);
            assertTrue(Platform.isFxApplicationThread());
            return Task.supplyAsync(Platform::isFxApplicationThread);
        });
        assertFalse(composed.run());
    }

    /// Composition preserves the result type and value of the selected child.
    @Test
    void preservesComposedChildResult() throws Exception {
        Task<String> supplied = Task.composeAsync(() -> Task.completed("child"));
        assertEquals("child", supplied.run());

        Task<@Nullable String> child = Task.completed(null);
        Task<@Nullable String> composed = Task.completed(1).thenComposeAsync(child);
        assertNull(composed.run());
        assertEquals(Task.State.SUCCEEDED, child.getState());
    }

    /// A continuation added after its source finishes observes its result without running the source again.
    @Test
    void composesAfterSourceCompletion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> source = Task.supplyAsync(calls::incrementAndGet);
        assertEquals(1, source.run());
        Task<Integer> continuation = source.thenApply(value -> value + 10);
        StageEvents events = new StageEvents();
        continuation.addTaskListener(events);
        assertEquals(11, continuation.run());
        assertEquals(1, calls.get());
        assertFalse(events.running.containsKey(source));
        assertTrue(events.completions.stream().noneMatch(completion -> completion.task() == source));
    }

    /// A missing child fails when passed directly or when its factory runs.
    @Test
    void rejectsMissingComposedChild() {
        Task<Integer> source = Task.completed(1);
        @Nullable Task<String> missing = null;
        assertThrows(NullPointerException.class, () -> source.thenComposeAsync(missing));
        assertThrows(NullPointerException.class, source.thenComposeAsync(value -> missing)::run);
        assertThrows(NullPointerException.class, Task.composeAsync(() -> missing)::run);
    }

    /// An optional factory can skip child execution and publish a successful null result.
    @Test
    void skipsAbsentComposedChildren() throws Exception {
        Task<@Nullable String> source = Task.composeOptionalAsync(() -> null);
        Task<@Nullable String> composed = source.thenComposeOptionalAsync(value -> {
            assertNull(value);
            return null;
        });
        assertNull(composed.run());

        assertEquals("present", Task.composeOptionalAsync(() -> Task.completed("present"))
                .thenComposeOptionalAsync(Task::completed).run());
    }

    /// Cancellation lets an active child finish, skips success callbacks, and reaches the completion observer.
    @Test
    void cancellationPropagatesThroughRunningTree() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean bodyFinished = new AtomicBoolean();
        AtomicBoolean continued = new AtomicBoolean();
        List<@Nullable Throwable> observed = new ArrayList<>();
        Task<Integer> completed = Task.completed(42);
        Task<@Nullable Void> child = task(Schedulers.io(), context -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            assertThrows(CancellationException.class, context::checkCancellation);
            bodyFinished.set(true);
            return null;
        });
        Task<?> root = task(parent -> {
            parent.run(completed);
            try {
                return parent.run(child);
            } finally {
                assertTrue(parent.isCancelled());
            }
        }).thenRun(() -> continued.set(true)).whenComplete((value, failure) -> observed.add(failure));

        CompletableFuture<Boolean> outcome = CompletableFuture.supplyAsync(root::test);
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            root.cancel();
            assertTrue(root.isCancellationRequested());
            assertFalse(root.isCancelled());
            assertFalse(child.isCancelled());
        } finally {
            release.countDown();
        }
        assertFalse(outcome.get(10, TimeUnit.SECONDS));
        assertTrue(bodyFinished.get());
        assertFalse(continued.get());
        assertTrue(root.isCancelled());
        assertTrue(child.isCancelled());
        assertEquals(42, completed.resultNow());
        assertEquals(List.of(root.exceptionNow()), observed);
    }

    /// Cancelling after dispatch keeps each kind of success callback from running.
    @ParameterizedTest
    @ValueSource(strings = {"thenApply", "thenRun", "thenCompose", "thenComposeOptional"})
    void skipsQueuedSuccessCallbackAfterCancellation(String method) throws Exception {
        CountDownLatch submitted = new CountDownLatch(1);
        CompletableFuture<@Nullable Void> release = new CompletableFuture<>();
        Executor executor = command -> {
            submitted.countDown();
            release.orTimeout(10, TimeUnit.SECONDS).join();
            command.run();
        };
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> source = Task.completed(1);
        Task<?> continuation = switch (method) {
            case "thenApply" -> source.thenApplyAsync(executor, value -> calls.incrementAndGet());
            case "thenRun" -> source.thenRunAsync(executor, calls::incrementAndGet);
            case "thenCompose" -> source.thenComposeAsync(executor, value -> Task.completed(calls.incrementAndGet()));
            case "thenComposeOptional" -> source.thenComposeOptionalAsync(executor, value -> Task.completed(calls.incrementAndGet()));
            default -> throw new AssertionError(method);
        };
        CompletableFuture<Boolean> outcome = CompletableFuture.supplyAsync(continuation::test);
        try {
            assertTrue(submitted.await(10, TimeUnit.SECONDS));
            continuation.cancel();
        } finally {
            release.complete(null);
        }
        assertFalse(outcome.get(10, TimeUnit.SECONDS));
        assertTrue(continuation.isCancelled());
        assertEquals(0, calls.get());
    }

    /// A cancelled parent leaves an unsubmitted child available for reuse through either submission API.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavesNewChildUnsubmittedAfterCancellation(boolean batch) throws Exception {
        Task<Integer> child = Task.completed(42);
        Task<Integer> completed = Task.completed(1);
        completed.run();
        Task<Integer> root = new Task<>() {
            /// Cancels the tree before attempting to submit the child.
            @Override
            public Integer execute(TaskExecutionContext context) throws Exception {
                cancel();
                if (batch)
                    assertThrows(CancellationException.class, () -> context.runAll(List.of(completed)));
                else
                    assertThrows(CancellationException.class, () -> context.run(completed));
                return batch ? context.runAll(List.of(child)).get(0) : context.run(child);
            }
        };
        assertThrows(CancellationException.class, root::run);
        assertEquals(Task.State.NEW, child.getState());
        assertEquals(42, child.run());
    }

    /// Cancellation retains the interruption, including any cleanup failures attached to it.
    @Test
    void interruptedTaskPreservesCause() {
        InterruptedException interrupted = new InterruptedException();
        Task<?> task = Task.runAsync(() -> { throw interrupted; });

        CancellationException cancellation = assertThrows(CancellationException.class, task::run);
        assertSame(interrupted, cancellation.getCause());
    }

    /// Querying cancellation observes the worker's interrupt status and leaves it set.
    @Test
    void observesInterruptionWithoutClearingIt() throws Exception {
        task(context -> {
            Thread.currentThread().interrupt();
            try {
                assertTrue(context.isCancelled());
                assertThrows(CancellationException.class, context::checkCancellation);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            return null;
        }).run();
    }

    /// Child failures are reported as false. Cancellation of a child alone leaves its parent usable.
    @Test
    void reportsTryRunOutcome() throws Exception {
        task(context -> {
            assertTrue(context.tryRun(Task.completed(false)));
            assertTrue(context.tryRunAll(List.of()));
            assertTrue(context.tryRunAll(List.of(Task.completed(1))));
            for (Throwable failure : List.of(new IOException(), new AssertionError(), new CancellationException())) {
                Task<?> child = Task.failed(failure);
                assertFalse(context.tryRun(child));
                assertFalse(context.tryRunAll(List.of(child)));
                assertSame(failure, child.exceptionNow());
            }
            return null;
        }).run();
    }

    /// A request to cancel the whole tree propagates through both failure-tolerant entry points.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tryRunPropagatesCancellation(boolean batch) {
        Task<?> root = new Task<@Nullable Void>() {
            /// Requests cancellation from inside the child.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) {
                Task<?> child = Task.runAsync(this::cancel);
                if (batch)
                    context.tryRunAll(List.of(child));
                else
                    context.tryRun(child);
                return fail("Cancellation must reach the parent");
            }
        };
        assertThrows(CancellationException.class, root::run);
    }

    /// A batch reports its failure only after its other children have finished.
    @Test
    void drainsConcurrentSiblingsOnFailure() {
        IOException failure = new IOException("Child failed");
        CountDownLatch failed = new CountDownLatch(1);
        Task<Integer> first = Task.failed(failure);
        Task<Integer> second = Task.supplyAsync(() -> {
            assertTrue(failed.await(10, TimeUnit.SECONDS));
            return 2;
        });
        Task<?> batch = Task.allOf(first, second);
        batch.addTaskListener(new TaskListener() {
            /// Allows the sibling to finish after the failure is published.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable cause) {
                if (task == first)
                    failed.countDown();
            }
        });
        assertSame(failure, assertThrows(IOException.class, batch::run));
        assertEquals(2, second.resultNow());
    }

    /// A dispatch failure reaches the decorated child and allows the remaining siblings to run.
    @Test
    void drainsSiblingsWhenExecutorSelectionFails() {
        IllegalStateException failure = new IllegalStateException("No executor available");
        Task<Integer> broken = new Task<>() {
            /// Rejects dispatch before the body starts.
            @Override
            protected Executor getExecutor() {
                throw failure;
            }

            /// {@inheritDoc}
            @Override
            public Integer execute(TaskExecutionContext context) {
                return fail("Unexpected execution");
            }
        };
        Task<Integer> branch = broken.withCounter();
        Task<Integer> sibling = Task.completed(2);
        assertSame(failure, assertThrows(IllegalStateException.class, Task.allOf(branch, sibling)::run));
        assertSame(failure, branch.exceptionNow());
        assertSame(failure, broken.exceptionNow());
        assertEquals(2, sibling.resultNow());
    }

    /// Fresh and completed children keep their input positions even when they finish out of order.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnsResultsInInputOrder(boolean bounded) throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        Task<String> first = Task.supplyAsync(() -> {
            assertTrue(released.await(10, TimeUnit.SECONDS));
            return "first";
        });
        Task<String> second = Task.completed("second");
        Task<@Nullable String> previous = Task.completed(null);
        previous.run();

        @Unmodifiable List<Task<@Nullable String>> children = List.of(first, second, previous);
        Task<List<@Nullable String>> batch = bounded ? Task.allOf(children, 2) : Task.allOf(children);
        batch.addTaskListener(new TaskListener() {
            /// Releases the first child after the second result is available.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                if (task == second)
                    released.countDown();
            }
        });
        assertEquals(Arrays.asList("first", "second", null), batch.run());
    }

    /// Empty batches succeed and still require a positive concurrency limit.
    @Test
    void validatesEmptyBatches() throws Exception {
        assertTrue(Task.allOf().run().isEmpty());
        assertTrue(Task.allOf(List.of(), 1).run().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Task.allOf(List.of(), 0));
    }

    /// A bounded batch can start another child when one slot frees while the first child is still busy.
    @Test
    void refillsAvailableSlotsWithinConcurrencyLimit() throws Exception {
        int limit = 2;
        CountDownLatch entered = new CountDownLatch(limit);
        CountDownLatch nextEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        List<Task<@Nullable Void>> children = new ArrayList<>();
        for (int i = 0; i <= limit; i++) {
            CountDownLatch started = i < limit ? entered : nextEntered;
            CountDownLatch release = i == 0 ? releaseFirst : releaseSecond;
            children.add(Task.runAsync(Schedulers.io(), () -> {
                int concurrent = active.incrementAndGet();
                try {
                    assertTrue(concurrent <= limit, "Too many active children");
                    started.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } finally {
                    active.decrementAndGet();
                }
            }));
        }
        Task<?> batch = Task.allOf(children, limit).start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            releaseSecond.countDown();
            assertTrue(nextEntered.await(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
        }
        batch.run();
    }

    /// A rejected submission frees its slot. A later error takes precedence and the batch drains.
    @Test
    void drainsBoundedBatchAfterFailures() {
        RejectedExecutionException rejection = new RejectedExecutionException("Download unavailable");
        Task<Integer> rejected = Task.supplyAsync(command -> {
            throw rejection;
        }, () -> fail("Unexpected execution"));
        AssertionError error = new AssertionError("Fatal download failure");
        Task<Integer> last = Task.completed(3);
        Task<?> batch = Task.allOf(List.of(rejected, Task.<Integer>failed(error), last), 1);
        assertSame(error, assertThrows(AssertionError.class, batch::run));
        assertSame(rejection, rejected.exceptionNow());
        assertEquals(3, last.resultNow());
    }

    /// Cancellation drains the running children and leaves a child outside the limit available for reuse.
    @Test
    void stopsBatchAdmissionOnCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        Task<Integer> running = Task.supplyAsync(Schedulers.io(), () -> {
            entered.countDown();
            assertTrue(cancelled.await(10, TimeUnit.SECONDS));
            return 1;
        });
        Task<Integer> pending = Task.completed(3);
        Task<?> root = new Task<List<Integer>>() {
            /// Cancels the parent while both available slots are occupied.
            @Override
            public List<Integer> execute(TaskExecutionContext context) throws Exception {
                Task<Integer> trigger = Task.supplyAsync(Schedulers.io(), () -> {
                    assertTrue(entered.await(10, TimeUnit.SECONDS));
                    cancel();
                    cancelled.countDown();
                    return 2;
                });
                return context.runAll(List.of(running, trigger, pending), 2);
            }
        };
        assertThrows(CancellationException.class, root::run);
        assertTrue(running.isCancelled());
        assertEquals(Task.State.NEW, pending.getState());
        assertEquals(3, pending.run());
    }

    /// The interface default stops on tree cancellation and continues after an independently cancelled child.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsDefaultBatchesOnTreeCancellation(boolean cancelTree) {
        Task<Integer> last = Task.completed(2);
        Task<?> root = new Task<List<Integer>>() {
            /// Runs the same children through the interface's bounded batch overload.
            @Override
            public List<Integer> execute(TaskExecutionContext context) throws Exception {
                Task<Integer> first = Task.supplyAsync(() -> {
                    if (cancelTree)
                        cancel();
                    throw new CancellationException();
                });
                return new DefaultBatchContext(context).runAll(List.of(first, last), 1);
            }
        };
        assertThrows(CancellationException.class, root::run);
        assertEquals(cancelTree ? Task.State.NEW : Task.State.SUCCEEDED, last.getState());
    }

    /// An interrupted batch drains its child, preserves the interrupt flag, and gives an error precedence.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainsInterruptedBatch(boolean fatal) {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                AssertionError error = new AssertionError("Admitted child failed");
                Task<Integer> pending = Task.completed(2);
                task(parent, context -> {
                    Task<Integer> child = task(Schedulers.io(), childContext -> {
                        childContext.call(parent, () -> {
                            Thread.currentThread().interrupt();
                            return null;
                        });
                        return childContext.call(parent, () -> {
                            if (fatal)
                                throw error;
                            return 1;
                        });
                    });
                    try {
                        @Unmodifiable List<Task<Integer>> children = List.of(child, pending);
                        if (fatal) {
                            assertSame(error, assertThrows(AssertionError.class, () -> context.runAll(children, 1)));
                            assertSame(error, child.exceptionNow());
                        } else {
                            assertThrows(InterruptedException.class, () -> context.runAll(children, 1));
                            assertEquals(1, child.resultNow());
                        }
                        assertTrue(Thread.currentThread().isInterrupted());
                        assertEquals(Task.State.NEW, pending.getState());
                    } finally {
                        Thread.interrupted();
                    }
                    return null;
                }).run();
            });
        } finally {
            parent.shutdownNow();
        }
    }

    /// Inline and explicitly null callbacks inherit the parent executor. A default async callback
    /// leaves that executor, and its composed child can return to it.
    @Test
    void continuationsRespectParentAndCallbackExecutors() throws Exception {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        try {
            task(parent, context -> {
                Thread parentWorker = Thread.currentThread();
                Task<Thread> chain = Task.supplyAsync(Schedulers.io(), Thread::currentThread)
                        .thenApply(sourceWorker -> {
                            assertNotSame(parentWorker, sourceWorker);
                            return Thread.currentThread();
                        })
                        .thenComposeAsync(inlineWorker -> {
                            assertSame(parentWorker, inlineWorker);
                            assertNotSame(parentWorker, Thread.currentThread());
                            return Task.supplyAsync(Thread::currentThread);
                        })
                        .thenApplyAsync(null, childWorker -> {
                            assertSame(parentWorker, childWorker);
                            return Thread.currentThread();
                        });
                assertSame(parentWorker, context.run(chain));
                return null;
            }).run();
        } finally {
            parent.shutdownNow();
        }
    }

    /// A mapping callback, recovery handler, and completion observer honor an explicit executor.
    @Test
    void dispatchesSuccessAndFailureCallbacksToRequestedExecutor() throws Exception {
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        try {
            Thread worker = callbacks.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            IOException failure = new IOException("Mapping failed");
            Task<Integer> failed = Task.completed(1).thenApplyAsync(callbacks, value -> {
                assertSame(worker, Thread.currentThread());
                throw failure;
            });
            Task<Integer> recovered = failed.handleAsync(callbacks, (value, cause) -> {
                assertSame(failure, cause);
                assertSame(worker, Thread.currentThread());
                return 42;
            }).whenCompleteAsync(callbacks, (value, cause) -> assertSame(worker, Thread.currentThread()));
            assertEquals(42, recovered.run());
        } finally {
            callbacks.shutdownNow();
        }
    }

    /// Decorated downloads can fill their pool while an independent job uses a spare parent worker.
    @Test
    void decoratedBatchLeavesParentWorkerAvailable() throws Exception {
        ExecutorService parents = Executors.newFixedThreadPool(2);
        ExecutorService downloads = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch entered = new CountDownLatch(3);
            List<Task<Integer>> children = new ArrayList<>();
            TaskStage stage = new TaskStage("download");
            for (int i = 0; i < 3; i++) {
                children.add(task(downloads, context -> {
                    entered.countDown();
                    assertTrue(entered.await(10, TimeUnit.SECONDS));
                    return parents.submit(() -> 1).get(10, TimeUnit.SECONDS);
                }).withCounter().withStage(stage).withStageHints(stage));
            }
            assertEquals(List.of(1, 1, 1), task(parents, context -> context.runAll(children)).run());
        } finally {
            parents.shutdownNow();
            downloads.shutdownNow();
        }
    }

    /// A rejected or cancelled download leaves no admitted task pending and contributes no completed items.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesDecoratorsBeforeBodyEntry(boolean cancel) throws Exception {
        RejectedExecutionException rejection = new RejectedExecutionException("Download unavailable");
        CompletableFuture<Runnable> submitted = new CompletableFuture<>();
        Task<Integer> source = Task.supplyAsync(command -> {
            if (!cancel)
                throw rejection;
            submitted.complete(command);
        }, () -> fail("Download should not start"));
        Task<Integer> counted = source.withCounter();
        Task<Integer> root = counted.withStage(new TaskStage("download"));
        AtomicInteger completedItems = new AtomicInteger();
        root.addTaskListener(new TaskListener() {
            /// Records items reported before the stage closes.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                completedItems.addAndGet(completedDelta);
            }
        });
        FutureTask<RuntimeException> outcome = new FutureTask<>(() -> assertThrows(RuntimeException.class, root::run));
        Schedulers.io().execute(outcome);
        if (cancel) {
            Runnable command = submitted.get(10, TimeUnit.SECONDS);
            root.cancel();
            command.run();
        }
        RuntimeException failure = outcome.get(10, TimeUnit.SECONDS);
        if (cancel)
            assertInstanceOf(CancellationException.class, failure);
        else
            assertSame(rejection, failure);
        assertSame(failure, root.exceptionNow());
        for (Task<?> child : List.of(counted, source)) {
            if (child.getState() != Task.State.NEW)
                assertSame(failure, child.exceptionNow());
        }
        assertEquals(0, completedItems.get());
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
        assertThrows(IllegalStateException.class, root::exceptionNow);
        assertNull(root.resultNowOrNull());
        assertNull(root.exceptionNowOrNull());
        assertNull(root.run());
        assertNull(root.resultNow());
        assertNull(root.resultNowOrNull());
        assertThrows(IllegalStateException.class, root::exceptionNow);
        assertNull(root.exceptionNowOrNull());
    }

    /// Observers see a throwable returned by a successful task as its value, even when an observer throws.
    @Test
    void observersSeeTerminalStateOnce() {
        CancellationException value = new CancellationException();
        Task<CancellationException> root = Task.completed(value);
        AtomicInteger completions = new AtomicInteger();
        root.addTaskListener(new TaskListener() {
            /// Observes the published state and result, then exercises listener failure isolation.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                assertSame(root, task);
                assertNull(failure);
                assertEquals(Task.State.SUCCEEDED, task.getState());
                assertSame(value, task.resultNow());
                assertSame(value, task.resultNowOrNull());
                completions.incrementAndGet();
                throw new IllegalStateException("Observer failed");
            }
        });
        assertTrue(root.test());
        assertEquals(1, completions.get());
        assertFalse(root.isCancelled());
        assertThrows(IllegalStateException.class, root::exceptionNow);
        assertNull(root.exceptionNowOrNull());
    }

    /// All root entry points observe the published outcome after the task has finished.
    @Test
    void reusesPublishedRootOutcomes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> root = Task.supplyAsync(calls::incrementAndGet);
        assertEquals(1, root.run());
        assertSame(root, root.start());
        assertTrue(root.test());
        assertEquals(1, root.run());
        assertEquals(1, calls.get());

        for (Throwable failure : List.of(new IOException(), new AssertionError(), new CancellationException())) {
            Task<?> failed = Task.failed(failure);
            assertSame(failure, assertThrows(failure.getClass(), failed::run));
            assertSame(failed, failed.start());
            assertFalse(failed.test());
            assertSame(failure, assertThrows(failure.getClass(), failed::run));
            assertSame(failure, failed.exceptionNowOrNull());
        }
    }

    /// Two simultaneous first callers receive the result of a single body invocation.
    @Test
    void sharesConcurrentFirstRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> root = Task.supplyAsync(calls::incrementAndGet);
        CyclicBarrier ready = new CyclicBarrier(2);
        Callable<Integer> run = () -> {
            ready.await(10, TimeUnit.SECONDS);
            return root.run();
        };
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(run);
            var second = callers.submit(run);
            assertEquals(1, first.get(10, TimeUnit.SECONDS));
            assertEquals(1, second.get(10, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            callers.shutdownNow();
        }
    }

    /// Root configuration reserves the task for direct submission, including after a rejected child submission.
    @Test
    void reservesConfiguredRoots() throws Exception {
        Task<Integer> observed = Task.completed(1);
        StageEvents events = new StageEvents();
        observed.addTaskListener(events);
        Task<Integer> cancelled = Task.completed(2);
        cancelled.cancel();
        task(context -> {
            assertThrows(IllegalStateException.class, () -> context.run(observed));
            assertThrows(IllegalStateException.class, () -> context.runAll(List.of(cancelled)));
            return null;
        }).run();

        assertEquals(1, observed.run());
        assertEquals(1, events.completions.size());
        assertTrue(cancelled.isCancellationRequested());
        assertFalse(cancelled.isCancelled());
        assertThrows(CancellationException.class, cancelled::run);
        assertTrue(cancelled.isCancelled());
    }

    /// Reading an unsubmitted task leaves it available as a child, which cannot control the enclosing tree.
    @Test
    void rejectsRootControlsOnChildren() throws Exception {
        StageEvents listener = new StageEvents();
        Task<Integer> child = new Task<>() {
            /// Attempts root-only operations while the task is running as a child.
            @Override
            public Integer execute(TaskExecutionContext context) {
                assertThrows(IllegalStateException.class, this::cancel);
                assertThrows(IllegalStateException.class, this::isCancellationRequested);
                assertFalse(isCancelled());
                assertThrows(IllegalStateException.class, () -> addTaskListener(listener));
                assertThrows(IllegalStateException.class, () -> removeTaskListener(listener));
                assertFalse(context.isCancelled());
                return 42;
            }
        };
        assertFalse(child.isCancelled());
        assertFalse(child.isCancellationRequested());
        child.removeTaskListener(listener);
        assertEquals(42, Task.composeAsync(() -> child).run());
        assertSame(child, child.start());
        assertTrue(child.test());
        assertEquals(42, child.run());
        assertThrows(IllegalStateException.class, child::cancel);
        assertThrows(IllegalStateException.class, () -> child.addTaskListener(listener));
    }

    /// Interrupting a synchronous test's waiting thread preserves its interrupt status and the root's outcome.
    @Test
    void interruptedTestWaitReportsRootOutcome() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<Integer> root = Task.supplyAsync(Schedulers.io(), () -> {
            started.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return 42;
        });
        List<Boolean> finished = new CopyOnWriteArrayList<>();
        root.addTaskListener(new TaskListener() {
            /// Records the outcome published to observers of the same root.
            @Override
            public void onExecutionFinished(Task<?> rootTask, boolean success) {
                finished.add(success);
            }
        });
        FutureTask<Boolean> outcome = new FutureTask<>(() -> {
            boolean success = root.test();
            assertTrue(Thread.currentThread().isInterrupted());
            return success;
        });
        Thread caller = new Thread(outcome);
        caller.start();
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            caller.interrupt();
        } finally {
            release.countDown();
        }
        assertTrue(outcome.get(10, TimeUnit.SECONDS));
        assertEquals(List.of(true), finished);
        assertEquals(42, root.resultNow());
        assertNull(root.exceptionNowOrNull());
    }

    /// A second start is rejected even while the first caller is notifying listeners.
    @Test
    void rejectsSecondStartDuringStartNotification() throws Exception {
        AtomicInteger bodies = new AtomicInteger();
        Task<Integer> root = Task.supplyAsync(bodies::incrementAndGet);
        CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<@Nullable Void> release = new CompletableFuture<>();
        root.addTaskListener(new TaskListener() {
            /// Holds the first caller before the root body can run.
            @Override
            public void onExecutionStarted(Task<?> task) {
                entered.countDown();
                release.orTimeout(10, TimeUnit.SECONDS).join();
            }
        });
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(root::tryStart);
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertEquals(Task.State.READY, root.getState());
            assertFalse(root.tryStart());
        } finally {
            release.complete(null);
        }
        assertTrue(first.get(10, TimeUnit.SECONDS));
        assertEquals(1, root.run());
    }

    /// A repeated child fails the batch once its siblings finish and retains its own successful result.
    @ParameterizedTest
    @ValueSource(strings = {"unbounded", "bounded", "defaultBounded"})
    void rejectsRepeatedChildWithoutFailingItsFirstExecution(String batch) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> shared = Task.supplyAsync(calls::incrementAndGet);
        Task<Integer> last = Task.completed(22);
        task(context -> {
            @Unmodifiable List<Task<Integer>> children = List.of(shared, shared, last);
            assertThrows(IllegalStateException.class, () -> {
                switch (batch) {
                    case "unbounded" -> context.runAll(children);
                    case "bounded" -> context.runAll(children, 1);
                    case "defaultBounded" -> new DefaultBatchContext(context).runAll(children, 1);
                    default -> throw new AssertionError(batch);
                }
            });
            assertEquals(22, last.resultNow());
            return null;
        }).run();
        assertEquals(1, calls.get());
        assertEquals(1, shared.resultNow());
    }

    /// A competing decorator fails without changing the outcome of the already running source.
    @Test
    void rejectsCompetingDecoratorWithoutChangingSource() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<Integer> source = Task.supplyAsync(Schedulers.io(), () -> {
            running.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return 42;
        });
        Task<Integer> winner = source.withCounter().start();
        try {
            assertTrue(running.await(10, TimeUnit.SECONDS));
            Task<Integer> contender = source.withStage(new TaskStage("download"));
            assertThrows(IllegalStateException.class, contender::run);
        } finally {
            release.countDown();
        }
        assertEquals(42, winner.run());
    }

    /// A notification uses the registration order captured before a callback changes the listeners.
    @Test
    void snapshotsListenersForEachNotification() throws Exception {
        List<TaskListener> started = new ArrayList<>();
        List<TaskListener> finished = new ArrayList<>();

        /// Records which registrations receive each lifecycle event.
        class RecordingListener extends TaskListener {
            /// {@inheritDoc}
            @Override
            public void onExecutionStarted(Task<?> rootTask) {
                started.add(this);
            }

            /// {@inheritDoc}
            @Override
            public void onExecutionFinished(Task<?> rootTask, boolean success) {
                finished.add(this);
            }
        }

        TaskListener repeated = new RecordingListener();
        TaskListener added = new RecordingListener();
        TaskListener middle = new RecordingListener() {
            /// Removes the first registration of the repeated listener and appends a new listener.
            @Override
            public void onExecutionStarted(Task<?> rootTask) {
                super.onExecutionStarted(rootTask);
                rootTask.removeTaskListener(repeated);
                rootTask.addTaskListener(added);
            }
        };
        Task<Integer> root = Task.completed(1);
        root.addTaskListener(repeated);
        root.addTaskListener(middle);
        root.addTaskListener(repeated);

        root.run();
        assertEquals(List.of(repeated, middle, repeated), started);
        assertEquals(List.of(middle, repeated, added), finished);
    }

    /// A reporter follows its task across workers and delivers every update, including retries and completion.
    @Test
    void reportsProgressForTheExecutingTask() {
        Task<?> child = task(context -> {
            TaskProgress progress = context.progress();
            context.call(Schedulers.io(), () -> {
                progress.update(1, 4);
                progress.update(0.5);
                progress.update(0.5);
                return null;
            });
            progress.update(0.0);
            progress.update(3, 2);
            return null;
        });
        Task<?> root = task(Runnable::run, context -> {
            context.progress().update(0.125);
            context.run(child);
            context.progress().update(0, 0);
            return null;
        });

        /// A fraction and the task to which it was attributed.
        record Report(Task<?> task, double fraction) {}
        List<Report> reports = new CopyOnWriteArrayList<>();
        root.addTaskListener(new TaskListener() {
            /// Collects progress from both the task thread and participating workers.
            @Override
            public void onTaskProgress(Task<?> task, double fraction) {
                reports.add(new Report(task, fraction));
            }
        });
        assertTrue(root.test());
        assertEquals(List.of(new Report(root, 0.125),
                new Report(child, 0.25), new Report(child, 0.5), new Report(child, 0.5),
                new Report(child, 0.0), new Report(child, 1.0), new Report(root, 1.0)), reports);
    }

    /// A fake progress wait lets queued work on the same single-worker pool satisfy its condition.
    @Test
    void keepsForkJoinPoolAvailableWhileWaitingForFakeProgress() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean done = new AtomicBoolean();
        Task<Integer> wrapper = Task.completed(42).withFakeProgress(new TaskName.Simple("Waiting"), () -> {
            waiting.countDown();
            return done.get();
        }, 1.0);
        Task<Integer> root = task(pool, context -> {
            int value = context.run(wrapper);
            assertTrue(done.get());
            return value;
        });
        FutureTask<Integer> result = new FutureTask<>(root::run);
        try {
            Schedulers.io().execute(result);
            assertTrue(waiting.await(10, TimeUnit.SECONDS));
            pool.execute(() -> done.set(true));
            assertEquals(42, result.get(10, TimeUnit.SECONDS));
        } finally {
            done.set(true);
            pool.shutdownNow();
        }
    }

    /// A pool limited to one worker can wait for a completion signal from outside the pool.
    @Test
    void waitsForFakeProgressWithNoSpareWorkers() {
        ForkJoinPool pool = new ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null,
                false, 1, 1, 1, null, 60, TimeUnit.SECONDS);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> task(pool, context -> {
                CompletableFuture<Boolean> signal = new CompletableFuture<>();
                Task<Integer> wrapper = Task.completed(42).withFakeProgress(new TaskName.Simple("Waiting"),
                        () -> signal.completeOnTimeout(true, 100, TimeUnit.MILLISECONDS).isDone(), 1.0);
                assertEquals(42, context.run(wrapper));
                assertTrue(signal.isDone());
                return null;
            }).run());
        } finally {
            pool.shutdownNow();
        }
    }

    /// Progress validation also applies when no listener observes the task.
    @Test
    void rejectsInvalidProgressWithoutListeners() throws Exception {
        task(Runnable::run, context -> {
            TaskProgress progress = context.progress();
            assertThrows(IllegalArgumentException.class, () -> progress.update(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> progress.update(1.5));
            assertThrows(IllegalArgumentException.class, () -> progress.update(-1, 1));
            assertThrows(IllegalArgumentException.class, () -> progress.update(1, -1));
            return null;
        }).run();
    }

    /// A task owns its declared stage from the running notification onward. Children on other
    /// workers inherit it, even when an enclosing task uses the same identifier.
    @Test
    void stagesBelongToTasksAcrossWorkerPools() {
        TaskStage download = new TaskStage("download");
        Task<Integer> leaf = Task.supplyAsync(Schedulers.io(), () -> 1);
        Task<Integer> nested = task(null, download, context -> {
            context.addStageItems(2);
            IOException failure = new IOException("Download failed");
            assertSame(failure, assertThrows(IOException.class, () -> context.run(Task.failed(failure).withCounter())));
            return context.run(leaf.withCounter());
        });
        Task<Integer> sibling = Task.completed(7);
        Task<Integer> outer = task(context -> {
            context.run(nested);
            return context.run(sibling);
        }).withStage(download);
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

        root.addTaskListener(events);
        assertTrue(root.test());
        assertEquals(7, root.resultNow());
        assertEquals(2, events.started.size());
        TaskStage.Scope first = events.started.get(0);
        TaskStage.Scope second = events.started.get(1);
        assertSame(outer, first.task());
        assertSame(nested, second.task());
        assertEquals(download, first.stage());
        assertEquals(download, second.stage());
        assertNull(first.parent());
        assertSame(first, second.parent());
        assertSame(first, events.running.get(outer));
        assertSame(second, events.running.get(nested));
        assertSame(second, events.running.get(leaf));
        assertSame(first, events.running.get(sibling));
        assertTrue(events.running.containsKey(root));
        assertNull(events.running.get(root));
        assertEquals(List.of(new StageProgress(second, 0, 2), new StageProgress(second, 1, 0)), progress);
        assertEquals(List.of(second, first), events.finished);
        assertEquals(Arrays.asList(null, null), events.failures);
    }

    /// A recovered child failure or rejected submission closes its own stage and lets the parent succeed.
    @Test
    void recoversFromFailedStages() {
        IOException exception = new IOException("Download failed");
        RejectedExecutionException rejection = new RejectedExecutionException("Worker unavailable");
        Task<?> failed = Task.failed(exception).withStage(new TaskStage("checked"));
        Task<?> rejected = task(command -> {
            throw rejection;
        }, new TaskStage("rejected"), context -> fail("Unexpected execution"));
        Task<Integer> root = task(null, new TaskStage("outer"), context -> {
            assertSame(exception, assertThrows(IOException.class, () -> context.run(failed)));
            assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> context.run(rejected)));
            return 42;
        });

        StageEvents events = new StageEvents();
        root.addTaskListener(events);
        assertTrue(root.test());
        assertEquals(42, root.resultNow());
        assertEquals(List.of(root, failed, rejected), events.started.stream().map(TaskStage.Scope::task).toList());
        assertFalse(events.running.containsKey(rejected));
        assertEquals(List.of(events.started.get(1), events.started.get(2), events.started.get(0)), events.finished);
        assertEquals(Arrays.asList(exception, rejection, null), events.failures);
    }

    /// A root cancelled before dispatch still reports the start and cancellation of its stage.
    @Test
    void closesStageOfPreCancelledRoot() {
        StageEvents events = new StageEvents();
        Task<?> root = task(null, new TaskStage("cancelled"), context -> fail("Unexpected execution"));
        root.addTaskListener(events);
        root.cancel();
        CancellationException failure = assertThrows(CancellationException.class, root::run);
        assertEquals(1, events.started.size());
        assertSame(root, events.started.get(0).task());
        assertTrue(events.running.isEmpty());
        assertEquals(List.of(new TaskCompletion(root, events.started.get(0), failure)), events.completions);
        assertEquals(events.started, events.finished);
        assertEquals(List.of(failure), events.failures);
    }

    /// Fresh decorators can stage and count a completed result after its executor has shut down.
    @Test
    void decoratesCompletedSourceAfterExecutorShutdown() throws Exception {
        ExecutorService original = Executors.newSingleThreadExecutor();
        Task<Integer> source = task(original, new TaskStage("original"), context -> 42);
        try {
            assertEquals(42, source.run());
        } finally {
            original.shutdownNow();
        }

        Task<Integer> counted = source.withCounter();
        Task<Integer> staged = counted.withStage(new TaskStage("current"));
        Task<Integer> parent = task(Runnable::run, context -> context.run(staged));
        List<StageProgress> progress = new ArrayList<>();
        StageEvents events = new StageEvents() {
            /// Records counted items in the new stage.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                progress.add(new StageProgress(scope, completedDelta, totalDelta));
            }
        };
        assertEquals(42, parent.addTaskListener(events).run());
        assertEquals(List.of(staged), events.started.stream().map(TaskStage.Scope::task).toList());
        assertEquals(events.started, events.finished);
        assertEquals(List.of(new StageProgress(events.started.get(0), 1, 0)), progress);
    }

    /// Cancelling a decorator before dispatch leaves its source available for a later submission.
    @Test
    void preCancelledDecoratorLeavesSourceUnsubmitted() throws Exception {
        Task<Integer> source = Task.completed(42);
        Task<Integer> root = source.withCounter();
        root.cancel();

        assertThrows(CancellationException.class, root::run);
        assertEquals(Task.State.NEW, source.getState());
        assertEquals(42, source.run());
    }

    /// Hints declared during execution follow root hints and precede the staged task they describe.
    @Test
    void announcesNestedHintsWhenDispatched() {
        TaskStage.Hint check = new TaskStage.Hint(new TaskStage("check"));
        TaskStage.Hint install = new TaskStage.Hint(new TaskStage("install"), List.of(new TaskStage("download")));
        TaskStage.Hint runtime = new TaskStage.Hint(new TaskStage("runtime"));
        Task<Integer> root = task(context ->
                context.run(Task.completed(42).withStage(runtime.stage()).withStageHints(runtime)))
                .withStageHints(check, install);
        List<TaskStage.Hint> declarations = new ArrayList<>();
        List<TaskStage.Hint> atStageStart = new ArrayList<>();
        root.addTaskListener(new TaskListener() {
            /// Preserves declaration order across callbacks.
            @Override
            public void onStagesDeclared(@Unmodifiable List<TaskStage.Hint> hints) {
                declarations.addAll(hints);
            }

            /// Records which declarations are already visible at stage entry.
            @Override
            public void onStageStarted(TaskStage.Scope scope) {
                atStageStart.addAll(declarations);
            }
        });
        assertTrue(root.test());
        assertEquals(List.of(check, install, runtime), declarations);
        assertEquals(declarations, atStageStart);
    }

    /// Uses the interface's bounded batch method with the current task's execution context.
    private record DefaultBatchContext(TaskExecutionContext delegate) implements TaskExecutionContext {
        /// {@inheritDoc}
        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        /// {@inheritDoc}
        @Override
        public TaskProgress progress() {
            return delegate.progress();
        }

        /// {@inheritDoc}
        @Override
        public void addStageItems(int count) {
            delegate.addStageItems(count);
        }

        /// {@inheritDoc}
        @Override
        public void completeStageItem() {
            delegate.completeStageItem();
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> T run(Task<T> task) throws Exception {
            return delegate.run(task);
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> List<T> runAll(Collection<? extends Task<? extends T>> tasks) throws Exception {
            return delegate.runAll(tasks);
        }

        /// {@inheritDoc}
        @Override
        public <T extends @Nullable Object> T call(@Nullable Executor executor, Callable<T> action) throws Exception {
            return delegate.call(executor, action);
        }
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
            @Nullable
            protected Executor getExecutor() {
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
