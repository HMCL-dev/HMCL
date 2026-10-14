/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
