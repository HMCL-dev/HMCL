// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.function.Predicate;

/// @author Glavo
@NotNullByDefault
public final class FileNameSet {
    public static FileNameSet list(Path directory, @Nullable Predicate<? super Path> predicate) throws IOException {
        try (var list = Files.list(directory)) {
            boolean caseSensitive = OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS
                    || directory.getFileSystem() != FileSystems.getDefault();
            var set = new FileNameSet(caseSensitive);

            list.forEachOrdered(path -> {
                if (predicate == null || predicate.test(path)) {
                    set.add(path.getFileName().toString());
                }
            });

            return set;
        }
    }

    private final boolean caseSensitive;

    private final HashSet<String> set = new HashSet<>();

    public FileNameSet(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean add(String name) {
        if (caseSensitive) {
            return set.add(name);
        } else {
            return set.add(name.toLowerCase(Locale.ROOT));
        }
    }

    public boolean contains(String name) {
        if (set.isEmpty())
            return false;

        if (caseSensitive) {
            return set.contains(name);
        } else {
            return set.contains(name.toLowerCase(Locale.ROOT));
        }
    }

    public boolean notContains(String name) {
        return !contains(name);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(caseSensitive) ^ set.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof FileNameSet that && this.caseSensitive == that.caseSensitive && this.set.equals(that.set);
    }

    @Override
    public String toString() {
        return set.toString();
    }
}
