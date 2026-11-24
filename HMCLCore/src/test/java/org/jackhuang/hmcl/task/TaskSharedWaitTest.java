// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
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

    /// A waiting worker can run either the shared root or a descendant requiring that same worker.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void helpsSharedExecutionOnSingleWorker(boolean descendant) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Task<Thread> onWorker = Task.supplyAsync(worker, Thread::currentThread);
            Task<Thread> shared = descendant ? Task.composeAsync(() -> onWorker) : onWorker;
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> Task.runAsync(worker, () -> {
                shared.start();
                if (descendant) {
                    assertTrue(shared.test());
                    assertSame(Thread.currentThread(), shared.resultNow());
                } else {
                    assertSame(Thread.currentThread(), shared.run());
                }
            }).run());
        } finally {
            worker.shutdownNow();
        }
    }

    /// A shared wait for an inline child finishes while its parent is still using the same worker.
    @Test
    void waitsForInlineChildIndependentlyOfParent() {
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
                int value = child.run();
                observed.countDown();
                return value;
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

    /// Both inline and dispatched children reject waits on themselves and on the parent awaiting them.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsWaitsOnExecutingAncestors(boolean dispatchChild) {
        Task<Integer> root = new Task<>() {
            /// Checks reentry from the root and from its child.
            @Override
            public Integer execute(TaskExecutionContext context) throws Exception {
                Task<Integer> parent = this;
                assertSame(parent, parent.start());
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
                        assertSame(this, start());
                        assertThrows(IllegalStateException.class, this::run);
                        assertThrows(IllegalStateException.class, parent::run);
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
                    Thread starter = Thread.currentThread();
                    Task.runAsync(nestedWorker, () -> {
                        assertNotSame(starter, Thread.currentThread());
                        assertThrows(IllegalStateException.class, this::run);
                    }).run();

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

    /// Synchronous lifecycle hooks reject self-waits even before the root's body has been dispatched.
    @Test
    void rejectsWaitsDuringRootPreparation() {
        Task<Integer> root = new Task<>() {
            /// Executor lookup is part of the first submission.
            @Override
            protected Executor getExecutor() {
                assertThrows(IllegalStateException.class, this::test);
                return Runnable::run;
            }

            /// Completes once the submission hooks return.
            @Override
            public Integer execute(TaskExecutionContext context) {
                return 42;
            }
        };
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
}
