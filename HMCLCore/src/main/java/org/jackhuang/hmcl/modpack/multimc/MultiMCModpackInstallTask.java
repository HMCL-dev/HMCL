// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.multimc;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.modpack.MinecraftInstanceTask;
import org.jackhuang.hmcl.modpack.Modpack;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackInstallTask;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

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
    private final DefaultDependencyManager dependencyManager;

    /// Previous modpack configuration when updating, or `null` for a new installation.
    private final @Nullable ModpackConfiguration<MultiMCInstanceConfiguration> config;

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
    }

    /// Extracts the pack files and returns their configuration for the repository commit.
    private Task<ModpackConfiguration<MultiMCInstanceConfiguration>> prepareOverrides() throws IOException {
        Path run = repository.getLayout().getInstanceRoot(instanceId);
        String mcDirectory;
        try (FileSystem fs = openModpack()) {
            mcDirectory = getRootPath(fs).resolve(".minecraft").toAbsolutePath().normalize().toString();
        }
        @Unmodifiable List<String> directories = List.of(mcDirectory);
        return new ModpackInstallTask<>(zipFile, run, directories, any -> true, config)
                .thenCompose(new MinecraftInstanceTask<>(zipFile, directories, manifest, MultiMCModpackProvider.INSTANCE,
                        manifest.getName(), null)).withStage(InstallStages.MODPACK);
    }

    /// Resolves the instance patches from the archive and MultiMC's metadata service.
    private MMCInstancePatchesAssembleTask preparePatches() throws Exception {
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
            return new MMCInstancePatchesAssembleTask(patches, mcVersion);
        }
    }

    private static final class MMCInstancePatchesAssembleTask extends Task<List<MultiMCInstancePatch>> {
        private final List<Task<MultiMCInstancePatch>> patches;
        private final String mcVersion;

        public MMCInstancePatchesAssembleTask(List<Task<MultiMCInstancePatch>> patches, String mcVersion) {
            this.patches = patches;
            this.mcVersion = mcVersion;
        }

        /// Resolves the initial patches and any transitive requirements discovered in them.
        @Override
        public List<MultiMCInstancePatch> execute(TaskExecutionContext context) throws Exception {
            Map<String, MultiMCInstancePatch> existed = new LinkedHashMap<>();
            for (MultiMCInstancePatch patch : context.runAll(patches, FetchTask.getDownloadExecutorConcurrency())) {
                existed.put(patch.getID(), patch);
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
                            MultiMCInstancePatch result = context.run(task);
                            existed.put(result.getID(), result);
                            continue checking;
                        }
                    }
                }

                break;
            }

            return new ArrayList<>(existed.values());
        }
    }

    /// Reserves the target before writing files and commits the manifest and completed client JAR together.
    /// Closing an uncommitted draft removes files under a newly reserved instance root.
    @Override
    public MultiMCInstancePatch.ResolvedInstance execute(TaskExecutionContext context) throws Exception {
        try (DefaultGameRepositoryDraft draft = repository.openDraft()) {
            @Nullable DefaultGameInstance current = draft.getBaseSnapshot().findInstance(instanceId);
            if (updateTarget == null && current != null)
                throw new IllegalStateException("Game instance already exists: " + instanceId);
            if (updateTarget != null && current == null)
                throw new IllegalStateException("Game instance no longer exists: " + instanceId);
            if (current != updateTarget)
                throw new IllegalStateException("Game instance has changed: " + instanceId);

            context.checkCancellation();
            draft.put(new GameInstanceManifest(instanceId));
            MultiMCInstancePatch.ResolvedInstance artifact = prepareInstance(context, draft);
            Path clientJar = downloadGame(context, artifact);
            context.checkCancellation();
            List<String> jarMods = artifact.getJarModFileNames();
            @Nullable Path patchedJar = jarMods.isEmpty() ? null : Files.createTempFile("hmcl-multimc-", ".jar");
            try {
                if (patchedJar != null) {
                    Files.copy(clientJar, patchedJar, StandardCopyOption.REPLACE_EXISTING);
                    applyJarMods(context, patchedJar, jarMods);
                }
                draft.put(artifact.getManifest());
                draft.putPrimaryJar(instanceId, patchedJar != null ? patchedJar : clientJar);
                context.commit();
                draft.commit();
                return artifact;
            } finally {
                if (patchedJar != null) {
                    try {
                        Files.deleteIfExists(patchedJar);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete temporary MultiMC client JAR " + patchedJar, e);
                    }
                }
            }
        }
    }

    /// Resolves the pack's patches and installs its files under the reserved instance root.
    private MultiMCInstancePatch.ResolvedInstance prepareInstance(TaskExecutionContext context, DefaultGameRepositoryDraft draft) throws Exception {
        var overrides = prepareOverrides();
        MMCInstancePatchesAssembleTask patches = preparePatches();
        context.runAll(overrides, patches);
        draft.putModpackConfiguration(instanceId, overrides.resultNow());
        MultiMCInstancePatch.ResolvedInstance artifact = MultiMCInstancePatch.resolveArtifact(patches.resultNow(), instanceId);

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

        return artifact;
    }

    /// Downloads the game's dependencies and returns the unmodified cached client JAR.
    private Path downloadGame(TaskExecutionContext context, MultiMCInstancePatch.ResolvedInstance artifact) throws Exception {
        GameInstanceManifest instanceManifest = artifact.getManifest();
        Task<Path> client = new GameDownloadTask(dependencyManager, instanceManifest).withStage(InstallStages.MODPACK_DOWNLOAD);
        context.runAll(client,
                new GameAssetDownloadTask(dependencyManager, instanceManifest, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true),
                new GameLibrariesTask(dependencyManager,
                        instanceManifest.withLibraries(Lang.merge(instanceManifest.getLibraries(), artifact.getMavenOnlyFiles())), true));
        return client.resultNow();
    }

    /// Applies the pack's JAR mods in order to a private copy of the client.
    private void applyJarMods(TaskExecutionContext context, Path clientJar, List<String> jarMods) throws IOException {
        try (FileSystem fs = openModpack(); FileSystem mc = CompressingUtils.writable(clientJar).build()) {
            Path root = getRootPath(fs).resolve("jarmods");
            for (String fileName : jarMods) {
                context.checkCancellation();
                try (FileSystem jm = CompressingUtils.readonly(root.resolve(fileName)).build()) {
                    FileUtils.copyDirectory(jm.getPath("/"), mc.getPath("/"));
                }
            }
        }
    }

    private FileSystem openModpack() throws IOException {
        return CompressingUtils.readonly(zipFile).build();
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
