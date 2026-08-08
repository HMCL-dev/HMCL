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
package org.jackhuang.hmcl.modpack.mcbbs;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.curse.CurseManifest;
import org.jackhuang.hmcl.modpack.curse.CurseManifestMinecraft;
import org.jackhuang.hmcl.modpack.curse.CurseManifestModLoader;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.game.GameComponentType.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Exports one registered game instance as an MCBBS modpack archive.
@NotNullByDefault
public class McbbsModpackExportTask extends Task<Void> {
    /// The fixed instance snapshot exported by this task.
    private final DefaultGameInstance instance;

    /// The validated export configuration.
    private final ModpackExportInfo info;

    /// The archive written by this task.
    private final Path modpackFile;

    /// Creates an MCBBS modpack export task.
    ///
    /// @param instance    the registered instance snapshot to export
    /// @param info        the export configuration
    /// @param modpackFile the archive to write
    public McbbsModpackExportTask(DefaultGameInstance instance, ModpackExportInfo info, Path modpackFile) {
        this.instance = instance;
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

    /// {@inheritDoc}
    @Override
    public void execute() throws Exception {
        var instanceId = instance.getId();
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(instanceId + ".jar");
        blackList.add(instanceId + ".json");
        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (var zip = new Zipper(modpackFile)) {
            Path runDirectory = instance.getRunDirectory();
            List<McbbsModpackManifest.File> files = new ArrayList<>();
            zip.putDirectory(runDirectory, "overrides", path -> {
                if (Modpack.acceptFile(path, blackList, info.getWhitelist())) {
                    Path file = runDirectory.resolve(path);
                    if (Files.isRegularFile(file)) {
                        String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                        files.add(new McbbsModpackManifest.AddonFile(true, relativePath, DigestUtils.digestToString("SHA-1", file)));
                    }
                    return true;
                } else {
                    return false;
                }
            });

            GameVersionNumber version = instance.getVersion();
            if (version == GameVersionNumber.unknown()) {
                throw new IOException("Cannot parse the version of " + instanceId);
            }
            String gameVersion = version.toString();
            GameComponentAnalyzer analyzer = instance.getAnalyzer();

            // Mcbbs manifest
            List<McbbsModpackManifest.Addon> addons = new ArrayList<>();
            addons.add(new McbbsModpackManifest.Addon(GAME.getPatchId(), gameVersion));
            for (GameComponentAnalyzer.Mark mark : analyzer) {
                if ((mark.componentType().isModLoader() || mark.componentType() == GameComponentType.OPTIFINE)) {
                    addons.add(new McbbsModpackManifest.Addon(mark.componentType().getPatchId(), mark.version()));
                }
            }

            List<Library> libraries = new ArrayList<>();
            // TODO libraries

            List<McbbsModpackManifest.Origin> origins = new ArrayList<>();
            // TODO origins

            McbbsModpackManifest.Settings settings = new McbbsModpackManifest.Settings();
            McbbsModpackManifest.LaunchInfo launchInfo = new McbbsModpackManifest.LaunchInfo(info.getMinMemory(), info.getSupportedJavaVersions(), StringUtils.tokenize(info.getLaunchArguments()), StringUtils.tokenize(info.getJavaArguments()));

            McbbsModpackManifest mcbbsManifest = new McbbsModpackManifest(McbbsModpackManifest.MANIFEST_TYPE, 2, info.getName(), info.getVersion(), info.getAuthor(), info.getDescription(), info.getFileApi() == null ? null : StringUtils.removeSuffix(info.getFileApi(), "/"), info.getUrl(), info.isForceUpdate(), origins, addons, libraries, files, settings, launchInfo);
            zip.putTextFile(JsonUtils.GSON.toJson(mcbbsManifest), "mcbbs.packmeta");

            // CurseForge manifest
            List<CurseManifestModLoader> modLoaders = new ArrayList<>();
            Optional.ofNullable(analyzer.getVersion(FORGE)).ifPresent(forgeVersion -> modLoaders.add(new CurseManifestModLoader("forge-" + forgeVersion, true)));
            Optional.ofNullable(analyzer.getVersion(NEO_FORGE)).ifPresent(forgeVersion -> modLoaders.add(new CurseManifestModLoader("neoforge-" + forgeVersion, true)));
            Optional.ofNullable(analyzer.getVersion(FABRIC)).ifPresent(fabricVersion -> modLoaders.add(new CurseManifestModLoader("fabric-" + fabricVersion, true)));
            // OptiFine and LiteLoader are not supported by CurseForge modpack.
            CurseManifest curseManifest = new CurseManifest(CurseManifest.MINECRAFT_MODPACK, 1, info.getName(), info.getVersion(), info.getAuthor(), "overrides", new CurseManifestMinecraft(gameVersion, modLoaders), Collections.emptyList());
            zip.putTextFile(JsonUtils.GSON.toJson(curseManifest), "manifest.json");
        }
    }

    /// Export options supported by the MCBBS format.
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
