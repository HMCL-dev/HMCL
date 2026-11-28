// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises cooperative waits for tasks whose execution already has an owner.
@NotNullByDefault
final class TaskSharedWaitTest {
    /// The first caller and later observers get the same published result if a display name fails to render.
    @Test
    void completionLoggingPreservesSharedOutcome() throws Exception {
        Task<Integer> task = Task.completed(42);
        task.setName(() -> {
            if (task.getState() == Task.State.SUCCEEDED)
                throw new IllegalStateException("Cannot render completed task");
            return "shared";
        });
        assertEquals(42, task.run());
        assertEquals(42, task.run());
    }

    /// Waiting for another root completes even when it needs the same single-thread executor.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsForSharedRootOnSingleWorker(boolean descendant) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Task<Integer> onWorker = Task.supplyAsync(worker, () -> 42);
            Task<Integer> shared = descendant ? Task.composeAsync(() -> onWorker) : onWorker;
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Task.runAsync(worker, () -> {
                shared.start();
                if (descendant) {
                    assertTrue(shared.test());
                    assertEquals(42, shared.resultNow());
                } else {
                    assertEquals(42, shared.run());
                }
            }).run());
        } finally {
            worker.shutdownNow();
        }
    }

    /// A shared wait ends when the child finishes, even if the caller was interrupted or the parent is still active.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void waitsForInlineChildIndependentlyOfParent(boolean interrupted) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch childRunning = new CountDownLatch(1);
            CountDownLatch observing = new CountDownLatch(1);
            CountDownLatch observed = new CountDownLatch(1);
            Task<Integer> child = Task.composeAsync(() -> {
                childRunning.countDown();
                assertTrue(observing.await(10, TimeUnit.SECONDS));
                return Task.supplyAsync(worker, () -> 42);
            });
            Task<Integer> parent = child.thenApply(value -> {
                assertTrue(observed.await(10, TimeUnit.SECONDS));
                return value;
            });
            Task<Integer> observer = Task.supplyAsync(worker, () -> {
                assertTrue(childRunning.await(10, TimeUnit.SECONDS));
                observing.countDown();
                try {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                        assertThrows(InterruptedException.class, child::run);
                        assertTrue(Thread.interrupted());
                        return child.resultNow();
                    }
                    return child.run();
                } finally {
                    observed.countDown();
                }
            });
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                parent.start();
                assertEquals(42, observer.run());
                assertEquals(42, parent.run());
            });
        } finally {
            worker.shutdownNow();
        }
    }

    /// An observer waiting for a child's submission leaves queued siblings on its executor alone.
    @Test
    void observesPreparingChildWithoutRunningSibling() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch preparing = new CountDownLatch(1);
        CountDownLatch observing = new CountDownLatch(1);
        CompletableFuture<@Nullable Executor> executor = new CompletableFuture<>();
        AtomicBoolean observed = new AtomicBoolean();
        try {
            Thread observerThread = worker.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            Task<Integer> target = new Task<>() {
                /// Holds the child in READY after the sibling has been queued.
                @Override
                protected @Nullable Executor getExecutor() {
                    preparing.countDown();
                    return executor.orTimeout(10, TimeUnit.SECONDS).join();
                }

                /// {@inheritDoc}
                @Override
                public Integer execute(TaskExecutionContext context) {
                    return 42;
                }
            };
            Task<Boolean> sibling = Task.supplyAsync(worker, observed::get);
            Task<?> parent = Task.allOf(List.of(sibling, target));
            Task<Integer> observer = Task.supplyAsync(worker, () -> {
                parent.start();
                assertTrue(preparing.await(10, TimeUnit.SECONDS));
                observing.countDown();
                int result = target.run();
                observed.set(true);
                return result;
            });
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                observer.start();
                assertTrue(observing.await(10, TimeUnit.SECONDS));
                // The sole worker is now in target.run() or on its way there. Let it park
                // before allowing the target to finish selecting its executor.
                while (observerThread.getState() != Thread.State.WAITING)
                    Thread.sleep(1);
                executor.complete(null);
                assertEquals(42, observer.run());
                parent.run();
                assertTrue(sibling.resultNow());
            });
        } finally {
            executor.complete(null);
            worker.shutdownNow();
        }
    }

    /// Both inline and dispatched children reject waits on themselves and on the parent awaiting them.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsWaitsOnExecutingAncestors(boolean dispatchChild) {
        Task<Integer> root = new Task<>() {
            /// Checks reentry from the root and from its child.
            @Override
            public Integer execute(TaskExecutionContext context) throws Exception {
                Task<Integer> parent = this;
                assertThrows(IllegalStateException.class, parent::run);
                assertThrows(IllegalStateException.class, () -> context.call(Schedulers.io(), parent::run));
                return context.run(new Task<Integer>() {
                    /// Inherits the root worker or dispatches the child to a different executor.
                    @Override
                    protected @Nullable Executor getExecutor() {
                        return dispatchChild ? Schedulers.io() : null;
                    }

                    /// Neither task can finish until this body returns.
                    @Override
                    public Integer execute(TaskExecutionContext childContext) {
                        assertThrows(IllegalStateException.class, this::run);
                        assertThrows(IllegalStateException.class, parent::test);
                        return 42;
                    }
                });
            }
        };
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(42, root.run()));
    }

    /// A nested root on another worker can wait on its starter only when started asynchronously.
    @Test
    void distinguishesSynchronousNestedRootFromAsyncObserver() {
        ExecutorService nestedWorker = Executors.newSingleThreadExecutor();
        try {
            Task<Task<Integer>> parent = new Task<>() {
                /// Runs on the calling thread, separately from the nested roots.
                @Override
                protected Executor getExecutor() {
                    return Runnable::run;
                }

                /// Launches one nested root synchronously and another independently.
                @Override
                public Task<Integer> execute(TaskExecutionContext context) throws Exception {
                    Task.runAsync(nestedWorker, () -> assertThrows(IllegalStateException.class, this::run)).run();

                    CountDownLatch observing = new CountDownLatch(1);
                    Task<Integer> observer = Task.supplyAsync(nestedWorker, () -> {
                        observing.countDown();
                        this.run();
                        return 42;
                    });
                    observer.start();
                    assertTrue(observing.await(10, TimeUnit.SECONDS));
                    // The parent can finish only after the observer services this callback.
                    context.call(nestedWorker, () -> true);
                    return observer;
                }
            };
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(42, parent.run().run()));
        } finally {
            nestedWorker.shutdownNow();
        }
    }

    /// A nested root cannot wait for a source that needs the enclosing decorator's listener to return.
    @Test
    void rejectsWaitOnInlineSourceFromNestedRoot() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Task<Integer> source = Task.completed(42);
            Task<Integer> decorated = source.withCounter().withStage(new TaskStage("download"));
            AtomicBoolean rejected = new AtomicBoolean();
            decorated.addTaskListener(new TaskListener() {
                /// Tries to observe the reserved source on another worker before the decorator starts it.
                @Override
                public void onTaskStarted(Task<?> task, TaskStage.@Nullable Scope scope) {
                    if (task == decorated) {
                        Task<Integer> nested = Task.supplyAsync(boundedDispatch(worker), source::run);
                        assertThrows(IllegalStateException.class, nested::run);
                        rejected.set(true);
                    }
                }
            });
            assertEquals(42, decorated.run());
            assertTrue(rejected.get());
        } finally {
            worker.shutdownNow();
        }
    }

    /// A nested root's synchronous caller waits until its completion listener returns.
    @Test
    void rejectsWaitingOnCallerFromNestedCompletion() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Executor dispatch = boundedDispatch(worker);
        AtomicBoolean listenerReturned = new AtomicBoolean();
        try {
            Task<Integer> root = new Task<>() {
                /// Runs the nested root synchronously on the other worker.
                @Override
                public Integer execute(TaskExecutionContext context) throws Exception {
                    Task<Integer> caller = this;
                    Task<Integer> nested = Task.supplyAsync(dispatch, () -> 42);
                    nested.addTaskListener(new TaskListener() {
                        /// Checks reentry after the nested result has been published.
                        @Override
                        public void onTaskFinished(Task<?> task, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                            if (failure == null) {
                                assertThrows(IllegalStateException.class, caller::run);
                                listenerReturned.set(true);
                            }
                        }
                    });
                    return nested.run();
                }
            };
            assertEquals(42, root.run());
            assertTrue(listenerReturned.get());
        } finally {
            worker.shutdownNow();
        }
    }

    /// The start listener cannot wait for the root it is about to run.
    @Test
    void rejectsSelfWaitFromStartListener() {
        Task<Integer> root = Task.completed(42);
        AtomicBoolean listenerReturned = new AtomicBoolean();
        root.addTaskListener(new TaskListener() {
            /// A waiting reentry would prevent the original caller from submitting the body.
            @Override
            public void onExecutionStarted(Task<?> task) {
                assertThrows(IllegalStateException.class, task::run);
                listenerReturned.set(true);
            }
        });
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(42, root.run()));
        assertTrue(listenerReturned.get());
    }

    /// Bounds the wait for dispatched work so a missed cycle releases the caller and its listener.
    private static Executor boundedDispatch(ExecutorService worker) {
        return command -> {
            try {
                worker.submit(command).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };
    }
}
