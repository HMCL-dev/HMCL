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
package org.jackhuang.hmcl.mod.multimc;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;

/**
 * Export the game to a mod pack file.
 */
public class MultiMCModpackExportTask extends Task<Void> {
    private final DefaultGameRepository repository;
    private final String versionId;
    private final List<String> whitelist;
    private final MultiMCInstanceConfiguration configuration;
    private final File output;

    /**
     * @param output    mod pack file.
     * @param versionId to locate version.json
     */
    public MultiMCModpackExportTask(DefaultGameRepository repository, String versionId, List<String> whitelist, MultiMCInstanceConfiguration configuration, File output) {
        this.repository = repository;
        this.versionId = versionId;
        this.whitelist = whitelist;
        this.configuration = configuration;
        this.output = output;

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
            zip.putDirectory(repository.getRunDirectory(versionId).toPath(), ".minecraft", path -> Modpack.acceptFile(path, blackList, whitelist));

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(repository.getResolvedPreservingPatchesVersion(versionId));
            String gameVersion = repository.getGameVersion(versionId)
                    .orElseThrow(() -> new IOException("Cannot parse the version of " + versionId));
            List<MultiMCManifest.MultiMCManifestComponent> components = new ArrayList<>();
            components.add(new MultiMCManifest.MultiMCManifestComponent(true, false, "net.minecraft", gameVersion));
            analyzer.getVersion(FORGE).ifPresent(forgeVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "net.minecraftforge", forgeVersion)));
            analyzer.getVersion(NEO_FORGE).ifPresent(neoForgeVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "net.neoforged", neoForgeVersion)));
            analyzer.getVersion(LITELOADER).ifPresent(liteLoaderVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "com.mumfrey.liteloader", liteLoaderVersion)));
            analyzer.getVersion(FABRIC).ifPresent(fabricVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "net.fabricmc.fabric-loader", fabricVersion)));
            analyzer.getVersion(QUILT).ifPresent(quiltVersion ->
                    components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, "org.quiltmc.quilt-loader", quiltVersion)));
            MultiMCManifest mmcPack = new MultiMCManifest(1, components);
            zip.putTextFile(JsonUtils.GSON.toJson(mmcPack), "mmc-pack.json");

            StringWriter writer = new StringWriter();
            configuration.toProperties().store(writer, "Auto generated by Hello Minecraft! Launcher");
            zip.putTextFile(writer.toString(), "instance.cfg");

            zip.putTextFile("", ".packignore");
        }
    }

    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options().requireMinMemory();
}
