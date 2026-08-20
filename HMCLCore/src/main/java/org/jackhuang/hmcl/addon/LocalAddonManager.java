/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/// Manages local addon files for a single [DefaultGameInstance] snapshot member.
///
/// Each manager is bound to one instance wrapper and must not be shared across repository snapshot
/// copies. Callers obtain a manager from the current instance after a refresh or COW publish.
///
/// @param <T> the local addon file type managed by this manager
public abstract class LocalAddonManager<T extends LocalAddonFile> {

    /// File-name suffix used for disabled addon files.
    public static final String DISABLED_EXTENSION = ".disabled";

    /// File-name suffix used for backed-up (old) addon files.
    public static final String OLD_EXTENSION = ".old";

    /// Returns the display file name of an addon path with disable/old suffixes stripped.
    ///
    /// @param file the addon file path
    /// @return the file name without [#DISABLED_EXTENSION] or [#OLD_EXTENSION]
    public static String getLocalAddonName(Path file) {
        return StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
    }

    /// Lock guarding [#localFiles] and subclass mutable state.
    protected final ReentrantLock lock = new ReentrantLock();

    /// Loaded local addon files for the bound instance.
    protected final Set<@NotNull T> localFiles = new LinkedHashSet<>();

    /// The snapshot member this manager serves.
    protected final DefaultGameInstance instance;

    /// Creates a manager bound to the given instance.
    ///
    /// @param instance the snapshot member whose addon directory this manager operates on
    public LocalAddonManager(DefaultGameInstance instance) {
        this.instance = instance;
    }

    /// Returns the instance this manager is bound to.
    ///
    /// @return the bound [DefaultGameInstance]
    public DefaultGameInstance getInstance() {
        return instance;
    }

    /// Returns the directory that stores local addon files for the bound instance.
    ///
    /// @return the addon directory path
    public abstract Path getDirectory();

    /// Reloads local addon files from disk into [#localFiles].
    ///
    /// @throws IOException if the directory cannot be listed or a required instance path cannot be read
    public abstract void refresh() throws IOException;

    /// Returns the comparator used to order [#getLocalFiles()].
    ///
    /// @return the sort order for local addon files
    public abstract Comparator<T> getComparator();

    /// Returns the currently loaded local addon files, sorted by [#getComparator()].
    ///
    /// @return an unmodifiable sorted list of local addon files
    /// @throws IOException if loading is required and fails
    public @Unmodifiable List<T> getLocalFiles() throws IOException {
        lock.lock();
        try {
            return localFiles.stream().sorted(getComparator()).toList();
        } finally {
            lock.unlock();
        }
    }

    /// Marks an addon file as old (backed up) or restores it from the old location.
    ///
    /// When `old` is `true`, the file is renamed with [#OLD_EXTENSION] and removed from
    /// [#localFiles]. When `old` is `false`, the suffix is removed and the file is re-added.
    ///
    /// @param modFile the local addon file to update
    /// @param old     whether the file should be treated as a backup
    /// @return the path after the rename
    /// @throws IOException if the file cannot be moved
    public Path setOld(T modFile, boolean old) throws IOException {
        lock.lock();
        try {
            Path newPath;
            if (old) {
                newPath = backupFile(modFile.getFile());
                localFiles.remove(modFile);
            } else {
                newPath = restoreFile(modFile.getFile());
                localFiles.add(modFile);
            }
            return newPath;
        } finally {
            lock.unlock();
        }
    }

    public static Path backupFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.addSuffix(
                        StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION),
                        OLD_EXTENSION
                )
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }

    public static Path restoreFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.removeSuffix(FileUtils.getName(file), OLD_EXTENSION)
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }
}
