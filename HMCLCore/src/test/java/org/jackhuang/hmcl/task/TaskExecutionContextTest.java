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

    /// A root's executor preference is resolved once, independently of its caller's executor.
    @Test
    void defaultsRootToTaskScheduler() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicInteger resolutions = new AtomicInteger();
        Task<Thread> root = new Task<>() {
            @Override
            @Nullable
            protected Executor getExecutor() {
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
                context.run(Task.runAsync(null, () -> assertSame(caller, Thread.currentThread())));
                return context.runAll(List.of(
                        Task.supplyAsync(Thread::currentThread),
                        task(other, child -> child.run(Task.supplyAsync(Thread::currentThread))),
                        Task.supplyAsync(Thread::currentThread).setName("last")
                                .setSignificance(TaskSignificance.MAJOR)));
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
        Task<Thread> composed = source.thenComposeAsync(Schedulers.javafx(), processed -> {
            assertTrue(processed);
            assertTrue(Platform.isFxApplicationThread());
            return Task.supplyAsync(Thread::currentThread);
        });
        assertSame(Thread.currentThread(), task(Runnable::run, context -> context.run(composed)).run());
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
        assertEquals(List.of(continuation), events.completions.stream().map(TaskCompletion::task).toList());
        assertFalse(events.running.containsKey(source));
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
        StageEvents events = new StageEvents();
        composed.addTaskListener(events);
        assertTrue(composed.test());
        assertNull(composed.resultNow());
        assertEquals(List.of(source, composed), events.completions.stream().map(TaskCompletion::task).toList());

        assertEquals("present", Task.composeOptionalAsync(() -> Task.completed("present"))
                .thenComposeOptionalAsync(Task::completed).run());
    }

    /// A cancellation request reaches parent and child contexts and permits cleanup on the callback executor.
    @Test
    void cleanupAfterCancellation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        CompletableFuture<Boolean> stopped = new CompletableFuture<>();
        Task<Integer> completedChild = Task.completed(42);
        Task<@Nullable Void> child = task(Schedulers.io(), context -> {
            assertFalse(context.isCancelled());
            context.checkCancellation();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            assertTrue(context.isCancelled());
            assertThrows(CancellationException.class, context::checkCancellation);
            return null;
        });
        Task<?> root = task(parent -> {
            assertFalse(parent.isCancelled());
            parent.run(completedChild);
            try {
                return parent.run(child);
            } finally {
                assertTrue(parent.isCancelled());
            }
        }).whenComplete((value, cause) -> {
            assertNull(value);
            assertInstanceOf(CancellationException.class, cause);
            cleanups.incrementAndGet();
        });
        root.addTaskListener(new TaskListener() {
            /// Signals the test thread after cancellation has finished propagating.
            @Override
            public void onExecutionFinished(Task<?> rootTask, boolean success) {
                stopped.complete(success);
            }
        });
        root.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, child::resultNow);
            assertThrows(IllegalStateException.class, child::exceptionNow);
            assertNull(child.resultNowOrNull());
            assertNull(child.exceptionNowOrNull());
            root.cancel();
            assertTrue(root.isCancellationRequested());
            assertEquals(Task.State.RUNNING, root.getState());
            assertEquals(Task.State.RUNNING, child.getState());
            assertFalse(root.isCancelled());
            assertFalse(child.isCancelled());
        } finally {
            release.countDown();
        }
        assertFalse(stopped.get(10, TimeUnit.SECONDS));
        assertEquals(1, cleanups.get());
        assertTrue(root.isCancelled());
        assertTrue(child.isCancelled());
        assertFalse(completedChild.isCancelled());
        assertEquals(42, completedChild.resultNow());
        CancellationException cancellation = assertInstanceOf(CancellationException.class, root.exceptionNow());
        assertSame(cancellation, root.exceptionNowOrNull());
        assertNull(root.resultNowOrNull());
        assertThrows(IllegalStateException.class, root::resultNow);
    }

    /// A queued success callback observes cancellation before doing work, and its observer can still clean up.
    @ParameterizedTest
    @ValueSource(strings = {"thenApply", "thenRun", "thenCompose", "thenComposeOptional"})
    void skipsQueuedSuccessCallbackAfterCancellation(String method) throws Exception {
        CompletableFuture<Runnable> submitted = new CompletableFuture<>();
        Executor executor = submitted::complete;
        AtomicInteger calls = new AtomicInteger();
        Task<Integer> source = Task.completed(1);
        Task<?> continuation = switch (method) {
            case "thenApply" -> source.thenApplyAsync(executor, value -> calls.incrementAndGet());
            case "thenRun" -> source.thenRunAsync(executor, calls::incrementAndGet);
            case "thenCompose" -> source.thenComposeAsync(executor, value -> Task.completed(calls.incrementAndGet()));
            case "thenComposeOptional" -> source.thenComposeOptionalAsync(executor, value -> Task.completed(calls.incrementAndGet()));
            default -> throw new AssertionError(method);
        };
        CompletableFuture<@Nullable Throwable> observed = new CompletableFuture<>();
        Task<?> root = continuation.whenComplete((value, cause) -> observed.complete(cause));
        FutureTask<CancellationException> outcome = new FutureTask<>(() -> assertThrows(CancellationException.class, root::run));
        Schedulers.io().execute(outcome);
        Runnable callback = submitted.get(10, TimeUnit.SECONDS);
        root.cancel();
        callback.run();

        assertSame(outcome.get(10, TimeUnit.SECONDS), observed.get(10, TimeUnit.SECONDS));
        assertEquals(0, calls.get());
        assertEquals(1, source.resultNow());
    }

    /// A cancelled parent leaves an unsubmitted child available for reuse through either submission API.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavesNewChildUnsubmittedAfterCancellation(boolean batch) throws Exception {
        Task<Integer> child = Task.completed(42).withStage(new TaskStage("child"));
        Task<Integer> completed = Task.completed(1);
        completed.run();
        Task<Integer> root = new Task<>() {
            /// Cancels the tree before attempting to submit the child.
            @Override
            public Integer execute(TaskExecutionContext context) throws Exception {
                cancel();
                assertThrows(CancellationException.class, () -> context.run(completed));
                assertThrows(CancellationException.class, () -> context.runAll(List.of(completed)));
                return batch ? context.runAll(List.of(child)).get(0) : context.run(child);
            }
        };
        StageEvents events = new StageEvents();
        root.addTaskListener(events);
        assertThrows(CancellationException.class, root::run);
        assertEquals(Task.State.NEW, child.getState());
        assertTrue(events.started.isEmpty());
        assertEquals(List.of(root), events.completions.stream().map(TaskCompletion::task).toList());
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

    /// A successful batch, including an empty one, returns true. A failed child retains its cause.
    @Test
    void reportsTryRunOutcome() throws Exception {
        Task<?> root = task(context -> {
            assertTrue(context.tryRunAll(List.of()));
            assertTrue(context.tryRun(Task.completed(false)));
            assertTrue(context.tryRunAll(List.of(Task.completed(1), Task.completed(2))));
            for (Throwable failure : List.of(new IOException(), new AssertionError(), new CancellationException())) {
                Task<?> child = Task.failed(failure);
                assertFalse(context.tryRun(child));
                assertSame(failure, child.exceptionNow());
                assertSame(failure, child.exceptionNowOrNull());
                boolean cancelled = failure instanceof CancellationException;
                assertEquals(cancelled ? Task.State.CANCELLED : Task.State.FAILED, child.getState());
                assertEquals(cancelled, child.isCancelled());
                assertSame(failure, assertThrows(failure.getClass(), () -> context.run(child)));
                Task<Integer> sibling = Task.completed(1);
                assertSame(failure, assertThrows(failure.getClass(), () -> context.runAll(List.of(child, sibling))));
                assertEquals(1, sibling.resultNow());
                assertFalse(context.isCancelled());
            }
            return null;
        });
        root.run();
        assertFalse(root.isCancellationRequested());
    }

    /// Cancelling the task tree interrupts a sequence that otherwise tolerates child failures.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tryRunPropagatesCancellation(boolean batch) {
        Task<?> child = Task.supplyAsync(() -> fail("Child body ran after cancellation"));
        AtomicInteger continued = new AtomicInteger();
        Task<?> root = task(context -> {
            if (batch)
                context.tryRunAll(List.of(child));
            else
                context.tryRun(child);
            return continued.incrementAndGet();
        });
        root.addTaskListener(new TaskListener() {
            /// Cancels the tree once the child begins.
            @Override
            public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
                if (task == child)
                    root.cancel();
            }
        });
        assertFalse(root.test());
        assertInstanceOf(CancellationException.class, root.exceptionNowOrNull());
        assertTrue(child.isCancelled());
        assertEquals(0, continued.get());
    }

    /// A failed sibling does not prevent concurrent work from completing before the batch returns.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainsConcurrentSiblingsOnFailure(boolean ignoreFailures) throws Exception {
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
            if (ignoreFailures)
                assertFalse(context.tryRunAll(List.of(first, second)));
            else
                assertSame(failure, assertThrows(IOException.class, () -> context.runAll(List.of(first, second))));
            assertEquals(1, finished.get());
            assertEquals(1, second.resultNow());
            assertEquals(Task.State.FAILED, first.getState());
            return null;
        }).run();
    }

    /// Failure to resolve a child's executor propagates through its decorators and allows every sibling to finish.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainsSiblingsWhenExecutorSelectionFails(boolean decorate) {
        IllegalStateException failure = new IllegalStateException("No executor available");
        Task<Integer> broken = new Task<>() {
            @Override
            protected Executor getExecutor() {
                throw failure;
            }

            @Override
            public Integer execute(TaskExecutionContext context) {
                return fail("A task with a failed executor selection should not start");
            }
        };
        Task<Integer> counted = decorate ? broken.withCounter() : broken;
        Task<Integer> branch = decorate ? counted.withStage(new TaskStage("download")) : broken;
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
            assertSame(failure, assertThrows(IllegalStateException.class, Task.allOf(first, branch, last)::run));
            assertSame(failure, branch.exceptionNow());
            assertSame(failure, counted.exceptionNow());
            assertSame(failure, broken.exceptionNow());
            assertSame(failure, broken.exceptionNowOrNull());
            assertNull(broken.resultNowOrNull());
            assertEquals(Task.State.FAILED, broken.getState());
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
        batch.addTaskListener(new TaskListener() {
            /// Releases the first child after the second child's result has been published.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                if (task == second)
                    released.countDown();
            }
        });
        assertTrue(batch.test());
        assertEquals(Arrays.asList("first", null), batch.resultNow());
        assertTrue(Task.allOf().run().isEmpty());
        assertTrue(Task.allOf(List.of(), 1).run().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Task.allOf(List.of(), 0).run());
    }

    /// Published and newly computed results occupy their respective input positions in each batch.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixesCompletedTasksWithNewChildren(boolean bounded) throws Exception {
        Task<@Nullable String> empty = Task.completed(null);
        Task<String> previous = Task.completed("previous");
        assertEquals(Arrays.asList(null, "previous"), Task.allOf(empty, previous).run());

        Task<String> first = Task.completed("first");
        Task<String> last = Task.completed("last");
        task(context -> {
            assertNull(context.run(empty));
            @Unmodifiable List<Task<? extends @Nullable String>> inputs = List.of(first, empty, previous, last);
            assertEquals(Arrays.asList("first", null, "previous", "last"),
                    bounded ? context.runAll(inputs, 1) : context.runAll(inputs));
            return null;
        }).run();
    }

    /// A bounded batch serves callbacks and counts only unfinished branches, even when they start during submission.
    @Test
    void refillsBoundedBatchWhenAnyBranchFinishes() throws Exception {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            Thread parentThread = parent.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            AtomicInteger submissions = new AtomicInteger();
            CompletableFuture<@Nullable Void> inContinuation = new CompletableFuture<>();
            Executor children = command -> {
                int submission = submissions.incrementAndGet();
                workers.execute(command);
                if (submission == 1)
                    inContinuation.orTimeout(10, TimeUnit.SECONDS).join();
            };
            CountDownLatch lastStarted = new CountDownLatch(1);
            Task<Integer> inline = Task.supplyAsync(Runnable::run, () -> 0);
            Task<Integer> first = task(children, context -> context.run(Task.completed(1).thenApply(value -> {
                inContinuation.complete(null);
                assertTrue(lastStarted.await(10, TimeUnit.SECONDS));
                return value;
            })));
            Task<@Nullable Integer> second = task(children, context -> context.call(parent, () -> {
                assertSame(parentThread, Thread.currentThread());
                assertEquals(2, submissions.get());
                return null;
            }));
            Task<Integer> last = task(children, context -> {
                lastStarted.countDown();
                return 3;
            });
            Task<List<@Nullable Integer>> batch = Task.allOf(List.of(inline, first, second, last), 2);
            assertEquals(Arrays.asList(0, 1, null, 3), task(parent, context -> context.run(batch)).run());
            assertEquals(3, submissions.get());
        } finally {
            workers.shutdownNow();
            parent.shutdownNow();
        }
    }

    /// A duplicate or failed submission does not consume a slot or hide a later error.
    @Test
    void drainsBoundedBatchAfterFailures() throws Exception {
        Task<Integer> shared = Task.completed(1);
        RejectedExecutionException rejection = new RejectedExecutionException("Download unavailable");
        Task<Integer> rejected = Task.supplyAsync(command -> {
            throw rejection;
        }, () -> fail("Unexpected execution"));
        IOException failure = new IOException("Download failed");
        Task<Integer> failed = Task.failed(failure);
        CancellationException cancellation = new CancellationException();
        Task<Integer> cancelled = Task.failed(cancellation);
        AssertionError error = new AssertionError("Fatal download failure");
        Task<Integer> fatal = Task.failed(error);
        Task<Integer> last = Task.completed(6);
        task(context -> {
            assertSame(error, assertThrows(AssertionError.class, () ->
                    context.runAll(List.of(shared, shared, rejected, failed, cancelled, fatal, last), 2)));
            assertEquals(1, shared.resultNow());
            assertSame(rejection, rejected.exceptionNow());
            assertSame(failure, failed.exceptionNow());
            assertSame(cancellation, cancelled.exceptionNow());
            assertSame(error, fatal.exceptionNow());
            assertEquals(6, last.resultNow());
            return null;
        }).run();
    }

    /// Cancelling either kind of batch drains admitted children and leaves the rest available for reuse.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBatchAdmissionOnCancellation(boolean bounded) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        Task<Integer> running = task(Schedulers.io(), context -> {
            entered.countDown();
            assertTrue(cancelled.await(10, TimeUnit.SECONDS));
            return 1;
        });
        Task<Integer> trigger = task(bounded ? Schedulers.io() : Runnable::run, context -> {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            return 2;
        });
        Task<Integer> pending = Task.completed(3);
        @Unmodifiable List<Task<Integer>> children = List.of(running, trigger, pending);
        Task<?> root = bounded ? Task.allOf(children, 2) : Task.allOf(children);
        List<Task<?>> finished = new CopyOnWriteArrayList<>();
        root.addTaskListener(new TaskListener() {
            /// Cancels the root when the trigger publishes its result.
            @Override
            public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                finished.add(task);
                if (task == trigger) {
                    root.cancel();
                    cancelled.countDown();
                }
            }
        });
        assertThrows(CancellationException.class, root::run);
        assertTrue(running.isCancelled());
        assertEquals(2, trigger.resultNow());
        assertFalse(finished.contains(pending));
        assertEquals(Task.State.NEW, pending.getState());
        assertEquals(3, pending.run());
    }

    /// The default bounded implementation stops later batches only when the whole tree is cancelled.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsDefaultBatchesOnTreeCancellation(boolean cancelTree) {
        Task<Integer> first = cancelTree ? Task.completed(1) : Task.failed(new CancellationException());
        Task<Integer> last = Task.completed(2);
        AtomicInteger batches = new AtomicInteger();
        Task<?> root = task(context -> new DefaultBatchContext(context, batches).runAll(List.of(first, last), 1));
        if (cancelTree) {
            root.addTaskListener(new TaskListener() {
                /// Cancels the tree after the first batch's only child succeeds.
                @Override
                public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                    if (task == first)
                        root.cancel();
                }
            });
        }
        assertThrows(CancellationException.class, root::run);
        assertEquals(cancelTree ? 1 : 2, batches.get());
        assertEquals(cancelTree ? Task.State.NEW : Task.State.SUCCEEDED, last.getState());
    }

    /// An interrupted window still serves admitted callbacks and reports a child's fatal failure ahead of interruption.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drainsInterruptedWindow(boolean fatal) {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                AssertionError error = new AssertionError("Admitted child failed");
                Task<Integer> pending = Task.completed(2);
                task(parent, context -> {
                    Thread submitter = Thread.currentThread();
                    Task<Integer> child = task(Schedulers.io(), childContext -> {
                        childContext.call(parent, () -> {
                            assertSame(submitter, Thread.currentThread());
                            submitter.interrupt();
                            return null;
                        });
                        return childContext.call(parent, () -> {
                            assertSame(submitter, Thread.currentThread());
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
                        assertTrue(submitter.isInterrupted());
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

    /// An interruption while draining a successful child precedes an ordinary failure from a later child.
    @Test
    void retainsInterruptionBeforeLaterFailure() {
        ExecutorService parent = Executors.newSingleThreadExecutor();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> task(parent, context -> {
                Task<Integer> first = task(Schedulers.io(), child -> child.call(parent, () -> {
                    Thread.currentThread().interrupt();
                    return 1;
                }));
                IOException failure = new IOException("Second child failed");
                Task<Integer> second = Task.failed(failure);
                try {
                    assertThrows(InterruptedException.class, () -> context.runAll(List.of(first, second)));
                    assertTrue(Thread.currentThread().isInterrupted());
                    assertEquals(1, first.resultNow());
                    assertSame(failure, second.exceptionNow());
                } finally {
                    Thread.interrupted();
                }
                return null;
            }).run());
        } finally {
            parent.shutdownNow();
        }
    }

    /// A root's inline continuation runs on the same task worker as its inherited source.
    @Test
    void rootContinuationUsesTaskWorker() throws Exception {
        Task<Thread> source = Task.supplyAsync(Thread::currentThread);
        Thread callback = source.thenApply(ignored -> Thread.currentThread()).run();
        assertNotSame(Thread.currentThread(), callback);
        assertSame(source.resultNow(), callback);
    }

    /// Asynchronous callbacks without an explicit executor use the task scheduler from another executor.
    @Test
    void asyncContinuationsUseTaskScheduler() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicInteger invocations = new AtomicInteger();
        Callable<Integer> checkWorker = () -> {
            Thread worker = Thread.currentThread();
            assertNotSame(caller, worker);
            // A nested task on the same executor runs inline on this worker.
            assertSame(worker, Task.supplyAsync(Schedulers.tasks(), Thread::currentThread).run());
            return invocations.incrementAndGet();
        };

        Task<@Nullable Integer> chain = Task.completed(0)
                .thenApplyAsync(value -> checkWorker.call())
                .thenAcceptAsync(value -> checkWorker.call())
                .thenRunAsync(checkWorker::call)
                .thenComposeAsync(() -> Task.completed(checkWorker.call()))
                .thenComposeAsync(value -> Task.completed(checkWorker.call()))
                .whenCompleteAsync((value, failure) -> {
                    assertNull(failure);
                    assertEquals(5, value);
                    checkWorker.call();
                })
                .thenSupplyAsync(checkWorker)
                .thenComposeOptionalAsync(value -> Task.completed(checkWorker.call()));
        assertEquals(8, task(Runnable::run, context -> context.run(chain)).run());
    }

    /// Inline continuations inherit the enclosing task's worker even when the source runs elsewhere.
    @Test
    void nestedContinuationsUseEnclosingWorker() throws Exception {
        Thread caller = Thread.currentThread();
        Task<Thread> source = Task.supplyAsync(Schedulers.io(), Thread::currentThread);
        Task<Thread> supplied = source.thenComposeOptional(sourceThread -> {
            assertNotSame(caller, sourceThread);
            assertSame(caller, Thread.currentThread());
            return Task.completed(1);
        }).thenSupply(Thread::currentThread);
        Task<Thread> finished = supplied.whenComplete((value, failure) -> {
            assertNull(failure);
            assertSame(caller, value);
            assertSame(caller, Thread.currentThread());
        });
        assertSame(caller, task(Runnable::run, context -> context.run(finished)).run());
        assertSame(caller, supplied.resultNow());
    }

    /// A null executor uses the enclosing worker for task factories and callbacks, preserving checked failures.
    @Test
    void nullExecutorUsesEnclosingWorker() {
        Thread caller = Thread.currentThread();
        IOException failure = new IOException("Callback failed");
        Task<Thread> source = Task.composeAsync(null, () -> {
            assertSame(caller, Thread.currentThread());
            return Task.supplyAsync(null, Thread::currentThread);
        });
        Task<?> continuation = source.thenApplyAsync(null, worker -> {
            assertSame(caller, worker);
            assertSame(caller, Thread.currentThread());
            throw failure;
        });
        Task<?> root = task(Runnable::run, context -> context.run(continuation));
        assertSame(failure, assertThrows(IOException.class, root::run));
    }

    /// An explicit null executor runs each continuation callback on the enclosing worker.
    @Test
    void nullContinuationExecutorUsesEnclosingWorker() throws Exception {
        Thread caller = Thread.currentThread();
        Task<Thread> continuation = Task.completed(1)
                .thenApplyAsync(null, value -> Thread.currentThread())
                .thenAcceptAsync(null, applyThread -> {
                    assertSame(caller, applyThread);
                    assertSame(caller, Thread.currentThread());
                })
                .thenRunAsync(null, () -> assertSame(caller, Thread.currentThread()))
                .thenComposeAsync(null, () -> Task.completed(Thread.currentThread()))
                .thenComposeAsync(null, supplierThread -> {
                    assertSame(caller, supplierThread);
                    return Task.completed(Thread.currentThread());
                })
                .whenCompleteAsync(null, (value, failure) -> {
                    assertNull(failure);
                    assertSame(caller, value);
                    assertSame(caller, Thread.currentThread());
                });
        assertSame(caller, task(Runnable::run, context -> context.run(continuation)).run());
    }

    /// A continuation's body inherits its parent's executor and dispatches the callback on the selected executor.
    @Test
    void separatesContinuationBodyAndCallbackExecutors() throws Exception {
        Thread caller = Thread.currentThread();
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        try {
            Thread worker = callbacks.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            Task<Thread> source = Task.supplyAsync(Thread::currentThread);
            Task<Thread> continuation = source
                    .whenCompleteAsync(callbacks, (value, failure) -> {
                        assertNull(failure);
                        assertSame(caller, value);
                        assertSame(worker, Thread.currentThread());
                    })
                    .thenApplyAsync(callbacks, dispatchThread -> {
                        assertSame(caller, dispatchThread);
                        return Thread.currentThread();
                    });
            Thread actual = task(Runnable::run, context -> context.run(continuation)).run();
            assertSame(worker, actual);
        } finally {
            callbacks.shutdownNow();
        }
    }

    /// Handlers dispatch successful and failed outcomes to the selected callback executor.
    @Test
    void dispatchesHandleCallbacks() throws Exception {
        Thread caller = Thread.currentThread();
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        try {
            Thread explicitWorker = callbacks.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            IOException cause = new IOException("Handler failed");
            Task<Thread> chain = Task.supplyAsync(callbacks, Thread::currentThread)
                    .handle((sourceThread, failure) -> {
                        assertNull(failure);
                        assertSame(explicitWorker, sourceThread);
                        return Thread.currentThread();
                    })
                    .handleAsync((inlineThread, failure) -> {
                        assertNull(failure);
                        assertSame(caller, inlineThread);
                        Thread worker = Thread.currentThread();
                        assertNotSame(caller, worker);
                        assertSame(worker, Task.supplyAsync(Schedulers.tasks(), Thread::currentThread).run());
                        throw cause;
                    })
                    .handleAsync(callbacks, (value, failure) -> {
                        assertSame(cause, failure);
                        return Thread.currentThread();
                    })
                    .handleAsync(null, (callbackThread, failure) -> {
                        assertNull(failure);
                        assertSame(explicitWorker, callbackThread);
                        return Thread.currentThread();
                    });
            assertSame(caller, task(Runnable::run, context -> context.run(chain)).run());
        } finally {
            callbacks.shutdownNow();
        }
    }

    /// Stacked stage decorators share the source worker and preserve their separate lifecycle events.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dispatchesStageDecoratorsOnSourceExecutor(boolean explicit) throws Exception {
        ExecutorService sourcePool = Executors.newSingleThreadExecutor();
        try {
            Thread expected = explicit ? sourcePool.submit(Thread::currentThread).get(10, TimeUnit.SECONDS) : Thread.currentThread();
            AtomicInteger resolutions = new AtomicInteger();
            Task<Thread> source = new Task<>() {
                /// Records each query for the source's executor preference.
                @Override
                protected @Nullable Executor getExecutor() {
                    assertEquals(Task.State.READY, getState());
                    resolutions.incrementAndGet();
                    return explicit ? sourcePool : null;
                }

                /// Returns the worker executing the source body.
                @Override
                public Thread execute(TaskExecutionContext context) {
                    return Thread.currentThread();
                }
            };
            TaskStage.Hint hint = new TaskStage.Hint(new TaskStage("download"));
            Task<Thread> counted = source.withCounter();
            Task<Thread> staged = counted.withStage(hint.stage());
            Task<Thread> hinted = staged.withStageHints(hint);
            Task<List<Thread>> root = task(Runnable::run, context -> context.runAll(List.of(hinted)));
            Map<Task<?>, Thread> workers = new HashMap<>();
            StageEvents events = new StageEvents() {
                /// Records the worker entering each task body.
                @Override
                public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
                    workers.put(task, Thread.currentThread());
                }
            };
            root.addTaskListener(events);

            assertEquals(0, resolutions.get());
            assertEquals(List.of(expected), root.run());
            assertEquals(1, resolutions.get());
            assertEquals(Map.of(root, Thread.currentThread(), hinted, expected, staged, expected,
                    counted, expected, source, expected), workers);
            assertEquals(List.of(source, counted, staged, hinted, root),
                    events.completions.stream().map(TaskCompletion::task).toList());
        } finally {
            sourcePool.shutdownNow();
        }
    }

    /// Counted downloads can fill their pool while an independent job uses a spare parent worker.
    @Test
    void countedBatchLeavesParentWorkerAvailable() throws Exception {
        ExecutorService parents = Executors.newFixedThreadPool(2);
        ExecutorService downloads = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch entered = new CountDownLatch(3);
            List<Task<Integer>> children = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                children.add(task(downloads, context -> {
                    entered.countDown();
                    assertTrue(entered.await(10, TimeUnit.SECONDS));
                    return parents.submit(() -> 1).get(10, TimeUnit.SECONDS);
                }).withCounter());
            }
            assertEquals(List.of(1, 1, 1), task(parents, context -> context.runAll(children)).run());
        } finally {
            parents.shutdownNow();
            downloads.shutdownNow();
        }
    }

    /// Rejection or cancellation before execution closes every reserved decorator from the inside out.
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
        StageEvents events = new StageEvents() {
            /// Records items reported before the stage closes.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                completedItems.addAndGet(completedDelta);
            }
        };
        root.addTaskListener(events);
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
        assertEquals(List.of(source, counted, root), events.completions.stream().map(TaskCompletion::task).toList());
        for (TaskCompletion completion : events.completions) {
            assertSame(failure, completion.failure());
            assertSame(failure, completion.task().exceptionNow());
            assertEquals(cancel ? Task.State.CANCELLED : Task.State.FAILED, completion.task().getState());
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

        for (Throwable failure : List.of(new IOException(), new AssertionError())) {
            Task<?> failed = Task.failed(failure);
            assertSame(failure, assertThrows(failure.getClass(), failed::run));
            assertSame(failed, failed.start());
            assertFalse(failed.test());
            assertSame(failure, assertThrows(failure.getClass(), failed::run));
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
        Task<Integer> first = Task.completed(11);
        Task<Integer> last = Task.completed(22);
        task(context -> {
            @Unmodifiable List<Task<Integer>> children = List.of(first, shared, shared, last);
            assertThrows(IllegalStateException.class, () -> {
                switch (batch) {
                    case "unbounded" -> context.runAll(children);
                    case "bounded" -> context.runAll(children, 1);
                    case "defaultBounded" -> new DefaultBatchContext(context, new AtomicInteger()).runAll(children, 1);
                    default -> throw new AssertionError(batch);
                }
            });
            assertEquals(11, first.resultNow());
            assertEquals(22, last.resultNow());
            assertEquals(1, context.run(shared));
            return null;
        }).run();
        assertEquals(1, calls.get());
        assertEquals(1, shared.resultNow());
        assertEquals(Task.State.SUCCEEDED, shared.getState());
        assertNull(shared.exceptionNowOrNull());
    }

    /// Competing decorators cannot resolve or fail a source already executing in another root.
    @Test
    void rejectsCompetingDecoratorWithoutChangingSource() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<Integer> source = new Task<>() {
            /// Counts how many dispatches inspect the source's executor preference.
            @Override
            protected Executor getExecutor() {
                resolutions.incrementAndGet();
                return Schedulers.io();
            }

            /// Keeps the source running while the other root tries to dispatch it.
            @Override
            public Integer execute(TaskExecutionContext context) throws InterruptedException {
                running.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return 42;
            }
        };
        Task<Integer> winner = source.withCounter();
        Task<Integer> contender = source.withStage(new TaskStage("download"));
        FutureTask<Integer> result = new FutureTask<>(winner::run);
        Schedulers.io().execute(result);
        try {
            assertTrue(running.await(10, TimeUnit.SECONDS));
            assertSame(assertThrows(IllegalStateException.class, contender::run), contender.exceptionNow());
            assertEquals(Task.State.RUNNING, source.getState());
        } finally {
            release.countDown();
        }
        assertEquals(42, result.get(10, TimeUnit.SECONDS));
        assertEquals(42, source.resultNow());
        assertEquals(1, resolutions.get());
    }

    /// A notification uses the registration order captured before a callback changes the listeners.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void snapshotsListenersForEachNotification(boolean useRun) throws Exception {
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

        if (useRun)
            assertEquals(1, root.run());
        else
            assertTrue(root.test());
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

    /// A pool that rejects compensation can still wait for an external completion signal.
    @Test
    void waitsForFakeProgressWhenForkJoinPoolRejectsCompensation() throws Exception {
        CountDownLatch saturated = new CountDownLatch(1);
        ForkJoinPool pool = new ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null,
                false, 1, 1, 1, ignored -> {
                    saturated.countDown();
                    return false;
                }, 60, TimeUnit.SECONDS);
        AtomicBoolean done = new AtomicBoolean();
        Task<Integer> wrapper = Task.completed(42).withFakeProgress(new TaskName.Simple("Waiting"), done::get, 1.0);
        Task<Integer> root = task(pool, context -> context.run(wrapper));
        FutureTask<Integer> result = new FutureTask<>(root::run);
        try {
            Schedulers.io().execute(result);
            assertTrue(saturated.await(10, TimeUnit.SECONDS));
            done.set(true);
            assertEquals(42, result.get(10, TimeUnit.SECONDS));
        } finally {
            done.set(true);
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

        root.addTaskListener(events);
        assertTrue(root.test());
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
        root.addTaskListener(events);
        assertTrue(root.test());
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
        root.addTaskListener(events);
        root.cancel();
        assertFalse(root.test());
        CancellationException failure = assertInstanceOf(CancellationException.class, root.exceptionNow());
        assertFalse(root.test());
        assertSame(root, root.start());
        assertSame(failure, assertThrows(CancellationException.class, root::run));
        assertSame(failure, root.exceptionNow());
        assertEquals(Task.State.CANCELLED, root.getState());
        assertEquals(1, events.started.size());
        assertSame(root, events.started.get(0).task());
        assertTrue(events.running.isEmpty());
        assertEquals(List.of(new TaskCompletion(root, events.started.get(0), failure)), events.completions);
        assertEquals(events.started, events.finished);
        assertEquals(List.of(failure), events.failures);
    }

    /// Fresh decorators use the parent's worker and report progress once for an already completed source.
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
        List<Thread> workers = new ArrayList<>();
        StageEvents events = new StageEvents() {
            /// Records which worker increments the new stage's item count.
            @Override
            public void onStageProgress(TaskStage.Scope scope, int completedDelta, int totalDelta) {
                workers.add(Thread.currentThread());
                progress.add(new StageProgress(scope, completedDelta, totalDelta));
            }
        };
        assertEquals(42, parent.addTaskListener(events).run());
        Task<Integer> continuation = staged.thenApply(value -> value + 1);
        assertEquals(43, continuation.addTaskListener(events).run());
        assertEquals(List.of(staged), events.started.stream().map(TaskStage.Scope::task).toList());
        assertEquals(events.started, events.finished);
        assertEquals(List.of(new StageProgress(events.started.get(0), 1, 0)), progress);
        assertEquals(List.of(Thread.currentThread()), workers);
        assertEquals(List.of(counted, staged, parent, continuation), events.completions.stream().map(TaskCompletion::task).toList());
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

    /// Counts batch submissions made by the default bounded implementation and forwards them to the task scheduler.
    private record DefaultBatchContext(TaskExecutionContext delegate, AtomicInteger batches) implements TaskExecutionContext {
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
            batches.incrementAndGet();
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
