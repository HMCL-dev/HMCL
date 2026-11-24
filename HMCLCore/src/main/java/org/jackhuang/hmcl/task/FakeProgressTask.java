// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/// Runs the source task, then reports estimated progress until the completion condition is met.
@NotNullByDefault
final class FakeProgressTask<T extends @Nullable Object> extends Task<T> {
    /// Upper bound of the estimate while waiting for completion.
    private static final double MAX_ESTIMATED_PROGRESS = 0.98D;

    /// Task whose result is returned when the wait finishes.
    private final Task<T> source;

    /// Signals when to report full progress and return.
    private final BooleanSupplier done;

    /// Larger values make the progress estimate rise more slowly.
    private final double scale;

    /// Wraps the source with a progress estimate for the subsequent wait.
    FakeProgressTask(Task<T> source, BooleanSupplier done, double scale) {
        this.source = source;
        this.done = done;
        this.scale = scale;
    }

    /// {@inheritDoc}
    @Override
    public T execute(TaskExecutionContext context) throws Exception {
        T value = context.run(source);
        TaskProgress progress = context.progress();
        if (!done.getAsBoolean()) {
            progress.update(0.0D);
            long start = System.nanoTime();
            while (!done.getAsBoolean()) {
                context.checkCancellation();
                double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
                double scaledTime = elapsedSeconds * MAX_ESTIMATED_PROGRESS;
                progress.update(MAX_ESTIMATED_PROGRESS * scaledTime / (scale + scaledTime));
                ProgressDelay.await();
            }
        }
        progress.update(1.0D);
        return value;
    }

    /// One interruptible interval between progress updates.
    @NotNullByDefault
    private static final class ProgressDelay implements ForkJoinPool.ManagedBlocker {
        /// Whether this interval has elapsed.
        private boolean elapsed;

        /// Allows a fork/join worker to be compensated while waiting for the next update.
        static void await() throws InterruptedException {
            var delay = new ProgressDelay();
            try {
                ForkJoinPool.managedBlock(delay);
            } catch (RejectedExecutionException ignored) {
                // A pool at its thread limit can still poll an external completion signal.
                delay.block();
            }
        }

        /// {@inheritDoc}
        @Override
        public boolean isReleasable() {
            return elapsed;
        }

        /// {@inheritDoc}
        @Override
        public boolean block() throws InterruptedException {
            Thread.sleep(1000);
            elapsed = true;
            return true;
        }
    }
}
