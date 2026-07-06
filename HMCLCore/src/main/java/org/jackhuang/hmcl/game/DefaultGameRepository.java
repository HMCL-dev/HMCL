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
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
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
public class DefaultGameRepository implements GameRepository {

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
        this.status = new Status(baseDirectory);
    }

    public Path getBaseDirectory() {
        return status.baseDirectory;
    }

    public void setBaseDirectory(Path baseDirectory) {
        this.status = new Status(baseDirectory);
        this.loaded = false;
        this.gameVersions.clear();
    }

    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public void refresh() {
        if (EventBus.EVENT_BUS.fireEvent(new RefreshingVersionsEvent(this)) == Event.Result.DENY) {
            return;
        }

        refreshImpl();
        loaded = true;
        EventBus.EVENT_BUS.fireEvent(new RefreshedVersionsEvent(this));
    }

    protected void refreshImpl() {
        Status newStatus = new Status(status.baseDirectory);

        if (hasClassicVersion(newStatus.baseDirectory)) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newStatus.instances.put(id, new InstanceHolder(newStatus, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newStatus.baseDirectory.resolve("versions");
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
                            moveInstanceFiles(newStatus.baseDirectory, id, manifest.id());
                        } catch (IOException e) {
                            LOG.warning("Ignoring version " + manifest.id()
                                    + " because version id does not match folder name " + id
                                    + ", and we cannot correct it.", e);
                            return Stream.empty();
                        }
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newStatus.instances.put(
                        it.id(),
                        new InstanceHolder(newStatus, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }

        Map<GameInstanceID, InstanceHolder> loadedInstances = new TreeMap<>();
        for (InstanceHolder holder : newStatus.instances.values()) {
            try {
                GameInstanceManifest resolved = newStatus.resolve(holder.manifest, new HashSet<>()).launchManifest();
                if (CompatibilityRule.appliesToCurrentEnvironment(resolved.compatibilityRules())) {
                    loadedInstances.put(holder.id, holder);
                }
            } catch (NoSuchGameInstanceException e) {
                LOG.warning("Ignoring version " + holder.id + " because it inherits from a nonexistent version.");
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
        return status.instances.containsKey(instanceId);
    }

    @Override
    public GameInstanceManifest getInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        InstanceHolder instanceHolder = status.instances.get(instanceId);
        if (instanceHolder == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }
        return instanceHolder.manifest;
    }

    @Override
    public GameInstanceManifest.Resolved getResolvedInstanceManifest(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        InstanceHolder instanceHolder = status.instances.get(instanceId);
        if (instanceHolder == null) {
            throw new NoSuchGameInstanceException(instanceId);
        }

        GameInstanceManifest.Resolved resolvedManifest = instanceHolder.resolvedManifest;
        if (resolvedManifest == null) {
            resolvedManifest = resolve(instanceHolder.manifest);
            instanceHolder.resolvedManifest = resolvedManifest;
        }
        return resolvedManifest;
    }

    @Override
    public int getInstanceCount() {
        return status.instances.size();
    }

    @Override
    public Path getInstanceRoot(GameInstanceID instanceId) {
        return getBaseDirectory().resolve("versions").resolve(instanceId.id());
    }

    @Override
    public Collection<GameInstanceManifest> getInstanceManifests() {
        return status.instances.values().stream().map(i -> i.manifest).toList();
    }

    @Override
    public Path getLibrariesDirectory(GameInstanceManifest manifest) {
        return getBaseDirectory().resolve("libraries");
    }

    @Override
    public Path getLibraryFile(GameInstanceManifest manifest, Library lib) {
        if ("local".equals(lib.getHint())) {
            if (lib.getFileName() != null) {
                return getInstanceRoot(manifest.id()).resolve("libraries/" + lib.getFileName());
            }

            return getInstanceRoot(manifest.id()).resolve("libraries/" + lib.getArtifact().getFileName());
        }

        return getLibrariesDirectory(manifest).resolve(lib.getPath());
    }

    public Path getArtifactFile(GameInstanceManifest manifest, Artifact artifact) {
        return artifact.getPath(getBaseDirectory().resolve("libraries"));
    }

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        return getBaseDirectory();
    }

    @Override
    public Path getInstanceJar(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getInstanceJar(getResolvedInstanceManifest(instanceId).launchManifest());
    }

    @Override
    public Path getInstanceJar(GameInstanceManifest manifest) {
        GameInstanceID jarID = Objects.requireNonNullElse(manifest.jar(), manifest.id());
        return getInstanceRoot(jarID).resolve(jarID + ".jar");
    }

    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        if (EventBus.EVENT_BUS.fireEvent(new RenameVersionEvent(this, from.id(), to.id())) == Event.Result.DENY) {
            return false;
        }

        try {
            Status currentStatus = status;
            InstanceHolder fromHolder = currentStatus.instances.get(from);
            if (fromHolder == null) {
                throw new NoSuchGameInstanceException(from);
            }

            moveInstanceFiles(currentStatus.baseDirectory, from, to);

            GameInstanceManifest renamedManifest = fromHolder.manifest;
            if (from.equals(renamedManifest.jar())) {
                renamedManifest = renamedManifest.withJar(null);
            }
            renamedManifest = renamedManifest.withId(to);
            JsonUtils.writeToJsonFile(getInstanceJson(to), renamedManifest);

            Map<GameInstanceID, InstanceHolder> updatedInstances = new TreeMap<>(currentStatus.instances);
            updatedInstances.remove(from);
            updatedInstances.put(to, new InstanceHolder(currentStatus, to, renamedManifest));

            for (InstanceHolder holder : currentStatus.instances.values()) {
                GameInstanceManifest manifest = holder.manifest;
                if (from.equals(manifest.inheritsFrom())) {
                    GameInstanceManifest updatedManifest = manifest.withInheritsFrom(to);
                    Path targetPath = getInstanceJson(updatedManifest.id());
                    Files.createDirectories(targetPath.getParent());
                    JsonUtils.writeToJsonFile(targetPath, updatedManifest);
                    updatedInstances.put(updatedManifest.id(), new InstanceHolder(currentStatus, updatedManifest.id(), updatedManifest));
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
        if (EventBus.EVENT_BUS.fireEvent(new RemoveVersionEvent(this, id.id())) == Event.Result.DENY) {
            return false;
        }

        Status currentStatus = status;
        currentStatus.instances.remove(id);

        Path file = getInstanceRoot(id);
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

    @Override
    public Path getNativeDirectory(GameInstanceID instanceId, Platform platform) {
        return getInstanceRoot(instanceId).resolve("natives-" + platform);
    }

    @Override
    public Path getModsDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("mods");
    }

    @Override
    public Path getResourcePackDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("resourcepacks");
    }

    public Path getInstanceJson(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(instanceId.id() + ".json");
    }

    @Override
    public AssetIndex getAssetIndex(GameInstanceID instanceId, String assetId) throws IOException {
        try {
            return Objects.requireNonNull(JsonUtils.fromJsonFile(getIndexFile(instanceId, assetId), AssetIndex.class));
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
            return getAssetDirectory(instanceId, assetId);
        }
    }

    @Override
    public Path getAssetDirectory(GameInstanceID instanceId, String assetId) {
        return getBaseDirectory().resolve("assets");
    }

    @Override
    public Optional<Path> getAssetObject(GameInstanceID instanceId, String assetId, String name) throws IOException {
        try {
            AssetObject assetObject = getAssetIndex(instanceId, assetId).getObjects().get(name);
            if (assetObject == null) return Optional.empty();
            return Optional.of(getAssetObject(instanceId, assetId, assetObject));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unrecognized asset object " + name + " in asset " + assetId + " of version " + instanceId, e);
        }
    }

    @Override
    public Path getAssetObject(GameInstanceID instanceId, String assetId, AssetObject obj) {
        return getAssetObject(instanceId, getAssetDirectory(instanceId, assetId), obj);
    }

    public Path getAssetObject(GameInstanceID instanceId, Path assetDir, AssetObject obj) {
        return assetDir.resolve("objects").resolve(obj.getLocation());
    }

    @Override
    public Path getIndexFile(GameInstanceID instanceId, String assetId) {
        return getAssetDirectory(instanceId, assetId).resolve("indexes").resolve(assetId + ".json");
    }

    @Override
    public Path getLoggingObject(GameInstanceID instanceId, String assetId, LoggingInfo loggingInfo) {
        return getAssetDirectory(instanceId, assetId).resolve("log_configs").resolve(loggingInfo.file().getId());
    }

    protected Path reconstructAssets(GameInstanceID instanceId, String assetId) throws IOException, JsonParseException {
        Path assetsDir = getAssetDirectory(instanceId, assetId);
        Path indexFile = getIndexFile(instanceId, assetId);
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
            Path json = getInstanceJson(instanceManifest.id()).toAbsolutePath();
            Files.createDirectories(json.getParent());
            JsonUtils.writeToJsonFile(json, instanceManifest);

            Status currentStatus = status;
            currentStatus.instances.put(instanceManifest.id(), new InstanceHolder(currentStatus, instanceManifest.id(), instanceManifest));
            gameVersions.clear();
            return instanceManifest;
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

    public Path getSavesDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("saves");
    }

    public Path getBackupsDirectory(GameInstanceID instanceID) {
        return getRunDirectory(instanceID).resolve("backups");
    }

    public Path getSchematicsDirectory(GameInstanceID instanceId) {
        return getRunDirectory(instanceId).resolve("schematics");
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

    protected static class Status {
        private final Path baseDirectory;
        private final Map<GameInstanceID, InstanceHolder> instances = new TreeMap<>();

        protected Status(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        private List<GameInstanceManifest> getInheritanceChain(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
            if (manifest.inheritsFrom() == null)
                return List.of(manifest);

            List<GameInstanceManifest> chain = new ArrayList<>(2);

            for (GameInstanceManifest currentManifest = manifest; ; ) {
                chain.add(currentManifest);
                if (currentManifest.inheritsFrom() == null) {
                    break;
                } else {
                    InstanceHolder parent = instances.get(currentManifest.inheritsFrom());
                    if (parent == null) {
                        throw new NoSuchGameInstanceException(currentManifest.inheritsFrom());
                    }
                    currentManifest = parent.manifest;
                }
            }

            return chain;
        }

        private GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest,
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
                    launchManifest = manifest.jar() == null ? manifest.withJar(manifest.id()) : manifest;
                } else {
                    InstanceHolder parentInstance = instances.get(manifest.inheritsFrom());
                    if (parentInstance == null) {
                        throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                    }

                    // It is supposed to auto-install a version in getVersion.
                    GameInstanceManifest.Resolved parentResolved = resolve(parentInstance.manifest, resolvedSoFar);
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

            return new GameInstanceManifest.Resolved(launchManifest, standaloneManifest);
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

    protected static class InstanceHolder {
        protected final Status status;
        protected final GameInstanceID id;
        protected final GameInstanceManifest manifest;
        protected @Nullable GameInstanceManifest.Resolved resolvedManifest;
        protected @Nullable GameVersionNumber version;

        protected InstanceHolder(Status status, GameInstanceID id, GameInstanceManifest manifest) {
            this.status = status;
            this.id = id;
            this.manifest = manifest;
        }
    }
}
