// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import java.util.Objects;

public final class Holder<T> implements InvalidationListener {
    public T value;

    public Holder() {
    }

    public Holder(T value) {
        this.value = value;
    }

    @Override
    public void invalidated(Observable observable) {
        // no-op
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof Holder))
            return false;

        return Objects.equals(this.value, ((Holder<?>) obj).value);
    }

    @Override
    public String toString() {
        return "Holder[" + value + "]";
    }
}
