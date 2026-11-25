// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.server;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.ModpackExportTask;
import org.jackhuang.hmcl.task.TaskExecutionContext;
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
import java.util.List;
import java.util.function.Predicate;

/// Exports one registered game instance as an HMCL server modpack archive.
@NotNullByDefault
public class ServerModpackExportTask extends ModpackExportTask {
    /// Creates a server modpack export task.
    ///
    /// @param instance    the registered instance snapshot to export
    /// @param exportInfo  the export configuration
    /// @param modpackFile the archive to write
    public ServerModpackExportTask(DefaultGameInstance instance, ModpackExportInfo exportInfo, Path modpackFile) {
        super(instance, exportInfo, modpackFile);
    }

    /// {@inheritDoc}
    @Override
    protected void writeArchive(Zipper zip, Predicate<String> include, TaskExecutionContext context) throws IOException {
        Path runDirectory = instance.getRunDirectory();
        List<ModpackConfiguration.FileInformation> files = new ArrayList<>();
        zip.putDirectoryProgressed(runDirectory, "overrides", path -> {
            if (include.test(path)) {
                Path file = runDirectory.resolve(path);
                if (Files.isRegularFile(file)) {
                    String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                    files.add(new ModpackConfiguration.FileInformation(relativePath, DigestUtils.digestToString("SHA-1", file, context::checkCancellation)));
                }
                return true;
            } else {
                return false;
            }
        }, context.progress()::update);

        GameVersionNumber version = instance.getVersion();
        if (version == GameVersionNumber.unknown()) {
            throw new IOException("Cannot parse the version of " + instance.getId());
        }
        String gameVersion = version.toString();
        List<ServerModpackManifest.Addon> addons = new ArrayList<>();
        addons.add(new ServerModpackManifest.Addon(GameComponentType.GAME.getPatchId(), gameVersion));

        for (GameComponentAnalyzer.Mark mark : instance.getAnalyzer()) {
            if ((mark.componentType().isModLoader() || mark.componentType() == GameComponentType.OPTIFINE)
                    && mark.version() != null) {
                addons.add(new ServerModpackManifest.Addon(mark.componentType().getPatchId(), mark.version()));
            }
        }

        ServerModpackManifest manifest = new ServerModpackManifest(info.getName(), info.getAuthor(), info.getVersion(), info.getDescription(), StringUtils.removeSuffix(info.getFileApi(), "/"), files, addons);
        zip.putTextFile(JsonUtils.GSON.toJson(manifest), "server-manifest.json");
    }

    /// Export options supported by the server modpack format.
    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireAuthor()
            .requireFileApi(false);
}
