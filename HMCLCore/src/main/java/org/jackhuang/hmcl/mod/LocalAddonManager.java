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
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.game.GameRepository;
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

public abstract class LocalAddonManager<T extends LocalAddonFile> {

    public static final String DISABLED_EXTENSION = ".disabled";
    public static final String OLD_EXTENSION = ".old";

    public static String getLocalAddonName(Path file) {
        return StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
    }

    protected final Set<@NotNull T> localFiles = new LinkedHashSet<>();

    protected final GameRepository repository;
    protected final String id;

    public LocalAddonManager(GameRepository gameRepository, String versionId) {
        this.repository = gameRepository;
        this.id = versionId;
    }

    public GameRepository getRepository() {
        return repository;
    }

    public String getInstanceId() {
        return id;
    }

    public abstract Path getDirectory();

    public abstract void refresh() throws IOException;

    public abstract Comparator<T> getComparator();

    public @Unmodifiable List<T> getLocalFiles() throws IOException {
        return localFiles.stream().sorted(getComparator()).toList();
    }

    public Path setOld(T modFile, boolean old) throws IOException {
        Path newPath;
        if (old) {
            newPath = backupFile(modFile.getFile());
            localFiles.remove(modFile);
        } else {
            newPath = restoreFile(modFile.getFile());
            localFiles.add(modFile);
        }
        return newPath;
    }

    private Path backupFile(Path file) throws IOException {
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

    private Path restoreFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.removeSuffix(FileUtils.getName(file), OLD_EXTENSION)
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }
}
