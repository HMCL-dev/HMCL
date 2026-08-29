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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A task transforming MultiMC Modpack Scheme to Official Launcher Scheme.
/// The transforming process contains 7 stage:
///
///   - General Setup: Compute checksum and copy 'overrides' files.
///   - Load Components: Parse all local Json-Patch and prepare to fetch others from Internet.
///   - Resolve Json-Patch: Fetch remote Json-Patch and their dependencies.
///   - Build Artifact: Transform Json-Patch to Official Scheme lossily, without original structure.
///   - Copy Embedded Files: Copy embedded libraries and icon.
///   - Assemble Game: Prepare to download main jar, libraries and assets.
///   - Download Game: Download files.
///   - Apply JAR mods: Apply JAR mods into main jar.
///
/// See codes below for detailed implementation.
///
/// @implNote To guarantee all features of MultiMC Modpack Scheme is super hard.
/// As f\*\*\* MMC never provides a detailed API docs, most codes below is guessed from its source code.
/// **FUNCTIONS OF GAMES MIGHT NOT BE COMPLETELY THE SAME WITH MMC.**
public final class MultiMCModpackInstallTask extends Task<MultiMCInstancePatch.ResolvedInstance> {

    private final Path zipFile;
    private final Modpack modpack;
    private final MultiMCInstanceConfiguration manifest;
    private final GameInstanceID instanceId;

    /// Existing instance selecting update mode, or `null` for a new installation.
    private final @Nullable DefaultGameInstance updateTarget;

    private final DefaultGameRepository repository;
    private final List<Task<?>> dependents = new ArrayList<>();
    private final List<Task<?>> dependencies = new ArrayList<>();
    private final DefaultDependencyManager dependencyManager;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<MultiMCInstanceConfiguration> config;

    /// The repository transaction that owns a newly created instance root until publication.
    private @Nullable DefaultGameRepositoryDraft draft;

    /// Whether this task successfully reserved a previously absent instance in its draft.
    private boolean newInstallationReserved;

    /// Creates a MultiMC modpack installation task.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the source modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MultiMC instance configuration
    /// @param instanceId        the id of the new instance
    public MultiMCModpackInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            MultiMCInstanceConfiguration manifest,
            GameInstanceID instanceId) {
        this(dependencyManager, zipFile, modpack, manifest, instanceId, null);
    }

    /// Creates a MultiMC modpack update task.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the source modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MultiMC instance configuration
    /// @param instance          the existing instance to update
    /// @throws IllegalArgumentException if `instance` belongs to another repository, has no
    ///                                  modpack configuration, or records another provider type
    public MultiMCModpackInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            MultiMCInstanceConfiguration manifest,
            DefaultGameInstance instance) {
        this(dependencyManager, zipFile, modpack, manifest, instance.getId(), instance);
    }

    /// Creates a MultiMC modpack task in the mode selected by `updateTarget`.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param zipFile           the source modpack archive
    /// @param modpack           the parsed modpack metadata
    /// @param manifest          the MultiMC instance configuration
    /// @param instanceId        the target instance id
    /// @param updateTarget      the existing instance selecting update mode, or `null` for install
    private MultiMCModpackInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            Modpack modpack,
            MultiMCInstanceConfiguration manifest,
            GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget) {
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        this.dependencyManager = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        if (this.updateTarget != null) {
            dependencyManager.validateGameInstance(this.updateTarget);
        }

        Path json = repository.getLayout().getModpackConfigurationFile(instanceId);
        if (this.updateTarget != null && Files.notExists(json))
            throw new IllegalArgumentException("Instance " + instanceId + " is not a MultiMC modpack. Cannot update this instance.");

        @Nullable ModpackConfiguration<MultiMCInstanceConfiguration> config = null;
        try {
            if (this.updateTarget != null && Files.exists(json)) {
                config = JsonUtils.fromJsonFile(json, ModpackConfiguration.typeOf(MultiMCInstanceConfiguration.class));

                if (config == null || !MultiMCModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Instance " + instanceId + " is not a MultiMC modpack. Cannot update this instance.");
            }
        } catch (JsonParseException | IOException ignore) {
        }
        this.config = config;

        onDone().register(event -> {
            abortOpenDraft();
            if (event.isFailed() && newInstallationReserved)
                repository.removeInstanceFromDisk(instanceId);
        });
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    /// Reserves the instance in a repository draft before preparing tasks that write its root.
    @Override
    public void preExecute() throws Exception {
        DefaultGameRepositoryDraft openedDraft = repository.openDraft();
        draft = openedDraft;
        try {
            // Construction fixes the mode; the captured snapshot only verifies that it is still valid.
            boolean targetExists = openedDraft.getBaseSnapshot().hasInstance(instanceId);
            if (this.updateTarget == null && targetExists) {
                throw new IllegalStateException("Game instance already exists: " + instanceId);
            }
            if (this.updateTarget != null && !targetExists) {
                throw new IllegalStateException("Game instance no longer exists: " + instanceId);
            }

            openedDraft.put(new GameInstanceManifest(instanceId));
            newInstallationReserved = this.updateTarget == null;
        } catch (IOException | RuntimeException e) {
            try {
                openedDraft.abort();
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            draft = null;
            throw e;
        }

        // Stage #0: General Setup
        {
            Path run = repository.getLayout().getInstanceRoot(instanceId);

            String mcDirectory;
            try (FileSystem fs = openModpack()) {
                mcDirectory = getRootPath(fs).resolve(".minecraft").toAbsolutePath().normalize().toString();
            }

            // TODO: Optimize unbearably slow ModpackInstallTask
            dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList(mcDirectory), any -> true, config).withStage("hmcl.modpack"));
            dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList(mcDirectory), manifest, MultiMCModpackProvider.INSTANCE, manifest.getName(), null, repository.getLayout().getModpackConfigurationFile(instanceId)).withStage("hmcl.modpack"));
        }

        // Stage #1: Load all related Json-Patch from meta maven or local mod pack.

        try (FileSystem fs = openModpack()) {
            Path root = getRootPath(fs);

            List<MultiMCManifest.MultiMCManifestComponent> components = Objects.requireNonNull(
                    Objects.requireNonNull(manifest.getMmcPack(), "mmc-pack.json").getComponents(), "components"
            );
            List<Task<MultiMCInstancePatch>> patches = new ArrayList<>();

            String mcVersion = null;
            for (MultiMCManifest.MultiMCManifestComponent component : components) {
                if (MultiMCComponents.getComponent(component.getUid()) == GameComponentType.GAME) {
                    mcVersion = component.getVersion();
                    break;
                }
            }
            if (mcVersion == null) {
                throw new IllegalStateException("Cannot load modpacks without Minecraft.");
            }

            for (MultiMCManifest.MultiMCManifestComponent component : components) {
                String componentID = Objects.requireNonNull(component.getUid(), "Component ID");
                Path patchPath = root.resolve(String.format("patches/%s.json", componentID));

                if (Files.exists(patchPath)) {
                    if (!Files.isRegularFile(patchPath)) {
                        throw new IllegalArgumentException("Json-Patch isn't a file: " + componentID);
                    }

                    MultiMCInstancePatch patch = MultiMCInstancePatch.read(componentID, Files.readString(patchPath));
                    patches.add(Task.supplyAsync(() -> patch)); // TODO: Task.completed has unclear compatibility issue.
                } else {
                    patches.add(
                            new GetTask(MultiMCComponents.getMetaURL(componentID, component.getVersion(), mcVersion))
                                    .thenApplyAsync(s -> MultiMCInstancePatch.read(componentID, s))
                    );
                }
            }
            dependents.add(new MMCInstancePatchesAssembleTask(patches, mcVersion));
        }
    }

    private static final class MMCInstancePatchesAssembleTask extends Task<List<MultiMCInstancePatch>> {
        private final List<Task<MultiMCInstancePatch>> patches;
        private final String mcVersion;

        public MMCInstancePatchesAssembleTask(List<Task<MultiMCInstancePatch>> patches, String mcVersion) {
            this.patches = patches;
            this.mcVersion = mcVersion;
        }

        @Override
        public Collection<? extends Task<?>> getDependents() {
            return patches;
        }

        @Override
        public void execute() throws Exception {
            Map<String, MultiMCInstancePatch> existed = new LinkedHashMap<>();
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
                                    componentID, Lang.requireNonNullElse(require.getEqualsVersion(), require.getSuggests()), mcVersion
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

    /// {@inheritDoc}
    @Override
    public void execute() throws Exception {
        // Stage #3: Build Json-Patch artifact.
        @Nullable MultiMCInstancePatch.ResolvedInstance artifact = null;
        for (int i = dependents.size() - 1; i >= 0; i--) {
            Task<?> task = dependents.get(i);
            if (task instanceof MMCInstancePatchesAssembleTask) {
                artifact = MultiMCInstancePatch.resolveArtifact(((MMCInstancePatchesAssembleTask) task).getResult(), instanceId);
                break;
            }
        }
        Objects.requireNonNull(artifact, "artifact");

        // Stage #4: Copy embedded files.
        try (FileSystem fs = openModpack()) {
            Path root = getRootPath(fs);

            Path libraries = root.resolve("libraries");
            if (Files.exists(libraries))
                FileUtils.copyDirectory(libraries, repository.getLayout().getInstanceRoot(instanceId).resolve("libraries"));

            for (Library library : artifact.getManifest().getLibraries()) {
                if ("local".equals(library.hint())) {
                    /* TODO: Determine whether we should erase community fields, like 'hint' and 'filename' from version json.
                        Retain them will facilitate compatibility, as some embedded libraries may check where their JAR is.
                        Meanwhile, potential compatibility issue with other launcher which never supports these fields might occur.
                        Here, we make the file stored twice, to keep maximum compatibility. */
                    Path from = repository.getLayout().getLibraryFile(artifact.getManifest().id(), library);
                    Path target = repository.getLayout().getLibraryFile(artifact.getManifest().id(), library.withoutCommunityFields());
                    Files.createDirectories(target.getParent());
                    Files.copy(from, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            try (InputStream input = Objects.requireNonNull(
                    MultiMCModpackInstallTask.class.getResourceAsStream(
                            "/assets/game/HMCLMultiMCBootstrap-1.0.jar"),
                    "Bundled HMCLMultiMCBootstrap is missing.")) {
                Path libraryPath = repository.getLayout().getLibraryFile(artifact.getManifest().id(), MultiMCInstancePatch.BOOTSTRAP_LIBRARY);

                Files.createDirectories(libraryPath.getParent());
                Files.copy(input, libraryPath, StandardCopyOption.REPLACE_EXISTING);
            }

            @Nullable String iconKey = this.manifest.getIconKey();
            if (iconKey != null) {
                Path iconFile = root.resolve(iconKey + ".png");
                if (Files.exists(iconFile)) {
                    FileUtils.copyFile(iconFile, repository.getLayout().getInstanceRoot(instanceId).resolve("icon.png"));
                }
            }
        }

        // Stage #5: Assemble game files.
        {
            GameInstanceManifest instanceManifest = artifact.getManifest();
            requireDraft().put(instanceManifest);

            dependencies.add(new GameAssetDownloadTask(dependencyManager, instanceManifest, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true));
            dependencies.add(new GameLibrariesTask(
                    dependencyManager,
                    // TODO: check integrity of maven-only files when launching games?
                    instanceManifest.withLibraries(Lang.merge(instanceManifest.getLibraries(), artifact.getMavenOnlyFiles())),
                    true
            ));

            Path instanceJar = requireDraft().getBaseSnapshot().getLayout().getInstanceJarFile(instanceId);
            dependencies.add(new GameDownloadTask(dependencyManager, instanceManifest)
                    .thenAcceptAsync(cachedJar -> FileUtils.copyFile(cachedJar, instanceJar)));
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

    /// Applies JAR mods after downloads succeed and then publishes the completed manifest.
    @Override
    public void postExecute() throws Exception {
        MultiMCInstancePatch.ResolvedInstance artifact = Objects.requireNonNull(getResult(), "ResolvedInstance");

        List<String> files = artifact.getJarModFileNames();
        if (!isDependenciesSucceeded()) {
            return;
        }

        if (!files.isEmpty()) {
            // Stage #7: Apply jar mods.
            try (FileSystem fs = openModpack()) {
                Path root = getRootPath(fs).resolve("jarmods");

                try (FileSystem mc = CompressingUtils.writable(
                        repository.getLayout().getInstanceRoot(instanceId).resolve(instanceId + ".jar")
                ).setAutoDetectEncoding(true).build()) {
                    for (String fileName : files) {
                        try (FileSystem jm = CompressingUtils.readonly(root.resolve(fileName)).setAutoDetectEncoding(true).build()) {
                            FileUtils.copyDirectory(jm.getPath("/"), mc.getPath("/"));
                        }
                    }
                }
            }
        }

        requireDraft().commit();
    }

    /// Returns the open draft associated with this task.
    ///
    /// @return the open draft
    /// @throws IllegalStateException if the task has not reserved a draft or already released it
    private DefaultGameRepositoryDraft requireDraft() {
        @Nullable DefaultGameRepositoryDraft currentDraft = draft;
        if (currentDraft == null || !currentDraft.isOpen()) {
            throw new IllegalStateException("MultiMC installation draft is not open");
        }
        return currentDraft;
    }

    /// Aborts the task's draft when execution ends before commit.
    private void abortOpenDraft() {
        @Nullable DefaultGameRepositoryDraft currentDraft = draft;
        if (currentDraft == null || !currentDraft.isOpen()) {
            return;
        }
        try {
            currentDraft.abort();
        } catch (IOException e) {
            LOG.warning("Failed to abort MultiMC installation draft for " + instanceId, e);
        }
    }

    private FileSystem openModpack() throws IOException {
        return CompressingUtils.readonly(zipFile).setAutoDetectEncoding(true).setEncoding(modpack.getEncoding()).build();
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
