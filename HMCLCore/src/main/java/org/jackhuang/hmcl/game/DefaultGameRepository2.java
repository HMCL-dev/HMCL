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
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public class DefaultGameRepository2 implements GameRepository2 {

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
            null,
            null,
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

    public DefaultGameRepository2(Path baseDirectory) {
        this.status = new Status(baseDirectory);
    }

    public Path getBaseDirectory() {
        return status.baseDirectory;
    }

    @Override
    public void refresh() {
        Status newStatus = new Status(status.baseDirectory);

        if (hasClassicVersion(newStatus.baseDirectory)) {
            GameInstanceID id = CLASSIC_MANIFEST.id();
            newStatus.instances.put(id, new InstanceHolder(newStatus, id, CLASSIC_MANIFEST));
        }

        Path versionsDir = newStatus.baseDirectory.resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (Stream<Path> stream = Files.list(versionsDir)) {
                stream.parallel().filter(Files::isDirectory).flatMap(dir -> {
                    GameInstanceID id = new GameInstanceID(FileUtils.getName(dir));
                    Path json = dir.resolve(id + ".json");

                    if (Files.notExists(json)) {
                        return Stream.empty();
                    }

                    GameInstanceManifest manifest;
                    try {
                        manifest = JsonUtils.fromJsonFile(json, GameInstanceManifest.class);
                        if (manifest == null)
                            throw new JsonParseException("Manifest is null");
                    } catch (Exception e) {
                        LOG.warning("Malformed version json " + id, e);
                        return Stream.empty();
                    }

                    if (!id.equals(manifest.id())) {
                        // TODO
                        return Stream.empty();
                    }

                    return Stream.of(manifest);
                }).forEachOrdered(it -> newStatus.instances.put(
                        it.id(),
                        new InstanceHolder(newStatus, it.id(), it)));
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
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

    public Path getArtifactFile(Version version, Artifact artifact) {
        return artifact.getPath(getBaseDirectory().resolve("libraries"));
    }

    @Override
    public Path getRunDirectory(GameInstanceID instanceId) {
        return getBaseDirectory();
    }

    @Override
    public Path getInstanceJar(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        GameInstanceManifest manifest = getResolvedInstanceManifest(instanceId).manifest();

        GameInstanceID jarID = Objects.requireNonNullElse(manifest.jar(), manifest.id());
        return getInstanceRoot(instanceId).resolve(jarID + ".jar");
    }

    @Override
    public Path getInstanceJar(GameInstanceManifest manifest) {
        GameInstanceID jarID = Objects.requireNonNullElse(manifest.jar(), manifest.id());
        return getInstanceRoot(manifest.id()).resolve(jarID + ".jar");
    }

    @Override
    public boolean renameInstance(GameInstanceID from, GameInstanceID to) {
        return false;
    }

    @Override
    public Optional<String> getGameVersion(GameInstanceManifest manifest) {
        try {
            return getGameVersion(manifest.id());
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
        throw new UnsupportedOperationException("TODO");
//        if (instanceManifest.isResolvedPreservingPatches()) {
//            return new VersionJsonSaveTask(this, MaintainTask.maintainPreservingPatches(this, instanceManifest));
//        } else {
//            return new VersionJsonSaveTask(this, instanceManifest);
//        }
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
        throw new UnsupportedOperationException("TODO");
        // TODO: // return new ModManager(this, instanceId);
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

    @Override
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
        return toResolved(status.resolve(manifest, new HashSet<>()));
    }

    @Override
    public GameInstanceManifest.Standalone resolvePreservingPatches(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
        GameInstanceManifest standaloneManifest = status.resolvePreservingPatches(manifest, new HashSet<>());
        GameInstanceID jar = resolve(manifest).manifest().jar();
        if (jar != null) {
            standaloneManifest = standaloneManifest.withJar(jar);
        }
        return new GameInstanceManifest.Standalone(standaloneManifest);
    }

    private static GameInstanceManifest.Resolved toResolved(GameInstanceManifest manifest) {
        List<GameInstancePatch> appliedPatches = manifest.patches() == null ? List.of() : manifest.patches();
        return new GameInstanceManifest.Resolved(manifest.withPatches(null), appliedPatches);
    }

    protected static class Status {
        private final Path baseDirectory;
        private final Map<GameInstanceID, InstanceHolder> instances = new TreeMap<>();

        protected Status(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        private GameInstanceManifest resolve(GameInstanceManifest manifest,
                                             Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
            GameInstanceManifest currentManifest;

            if (manifest.inheritsFrom() == null) {
                if (manifest.isRoot()) {
                    // TODO: Breaking change, require much testing on versions installed with external installer, other launchers, and all kinds of versions.
                    currentManifest = manifest.patches() != null ? new GameInstanceManifest(manifest.id()).withPatches(manifest.patches()) : manifest;
                } else {
                    currentManifest = manifest;
                }
                currentManifest = manifest.jar() == null ? manifest.withJar(manifest.id()) : currentManifest.withJar(manifest.jar());
            } else {
                // To maximize the compatibility.
                if (!resolvedSoFar.add(manifest.id())) {
                    LOG.warning("Found circular dependency versions: " + resolvedSoFar);
                    currentManifest = manifest.jar() == null ? manifest.withJar(manifest.id()) : manifest;
                } else {
                    InstanceHolder parentInstance = instances.get(manifest.inheritsFrom());
                    if (parentInstance == null) {
                        throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                    }

                    // It is supposed to auto-install a version in getVersion.
                    currentManifest = manifest.merge(resolve(parentInstance.manifest, resolvedSoFar));
                }
            }

            if (manifest.patches() == null) {
                // This is a version from an external launcher. NO need to resolve the patches.
                return currentManifest;
            } else if (!manifest.patches().isEmpty()) {
                // Assume patches themselves do not have patches recursively.
                List<GameInstancePatch> sortedPatches = manifest.patches().stream()
                        .sorted(Comparator.comparing(GameInstancePatch::priority))
                        .toList();
                for (GameInstancePatch patch : sortedPatches) {
                    currentManifest = patch.withJar(null).merge(currentManifest);
                }
            }

            return currentManifest.withId(manifest.id());
        }

        private GameInstanceManifest resolvePreservingPatches(GameInstanceManifest manifest,
                                                              Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
            GameInstanceManifest currentManifest = manifest.isRoot()
                    ? manifest
                    : addPatches(
                            addPatches(new GameInstanceManifest(manifest.id()), Collections.singleton(manifest.toPatch())),
                            manifest.patches());

            if (manifest.inheritsFrom() != null) {
                // To maximize the compatibility.
                if (!resolvedSoFar.add(manifest.id())) {
                    LOG.warning("Found circular dependency versions: " + resolvedSoFar);
                } else {
                    InstanceHolder parentInstance = instances.get(manifest.inheritsFrom());
                    if (parentInstance == null) {
                        throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                    }

                    currentManifest = addPatches(
                            addPatches(resolvePreservingPatches(parentInstance.manifest, resolvedSoFar), Collections.singleton(manifest.toPatch())),
                            manifest.patches());
                }
            }

            return currentManifest.withId(manifest.id());
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
