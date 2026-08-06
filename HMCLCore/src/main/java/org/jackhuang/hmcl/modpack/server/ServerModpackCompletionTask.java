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
package org.jackhuang.hmcl.modpack.server;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Synchronizes an installed server modpack with its remote manifest.
@NotNullByDefault
public class ServerModpackCompletionTask extends Task<Void> {

    /// The dependency manager used for downloads and game-component updates.
    private final DefaultDependencyManager dependencyManager;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The fixed configuration-file path for [#instance].
    private final Path configurationFile;

    /// The installed configuration supplied by the caller or loaded from disk.
    private @Nullable ModpackConfiguration<ServerModpackManifest> manifest;

    /// The remote-manifest request created during [#preExecute()].
    private @Nullable GetTask dependent;

    /// The remote manifest parsed during [#execute()].
    private @Nullable ServerModpackManifest remoteManifest;

    /// Download and game-builder tasks produced during [#execute()].
    private final List<Task<?>> dependencies = new ArrayList<>();

    /// Creates a task that loads the installed configuration from disk.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public ServerModpackCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null);
    }

    /// Creates a task using an optional preloaded configuration.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the installed configuration, or `null` to read it from disk
    public ServerModpackCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable ModpackConfiguration<ServerModpackManifest> manifest) {
        dependencyManager.validateGameInstance(instance);
        this.dependencyManager = dependencyManager;
        this.instance = instance;
        this.configurationFile = instance.getRepository().getModpackConfiguration(instance.getId());

        if (manifest == null) {
            try {
                if (Files.exists(configurationFile)) {
                    this.manifest = JsonUtils.fromJsonFile(configurationFile, ModpackConfiguration.typeOf(ServerModpackManifest.class));
                }
            } catch (Exception e) {
                LOG.warning("Unable to read Server modpack manifest.json", e);
            }
        } else {
            this.manifest = manifest;
        }

        setStage("hmcl.modpack.download");
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
        dependent = new GetTask(manifest.getManifest().getFileApi() + "/server-manifest.json");
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    private Map<String, String> toMap(Collection<ServerModpackManifest.Addon> addons) {
        return addons.stream().collect(Collectors.toMap(ServerModpackManifest.Addon::getId, ServerModpackManifest.Addon::getVersion));
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;

        try {
            remoteManifest = JsonUtils.fromNonNullJson(dependent.getResult(), ServerModpackManifest.class);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        Map<String, String> oldAddons = toMap(manifest.getManifest().getAddons());
        Map<String, String> newAddons = toMap(remoteManifest.getAddons());
        if (!Objects.equals(oldAddons, newAddons)) {
            GameBuilder builder = dependencyManager.newGameBuilder().name(instance.getId());
            for (ServerModpackManifest.Addon addon : remoteManifest.getAddons()) {
                builder.version(addon.getId(), addon.getVersion());
            }

            dependencies.add(builder.buildAsync());
        }

        Path rootPath = instance.getInstanceRoot().toAbsolutePath().normalize();
        Map<String, ModpackConfiguration.FileInformation> files = manifest.getManifest().getFiles().stream()
                .collect(Collectors.toMap(ModpackConfiguration.FileInformation::getPath,
                        Function.identity()));

        Set<String> remoteFiles = remoteManifest.getFiles().stream().map(ModpackConfiguration.FileInformation::getPath)
                .collect(Collectors.toSet());

        Path runDirectory = instance.getRunDirectory().toAbsolutePath().normalize();
        Path modsDirectory = runDirectory.resolve("mods");

        int total = 0;
        // for files in new modpack
        for (ModpackConfiguration.FileInformation file : remoteManifest.getFiles()) {
            Path actualPath = rootPath.resolve(file.getPath()).toAbsolutePath().normalize();
            String fileName = actualPath.getFileName().toString();

            if (!actualPath.startsWith(rootPath)) {
                throw new IOException("Unsecure path: " + file.getPath());
            }

            boolean download;

            boolean isModDisabled = modsDirectory.equals(actualPath.getParent()) &&
                    (Files.exists(actualPath.resolveSibling(fileName + LocalAddonManager.DISABLED_EXTENSION)) ||
                            Files.exists(actualPath.resolveSibling(fileName + LocalAddonManager.OLD_EXTENSION)));

            if (isModDisabled) {
                download = false;
            } else if (!files.containsKey(file.getPath())) {
                // If old modpack does not have this entry, download it
                download = true;
            } else if (!Files.exists(actualPath)) {
                // If both old and new modpacks have this entry, but the file is missing...
                // Re-download it since network problem may cause file missing
                download = true;
            } else {
                // If user modified this entry file, we will not replace this file since this modified file is that user expects.
                String fileHash = DigestUtils.digestToString("SHA-1", actualPath);
                String oldHash = files.get(file.getPath()).getHash();
                download = !Objects.equals(oldHash, file.getHash()) && Objects.equals(oldHash, fileHash);
            }

            if (download) {
                total++;
                dependencies.add(new FileDownloadTask(
                        remoteManifest.getFileApi() + "/overrides/" + file.getPath(),
                        actualPath,
                        new FileDownloadTask.IntegrityCheck("SHA-1", file.getHash()))
                        .withCounter("hmcl.modpack.download"));
            }
        }

        // If old modpack have this entry, and new modpack deleted it. Delete this file.
        for (ModpackConfiguration.FileInformation file : manifest.getManifest().getFiles()) {
            Path actualPath = rootPath.resolve(file.getPath());
            if (Files.exists(actualPath) && !remoteFiles.contains(file.getPath()))
                Files.deleteIfExists(actualPath);
        }

        getProperties().put("total", dependencies.size());
        notifyPropertiesChanged();
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (manifest == null || StringUtils.isBlank(manifest.getManifest().getFileApi())) return;
        Files.createDirectories(configurationFile.getParent());
        JsonUtils.writeToJsonFile(configurationFile, new ModpackConfiguration<>(remoteManifest, this.manifest.getType(), this.manifest.getName(), this.manifest.getVersion(), remoteManifest.getFiles()));
    }
}
