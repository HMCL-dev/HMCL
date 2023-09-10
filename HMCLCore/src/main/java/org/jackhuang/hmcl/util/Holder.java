package org.jackhuang.hmcl.util;

import java.util.Objects;

public final class Holder<T> {
    public T value;

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
