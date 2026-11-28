// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises scheduling through the public executor service and independently submitted tasks.
@NotNullByDefault
final class SchedulersTest {
    /// A named service can run a second submission while its first is blocked and cancel the first.
    @Test
    void runsIndependentSubmissionsOnVirtualThreadRuntimes() throws Exception {
        @Nullable ExecutorService service = Schedulers.newVirtualThreadPerTaskExecutor("test-worker-");
        if (Runtime.version().feature() < 21) {
            assertNull(service);
            return;
        }
        assertNotNull(service);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> blocked = service.submit(() -> {
                started.countDown();
                release.await();
                return null;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread worker = service.submit(Thread::currentThread).get(10, TimeUnit.SECONDS);
            assertTrue(worker.getName().startsWith("test-worker-"));
            // The factory is also exposed on Java 17, which has no Thread.isVirtual() method.
            assertTrue((boolean) Thread.class.getMethod("isVirtual").invoke(worker));
            assertTrue(blocked.cancel(true));
            service.shutdown();
            assertTrue(service.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            service.shutdownNow();
        }
    }

    /// An independent root can run while earlier roots wait for work on another executor.
    @Test
    void runsIndependentRootWhileWorkersWaitForChildren() throws Exception {
        int parents = Schedulers.tasks() instanceof ForkJoinPool pool ? pool.getParallelism() : 4;

        CountDownLatch childrenRunning = new CountDownLatch(parents);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(parents);
        try {
            for (int i = 0; i < parents; i++) {
                Task.runAsync(Schedulers.io(), () -> {
                    childrenRunning.countDown();
                    release.await();
                }).thenRunAsync(finished::countDown).start();
            }
            assertTrue(childrenRunning.await(10, TimeUnit.SECONDS));

            CountDownLatch independent = new CountDownLatch(1);
            Task.runAsync(independent::countDown).start();
            assertTrue(independent.await(10, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(10, TimeUnit.SECONDS));
    }
}
