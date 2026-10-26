// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe exceptional lazy initialization wrapper.
 * <p>
 * On failure, the result is set to null in order to allow retrying.
 *
 * @param <T> value type
 */
public final class SynchronizedExceptionalLazy<T> {
    private final ExceptionalSupplier<@Nullable T, Exception> supplier;
    private volatile Result<T> result = null;

    private final ReentrantLock lock = new ReentrantLock();

    public SynchronizedExceptionalLazy(ExceptionalSupplier<@Nullable T, Exception> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    /// Gets and returns the result.
    ///
    /// If the previous attempt succeeded, the cached value is returned directly.
    ///
    /// Otherwise, the supplier is invoked to compute a new value.If this attempt fails,
    /// {@code null} is returned; if succeeded, the computed value is cached and returned.
    ///
    /// @return the result, or null on failure
    public T get() {
        if (result != null) return result.value();
        lock.lock();
        try {
            if (result == null) {
                try {
                    result = new Result<>(supplier.get());
                } catch (Exception e) {
                    result = null;
                    return null;
                }
            }
            return result.value();
        } finally {
            lock.unlock();
        }
    }

    private record Result<T>(T value) {
    }
}
