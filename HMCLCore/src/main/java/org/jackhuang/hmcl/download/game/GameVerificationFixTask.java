/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Remove class digital verification file in game jar
 * @author huangyuhui
 */
public final class GameVerificationFixTask extends Task<Void> {
    private final DefaultDependencyManager dependencyManager;
    private final String gameVersion;
    private final Version version;
    private final List<Task<?>> dependencies = new LinkedList<>();

    public GameVerificationFixTask(DefaultDependencyManager dependencyManager, String gameVersion, Version version) {
        this.dependencyManager = dependencyManager;
        this.gameVersion = gameVersion;
        this.version = version;

        if (!version.isResolved()) {
            throw new IllegalArgumentException("GameVerificationFixTask requires a resolved game version");
        }

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws IOException {
        File jar = dependencyManager.getGameRepository().getVersionJar(version);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);

        if (jar.exists() && VersionNumber.VERSION_COMPARATOR.compare(gameVersion, "1.6") < 0 && analyzer.has(LibraryAnalyzer.LibraryType.FORGE)) {
            try (FileSystem fs = CompressingUtils.createWritableZipFileSystem(jar.toPath(), StandardCharsets.UTF_8)) {
                Files.deleteIfExists(fs.getPath("META-INF/MOJANG_C.DSA"));
                Files.deleteIfExists(fs.getPath("META-INF/MOJANG_C.SF"));
            }
        }
    }
    
}
