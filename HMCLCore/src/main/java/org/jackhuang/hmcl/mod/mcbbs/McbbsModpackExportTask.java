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
package org.jackhuang.hmcl.mod.mcbbs;

import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.mod.curse.CurseManifest;
import org.jackhuang.hmcl.mod.curse.CurseManifestMinecraft;
import org.jackhuang.hmcl.mod.curse.CurseManifestModLoader;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Export task for MCBBS modpack format.
 * <p>
 * Note: This implementation performs two passes over the game directory:
 * 1. First pass: walks the file tree to generate the manifest JSON (calculating SHA‑1 hashes).
 * 2. Second pass: compresses the files into the final ZIP.
 * <p>
 * This double traversal is a deliberate trade‑off to avoid holding all file information in memory,
 * which would cause OutOfMemoryError on very large modpacks. The streaming JSON writer writes the
 * manifest to a temporary file, keeping memory usage constant regardless of file count.
 * <p>
 * SHA‑1 hashes are computed using {@link DigestUtils#digestToString(String, Path)} which uses
 * a streaming {@code DigestInputStream}, making it safe for large files without OOM risk.
 */
public class McbbsModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String version;
    private final ModpackExportInfo info;
    private final Path modpackFile;

    public McbbsModpackExportTask(DefaultGameRepository repository, String version, ModpackExportInfo info, Path modpackFile) {
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

        Path tempManifest = Files.createTempFile("mcbbs_packmeta_", ".json");
        tempManifest.toFile().deleteOnExit(); // final backup cleanup
        try {
            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(tempManifest, StandardCharsets.UTF_8))) {
                writer.setIndent("  ");
                writer.beginObject();

                writer.name("manifestType").value(McbbsModpackManifest.MANIFEST_TYPE);
                writer.name("manifestVersion").value(2);
                writer.name("name").value(info.getName());
                writer.name("version").value(info.getVersion());
                writer.name("author").value(info.getAuthor());
                writer.name("description").value(info.getDescription());
                if (info.getFileApi() != null) {
                    writer.name("fileApi").value(StringUtils.removeSuffix(info.getFileApi(), "/"));
                }
                writer.name("url").value(info.getUrl());
                writer.name("forceUpdate").value(info.isForceUpdate());

                writer.name("origin").beginArray();
                if (info.getOrigins() != null) {
                    for (McbbsModpackManifest.Origin origin : info.getOrigins()) {
                        writer.beginObject();
                        writer.name("type").value(origin.getType());
                        writer.name("id").value(origin.getId());
                        writer.endObject();
                    }
                }
                writer.endArray();

                writer.name("addons").beginArray();
                writer.beginObject();
                writer.name("id").value(MINECRAFT.getPatchId());
                writer.name("version").value(gameVersion);
                writer.endObject();

                LibraryAnalyzer.LibraryType[] addonTypes = {
                        FORGE, CLEANROOM, NEO_FORGE, LITELOADER, OPTIFINE, FABRIC, QUILT, LEGACY_FABRIC
                };
                for (LibraryAnalyzer.LibraryType type : addonTypes) {
                    Optional<String> addonVersion = analyzer.getVersion(type);
                    if (addonVersion.isPresent()) {
                        writer.beginObject();
                        writer.name("id").value(type.getPatchId());
                        writer.name("version").value(addonVersion.get());
                        writer.endObject();
                    }
                }
                writer.endArray();

                writer.name("libraries").beginArray();
                writer.endArray();

                writer.name("files").beginArray();
                Files.walkFileTree(runDirectory, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        String relativePath = runDirectory.relativize(dir).normalize().toString().replace(File.separatorChar, '/');
                        if (relativePath.isEmpty()) {
                            return FileVisitResult.CONTINUE;
                        }
                        // Consistent with zip.putDirectory filter: only skip blacklisted directories
                        if (ModAdviser.match(blackList, relativePath, false)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                        if (Modpack.acceptFile(relativePath, blackList, info.getWhitelist())) {
                            String sha1 = DigestUtils.digestToString("SHA-1", file);
                            writer.beginObject();
                            writer.name("type").value("addon");
                            writer.name("force").value(true);
                            writer.name("path").value(relativePath);
                            writer.name("hash").value(sha1);
                            writer.endObject();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                writer.endArray();

                writer.name("settings").beginObject();
                writer.name("install_mods").value(true);
                writer.name("install_resourcepack").value(true);
                writer.endObject();

                writer.name("launchInfo").beginObject();
                writer.name("minMemory").value(info.getMinMemory());
                writer.name("supportJava").beginArray();
                if (info.getSupportedJavaVersions() != null) {
                    for (Integer ver : info.getSupportedJavaVersions()) {
                        if (ver != null) {
                            writer.value(ver);
                        }
                    }
                }
                writer.endArray();

                writer.name("launchArgument").beginArray();
                List<String> launchArgs = StringUtils.tokenize(info.getLaunchArguments());
                if (launchArgs != null) {
                    for (String arg : launchArgs) {
                        writer.value(arg);
                    }
                }
                writer.endArray();

                writer.name("javaArgument").beginArray();
                List<String> javaArgs = StringUtils.tokenize(info.getJavaArguments());
                if (javaArgs != null) {
                    for (String arg : javaArgs) {
                        writer.value(arg);
                    }
                }
                writer.endArray();
                writer.endObject();

                writer.endObject();
            }

            try (var zip = new Zipper(modpackFile)) {
                zip.putFile(tempManifest, "mcbbs.packmeta");

                List<CurseManifestModLoader> modLoaders = new ArrayList<>();
                analyzer.getVersion(FORGE).ifPresent(forgeVersion -> modLoaders.add(new CurseManifestModLoader("forge-" + forgeVersion, true)));
                analyzer.getVersion(NEO_FORGE).ifPresent(neoForgeVersion -> modLoaders.add(new CurseManifestModLoader("neoforge-" + neoForgeVersion, true)));
                analyzer.getVersion(FABRIC).ifPresent(fabricVersion -> modLoaders.add(new CurseManifestModLoader("fabric-" + fabricVersion, true)));
                CurseManifest curseManifest = new CurseManifest(CurseManifest.MINECRAFT_MODPACK, 1, info.getName(), info.getVersion(), info.getAuthor(), "overrides", new CurseManifestMinecraft(gameVersion, modLoaders), Collections.emptyList());
                zip.putTextFile(JsonUtils.GSON.toJson(curseManifest), "manifest.json");

                zip.putDirectory(runDirectory, "overrides", path -> {
                    if (path == null || path.isEmpty()) {
                        return true;
                    }
                    // Normalize path for consistency with other parts
                    String normalizedPath = Paths.get(path).normalize().toString().replace(File.separatorChar, '/');
                    Path resolved = runDirectory.resolve(normalizedPath);
                    if (Files.isDirectory(resolved)) {
                        return !ModAdviser.match(blackList, normalizedPath, false);
                    } else {
                        return Modpack.acceptFile(normalizedPath, blackList, info.getWhitelist());
                    }
                });
            }
        } finally {
            Files.deleteIfExists(tempManifest);
        }
    }

    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireFileApi(true)
            .requireUrl()
            .requireForceUpdate()
            .requireMinMemory()
            .requireAuthlibInjectorServer()
            .requireJavaArguments()
            .requireLaunchArguments()
            .requireOrigins()
            .requireAuthor();
}
