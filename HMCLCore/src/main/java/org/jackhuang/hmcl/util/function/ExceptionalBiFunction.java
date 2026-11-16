// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.function;

import static java.util.Objects.requireNonNull;

public interface ExceptionalBiFunction<T, U, R, E extends Exception> {
    R apply(T t, U u) throws E;

    default <V> ExceptionalBiFunction<T, U, V, ?> andThen(ExceptionalFunction<? super R, ? extends V, ?> after) {
        requireNonNull(after);
        return (t, u) -> after.apply(apply(t, u));
    }
}
