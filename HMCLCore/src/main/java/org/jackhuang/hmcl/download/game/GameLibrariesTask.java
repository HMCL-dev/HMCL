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
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
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

        setStage("hmcl.install.libraries");
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    public static boolean shouldDownloadLibrary(GameRepository gameRepository, Version version, Library library, boolean integrityCheck) {
        Path file = gameRepository.getLibraryFile(version, library);
        if (!Files.isRegularFile(file)) return true;

        if (!integrityCheck) {
            return false;
        }
        try {
            if (!library.getDownload().validateChecksum(file, true)) {
                return true;
            }
            if (library.getChecksums() != null && !library.getChecksums().isEmpty() && !LibraryDownloadTask.checksumValid(file, library.getChecksums())) {
                return true;
            }
            if (FileUtils.getExtension(file).equals("jar")) {
                try {
                    FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER.checkIntegrity(file, file);
                } catch (IOException ignored) {
                    // the Jar file is malformed, so re-download it.
                    return true;
                }
            }
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + file, e);
        }

        return false;
    }

    private static boolean shouldDownloadFMLLib(FMLLib fmlLib, Path file) {
        if (!Files.isRegularFile(file))
            return true;

        try {
            return !DigestUtils.digestToString("SHA-1", file).equalsIgnoreCase(fmlLib.sha1);
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + file, e);
            return true;
        }
    }

    @Override
    public void execute() throws IOException {
        int progress = 0;
        GameRepository gameRepository = dependencyManager.getGameRepository();
        for (Library library : libraries) {
            if (!library.appliesToCurrentEnvironment()) {
                continue;
            }

            // https://github.com/HMCL-dev/HMCL/issues/3975
            if ("net.minecraftforge".equals(library.getGroupId()) && "minecraftforge".equals(library.getArtifactId())
                    && gameRepository instanceof DefaultGameRepository defaultGameRepository) {
                List<FMLLib> fmlLibs = getFMLLibs(library.getVersion());
                if (fmlLibs != null) {
                    Path libDir = defaultGameRepository.getBaseDirectory().resolve("lib")
                            .toAbsolutePath().normalize();

                    for (FMLLib fmlLib : fmlLibs) {
                        Path file = libDir.resolve(fmlLib.name);
                        if (shouldDownloadFMLLib(fmlLib, file)) {
                            List<URI> uris = dependencyManager.getDownloadProvider()
                                    .injectURLWithCandidates(fmlLib.downloadUrl());
                            dependencies.add(new FileDownloadTask(uris, file)
                                    .withCounter("hmcl.install.libraries"));
                        }
                    }
                }
            }

            Path file = gameRepository.getLibraryFile(version, library);
            if ("optifine".equals(library.getGroupId()) && Files.exists(file) && GameVersionNumber.asGameVersion(gameRepository.getGameVersion(version)).compareTo("1.20.4") == 0) {
                String forgeVersion = LibraryAnalyzer.analyze(version, "1.20.4")
                        .getVersion(LibraryAnalyzer.LibraryType.FORGE)
                        .orElse(null);
                if (forgeVersion != null && LibraryAnalyzer.FORGE_OPTIFINE_BROKEN_RANGE.contains(VersionNumber.asVersion(forgeVersion))) {
                    try (FileSystem fs2 = CompressingUtils.createWritableZipFileSystem(file)) {
                        Files.deleteIfExists(fs2.getPath("/META-INF/mods.toml"));
                    } catch (IOException e) {
                        throw new IOException("Cannot fix optifine", e);
                    }
                }
            }
            if (shouldDownloadLibrary(gameRepository, version, library, integrityCheck) && (library.hasDownloadURL() || !"optifine".equals(library.getGroupId()))) {
                dependencies.add(new LibraryDownloadTask(dependencyManager, file, library).withCounter("hmcl.install.libraries"));
            } else {
                dependencyManager.getCacheRepository().tryCacheLibrary(library, file);
            }

            updateProgress(++progress, libraries.size());
        }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    private static @Nullable List<FMLLib> getFMLLibs(String forgeVersion) {
        if (forgeVersion == null)
            return null;

        // Minecraft 1.5.2
        if (forgeVersion.startsWith("7.8.1.")) {
            return List.of(
                    new FMLLib("argo-small-3.2.jar", "58912ea2858d168c50781f956fa5b59f0f7c6b51"),
                    new FMLLib("guava-14.0-rc3.jar", "931ae21fa8014c3ce686aaa621eae565fefb1a6a",
                            "https://repo1.maven.org/maven2/com/google/guava/guava/14.0-rc3/guava-14.0-rc3.jar"),
                    new FMLLib("asm-all-4.1.jar", "054986e962b88d8660ae4566475658469595ef58",
                            "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/4.1/asm-all-4.1.jar"),
                    new FMLLib("bcprov-jdk15on-148.jar", "960dea7c9181ba0b17e8bab0c06a43f0a5f04e65",
                            "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48.jar"),
                    new FMLLib("deobfuscation_data_1.5.2.zip", "446e55cd986582c70fcf12cb27bc00114c5adfd9"),
                    new FMLLib("scala-library.jar", "458d046151ad179c85429ed7420ffb1eaf6ddf85")
            );
        }

        return null;
    }

    private record FMLLib(String name, String sha1, String downloadUrl) {
        FMLLib(String name, String sha1) {
            this(name, sha1, "https://hmcl.glavo.site/metadata/fmllibs/" + name);
        }
    }
}
