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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackManager;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public abstract class DefaultGameRepository implements GameRepository {

    private static final GameInstanceManifest CLASSIC_MANIFEST = new GameInstanceManifest(
            new GameInstanceID("Classic"),
            "${auth_player_name} ${auth_session} --workDir ${game_directory}",
            null,
            "net.minecraft.client.Minecraft",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                    classicLibrary("lwjgl"),
                    classicLibrary("jinput"),
                    classicLibrary("lwjgl_util")),
            null,
            null,
            null,
            ReleaseType.UNKNOWN,
            null,
            null,
            0,
            false,
            false,
            null,
            null
    );

    private static Library classicLibrary(String name) {
        return new Library(new Artifact("", "", ""), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo("bin/" + name + ".jar"), null),
                null, null, null, null, null, null);
    }

    private static boolean hasClassicVersion(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }

    private volatile Status status;
    private volatile boolean loaded;
    private final ConcurrentHashMap<Path, Optional<String>> gameVersions = new ConcurrentHashMap<>();

    public DefaultGameRepository(Path baseDirectory) {
        this.status = new Status(this, createLayout(baseDirectory));
    }

    /// Creates the repository layout rooted at the given directory.
    ///
    /// @param baseDirectory the repository base directory
    /// @return the layout used by this repository
    protected abstract DefaultGameRepositoryLayout createLayout(Path baseDirectory);

    public void setBaseDirectory(Path baseDirectory) {
        this.status = new Status(this, createLayout(baseDirectory));
        this.loaded = false;
        this.gameVersions.clear();
    }

    /// Returns the current repository status snapshot.
    ///
    /// @return the current status
    protected Status currentStatus() {
        return status;
    }

    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return status.layout;
    }

    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public void refresh() {
        if (EventBus.EVENT_BUS.fireEvent(new RefreshingInstancesEvent(this)) == Event.Result.DENY) {
            return;
        }

        refreshImpl();
        loaded = true;
        EventBus.EVENT_BUS.fireEvent(new RefreshedGameInstancesEvent(this));
    }

    protected void refreshImpl() {
        Status newStatus = new Status(this, status.layout);

        if (hasClassicVersion(newStatus.layout.getBaseDirectory())) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newStatus.instances.put(id, createInstance(newStatus, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newStatus.layout.getBaseDirectory().resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (Stream<Path> stream = Files.list(versionsDir)) {
                stream.parallel().filter(Files::isDirectory).flatMap(dir -> {
                    GameInstanceID id;
                    try {
                        id = new GameInstanceID(FileUtils.getName(dir));
                    } catch (IllegalArgumentException e) {
                        LOG.warning("Ignoring version folder with invalid id " + dir, e);
                        return Stream.empty();
                    }

                    Path json = dir.resolve(id + ".json");

                    if (Files.notExists(json)) {
                        List<Path> jsons = FileUtils.listFilesByExtension(dir, "json");
                        if (jsons.size() == 1) {
                            LOG.info("Renaming json file " + jsons.get(0) + " to " + json);

                            try {
                                Files.move(jsons.get(0), json);
                            } catch (IOException e) {
                                LOG.warning("Cannot rename json file, ignoring version " + id, e);
                                return Stream.empty();
                            }

                            Path jar = dir.resolve(FileUtils.getNameWithoutExtension(jsons.get(0)) + ".jar");
                            if (Files.exists(jar)) {
                                try {
                                    Files.move(jar, dir.resolve(id + ".jar"));
                                } catch (IOException e) {
                                    LOG.warning("Cannot rename jar file, ignoring version " + id, e);
                                    return Stream.empty();
                                }
                            }
                        } else {
                            LOG.info("No available json file found, ignoring version " + id);
                            return Stream.empty();
                        }
                    }

                    GameInstanceManifest manifest;
                    try {
                        manifest = readInstanceManifest(json);
                    } catch (Exception e) {
                        LOG.warning("Malformed version json " + id, e);
                        if (EventBus.EVENT_BUS.fireEvent(new GameJsonParseFailedEvent(this, json, id.id())) != Event.Result.ALLOW) {
                            return Stream.empty();
                        }

                        try {
                            manifest = readInstanceManifest(json);
                        } catch (Exception e2) {
                            LOG.error("User corrected version json is still malformed", e2);
                            return Stream.empty();
                        }
                    }

                    if (!id.equals(manifest.id())) {
                        try {
                            moveInstanceFiles(newStatus.layout.getBaseDirectory(), id, manifest.id());
                        } catch (IOException e) {
                            LOG.warning("Ignoring instance " + manifest.id()
                                    + " because instance id does not match folder name " + id
                                    + ", and we cannot correct it.", e);
                            return Stream.empty();
                        }
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newStatus.instances.put(
                        it.id(),
                        createInstance(newStatus, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }

        Map<GameInstanceID, DefaultGameInstance> loadedInstances = new TreeMap<>();
        for (DefaultGameInstance instance : newStatus.instances.values()) {
            try {
                GameInstanceManifest resolved = newStatus.resolve(instance.getManifest(), new HashSet<>()).launchManifest();
                if (CompatibilityRule.appliesToCurrentEnvironment(resolved.compatibilityRules())) {
                    loadedInstances.put(instance.getId(), instance);
                }
            } catch (NoSuchGameInstanceException e) {
                LOG.warning("Ignoring instance " + instance.getId() + " because it inherits from a nonexistent version.");
            }
        }

        newStatus.instances.clear();
        newStatus.instances.putAll(loadedInstances);
        gameVersions.clear();
        this.status = newStatus;
    }

    private static GameInstanceManifest readInstanceManifest(Path json) throws IOException, JsonParseException {
        GameInstanceManifest manifest = JsonUtils.fromJsonFile(json, GameInstanceManifest.class);
        if (manifest == null) {
            throw new JsonParseException("Manifest is null");
        }
        return manifest;
    }

    private static void moveInstanceFiles(Path baseDirectory, GameInstanceID from, GameInstanceID to) throws IOException {
        Path versionsDir = baseDirectory.resolve("versions");
        Path fromDir = versionsDir.resolve(from.id());
        Path toDir = versionsDir.resolve(to.id());
        Files.move(fromDir, toDir);

        Path fromJson = toDir.resolve(from + ".json");
        Path fromJar = toDir.resolve(from + ".jar");
        Path toJson = toDir.resolve(to + ".json");
        Path toJar = toDir.resolve(to + ".jar");

        boolean hasJarFile = Files.exists(fromJar);

        try {
            Files.move(fromJson, toJson);
            if (hasJarFile) {
                Files.move(fromJar, toJar);
            }
        } catch (IOException e) {
            Lang.ignoringException(() -> Files.move(toJson, fromJson));
            if (hasJarFile) {
                Lang.ignoringException(() -> Files.move(toJar, fromJar));
            }
            Lang.ignoringException(() -> Files.move(toDir, fromDir));
            throw e;
        }
    }

    @Override
    public boolean hasInstance(GameInstanceID instanceId) {
        DefaultGameInstance instance = status.instances.get(instanceId);
        return instance != null && !instance.isProvisional();
    }

    @Override
    public GameInstanceManifest getInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstance(instanceId).getManifest();
    }

    @Override
    public GameInstanceManifest.Resolved getResolvedInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstance(instanceId).getResolvedManifest();
    }

    @Override
    public int getInstanceCount() {
        int count = 0;
        for (DefaultGameInstance instance : status.instances.values()) {
            if (!instance.isProvisional()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Collection<GameInstanceManifest> getInstanceManifests() {
        return status.instances.values().stream()
                .filter(instance -> !instance.isProvisional())
                .map(instance -> instance.manifest)
                .toList();
    }

    @Override
    public DefaultGameInstance getInstance(GameInstanceID id) throws NoSuchGameInstanceException {
        @Nullable DefaultGameInstance instance = status.instances.get(id);
        if (instance != null && !instance.isProvisional()) {
            return instance;
        } else {
            throw new NoSuchGameInstanceException(id);
        }
    }

    /// Returns the instance recorded in the current status for the given id, including provisional
    /// placeholders.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when absent from the current status
    protected @Nullable DefaultGameInstance findStatusInstance(GameInstanceID id) {
        return status.instances.get(id);
    }

    public Path getArtifactFile(GameInstanceManifest manifest, Artifact artifact) {
        return artifact.getPath(getLayout().getLibrariesDirectory());
    }

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        return getBaseDirectory();
    }

    @Override
    public Path getInstanceJar(GameInstanceManifest manifest) {
        GameInstanceManifest resolved = this.resolve(manifest).launchManifest();
        GameInstanceID id = Optional.ofNullable(resolved.jar()).orElse(resolved.id());
        return getLayout().getInstanceJarFile(id);
    }

    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        if (EventBus.EVENT_BUS.fireEvent(new RenameInstanceEvent(this, from, to)) == Event.Result.DENY) {
            return false;
        }

        try {
            Status currentStatus = status;
            DefaultGameInstance fromHolder = currentStatus.instances.get(from);
            if (fromHolder == null) {
                throw new NoSuchGameInstanceException(from);
            }

            moveInstanceFiles(currentStatus.layout.getBaseDirectory(), from, to);

            GameInstanceManifest renamedManifest = fromHolder.manifest;
            if (from.equals(renamedManifest.jar())) {
                renamedManifest = renamedManifest.withJar(null);
            }
            renamedManifest = renamedManifest.withId(to);
            JsonUtils.writeToJsonFile(getInstanceJson(to), renamedManifest);

            Map<GameInstanceID, DefaultGameInstance> updatedInstances = new TreeMap<>(currentStatus.instances);
            updatedInstances.remove(from);
            updatedInstances.put(to, createInstance(currentStatus, to, renamedManifest));

            for (DefaultGameInstance instance : currentStatus.instances.values()) {
                GameInstanceManifest manifest = instance.manifest;
                if (from.equals(manifest.inheritsFrom())) {
                    GameInstanceManifest updatedManifest = manifest.withInheritsFrom(to);
                    Path targetPath = getInstanceJson(updatedManifest.id());
                    Files.createDirectories(targetPath.getParent());
                    JsonUtils.writeToJsonFile(targetPath, updatedManifest);
                    updatedInstances.put(updatedManifest.id(), createInstance(currentStatus, updatedManifest.id(), updatedManifest));
                }
            }

            currentStatus.instances.clear();
            currentStatus.instances.putAll(updatedInstances);
            gameVersions.clear();
            return true;
        } catch (IOException | JsonParseException | NoSuchGameInstanceException | InvalidPathException e) {
            LOG.warning("Unable to rename version " + from + " to " + to, e);
            return false;
        }
    }

    public boolean removeInstanceFromDisk(GameInstanceID id) {
        if (EventBus.EVENT_BUS.fireEvent(new RemoveInstanceEvent(this, id)) == Event.Result.DENY) {
            return false;
        }

        Status currentStatus = status;
        currentStatus.instances.remove(id);

        Path file = getLayout().getInstanceRoot(id);
        if (Files.notExists(file)) {
            return true;
        }

        Path removedFile = file.toAbsolutePath().resolveSibling(FileUtils.getName(file) + "_removed");
        try {
            Files.move(file, removedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Unable to remove version folder: " + file, e);
            return false;
        }

        try {
            if (FileUtils.moveToTrash(removedFile)) {
                return true;
            }

            for (Path path : FileUtils.listFilesByExtension(removedFile, "json")) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOG.warning("Failed to delete file " + path, e);
                }
            }

            try {
                FileUtils.deleteDirectory(removedFile);
            } catch (IOException e) {
                LOG.warning("Unable to remove version folder: " + file, e);
            }
            return true;
        } finally {
            refreshAsync().start();
        }
    }

    @Override
    public Optional<String> getGameVersion(GameInstanceManifest manifest) {
        try {
            GameInstanceManifest resolved = resolve(manifest).launchManifest();
            Path instanceJar = getInstanceJar(resolved);
            return gameVersions.computeIfAbsent(instanceJar, jar -> {
                Optional<String> gameVersion = GameVersion.minecraftVersion(jar);
                if (gameVersion.isEmpty()) {
                    LOG.warning("Cannot find out game version of " + manifest.id()
                            + ", primary jar: " + jar
                            + ", jar exists: " + Files.exists(jar));
                }
                return gameVersion;
            });
        } catch (NoSuchGameInstanceException e) {
            return Optional.empty();
        }
    }

    /// Returns the official version manifest file for an instance.
    ///
    /// @param instanceId the instance id
    /// @return the path `versions/<id>/<id>.json` below the base directory
    public Path getInstanceJson(GameInstanceID instanceId) {
        return getLayout().getInstanceJson(instanceId);
    }

    @Override
    public AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException {
        try {
            return Objects.requireNonNull(JsonUtils.fromJsonFile(getLayout().getAssetIndexFile(assetId), AssetIndex.class));
        } catch (JsonParseException | NullPointerException e) {
            throw new IOException("Asset index file malformed", e);
        }
    }

    @Override
    public Path getActualAssetDirectory(GameInstanceID instanceId, String assetId) {
        try {
            return reconstructAssets(instanceId, assetId);
        } catch (IOException | JsonParseException e) {
            LOG.error("Unable to reconstruct asset directory", e);
            return getLayout().getAssetDirectory();
        }
    }

    @Override
    public Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException {
        try {
            AssetObject assetObject = getAssetIndex(instanceId, assetId).getObjects().get(name);
            if (assetObject == null) return Optional.empty();
            return Optional.of(getLayout().getAssetObject(assetObject));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unrecognized asset object " + name + " in asset " + assetId + " of version " + instanceId, e);
        }
    }

    public Path getAssetObject(GameInstanceID instanceId, Path assetDir, AssetObject obj) {
        return assetDir.resolve("objects").resolve(obj.getLocation());
    }

    protected Path reconstructAssets(GameInstanceID instanceId, String assetId) throws IOException, JsonParseException {
        Path assetsDir = getLayout().getAssetDirectory();
        Path indexFile = getLayout().getAssetIndexFile(assetId);
        Path virtualRoot = assetsDir.resolve("virtual").resolve(assetId);

        if (!Files.isRegularFile(indexFile))
            return assetsDir;

        AssetIndex index = JsonUtils.fromJsonFile(indexFile, AssetIndex.class);

        if (index == null)
            return assetsDir;

        if (index.isVirtual()) {
            Path resourcesDir = getRunDirectory(instanceId).resolve("resources");

            int cnt = 0;
            int tot = index.getObjects().size();
            for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
                Path target = virtualRoot.resolve(entry.getKey());
                Path original = getAssetObject(instanceId, assetsDir, entry.getValue());
                if (Files.exists(original)) {
                    cnt++;
                    if (!Files.isRegularFile(target))
                        FileUtils.copyFile(original, target);

                    if (index.needMapToResources()) {
                        target = resourcesDir.resolve(entry.getKey());
                        if (!Files.isRegularFile(target))
                            FileUtils.copyFile(original, target);
                    }
                }
            }

            // If the scale new format existent file is lower than 0.1, use the old format.
            if (cnt * 10 < tot)
                return assetsDir;
            else
                return virtualRoot;
        }

        return assetsDir;
    }

    public Task<GameInstanceManifest> saveAsync(GameInstanceManifest instanceManifest) {
        return Task.supplyAsync(() -> {
            GameInstanceManifest savedManifest = instanceManifest.isResolvedPreservingPatches()
                    ? MaintainTask.maintainPreservingPatches(this, instanceManifest)
                    : instanceManifest;

            Path json = getInstanceJson(savedManifest.id()).toAbsolutePath();
            Files.createDirectories(json.getParent());
            JsonUtils.writeToJsonFile(json, savedManifest);

            Status newStatus = status.clone();
            DefaultGameInstance existing = newStatus.instances.get(savedManifest.id());
            if (existing != null) {
                newStatus.instances.put(savedManifest.id(), existing.withManifest(newStatus, savedManifest));
            } else {
                newStatus.instances.put(savedManifest.id(), createInstance(newStatus, savedManifest.id(), savedManifest));
            }
            status = newStatus;

            gameVersions.clear();
            return savedManifest;
        });
    }

    public Path getModpackConfiguration(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve("modpack.json");
    }

    @Nullable
    public ModpackConfiguration<?> readModpackConfiguration(GameInstanceID instanceId) throws IOException, NoSuchGameInstanceException {
        if (!hasInstance(instanceId)) throw new NoSuchGameInstanceException(instanceId);
        Path file = getModpackConfiguration(instanceId);
        if (Files.notExists(file)) return null;
        return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
    }

    public boolean isModpack(GameInstanceID instanceId) {
        return Files.exists(getModpackConfiguration(instanceId));
    }

    public ModManager getModManager(GameInstanceID instanceId) {
        return new ModManager(this, instanceId);
    }

    public ResourcePackManager getResourcePackManager(GameInstanceID instanceId) {
        return new ResourcePackManager(this, instanceId);
    }

    @Override
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
        return status.resolve(manifest, new HashSet<>());
    }

    protected abstract DefaultGameInstance createInstance(Status status, GameInstanceID id, GameInstanceManifest manifest);

    protected static class Status {
        public final DefaultGameRepository repository;
        public final DefaultGameRepositoryLayout layout;
        public final Map<GameInstanceID, DefaultGameInstance> instances = new TreeMap<>();

        protected Status(DefaultGameRepository repository, DefaultGameRepositoryLayout layout) {
            this.repository = repository;
            this.layout = layout;
        }

        public Status clone() {
            Status newStatus = new Status(repository, layout);
            for (DefaultGameInstance instance : instances.values()) {
                newStatus.instances.put(instance.getId(), instance.withNewStatus(newStatus));
            }
            return newStatus;
        }

        GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest,
                                              Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
            GameInstanceManifest launchManifest;
            GameInstanceManifest standaloneManifest = manifest.isRoot()
                    ? manifest
                    : addPatches(
                    addPatches(new GameInstanceManifest(manifest.id()), List.of(manifest.toPatch())),
                    manifest.patches());

            if (manifest.inheritsFrom() == null) {
                if (manifest.isRoot()) {
                    // TODO: Breaking change, require much testing on versions installed with external installer, other launchers, and all kinds of versions.
                    launchManifest = manifest.patches() != null ? new GameInstanceManifest(manifest.id()).withPatches(manifest.patches()) : manifest;
                } else {
                    launchManifest = manifest;
                }
                launchManifest = launchManifest.withJar(manifest.jar() == null ? manifest.id() : manifest.jar());
            } else {
                // To maximize the compatibility.
                if (!resolvedSoFar.add(manifest.id())) {
                    LOG.warning("Found circular dependency versions: " + resolvedSoFar);
                    launchManifest = (manifest.jar() == null ? manifest.withJar(manifest.id()) : manifest)
                            .withInheritsFrom(null);
                } else {
                    DefaultGameInstance parentInstance = instances.get(manifest.inheritsFrom());
                    if (parentInstance == null) {
                        throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                    }

                    // It is supposed to auto-install a version in getVersion.
                    GameInstanceManifest.Resolved parentResolved = resolve(parentInstance.getManifest(), resolvedSoFar);
                    launchManifest = manifest.merge(parentResolved.launchManifest());
                    standaloneManifest = addPatches(
                            addPatches(parentResolved.standaloneManifest(), Collections.singleton(manifest.toPatch())),
                            manifest.patches());
                }
            }

            if (manifest.patches() != null && !manifest.patches().isEmpty()) {
                // Assume patches themselves do not have patches recursively.
                List<GameInstancePatch> sortedPatches = manifest.patches().stream()
                        .sorted(Comparator.comparing(GameInstancePatch::getPriority))
                        .toList();
                for (GameInstancePatch patch : sortedPatches) {
                    launchManifest = patch.merge(launchManifest);
                }
            }

            launchManifest = launchManifest.withId(manifest.id()).withPatches(null);
            standaloneManifest = standaloneManifest.withId(manifest.id());
            if (launchManifest.jar() != null) {
                standaloneManifest = standaloneManifest.withJar(launchManifest.jar());
            }

            return new GameInstanceManifest.Resolved(manifest, launchManifest, standaloneManifest);
        }

        private static GameInstanceManifest addPatches(GameInstanceManifest manifest, @Nullable Collection<GameInstancePatch> additional) {
            if (additional == null || additional.isEmpty()) {
                return manifest;
            }

            Set<String> patchIds = new HashSet<>();
            for (GameInstancePatch patch : additional) {
                if (patch.id() != null) {
                    patchIds.add(patch.id());
                }
            }

            List<GameInstancePatch> patches = new ArrayList<>();
            if (manifest.patches() != null) {
                for (GameInstancePatch patch : manifest.patches()) {
                    if (patch.id() == null || !patchIds.contains(patch.id())) {
                        patches.add(patch);
                    }
                }
            }
            patches.addAll(additional);
            return manifest.withPatches(patches);
        }

    }

}
