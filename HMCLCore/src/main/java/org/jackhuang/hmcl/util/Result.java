// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
