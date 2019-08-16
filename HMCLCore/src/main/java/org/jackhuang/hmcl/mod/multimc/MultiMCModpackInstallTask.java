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
package org.jackhuang.hmcl.mod.multimc;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.game.VersionLibraryBuilder;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author huangyuhui
 */
public final class MultiMCModpackInstallTask extends Task<Void> {

    private final File zipFile;
    private final Modpack modpack;
    private final MultiMCInstanceConfiguration manifest;
    private final String name;
    private final DefaultGameRepository repository;
    private final List<Task<?>> dependencies = new LinkedList<>();
    private final List<Task<?>> dependents = new LinkedList<>();
    
    public MultiMCModpackInstallTask(DefaultDependencyManager dependencyManager, File zipFile, Modpack modpack, MultiMCInstanceConfiguration manifest, String name) {
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.name = name;
        this.repository = dependencyManager.getGameRepository();

        File json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");

        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.getGameVersion());

        if (manifest.getMmcPack() != null) {
            Optional<MultiMCManifest.MultiMCManifestComponent> forge = manifest.getMmcPack().getComponents().stream().filter(e -> e.getUid().equals("net.minecraftforge")).findAny();
            forge.ifPresent(c -> {
                if (c.getVersion() != null)
                    builder.version("forge", c.getVersion());
            });

            Optional<MultiMCManifest.MultiMCManifestComponent> liteLoader = manifest.getMmcPack().getComponents().stream().filter(e -> e.getUid().equals("com.mumfrey.liteloader")).findAny();
            liteLoader.ifPresent(c -> {
                if (c.getVersion() != null)
                    builder.version("liteloader", c.getVersion());
            });
        }

        dependents.add(builder.buildAsync());
        onDone().register(event -> {
            if (event.isFailed())
                repository.removeVersionFromDisk(name);
        });
    }
    
    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        File run = repository.getRunDirectory(name);
        File json = repository.getModpackConfiguration(name);

        ModpackConfiguration<MultiMCInstanceConfiguration> config = null;
        try {
            if (json.exists()) {
                config = JsonUtils.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<MultiMCInstanceConfiguration>>() {
                }.getType());

                if (!MODPACK_TYPE.equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a MultiMC modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(modpack.getEncoding()).build()) {
            if (Files.exists(fs.getPath("/" + manifest.getName() + "/.minecraft")))
                dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), "/" + manifest.getName() + "/.minecraft", any -> true, config));
            else if (Files.exists(fs.getPath("/" + manifest.getName() + "/minecraft")))
                dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), "/" + manifest.getName() + "/minecraft", any -> true, config));
        }
    }

    @Override
    public List<Task<?>> getDependents() {
        return dependents;
    }
    
    @Override
    public void execute() throws Exception {
        Version version = repository.readVersionJson(name);

        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(zipFile.toPath())) {
            Path root = MultiMCInstanceConfiguration.getRootPath(fs.getPath("/"));
            Path patches = root.resolve("patches");

            if (Files.exists(patches))
                for (Path patchJson : Files.newDirectoryStream(patches)) {
                    if (patchJson.toString().endsWith(".json")) {
                        // If json is malformed, we should stop installing this modpack instead of skipping it.
                        MultiMCInstancePatch patch = JsonUtils.GSON.fromJson(IOUtils.readFullyAsString(Files.newInputStream(patchJson)), MultiMCInstancePatch.class);

                        VersionLibraryBuilder builder = new VersionLibraryBuilder(version);
                        for (String arg : patch.getTweakers())
                            builder.addArgument("--tweakClass", arg);

                        version = builder.build()
                                .setLibraries(Lang.merge(version.getLibraries(), patch.getLibraries()))
                                .setMainClass(patch.getMainClass());
                    }
                }
        }

        dependencies.add(new MaintainTask(version).thenComposeAsync(repository::save));
        dependencies.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), "/" + manifest.getName() + "/minecraft", manifest, MODPACK_TYPE, repository.getModpackConfiguration(name)));
    }

    public static final String MODPACK_TYPE = "MultiMC";
}
