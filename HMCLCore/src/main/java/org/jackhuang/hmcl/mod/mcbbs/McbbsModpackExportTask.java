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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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
        try {
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(Files.newOutputStream(tempManifest), StandardCharsets.UTF_8))) {
                writer.setIndent("  ");
                writer.beginObject();

                writer.name("manifestType").value(McbbsModpackManifest.MANIFEST_TYPE);
                writer.name("manifestVersion").value(2);
                writer.name("name").value(info.getName());
                writer.name("version").value(info.getVersion());
                writer.name("author").value(info.getAuthor());
                writer.name("description").value(info.getDescription());
                writer.name("fileApi").value(info.getFileApi() == null ? null : StringUtils.removeSuffix(info.getFileApi(), "/"));
                writer.name("url").value(info.getUrl());
                writer.name("forceUpdate").value(info.isForceUpdate());

                writer.name("origins").beginArray();
                writer.endArray();

                writer.name("addons").beginArray();
                writer.beginObject();
                writer.name("id").value(MINECRAFT.getPatchId());
                writer.name("version").value(gameVersion);
                writer.endObject();
                analyzer.getVersion(FORGE).ifPresent(forgeVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(FORGE.getPatchId());
                        writer.name("version").value(forgeVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(CLEANROOM).ifPresent(cleanroomVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(CLEANROOM.getPatchId());
                        writer.name("version").value(cleanroomVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(NEO_FORGE).ifPresent(neoForgeVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(NEO_FORGE.getPatchId());
                        writer.name("version").value(neoForgeVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(LITELOADER).ifPresent(liteLoaderVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(LITELOADER.getPatchId());
                        writer.name("version").value(liteLoaderVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(OPTIFINE).ifPresent(optifineVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(OPTIFINE.getPatchId());
                        writer.name("version").value(optifineVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(FABRIC).ifPresent(fabricVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(FABRIC.getPatchId());
                        writer.name("version").value(fabricVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(QUILT).ifPresent(quiltVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(QUILT.getPatchId());
                        writer.name("version").value(quiltVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                analyzer.getVersion(LEGACY_FABRIC).ifPresent(legacyfabricVersion -> {
                    try {
                        writer.beginObject();
                        writer.name("id").value(LEGACY_FABRIC.getPatchId());
                        writer.name("version").value(legacyfabricVersion);
                        writer.endObject();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                writer.endArray();

                writer.name("libraries").beginArray();
                writer.endArray();

                writer.name("files").beginArray();
                try (var stream = Files.walk(runDirectory)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    Path relative = runDirectory.relativize(file);
                                    String relativePath = relative.toString().replace(File.separatorChar, '/');
                                    if (Modpack.acceptFile(relativePath, blackList, info.getWhitelist())) {
                                        String sha1 = DigestUtils.digestToString("SHA-1", file);
                                        writer.beginObject();
                                        writer.name("type").value(true);
                                        writer.name("path").value(relativePath);
                                        writer.name("hash").value(sha1);
                                        writer.endObject();
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
                writer.endArray();

                writer.name("settings").beginObject();
                writer.endObject();

                writer.name("launchInfo").beginObject();
                writer.name("minMemory").value(info.getMinMemory());
                writer.name("supportedJavaVersions").beginArray();
                if (info.getSupportedJavaVersions() != null) {
                    for (int ver : info.getSupportedJavaVersions()) {
                        writer.value(ver);
                    }
                }
                writer.endArray();
                writer.name("launchArguments").beginArray();
                for (String arg : StringUtils.tokenize(info.getLaunchArguments())) {
                    writer.value(arg);
                }
                writer.endArray();
                writer.name("javaArguments").beginArray();
                for (String arg : StringUtils.tokenize(info.getJavaArguments())) {
                    writer.value(arg);
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
                    return Modpack.acceptFile(path, blackList, info.getWhitelist());
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