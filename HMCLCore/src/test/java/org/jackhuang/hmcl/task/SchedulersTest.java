// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.glavo.url.WebURL;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the default scheduler through independently submitted task trees.
@NotNullByDefault
final class SchedulersTest {
    /// The task and download executors provide independent workers on virtual-thread runtimes.
    @Test
    void identifiesExecutorsWithIndependentWorkers() {
        boolean virtualThreads = Runtime.version().feature() >= 21;
        Executor downloads = new GetTask(WebURL.parse("https://example.org/artifact")).getExecutor();
        assertEquals(virtualThreads, downloads instanceof UnboundedExecutorService);
        assertEquals(virtualThreads, Schedulers.tasks() instanceof UnboundedExecutorService);
        assertInstanceOf(UnboundedExecutorService.class, Schedulers.io());
        assertFalse(Schedulers.javafx() instanceof UnboundedExecutorService);
    }

    /// The public factory marks each new virtual-thread service as unbounded.
    @Test
    void identifiesNewVirtualThreadExecutors() {
        @Nullable ExecutorService created = Schedulers.newVirtualThreadPerTaskExecutor("Test");
        if (Runtime.version().feature() < 21) {
            assertNull(created);
            return;
        }

        assertNotNull(created);
        try {
            assertInstanceOf(UnboundedExecutorService.class, created);
        } finally {
            created.shutdownNow();
        }
    }

    /// Submissions keep the delegate's futures, cancellation policy, and shutdown lifecycle.
    @Test
    void delegatesUnboundedExecutorServiceOperations() throws Exception {
        ScheduledThreadPoolExecutor delegate = new ScheduledThreadPoolExecutor(1);
        delegate.setRemoveOnCancelPolicy(true);
        ExecutorService service = new UnboundedExecutorService(delegate);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            service.submit(() -> {
                started.countDown();
                release.await();
                return 1;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Future<Integer> cancelled = service.submit(() -> 2);
            assertSame(cancelled, delegate.getQueue().element());
            assertTrue(cancelled.cancel(false));
            assertTrue(delegate.getQueue().isEmpty());

            Future<Integer> pending = service.submit(() -> 3);
            service.shutdown();
            assertTrue(delegate.isShutdown());
            assertTrue(service.isShutdown());
            assertFalse(service.awaitTermination(0, TimeUnit.SECONDS));
            assertEquals(List.of(pending), service.shutdownNow());
            assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(service.isTerminated());
        } finally {
            release.countDown();
            delegate.shutdownNow();
        }
    }

    /// An independent root can run while earlier roots wait for work on another executor.
    @Test
    void runsIndependentRootWhileWorkersWaitForChildren() throws Exception {
        Executor scheduler = Schedulers.tasks();
        assertNotSame(ForkJoinPool.commonPool(), scheduler);
        int parents = Runtime.version().feature() < 21
                ? assertInstanceOf(ForkJoinPool.class, scheduler).getParallelism()
                : 4;

        ExecutorService children = Executors.newSingleThreadExecutor();
        CountDownLatch submitted = new CountDownLatch(parents);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(parents);
        Executor childExecutor = command -> {
            children.execute(command);
            submitted.countDown();
        };
        try {
            for (int i = 0; i < parents; i++)
                Task.runAsync(childExecutor, release::await).thenRunAsync(finished::countDown).start();
            assertTrue(submitted.await(10, TimeUnit.SECONDS));

            CountDownLatch independent = new CountDownLatch(1);
            Task.runAsync(independent::countDown).start();
            assertTrue(independent.await(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            children.shutdown();
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS));
    }
}
