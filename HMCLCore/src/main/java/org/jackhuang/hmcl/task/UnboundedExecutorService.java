// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/// Marks an executor service that can start new work even when all its current workers are blocked.
@NotNullByDefault
final class UnboundedExecutorService implements ExecutorService {
    /// The service responsible for execution and termination.
    private final ExecutorService delegate;

    /// Wraps a service whose worker concurrency is unbounded.
    UnboundedExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    /// {@inheritDoc}
    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    /// {@inheritDoc}
    @Override
    public Future<? extends @Nullable Object> submit(Runnable task) {
        return delegate.submit(task);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                                                 long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    /// {@inheritDoc}
    @Override
    public <T extends @Nullable Object> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    /// {@inheritDoc}
    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    /// {@inheritDoc}
    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    /// {@inheritDoc}
    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    /// {@inheritDoc}
    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    /// {@inheritDoc}
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
