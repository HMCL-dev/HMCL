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
package org.jackhuang.hmcl.modpack.multimc;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Exports one registered game instance as a MultiMC modpack archive.
@NotNullByDefault
public class MultiMCModpackExportTask extends Task<Void> {
    /// The fixed instance snapshot exported by this task.
    private final DefaultGameInstance instance;

    /// The paths selected for inclusion in the archive.
    private final List<String> whitelist;

    /// The MultiMC instance configuration written to the archive.
    private final MultiMCInstanceConfiguration configuration;

    /// The archive written by this task.
    private final Path output;

    /// Creates a MultiMC modpack export task.
    ///
    /// @param instance      the registered instance snapshot to export
    /// @param whitelist     the paths selected for inclusion
    /// @param configuration the MultiMC instance configuration
    /// @param output        the archive to write
    public MultiMCModpackExportTask(DefaultGameInstance instance, List<String> whitelist, MultiMCInstanceConfiguration configuration, Path output) {
        this.instance = instance;
        this.whitelist = whitelist;
        this.configuration = configuration;
        this.output = output;

        onDone().register(event -> {
            if (event.isFailed()) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException e) {
                    LOG.warning("Failed to delete modpack file: " + output, e);
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
        try (Zipper zip = new Zipper(output)) {
            zip.putDirectory(instance.getRunDirectory(), ".minecraft", path -> Modpack.acceptFile(path, blackList, whitelist));

            GameVersionNumber version = instance.getVersion();
            if (version == GameVersionNumber.unknown()) {
                throw new IOException("Cannot parse the version of " + instanceId);
            }
            String gameVersion = version.toString();
            GameComponentAnalyzer analyzer = instance.getAnalyzer();
            List<MultiMCManifest.MultiMCManifestComponent> components = new ArrayList<>();
            components.add(new MultiMCManifest.MultiMCManifestComponent(true, false, MultiMCComponents.getComponent(GameComponentType.GAME), gameVersion));

            for (Map.Entry<String, GameComponentType> pair : MultiMCComponents.getPairs()) {
                if (pair.getValue().isModLoader()) {
                    String componentVersion = analyzer.getVersion(pair.getValue());
                    if (componentVersion != null) {
                        components.add(new MultiMCManifest.MultiMCManifestComponent(false, false, pair.getKey(), componentVersion));
                    }
                }
            }

            MultiMCManifest mmcPack = new MultiMCManifest(1, components);
            zip.putTextFile(JsonUtils.GSON.toJson(mmcPack), "mmc-pack.json");

            StringWriter writer = new StringWriter();
            configuration.toProperties().store(writer, "Auto generated by Hello Minecraft! Launcher");
            zip.putTextFile(writer.toString(), "instance.cfg");

            zip.putTextFile("", ".packignore");
        }
    }

    /// Export options supported by the MultiMC format.
    public static final ModpackExportInfo.Options OPTION = new ModpackExportInfo.Options()
            .requireAuthor()
            .requireMinMemory();
}
