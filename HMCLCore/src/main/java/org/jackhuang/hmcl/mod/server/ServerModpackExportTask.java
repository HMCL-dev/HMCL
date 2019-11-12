/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.server;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;

public class ServerModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String versionId;
    private final List<String> whitelist;
    private final File output;
    private final String modpackName;
    private final String modpackAuthor;
    private final String modpackVersion;
    private final String modpackDescription;
    private final String modpackFileApi;

    public ServerModpackExportTask(DefaultGameRepository repository, String versionId, List<String> whitelist, String modpackName, String modpackAuthor, String modpackVersion, String modpackDescription, String modpackFileApi, File output) {
        this.repository = repository;
        this.versionId = versionId;
        this.whitelist = whitelist;
        this.output = output;
        this.modpackName = modpackName;
        this.modpackAuthor = modpackAuthor;
        this.modpackVersion = modpackVersion;
        this.modpackDescription = modpackDescription;
        this.modpackFileApi = modpackFileApi;

        onDone().register(event -> {
            if (event.isFailed()) output.delete();
        });
    }

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(versionId + ".jar");
        blackList.add(versionId + ".json");
        Logging.LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (Zipper zip = new Zipper(output.toPath())) {
            Path runDirectory = repository.getRunDirectory(versionId).toPath();
            List<ModpackConfiguration.FileInformation> files = new ArrayList<>();
            zip.putDirectory(runDirectory, "overrides", path -> {
                if (Modpack.acceptFile(path, blackList, whitelist)) {
                    Path file = runDirectory.resolve(path);
                    if (Files.isRegularFile(file)) {
                        String relativePath = runDirectory.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                        files.add(new ModpackConfiguration.FileInformation(relativePath, encodeHex(digest("SHA-1", file))));
                    }
                    return true;
                } else {
                    return false;
                }
            });

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(repository.getResolvedPreservingPatchesVersion(versionId));
            String gameVersion = GameVersion.minecraftVersion(repository.getVersionJar(versionId))
                    .orElseThrow(() -> new IOException("Cannot parse the version of " + versionId));
            List<ServerModpackManifest.Addon> addons = new ArrayList<>();
            addons.add(new ServerModpackManifest.Addon(MINECRAFT.getPatchId(), gameVersion));
            analyzer.getVersion(FORGE).ifPresent(forgeVersion ->
                    addons.add(new ServerModpackManifest.Addon(FORGE.getPatchId(), forgeVersion)));
            analyzer.getVersion(LITELOADER).ifPresent(liteLoaderVersion ->
                    addons.add(new ServerModpackManifest.Addon(LITELOADER.getPatchId(), liteLoaderVersion)));
            analyzer.getVersion(OPTIFINE).ifPresent(optifineVersion ->
                    addons.add(new ServerModpackManifest.Addon(OPTIFINE.getPatchId(), optifineVersion)));
            analyzer.getVersion(FABRIC).ifPresent(fabricVersion ->
                    addons.add(new ServerModpackManifest.Addon(FABRIC.getPatchId(), fabricVersion)));
            ServerModpackManifest manifest = new ServerModpackManifest(modpackName, modpackAuthor, modpackVersion, modpackDescription, StringUtils.removeSuffix(modpackFileApi, "/"), files, addons);
            zip.putTextFile(JsonUtils.GSON.toJson(manifest), "server-manifest.json");
        }
    }
}
