package org.jackhuang.hmcl.util.versioning;

import org.jetbrains.annotations.NotNull;

public interface VersionNumber<V extends VersionNumber<V>> extends Comparable<V> {
    @Override
    int compareTo(@NotNull V other);

    int compareTo(@NotNull String other);
}
