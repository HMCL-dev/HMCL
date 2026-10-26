// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Non thread-safe lazy initialization wrapper.
 *
 * @param <T> value type
 */
public final class Lazy<T> {
    private Supplier<T> supplier;
    private T value = null;

    public Lazy(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    public T get() {
        if (supplier != null) {
            value = supplier.get();
            supplier = null;
        }
        return value;
    }
}
