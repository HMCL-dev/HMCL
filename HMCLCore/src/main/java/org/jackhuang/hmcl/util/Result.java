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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// @author Glavo
public final class Result<T> {
    public static <T> Result<T> success(T value) {
        return new Result<>(value, null);
    }

    public static <T> Result<T> failure(@NotNull Throwable exception) {
        Objects.requireNonNull(exception);
        return new Result<>(null, exception);
    }

    private final T value;
    private final Throwable exception;

    private Result(T value, Throwable exception) {
        this.value = value;
        this.exception = exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }

    public boolean isFailure() {
        return exception != null;
    }

    public T get() throws Throwable {
        if (exception != null)
            throw exception;
        else
            return value;
    }

    public @Nullable T getOrNull() {
        return exception == null ? value : null;
    }

    public @Nullable Throwable getException() {
        return exception;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, exception);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof Result<?> other
                && Objects.equals(value, other.value)
                && Objects.equals(exception, other.exception);
    }
}
