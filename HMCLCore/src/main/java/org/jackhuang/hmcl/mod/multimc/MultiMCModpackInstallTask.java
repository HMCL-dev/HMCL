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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>A task transforming MultiMC Modpack Scheme to Official Launcher Scheme.
 * The transforming process contains 7 stage:
 * <ul>
 *     <li>General Setup</li>
 *     <li>Load Components</li>
 *     <li>Resolve Json-Patch</li>
 *     <li>Build Artifact</li>
 *     <li>Copy Embedded Files</li>
 *     <li>Assemble Game</li>
 *     <li>Download Game</li>
 *     <li>Apply JAR mods</li>
 * </ul>
 * See codes below for detailed implementation.
 * </p>
 */
public final class MultiMCModpackInstallTask extends Task<MultiMCInstancePatch.ResolvedInstance> {

    private final File zipFile;
    private final Modpack modpack;
    private final MultiMCInstanceConfiguration manifest;
    private final String name;
    private final DefaultGameRepository repository;
    private final List<Task<?>> dependents = new ArrayList<>();
    private final List<Task<?>> dependencies = new ArrayList<>();
    private final DefaultDependencyManager dependencyManager;

    public MultiMCModpackInstallTask(DefaultDependencyManager dependencyManager, File zipFile, Modpack modpack, MultiMCInstanceConfiguration manifest, String name) {
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.name = name;
        this.dependencyManager = dependencyManager;
        this.repository = dependencyManager.getGameRepository();

        File json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");

        onDone().register(event -> {
            if (event.isFailed())
                repository.removeVersionFromDisk(name);
        });
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        // Stage #0: General Setup
        {
            File run = repository.getRunDirectory(name);
            File json = repository.getModpackConfiguration(name);

            ModpackConfiguration<MultiMCInstanceConfiguration> config = null;
            try {
                if (json.exists()) {
                    config = JsonUtils.GSON.fromJson(FileUtils.readText(json), ModpackConfiguration.typeOf(MultiMCInstanceConfiguration.class));

                    if (!MultiMCModpackProvider.INSTANCE.getName().equals(config.getType()))
                        throw new IllegalArgumentException("Version " + name + " is not a MultiMC modpack. Cannot update this version.");
                }
            } catch (JsonParseException | IOException ignore) {
            }

            String mcDirectory;
            try (FileSystem fs = openModpack()) {
                mcDirectory = getRootPath(fs).resolve(".minecraft").toAbsolutePath().normalize().toString();
            }

            // TODO: Optimize unbearably slow ModpackInstallTask
            dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList(mcDirectory), any -> true, config).withStage("hmcl.modpack"));
            dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList(mcDirectory), manifest, MultiMCModpackProvider.INSTANCE, manifest.getName(), null, repository.getModpackConfiguration(name)).withStage("hmcl.modpack"));
        }

        // Stage #1: Load all related Json-Patch from meta maven or local mod pack.

        try (FileSystem fs = openModpack()) {
            Path root = getRootPath(fs);

            List<Task<MultiMCInstancePatch>> patches = new ArrayList<>();
            for (MultiMCManifest.MultiMCManifestComponent component : Objects.requireNonNull(
                    Objects.requireNonNull(manifest.getMmcPack(), "mmc-pack.json").getComponents(), "components"
            )) {
                String componentID = Objects.requireNonNull(component.getUid(), "Component ID");
                Path patchPath = root.resolve(String.format("patches/%s.json", componentID));

                if (Files.exists(patchPath)) {
                    if (!Files.isRegularFile(patchPath)) {
                        throw new IllegalArgumentException("Json-Patch isn't a file: " + componentID);
                    }

                    MultiMCInstancePatch patch = MultiMCInstancePatch.read(componentID, FileUtils.readText(patchPath, StandardCharsets.UTF_8));
                    patches.add(Task.supplyAsync(() -> patch)); // TODO: Task.completed has unclear compatibility issue.
                } else {
                    patches.add(
                            new GetTask(MultiMCComponents.getMetaURL(componentID, component.getVersion()))
                                    .thenApplyAsync(s -> MultiMCInstancePatch.read(componentID, s))
                    );
                }
            }
            dependents.add(new MMCInstancePatchesAssembleTask(patches));
        }
    }

    private static final class MMCInstancePatchesAssembleTask extends Task<List<MultiMCInstancePatch>> {
        private final List<Task<MultiMCInstancePatch>> patches;

        public MMCInstancePatchesAssembleTask(List<Task<MultiMCInstancePatch>> patches) {
            this.patches = patches;
        }

        @Override
        public Collection<? extends Task<?>> getDependents() {
            return patches;
        }

        @Override
        public void execute() throws Exception {
            Map<String, MultiMCInstancePatch> existed = new HashMap<>();
            for (Task<MultiMCInstancePatch> patch : patches) {
                MultiMCInstancePatch result = patch.getResult();

                existed.put(result.getID(), result);
            }

            checking:
            while (true) {
                for (MultiMCInstancePatch patch : existed.values()) {
                    for (MultiMCManifest.MultiMCManifestCachedRequires require : patch.getRequires()) {
                        String componentID = require.getID();
                        if (!existed.containsKey(componentID)) {
                            Task<MultiMCInstancePatch> task = new GetTask(MultiMCComponents.getMetaURL(
                                    componentID, Lang.requireNonNullElse(require.getEqualsVersion(), require.getSuggests())
                            )).thenApplyAsync(s -> MultiMCInstancePatch.read(componentID, s));
                            task.run();

                            MultiMCInstancePatch result = Objects.requireNonNull(task.getResult());
                            existed.put(result.getID(), result);
                            continue checking;
                        }
                    }
                }

                break;
            }

            setResult(new ArrayList<>(existed.values()));
        }
    }

    @Override
    public List<Task<?>> getDependents() {
        // Stage #2: Resolve all Json-Patch
        return dependents;
    }

    @Override
    public void execute() throws Exception {
        // Stage #3: Build Json-Patch artifact.
        MultiMCInstancePatch.ResolvedInstance artifact = null;
        for (int i = dependents.size() - 1; i >= 0; i--) {
            Task<?> task = dependents.get(i);
            if (task instanceof MMCInstancePatchesAssembleTask) {
                artifact = MultiMCInstancePatch.resolveArtifact(((MMCInstancePatchesAssembleTask) task).getResult(), name);
                break;
            }
        }
        Objects.requireNonNull(artifact, "artifact");

        // Stage #4: Copy embedded files.
        try (FileSystem fs = openModpack()) {
            Path root = getRootPath(fs);

            Path libraries = root.resolve("libraries");
            if (Files.exists(libraries))
                FileUtils.copyDirectory(libraries, repository.getVersionRoot(name).toPath().resolve("libraries"));

            String iconKey = this.manifest.getIconKey();
            if (iconKey != null) {
                Path iconFile = root.resolve(iconKey + ".png");
                if (Files.exists(iconFile)) {
                    FileUtils.copyFile(iconFile, repository.getVersionRoot(name).toPath().resolve("icon.png"));
                }
            }
        }

        // Stage #5: Assemble game files.
        {
            Version version = artifact.getVersion();

            dependencies.add(repository.saveAsync(artifact.getVersion()));
            dependencies.add(new GameAssetDownloadTask(dependencyManager, version, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true));
            dependencies.add(new GameLibrariesTask(
                    dependencyManager,
                    // TODO: check integrity of maven-only files when launching games?
                    version.setLibraries(Lang.merge(version.getLibraries(), artifact.getMavenOnlyFiles())),
                    true
            ));

            Artifact mainJarArtifact = artifact.getMainJar().getArtifact();
            String gameVersion = artifact.getGameVersion();
            if (gameVersion != null &&
                    "com.mojang".equals(mainJarArtifact.getGroup()) &&
                    "minecraft".equals(mainJarArtifact.getName()) &&
                    Objects.equals(gameVersion, mainJarArtifact.getVersion()) &&
                    "client".equals(mainJarArtifact.getClassifier())
            ) {
                dependencies.add(new GameDownloadTask(dependencyManager, gameVersion, version));
            } else {
                dependencies.add(new GameDownloadTask(dependencyManager, null, version));
            }
        }

        setResult(artifact);
    }

    @Override
    public List<Task<?>> getDependencies() {
        // Stage #6: Download game files.
        return dependencies;
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        MultiMCInstancePatch.ResolvedInstance artifact = Objects.requireNonNull(getResult(), "ResolvedInstance");

        List<String> files = artifact.getJarModFileNames();
        if (!isDependenciesSucceeded() || files.isEmpty()) {
            return;
        }

        // Stage #7: Apply jar mods.
        try (FileSystem fs = openModpack()) {
            Path root = getRootPath(fs).resolve("jarmods");

            try (FileSystem mc = CompressingUtils.writable(
                    repository.getVersionRoot(name).toPath().resolve(name + ".jar")
            ).setAutoDetectEncoding(true).build()) {
                for (String fileName : files) {
                    try (FileSystem jm = CompressingUtils.readonly(root.resolve(fileName)).setAutoDetectEncoding(true).build()) {
                        FileUtils.copyDirectory(jm.getPath("/"), mc.getPath("/"));
                    }
                }
            }
        }
    }

    private FileSystem openModpack() throws IOException {
        return CompressingUtils.readonly(zipFile.toPath()).setAutoDetectEncoding(true).setEncoding(modpack.getEncoding()).build();
    }

    private static boolean testPath(Path root) {
        return Files.exists(root.resolve("instance.cfg"));
    }

    private static Path getRootPath(FileSystem fs) throws IOException {
        Path root = fs.getPath("/");

        if (testPath(root)) {
            return root;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path candidate : stream) {
                if (testPath(candidate)) {
                    return candidate;
                }
            }
        }

        throw new IOException("Not a valid MultiMC modpack");
    }
}
