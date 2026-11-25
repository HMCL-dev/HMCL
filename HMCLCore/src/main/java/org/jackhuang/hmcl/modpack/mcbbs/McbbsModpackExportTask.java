// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.mcbbs;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.ModpackExportTask;
import org.jackhuang.hmcl.modpack.curse.CurseManifest;
import org.jackhuang.hmcl.modpack.curse.CurseManifestMinecraft;
import org.jackhuang.hmcl.modpack.curse.CurseManifestModLoader;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskProgress;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.game.GameComponentType.*;

/// Exports one registered game instance as an MCBBS modpack archive.
@NotNullByDefault
public class McbbsModpackExportTask extends ModpackExportTask {
    /// Creates an MCBBS modpack export task.
    ///
    /// @param instance    the registered instance snapshot to export
    /// @param info        the export configuration
    /// @param modpackFile the archive to write
    public McbbsModpackExportTask(DefaultGameInstance instance, ModpackExportInfo info, Path modpackFile) {
        super(instance, info, modpackFile);
    }

    /// {@inheritDoc}
    @Override
    protected void writeArchive(Zipper zip, Predicate<String> include, TaskExecutionContext context) throws IOException {
        Path runDirectory = instance.getRunDirectory();
        TaskProgress progress = context.progress();

        List<Path> paths = new ArrayList<>();
        zip.putDirectoryProgressed(runDirectory, "overrides", path -> {
            if (include.test(path)) {
                Path file = runDirectory.resolve(path);
                if (Files.isRegularFile(file)) {
                    paths.add(file);
                }
                return true;
            } else {
                return false;
            }
        }, fraction -> progress.update(0.5 * fraction));

        List<McbbsModpackManifest.File> files = new ArrayList<>();
        long current = 0;
        for (Path file : paths) {
            String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
            files.add(new McbbsModpackManifest.AddonFile(true, relativePath, DigestUtils.digestToString("SHA-1", file, context::checkCancellation)));
            progress.update(0.5 + 0.5 * ++current / paths.size());
        }

        GameVersionNumber version = instance.getVersion();
        if (version == GameVersionNumber.unknown()) {
            throw new IOException("Cannot parse the version of " + instance.getId());
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
        try (Writer writer = new OutputStreamWriter(zip.putStream("mcbbs.packmeta"), StandardCharsets.UTF_8)) {
            JsonUtils.GSON.toJson(mcbbsManifest, writer);
        }

        // CurseForge manifest
        List<CurseManifestModLoader> modLoaders = new ArrayList<>();
        Optional.ofNullable(analyzer.getVersion(FORGE))
                .ifPresent(forgeVersion -> modLoaders.add(new CurseManifestModLoader("forge-" + forgeVersion, true)));
        Optional.ofNullable(analyzer.getVersion(NEO_FORGE))
                .ifPresent(forgeVersion -> modLoaders.add(new CurseManifestModLoader("neoforge-" + forgeVersion, true)));
        Optional.ofNullable(analyzer.getVersion(FABRIC))
                .ifPresent(fabricVersion -> modLoaders.add(new CurseManifestModLoader("fabric-" + fabricVersion, true)));
        // OptiFine and LiteLoader are not supported by CurseForge modpack.
        CurseManifest curseManifest = new CurseManifest(CurseManifest.MINECRAFT_MODPACK, 1, info.getName(), info.getVersion(), info.getAuthor(), "overrides", new CurseManifestMinecraft(gameVersion, modLoaders), Collections.emptyList());
        zip.putTextFile(JsonUtils.GSON.toJson(curseManifest), "manifest.json");
        progress.update(1);
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
