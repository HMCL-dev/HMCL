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

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe lazy initialization wrapper.
 *
 * @param <T> value type
 */
public final class SynchronizedLazy<T> {
    private final Supplier<T> supplier;
    private Result<T> result = null;

    private final ReentrantLock lock = new ReentrantLock();

    public SynchronizedLazy(Supplier<@Nullable T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    public T get() {
        if (result != null) return result.value();
        lock.lock();
        try {
            if (result == null) result = new Result<>(supplier.get());
            return result.value();
        } finally {
            lock.unlock();
        }
    }

    private record Result<T>(T value) {
    }
}
