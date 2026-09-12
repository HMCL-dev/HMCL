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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.download.forge.ForgeLegacyInstallTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * This task is to download game libraries.
 * This task should be executed last(especially after game downloading, Forge, LiteLoader and OptiFine install task).
 *
 * @author huangyuhui
 */
public final class GameLibrariesTask extends Task<Void> {

    private final AbstractDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final boolean integrityCheck;
    private final List<Library> libraries;
    private final List<Task<?>> dependencies = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link GameRepository}
     * @param manifest          the game version
     */
    public GameLibrariesTask(AbstractDependencyManager dependencyManager, GameInstanceManifest manifest, boolean integrityCheck) {
        this(dependencyManager, manifest, integrityCheck, manifest.getLibraries());
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link GameRepository}
     * @param manifest          the game version
     */
    public GameLibrariesTask(AbstractDependencyManager dependencyManager, GameInstanceManifest manifest, boolean integrityCheck, List<Library> libraries) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.integrityCheck = integrityCheck;
        this.libraries = libraries;

        setStage("hmcl.install.libraries");
        setSignificance(TaskSignificance.MODERATE);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    public static boolean shouldDownloadLibrary(GameRepository gameRepository, GameInstanceManifest manifest, Library library, boolean integrityCheck) {
        Path file = gameRepository.getLayout().getLibraryFile(manifest.id(), library);
        if (!Files.isRegularFile(file)) return true;

        if (!integrityCheck) {
            return false;
        }
        try {
            if (!library.getDownload().validateChecksum(file, true)) {
                return true;
            }
            if (library.checksums() != null) {
                if (!library.checksums().isEmpty()) {
                    if (!LibraryDownloadTask.checksumValid(file, library.checksums())) {
                        return true;
                    }
                }
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
            return !DigestUtils.digestToString("SHA-1", file).equalsIgnoreCase(fmlLib.checksum);
        } catch (IOException e) {
            LOG.warning("Unable to calc hash value of file " + file, e);
            return true;
        }
    }

    /// {@inheritDoc}
    @Override
    public void execute() throws IOException {
        int progress = 0;
        GameRepository gameRepository = dependencyManager.getGameRepository();

        @Nullable GameComponentAnalyzer analyzer = null;

        try {
            analyzer = gameRepository.getInstance(manifest.id()).getAnalyzer();
        } catch (Exception e) {
            LOG.warning("Failed to analyze instance", e);
        }

        for (Library library : libraries) {
            boolean handled = false;

            if (!library.appliesToCurrentEnvironment()) {
                continue;
            }

            // https://github.com/HMCL-dev/HMCL/issues/3975
            if (analyzer != null && analyzer.has(ModLoaderType.FORGE)) {
                List<FMLLib> fmlLibs = getFMLLibs(library.version());
                if (fmlLibs != null) {
                    Path libDir = gameRepository.getBaseDirectory().resolve("lib")
                            .toAbsolutePath().normalize();

                    for (FMLLib fmlLib : fmlLibs) {
                        Path file = libDir.resolve(fmlLib.filename);
                        if (shouldDownloadFMLLib(fmlLib, file)) {
                            List<URI> uris = dependencyManager.getDownloadProvider()
                                    .injectURLWithCandidates(fmlLib.downloadUrl());
                            dependencies.add(new FileDownloadTask(uris, file)
                                    .withCounter("hmcl.install.libraries"));
                        }
                    }
                }
            }

            Path file = gameRepository.getLayout().getLibraryFile(manifest.id(), library);
            if ("optifine".equals(library.groupId()) && Files.exists(file)) {
                if (Files.exists(file) && libraries.stream().filter(it -> it.is("optifine", "OptiFine"))
                        .anyMatch(it -> it.version().startsWith("1.20.4_"))) {
                    if (analyzer != null && analyzer.getVersion(GameComponentType.FORGE) != null && GameComponentAnalyzer.FORGE_OPTIFINE_BROKEN_RANGE.contains(VersionNumber.asVersion(analyzer.getVersion(GameComponentType.FORGE)))) {
                        try (FileSystem fs2 = CompressingUtils.createWritableZipFileSystem(file)) {
                            Files.deleteIfExists(fs2.getPath("/META-INF/mods.toml"));
                        } catch (IOException e) {
                            throw new IOException("Cannot fix optifine", e);
                        }
                    }
                }
            } else if (library.is("org.jackhuang.hmcl", "mmc-bootstrap")) {
                if (!Files.exists(file)) {
                    try (InputStream input = Objects.requireNonNull(
                            GameLibrariesTask.class.getResourceAsStream(
                                    "/assets/game/HMCLMultiMCBootstrap-1.0.jar"),
                            "Bundled HMCLMultiMCBootstrap is missing.")) {
                        Files.createDirectories(file.getParent());
                        Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
                        handled = true;
                    }
                }
            } else if (library.is("org.jackhuang.hmcl", "transformer-discovery-service")) {
                try (InputStream input = Objects.requireNonNull(
                        GameLibrariesTask.class.getResourceAsStream(
                                "/assets/game/HMCLTransformerDiscoveryService-1.0.jar"),
                        "Bundled HMCLTransformerDiscoveryService is missing.")) {
                    Files.createDirectories(file.getParent());
                    Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
                    handled = true;
                }
            } else if (library.groupId().equals("modloader")) {
                String url = switch (library.artifactId()) {
                    case "modloader" -> ForgeLegacyInstallTask.MODLOADER_DOWNLOAD_URL;
                    case "modloader-mp" -> ForgeLegacyInstallTask.MODLOADER_MP_DOWNLOAD_URL;
                    default -> null;
                };
                if (url != null && shouldDownloadLibrary(gameRepository, manifest, library, integrityCheck)) {
                    var fileDownloadTask = new FileDownloadTask(url, file, null);
                    fileDownloadTask.setCacheRepository(dependencyManager.getCacheRepository());
                    fileDownloadTask.setCaching(true);
                    fileDownloadTask.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
                    dependencies.add(fileDownloadTask.withCounter("hmcl.install.libraries"));

                    handled = true;
                }
            }

            if (!handled && shouldDownloadLibrary(gameRepository, manifest, library, integrityCheck) && (library.hasDownloadURL() || !"optifine".equals(library.groupId()))) {
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
        //noinspection DataFlowIssue
        try (Reader input = new InputStreamReader(
                GameLibrariesTask.class.getResourceAsStream("/assets/game/fmllibs.json"))) {
            var fmlLibs = JsonUtils.GSON.fromJson(input, new TypeToken<Map<String, List<FMLLib>>>() {
            });

            if (forgeVersion.startsWith("4."))
                return fmlLibs.get("1.3.x");
            else if (forgeVersion.startsWith("6.") || forgeVersion.startsWith("5."))
                return fmlLibs.get("1.4.x");
            else if (forgeVersion.startsWith("7.7.0.5"))
                return fmlLibs.get("1.5");
            else if (forgeVersion.startsWith("7.7.0.6") || forgeVersion.startsWith("7.7.1.") || forgeVersion.startsWith("7.7.2."))
                return fmlLibs.get("1.5.1");
            else if (forgeVersion.startsWith("7.8."))
                return fmlLibs.get("1.5.2");
        } catch (Throwable e) {
            LOG.warning("Failed to load fmllibs", e);
        }


        return null;
    }

    @JsonSerializable
    private record FMLLib(String filename, String checksum) {
        public String downloadUrl() {
            return "https://hmcl.glavo.site/metadata/fmllibs/" + filename;
        }
    }
}
