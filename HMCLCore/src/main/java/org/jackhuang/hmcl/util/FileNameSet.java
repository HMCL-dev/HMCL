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
