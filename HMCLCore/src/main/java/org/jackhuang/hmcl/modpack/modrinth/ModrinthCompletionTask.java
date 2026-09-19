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
package org.jackhuang.hmcl.modpack.modrinth;

import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.modpack.ModpackCompletionException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Completes missing files for an installed Modrinth modpack.
@NotNullByDefault
public class ModrinthCompletionTask extends Task<Void> {

    /// The dependency manager used to download remote files.
    private final DefaultDependencyManager dependency;

    /// The fixed registered instance completed by this task.
    private final DefaultGameInstance instance;

    /// The mod manager associated with [#instance].
    private final ModManager modManager;

    /// The manifest supplied by the caller or loaded from disk, if available.
    private @Nullable ModrinthManifest manifest;

    /// Keys of optional files the user chose not to install; `null` means install all.
    private @Nullable Set<String> excludedFiles;

    /// Download tasks produced during [#execute()].
    private final List<Task<?>> dependencies = new ArrayList<>();

    /// Whether every required download has at least one usable URL.
    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);

    /// The number of manifest entries processed.
    private final AtomicInteger finished = new AtomicInteger(0);

    /// Whether a required file has no usable download URL.
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /// Creates a task that completes the installed Modrinth modpack.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, DefaultGameInstance instance) {
        this(dependencyManager, instance, null, null);
    }

    /// Creates a task that completes the installed Modrinth modpack using an optional manifest.
    ///
    /// @param dependencyManager the dependency manager
    /// @param instance          the registered instance to complete
    /// @param manifest          the Modrinth manifest, or `null` to read it from disk
    /// @param excludedFiles     keys of optional files the user chose not to install; `null` means install all.
    ///                          When non-null, must not contain `null` elements.
    public ModrinthCompletionTask(
            DefaultDependencyManager dependencyManager,
            DefaultGameInstance instance,
            @Nullable ModrinthManifest manifest,
            @Nullable Set<String> excludedFiles) {
        dependencyManager.validateGameInstance(instance);
        this.dependency = dependencyManager;
        this.instance = instance;
        this.modManager = instance.getModManager();
        this.manifest = manifest;
        this.excludedFiles = excludedFiles == null ? null : Set.copyOf(excludedFiles);

        if (manifest == null)
            try {
                Path root = instance.getInstanceRoot();
                Path manifestFile = root.resolve("modrinth.index.json");
                if (Files.exists(manifestFile))
                    this.manifest = JsonUtils.fromJsonFile(manifestFile, ModrinthManifest.class);
                Path excludedFile = root.resolve("excluded.json");
                if (Files.exists(excludedFile)) {
                    this.excludedFiles = Set.copyOf(Objects.requireNonNull(
                            JsonUtils.fromJsonFile(excludedFile, JsonUtils.listTypeOf(String.class))));
                } else {
                    this.excludedFiles = null;
                }
            } catch (Exception e) {
                LOG.warning("Unable to read Modrinth modpack manifest.json", e);
            }

        setStage("hmcl.modpack.download");
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null)
            return;

        Path runDirectory = FileUtils.toAbsolute(instance.getRunDirectory());
        Path modsDirectory = runDirectory.resolve("mods");

        if (excludedFiles != null) {
            JsonUtils.writeToJsonFile(
                    instance.getInstanceRoot().resolve("excluded.json"),
                    List.copyOf(excludedFiles));
        }

        for (ModrinthManifest.File file : manifest.getFiles()) {
            if (file.env() != null && file.env().getOrDefault("client", "required").equals("unsupported"))
                continue;
            if (file.downloads().isEmpty())
                continue;
            if (excludedFiles != null && excludedFiles.contains(file.key()))
                continue;

            Path filePath = runDirectory.resolve(file.path()).toAbsolutePath().normalize();
            if (!filePath.startsWith(runDirectory))
                throw new IOException("Unsecure path: " + file.path());

            if (Files.exists(filePath))
                continue;
            if (modsDirectory.equals(filePath.getParent()) && this.modManager.hasSimpleMod(FileUtils.getName(filePath)))
                continue;

            var task = new FileDownloadTask(
                    dependency.getDownloadProvider().injectURLsWithCandidates(file.downloads()),
                    filePath);
            task.setCacheRepository(dependency.getCacheRepository());
            task.setCaching(true);
            dependencies.add(task.withCounter("hmcl.modpack.download"));
        }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        // Let this task fail if the curse manifest has not been completed.
        // But continue other downloads.
        if (notFound.get())
            throw new ModpackCompletionException(new FileNotFoundException());
        if (!allNameKnown.get() || !isDependenciesSucceeded())
            throw new ModpackCompletionException();
    }
}
