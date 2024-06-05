/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * This task is to download game libraries.
 * This task should be executed last(especially after game downloading, Forge, LiteLoader and OptiFine install task).
 *
 * @author huangyuhui
 */
public final class GameLibrariesTask extends Task<Void> {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final boolean integrityCheck;
    private final List<Library> libraries;
    private final List<Task<?>> dependencies = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version           the game version
     */
    public GameLibrariesTask(AbstractDependencyManager dependencyManager, Version version, boolean integrityCheck) {
        this(dependencyManager, version, integrityCheck, version.resolve(dependencyManager.getGameRepository()).getLibraries());
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link org.jackhuang.hmcl.game.GameRepository}
     * @param version           the game version
     */
    public GameLibrariesTask(AbstractDependencyManager dependencyManager, Version version, boolean integrityCheck, List<Library> libraries) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.integrityCheck = integrityCheck;
        this.libraries = libraries;

        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    public static boolean shouldDownloadLibrary(GameRepository gameRepository, Version version, Library library, boolean integrityCheck) {
        File file = gameRepository.getLibraryFile(version, library);
        Path jar = file.toPath();
        if (!file.isFile()) return true;

        if (!integrityCheck) {
            return false;
        }
        try {
            if (!library.getDownload().validateChecksum(jar, true)) {
                return true;
            }
            if (library.getChecksums() != null && !library.getChecksums().isEmpty() && !LibraryDownloadTask.checksumValid(file, library.getChecksums())) {
                return true;
            }
            if (FileUtils.getExtension(file).equals("jar")) {
                try {
                    FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER.checkIntegrity(jar, jar);
                } catch (IOException ignored) {
                    // the Jar file is malformed, so re-download it.
                    return true;
                }
            }
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + jar, e);
        }

        return false;
    }

    @Override
    public void execute() throws IOException {
        GameRepository gameRepository = dependencyManager.getGameRepository();
        for (Library library : libraries) {
            if (!library.appliesToCurrentEnvironment()) {
                continue;
            }

            File file = gameRepository.getLibraryFile(version, library);
            if ("optifine".equals(library.getGroupId()) && file.exists() && GameVersionNumber.asGameVersion(gameRepository.getGameVersion(version)).compareTo("1.20.4") == 0) {
                String forgeVersion = LibraryAnalyzer.analyze(version, "1.20.4")
                        .getVersion(LibraryAnalyzer.LibraryType.FORGE)
                        .orElse(null);
                if (forgeVersion != null && LibraryAnalyzer.FORGE_OPTIFINE_BROKEN_RANGE.contains(VersionNumber.asVersion(forgeVersion))) {
                    try (FileSystem fs2 = CompressingUtils.createWritableZipFileSystem(file.toPath())) {
                        Files.deleteIfExists(fs2.getPath("/META-INF/mods.toml"));
                    } catch (IOException e) {
                        throw new IOException("Cannot fix optifine", e);
                    }
                }
            }
            if (shouldDownloadLibrary(gameRepository, version, library, integrityCheck) && library.hasDownloadURL()) {
                dependencies.add(new LibraryDownloadTask(dependencyManager, file, library));
            } else {
                dependencyManager.getCacheRepository().tryCacheLibrary(library, file.toPath());
            }
        }
    }

}
