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
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
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

    private volatile DefaultGameRepositoryStatus status;
    private volatile boolean loaded;

    public DefaultGameRepository(Path baseDirectory) {
        DefaultGameRepositoryStatus initial = createStatus(createLayout(baseDirectory));
        initial.seal();
        this.status = initial;
    }

    /// Creates the repository layout rooted at the given directory.
    ///
    /// @param baseDirectory the repository base directory
    /// @return the layout used by this repository
    protected abstract DefaultGameRepositoryLayout createLayout(Path baseDirectory);

    public void setBaseDirectory(Path baseDirectory) {
        DefaultGameRepositoryStatus initial = createStatus(createLayout(baseDirectory));
        publishStatus(initial);
        this.loaded = false;
    }

    /// Returns the current published repository status snapshot.
    ///
    /// The returned status is sealed and must not be modified. Writers must [#clone()] it, edit the
    /// copy, and publish the result with [#publishStatus(DefaultGameRepositoryStatus)].
    ///
    /// @return the current status
    protected DefaultGameRepositoryStatus currentStatus() {
        return status;
    }

    /// {@inheritDoc}
    @Override
    public GameRepositorySnapshot getSnapshot() {
        return status;
    }

    /// Seals `newStatus` if needed and publishes it as the current repository snapshot.
    ///
    /// @param newStatus the status to publish; must not already be visible as [#currentStatus()]
    ///                  unless it is a freshly built replacement
    protected void publishStatus(DefaultGameRepositoryStatus newStatus) {
        newStatus.seal();
        this.status = newStatus;
    }

    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return status.getLayout();
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
        DefaultGameRepositoryStatus newStatus = createStatus(status.getLayout());

        if (hasClassicVersion(newStatus.getLayout().getBaseDirectory())) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newStatus.put(createInstance(newStatus, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newStatus.getLayout().getBaseDirectory().resolve("versions");
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
                            moveInstanceFiles(newStatus.getLayout().getBaseDirectory(), id, manifest.id());
                        } catch (IOException e) {
                            LOG.warning("Ignoring instance " + manifest.id()
                                    + " because instance id does not match folder name " + id
                                    + ", and we cannot correct it.", e);
                            return Stream.empty();
                        }
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newStatus.put(createInstance(newStatus, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }

        Map<GameInstanceID, DefaultGameInstance> loadedInstances = new TreeMap<>();
        for (DefaultGameInstance instance : newStatus.values()) {
            try {
                GameInstanceManifest resolved = newStatus.resolve(instance.getManifest()).launchManifest();
                if (CompatibilityRule.appliesToCurrentEnvironment(resolved.compatibilityRules())) {
                    loadedInstances.put(instance.getId(), instance);
                }
            } catch (NoSuchGameInstanceException e) {
                LOG.warning("Ignoring instance " + instance.getId() + " because it inherits from a nonexistent version.");
            }
        }

        newStatus.clear();
        newStatus.putAll(loadedInstances);
        publishStatus(newStatus);
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
    public DefaultGameInstance getInstance(GameInstanceID id) throws NoSuchGameInstanceException {
        return status.getRegistered(id);
    }

    /// Returns the instance recorded in the current status for the given id, including provisional
    /// placeholders.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when absent from the current status
    protected @Nullable DefaultGameInstance findStatusInstance(GameInstanceID id) {
        return status.get(id);
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
            DefaultGameRepositoryStatus newStatus = status.clone();
            DefaultGameInstance fromHolder = newStatus.get(from);
            if (fromHolder == null || fromHolder.isProvisional()) {
                throw new NoSuchGameInstanceException(from);
            }

            moveInstanceFiles(newStatus.getLayout().getBaseDirectory(), from, to);

            GameInstanceManifest renamedManifest = fromHolder.manifest;
            if (from.equals(renamedManifest.jar())) {
                renamedManifest = renamedManifest.withJar(null);
            }
            renamedManifest = renamedManifest.withId(to);
            JsonUtils.writeToJsonFile(getInstanceJson(to), renamedManifest);

            newStatus.remove(from);
            newStatus.put(fromHolder.withManifest(newStatus, renamedManifest));

            for (DefaultGameInstance instance : List.copyOf(newStatus.values())) {
                GameInstanceManifest manifest = instance.manifest;
                if (from.equals(manifest.inheritsFrom())) {
                    GameInstanceManifest updatedManifest = manifest.withInheritsFrom(to);
                    Path targetPath = getInstanceJson(updatedManifest.id());
                    Files.createDirectories(targetPath.getParent());
                    JsonUtils.writeToJsonFile(targetPath, updatedManifest);
                    newStatus.put(instance.withManifest(newStatus, updatedManifest));
                }
            }

            publishStatus(newStatus);
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

        if (status.get(id) != null) {
            DefaultGameRepositoryStatus newStatus = status.clone();
            newStatus.remove(id);
            publishStatus(newStatus);
        }

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
    public Optional<String> getGameVersion(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        GameVersionNumber version = getInstance(instanceId).getVersion();
        if (version == GameVersionNumber.unknown()) {
            return Optional.empty();
        }
        return Optional.of(version.toString());
    }

    @Override
    public Optional<String> getGameVersion(GameInstanceManifest manifest) {
        DefaultGameInstance instance = findStatusInstance(manifest.id());
        if (instance != null && !instance.isProvisional()) {
            GameVersionNumber version = instance.getVersion();
            if (version == GameVersionNumber.unknown()) {
                return Optional.empty();
            }
            return Optional.of(version.toString());
        }

        try {
            GameInstanceManifest resolved = resolve(manifest).launchManifest();
            Path instanceJar = getInstanceJar(resolved);
            Optional<String> gameVersion = GameVersion.minecraftVersion(instanceJar);
            if (gameVersion.isEmpty()) {
                LOG.warning("Cannot find out game version of " + manifest.id()
                        + ", primary jar: " + instanceJar
                        + ", jar exists: " + Files.exists(instanceJar));
            }
            return gameVersion;
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

            DefaultGameRepositoryStatus newStatus = status.clone();
            DefaultGameInstance existing = newStatus.get(savedManifest.id());
            if (existing != null) {
                newStatus.put(existing.withManifest(newStatus, savedManifest));
            } else {
                newStatus.put(createInstance(newStatus, savedManifest.id(), savedManifest));
            }
            publishStatus(newStatus);
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
        return status.resolve(manifest);
    }

    /// Creates an empty unsealed status for the given layout.
    ///
    /// @param layout the layout for the new status
    /// @return a new unsealed status
    protected DefaultGameRepositoryStatus createStatus(DefaultGameRepositoryLayout layout) {
        return new DefaultGameRepositoryStatus(this, layout);
    }

    protected abstract DefaultGameInstance createInstance(DefaultGameRepositoryStatus status, GameInstanceID id, GameInstanceManifest manifest);


}
