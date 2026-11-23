// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises the default scheduler through independently submitted task trees.
@NotNullByDefault
final class SchedulersTest {
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
