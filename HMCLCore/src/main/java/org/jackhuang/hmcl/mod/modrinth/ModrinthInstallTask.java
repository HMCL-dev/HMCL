/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ModrinthInstallTask extends Task<Void> {
    private static final Set<String> SUPPORTED_ICON_EXTS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp", "apng");

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository repository;
    private final Path zipFile;
    private final Modpack modpack;
    private final ModrinthManifest manifest;
    private final String name;
    private final String iconUrl;
    private final Path run;
    private final ModpackConfiguration<ModrinthManifest> config;
    private String iconExt;
    private Task<Path> downloadIconTask;
    private final List<Task<?>> dependents = new ArrayList<>(4);
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public ModrinthInstallTask(DefaultDependencyManager dependencyManager, Path zipFile, Modpack modpack, ModrinthManifest manifest, String name, String iconUrl) {
        this.dependencyManager = dependencyManager;
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.name = name;
        this.iconUrl = iconUrl;
        this.repository = dependencyManager.getGameRepository();
        this.run = repository.getRunDirectory(name);

        Path json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && Files.notExists(json))
            throw new IllegalArgumentException("Version " + name + " already exists.");

        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.getGameVersion());
        for (Map.Entry<String, String> modLoader : manifest.getDependencies().entrySet()) {
            switch (modLoader.getKey()) {
                case "minecraft":
                    break;
                case "forge":
                    builder.version("forge", modLoader.getValue());
                    break;
                case "neoforge":
                // https://github.com/HMCL-dev/HMCL/pull/5170
                case "neo-forge":
                    builder.version("neoforge", modLoader.getValue());
                    break;
                case "fabric-loader":
                    builder.version("fabric", modLoader.getValue());
                    break;
                case "quilt-loader":
                    builder.version("quilt", modLoader.getValue());
                    break;
                default:
                    throw new IllegalStateException("Unsupported mod loader " + modLoader.getKey());
            }
        }
        dependents.add(builder.buildAsync());

        onDone().register(event -> {
            Exception ex = event.getTask().getException();
            if (event.isFailed()) {
                if (!(ex instanceof ModpackCompletionException)) {
                    repository.removeVersionFromDisk(name);
                }
            }
        });

        ModpackConfiguration<ModrinthManifest> config = null;
        try {
            if (Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(ModrinthManifest.class));

                if (!ModrinthModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a Modrinth modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        this.config = config;
        List<String> subDirectories = Arrays.asList("/client-overrides", "/overrides");
        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), subDirectories, any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), subDirectories, manifest, ModrinthModpackProvider.INSTANCE, manifest.getName(), manifest.getVersionId(), repository.getModpackConfiguration(name)).withStage("hmcl.modpack"));

        URI iconUri = NetworkUtils.toURIOrNull(iconUrl);
        if (iconUri != null) {
            String ext = FileUtils.getExtension(StringUtils.substringAfter(iconUri.getPath(), '/')).toLowerCase(Locale.ROOT);
            if (SUPPORTED_ICON_EXTS.contains(ext)) {
                iconExt = ext;
                dependents.add(downloadIconTask = new CacheFileTask(iconUrl));
            }
        }
        dependencies.add(new ModrinthCompletionTask(dependencyManager, name, manifest));
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        if (config != null) {
            // For update, remove mods not listed in new manifest
            for (ModrinthManifest.File oldManifestFile : config.getManifest().getFiles()) {
                Path oldFile = run.resolve(oldManifestFile.getPath());
                if (!Files.exists(oldFile)) continue;
                if (manifest.getFiles().stream().noneMatch(oldManifestFile::equals)) {
                    Files.deleteIfExists(oldFile);
                }
            }
        }

        Path root = repository.getVersionRoot(name);
        Files.createDirectories(root);
        JsonUtils.writeToJsonFile(root.resolve("modrinth.index.json"), manifest);

        if (iconExt != null) {
            try {
                Files.copy(downloadIconTask.getResult(), root.resolve("icon." + iconExt));
            } catch (Exception e) {
                LOG.warning("Failed to copy modpack icon", e);
            }
        }
    }
}
