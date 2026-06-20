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
package org.jackhuang.hmcl.mod.modrinth;

import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.Zipper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Export task for Modrinth modpack format.
 * <p>
 * This implementation streams the index JSON directly to a temporary file using {@link JsonWriter},
 * keeping memory usage low regardless of file count.
 * <p>
 * SHA‑1 and SHA‑512 hashes are computed using {@link DigestUtils#digestToString(String, Path)}
 * which uses a streaming {@code DigestInputStream}, safe for large files without OOM risk.
 */
public class ModrinthModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String version;
    private final ModpackExportInfo info;
    private final Path modpackFile;

    public ModrinthModpackExportTask(DefaultGameRepository repository, String version, ModpackExportInfo info, Path modpackFile) {
        this.repository = repository;
        this.version = version;
        this.info = info.validate();
        this.modpackFile = modpackFile;

        onDone().register(event -> {
            if (event.isFailed()) {
                try {
                    Files.deleteIfExists(modpackFile);
                } catch (IOException e) {
                    LOG.warning("Failed to delete modpack file: " + modpackFile, e);
                }
            }
        });
    }

    private ModrinthManifest.File tryGetRemoteFile(Path file, String relativePath, Set<Path> temporarilyEnabledFiles) throws IOException {
        if (info.isNoCreateRemoteFiles()) {
            return null;
        }

        boolean isDisabled = repository.getModManager(version).isDisabled(file);
        if (isDisabled) {
            temporarilyEnabledFiles.add(file); // record original disabled file
            relativePath = repository.getModManager(version).enableMod(Paths.get(relativePath)).toString();
            file = repository.getRunDirectory(version).resolve(relativePath).normalize();
        }

        Optional<RemoteMod.Version> modrinthVersion = Optional.empty();
        Optional<RemoteMod.Version> curseForgeVersion = Optional.empty();

        try {
            modrinthVersion = ModrinthRemoteModRepository.MODS.getRemoteVersionByLocalFile(file);
        } catch (IOException e) {
            LOG.warning("Failed to get remote file from Modrinth for: " + file, e);
        }

        if (!info.isSkipCurseForgeRemoteFiles() && CurseForgeRemoteModRepository.isAvailable()) {
            try {
                curseForgeVersion = CurseForgeRemoteModRepository.MODS.getRemoteVersionByLocalFile(file);
            } catch (IOException e) {
                LOG.warning("Failed to get remote file from CurseForge for: " + file, e);
            }
        }

        if (modrinthVersion.isEmpty() && curseForgeVersion.isEmpty()) {
            return null;
        }

        Map<String, String> hashes = new HashMap<>();
        hashes.put("sha1", DigestUtils.digestToString("SHA-1", file));
        hashes.put("sha512", DigestUtils.digestToString("SHA-512", file));

        Map<String, String> env = null;
        if (isDisabled) {
            env = new HashMap<>();
            env.put("client", "optional");
        }

        List<String> downloads = new ArrayList<>();
        if (modrinthVersion.isPresent())
            downloads.add(modrinthVersion.get().getFile().getUrl());
        if (curseForgeVersion.isPresent())
            downloads.add(curseForgeVersion.get().getFile().getUrl());

        long fileSize = Files.size(file);
        if (fileSize > Integer.MAX_VALUE) {
            LOG.warning("File " + relativePath + " is too large (size: " + fileSize + " bytes), precision may be lost when converting to int");
        }
        return new ModrinthManifest.File(
                relativePath,
                hashes,
                env,
                downloads,
                (int) fileSize
        );
    }

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(version + ".jar");
        blackList.add(version + ".json");
        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");

        Path runDirectory = repository.getRunDirectory(version);
        String gameVersion = repository.getGameVersion(version)
                .orElseThrow(() -> new IOException("Cannot parse the version of " + version));
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(repository.getResolvedPreservingPatchesVersion(version), gameVersion);

        Set<String> whitelistSet = new HashSet<>(info.getWhitelist());

        String[] resourceDirs = {"resourcepacks", "shaderpacks", "mods"};
        Set<String> remoteFilePaths = new HashSet<>();
        Set<Path> temporarilyEnabledFiles = new HashSet<>(); // track disabled mods that were temporarily enabled

        Path tempIndex = Files.createTempFile("modrinth_index_", ".json");
        try {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(tempIndex, StandardCharsets.UTF_8))) {
                writer.setIndent("  ");
                writer.beginObject();

                writer.name("formatVersion").value(1);
                writer.name("game").value("minecraft");
                writer.name("versionId").value(info.getVersion());
                writer.name("name").value(info.getName());
                if (info.getDescription() != null) {
                    writer.name("summary").value(info.getDescription());
                }

                writer.name("files").beginArray();

                Set<String> processedPaths = new HashSet<>();

                for (String dir : resourceDirs) {
                    Path dirPath = runDirectory.resolve(dir);
                    if (Files.exists(dirPath)) {
                        Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                                if (!whitelistSet.contains(relativePath)) {
                                    return FileVisitResult.CONTINUE;
                                }
                                if (processedPaths.contains(relativePath)) {
                                    return FileVisitResult.CONTINUE;
                                }
                                processedPaths.add(relativePath);

                                ModrinthManifest.File fileEntry = null;
                                try {
                                    fileEntry = tryGetRemoteFile(file, relativePath, temporarilyEnabledFiles);
                                } catch (IOException e) {
                                    LOG.warning("Failed to process file: " + file, e);
                                }
                                if (fileEntry != null) {
                                    remoteFilePaths.add(relativePath);
                                    writer.beginObject();
                                    writer.name("path").value(fileEntry.getPath());
                                    writer.name("hashes").beginObject();
                                    for (Map.Entry<String, String> hash : fileEntry.getHashes().entrySet()) {
                                        writer.name(hash.getKey()).value(hash.getValue());
                                    }
                                    writer.endObject();
                                    if (fileEntry.getEnv() != null) {
                                        writer.name("env").beginObject();
                                        for (Map.Entry<String, String> env : fileEntry.getEnv().entrySet()) {
                                            writer.name(env.getKey()).value(env.getValue());
                                        }
                                        writer.endObject();
                                    }
                                    writer.name("downloads").beginArray();
                                    for (String url : fileEntry.getDownloads()) {
                                        writer.value(url);
                                    }
                                    writer.endArray();
                                    writer.name("fileSize").value(fileEntry.getFileSize());
                                    writer.endObject();
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                }

                writer.endArray();

                writer.name("dependencies").beginObject();
                writer.name("minecraft").value(gameVersion);
                Optional<String> forgeVersion = analyzer.getVersion(FORGE);
                if (forgeVersion.isPresent()) {
                    writer.name("forge").value(forgeVersion.get());
                }
                Optional<String> neoForgeVersion = analyzer.getVersion(NEO_FORGE);
                if (neoForgeVersion.isPresent()) {
                    writer.name("neoforge").value(neoForgeVersion.get());
                }
                Optional<String> fabricVersion = analyzer.getVersion(FABRIC);
                if (fabricVersion.isPresent()) {
                    writer.name("fabric-loader").value(fabricVersion.get());
                }
                Optional<String> quiltVersion = analyzer.getVersion(QUILT);
                if (quiltVersion.isPresent()) {
                    writer.name("quilt-loader").value(quiltVersion.get());
                }
                writer.endObject();

                writer.endObject();
            }

            try (var zip = new Zipper(modpackFile)) {
                zip.putFile(tempIndex, "modrinth.index.json");

                zip.putDirectory(runDirectory, "client-overrides", path -> {
                    if (path == null || path.isEmpty()) {
                        return true;
                    }
                    Path resolved = runDirectory.resolve(path);
                    if (Files.isDirectory(resolved)) {
                        return !ModAdviser.match(blackList, path, false);
                    }
                    if (remoteFilePaths.contains(path)) {
                        return false;
                    }
                    return Modpack.acceptFile(path, blackList, info.getWhitelist());
                });
            }
        } finally {
            Files.deleteIfExists(tempIndex);
            // Restore disabled mods to their original disabled state
            for (Path disabledFile : temporarilyEnabledFiles) {
                try {
                    // disabledFile is the original .disabled file path (e.g., mods/SomeMod.jar.disabled)
                    // The enabled version is the same path without the .disabled suffix
                    String fileName = disabledFile.getFileName().toString();
                    if (fileName.endsWith(".disabled")) {
                        String enabledName = fileName.substring(0, fileName.length() - 9); // remove ".disabled"
                        Path enabledFile = disabledFile.resolveSibling(enabledName);
                        if (Files.exists(enabledFile)) {
                            // Move enabled file back to .disabled (overwrite if exists)
                            Files.move(enabledFile, disabledFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            LOG.info("Restored disabled mod: " + disabledFile);
                        } else {
                            LOG.warning("Enabled file not found, cannot restore: " + enabledFile);
                        }
                    }
                } catch (IOException e) {
                    LOG.warning("Failed to restore disabled mod: " + disabledFile, e);
                }
            }
        }
    }

    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireNoCreateRemoteFiles()
            .requireSkipCurseForgeRemoteFiles();
}
