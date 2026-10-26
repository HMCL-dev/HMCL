// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.function;

import static java.util.Objects.requireNonNull;

public interface ExceptionalBiConsumer<T, U, E extends Exception> {
    void accept(T t, U u) throws E;

    default ExceptionalBiConsumer<T, U, ?> andThen(ExceptionalBiConsumer<? super T, ? super U, ?> after) {
        requireNonNull(after);

        return (l, r) -> {
            this.accept(l, r);
            after.accept(l, r);
        };
    }
}
