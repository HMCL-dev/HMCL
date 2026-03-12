/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.game.tlauncher.TLauncherVersion;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Platform;
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

/**
 * An implementation of classic Minecraft game repository.
 *
 * @author huangyuhui
 */
public class DefaultGameRepository implements GameRepository {

    private Path baseDirectory;
    protected Map<String, Version> versions;
    private final ConcurrentHashMap<Path, Optional<String>> gameVersions = new ConcurrentHashMap<>();

    public DefaultGameRepository(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public boolean hasVersion(String id) {
        return id != null && versions.containsKey(id);
    }

    @Override
    public Version getVersion(String id) {
        if (!hasVersion(id))
            throw new VersionNotFoundException("Version '" + id + "' does not exist in " + versions.keySet() + ".");
        return versions.get(id);
    }

    @Override
    public int getVersionCount() {
        return versions.size();
    }

    @Override
    public Collection<Version> getVersions() {
        return versions.values();
    }

    @Override
    public Path getLibrariesDirectory(Version version) {
        return getBaseDirectory().resolve("libraries");
    }

    @Override
    public Path getLibraryFile(Version version, Library lib) {
        if ("local".equals(lib.getHint())) {
            if (lib.getFileName() != null) {
                return getVersionRoot(version.getId()).resolve("libraries/" + lib.getFileName());
            }

            return getVersionRoot(version.getId()).resolve("libraries/" + lib.getArtifact().getFileName());
        }

        return getLibrariesDirectory(version).resolve(lib.getPath());
    }

    public Path getArtifactFile(Version version, Artifact artifact) {
        return artifact.getPath(getBaseDirectory().resolve("libraries"));
    }

    public GameDirectoryType getGameDirectoryType(String id) {
        return GameDirectoryType.ROOT_FOLDER;
    }

    @Override
    public Path getRunDirectory(String id) {
        return switch (getGameDirectoryType(id)) {
            case VERSION_FOLDER -> getVersionRoot(id);
            case ROOT_FOLDER -> getBaseDirectory();
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public Path getVersionJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return getVersionRoot(id).resolve(id + ".jar");
    }

    @Override
    public Optional<String> getGameVersion(Version version) {
        // This implementation may cause multiple flows against the same version entering
        // this function, which is accepted because GameVersion::minecraftVersion should
        // be consistent.
        return gameVersions.computeIfAbsent(getVersionJar(version), versionJar -> {
            Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
            if (gameVersion.isEmpty()) {
                LOG.warning("Cannot find out game version of " + version.getId() + ", primary jar: " + versionJar.toString() + ", jar exists: " + Files.exists(versionJar));
            }
            return gameVersion;
        });
    }

    @Override
    public Path getNativeDirectory(String id, Platform platform) {
        return getVersionRoot(id).resolve("natives-" + platform);
    }

    @Override
    public Path getModsDirectory(String id) {
        return getRunDirectory(id).resolve("mods");
    }

    @Override
    public Path getVersionRoot(String id) {
        return getBaseDirectory().resolve("versions/" + id);
    }

    public Path getVersionJson(String id) {
        return getVersionRoot(id).resolve(id + ".json");
    }

    public Version readVersionJson(String id) throws IOException, JsonParseException {
        return readVersionJson(getVersionJson(id));
    }

    public Version readVersionJson(Path file) throws IOException, JsonParseException {
        String jsonText = Files.readString(file);
        try {
            // Try TLauncher version json format
            return JsonUtils.fromNonNullJson(jsonText, TLauncherVersion.class).toVersion();
        } catch (JsonParseException ignored) {
        }

        try {
            // Try official version json format
            return JsonUtils.fromNonNullJson(jsonText, Version.class);
        } catch (JsonParseException ignored) {
        }

        LOG.warning("Cannot parse version json: " + file + "\n" + jsonText);
        throw new JsonParseException("Version json incorrect");
    }

    @Override
    public boolean renameVersion(String from, String to) {
        if (EventBus.EVENT_BUS.fireEvent(new RenameVersionEvent(this, from, to)) == Event.Result.DENY)
            return false;

        try {
            Version fromVersion = getVersion(from);
            Path fromDir = getVersionRoot(from);
            Path toDir = getVersionRoot(to);
            Files.move(fromDir, toDir);

            Path fromJson = toDir.resolve(from + ".json");
            Path fromJar = toDir.resolve(from + ".jar");
            Path toJson = toDir.resolve(to + ".json");
            Path toJar = toDir.resolve(to + ".jar");

            boolean hasJarFile = Files.exists(fromJar);

            try {
                Files.move(fromJson, toJson);
                if (hasJarFile) Files.move(fromJar, toJar);
            } catch (IOException e) {
                // recovery
                Lang.ignoringException(() -> Files.move(toJson, fromJson));
                if (hasJarFile) Lang.ignoringException(() -> Files.move(toJar, fromJar));
                Lang.ignoringException(() -> Files.move(toDir, fromDir));
                throw e;
            }

            if (fromVersion.getId().equals(fromVersion.getJar()))
                fromVersion = fromVersion.setJar(null);
            JsonUtils.writeToJsonFile(toJson, fromVersion.setId(to));

            // fix inheritsFrom of versions that inherits from version [from].
            for (Version version : getVersions()) {
                if (from.equals(version.getInheritsFrom())) {
                    Path targetPath = getVersionJson(version.getId());
                    Files.createDirectories(targetPath.getParent());
                    JsonUtils.writeToJsonFile(targetPath, version.setInheritsFrom(to));
                }
            }
            return true;
        } catch (IOException | JsonParseException | VersionNotFoundException | InvalidPathException e) {
            LOG.warning("Unable to rename version " + from + " to " + to, e);
            return false;
        }
    }

    public boolean removeVersionFromDisk(String id) {
        if (EventBus.EVENT_BUS.fireEvent(new RemoveVersionEvent(this, id)) == Event.Result.DENY)
            return false;
        if (!versions.containsKey(id))
            return FileUtils.deleteDirectoryQuietly(getVersionRoot(id));
        Path file = getVersionRoot(id);
        if (Files.notExists(file))
            return true;
        // test if no file in this version directory is occupied.
        Path removedFile = file.toAbsolutePath().resolveSibling(FileUtils.getName(file) + "_removed");
        try {
            Files.move(file, removedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Failed to rename file " + file, e);
            return false;
        }

        try {
            versions.remove(id);

            if (FileUtils.moveToTrash(removedFile)) {
                return true;
            }

            // remove json files first to ensure HMCL will not recognize this folder as a valid version.

            for (Path path : FileUtils.listFilesByExtension(removedFile, "json")) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOG.warning("Failed to delete file " + path, e);
                }
            }

            // remove the version from version list regardless of whether the directory was removed successfully or not.
            try {
                FileUtils.deleteDirectory(removedFile);
            } catch (IOException e) {
                LOG.warning("Unable to remove version folder: " + file, e);
            }
            return true;
        } finally {
            refreshVersionsAsync().start();
        }
    }

    protected void refreshVersionsImpl() {
        Map<String, Version> versions = new TreeMap<>();

        if (ClassicVersion.hasClassicVersion(getBaseDirectory())) {
            Version version = new ClassicVersion();
            versions.put(version.getId(), version);
        }

        SimpleVersionProvider provider = new SimpleVersionProvider();

        Path versionsDir = getBaseDirectory().resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (Stream<Path> stream = Files.list(versionsDir)) {
                stream.parallel().filter(Files::isDirectory).flatMap(dir -> {
                    String id = FileUtils.getName(dir);
                    Path json = dir.resolve(id + ".json");

                    // If user renamed the json file by mistake or created the json file in a wrong name,
                    // we will find the only json and rename it to correct name.
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

                    Version version;
                    try {
                        version = readVersionJson(json);
                    } catch (Exception e) {
                        LOG.warning("Malformed version json " + id, e);
                        // JsonSyntaxException or IOException or NullPointerException(!!)
                        if (EventBus.EVENT_BUS.fireEvent(new GameJsonParseFailedEvent(this, json, id)) != Event.Result.ALLOW)
                            return Stream.empty();

                        try {
                            version = readVersionJson(json);
                        } catch (Exception e2) {
                            LOG.error("User corrected version json is still malformed", e2);
                            return Stream.empty();
                        }
                    }

                    if (!id.equals(version.getId())) {
                        try {
                            String from = id;
                            String to = version.getId();
                            Path fromDir = getVersionRoot(from);
                            Path toDir = getVersionRoot(to);
                            Files.move(fromDir, toDir);

                            Path fromJson = toDir.resolve(from + ".json");
                            Path fromJar = toDir.resolve(from + ".jar");
                            Path toJson = toDir.resolve(to + ".json");
                            Path toJar = toDir.resolve(to + ".jar");

                            try {
                                Files.move(fromJson, toJson);
                                if (Files.exists(fromJar))
                                    Files.move(fromJar, toJar);
                            } catch (IOException e) {
                                // recovery
                                Lang.ignoringException(() -> Files.move(toJson, fromJson));
                                Lang.ignoringException(() -> Files.move(toJar, fromJar));
                                Lang.ignoringException(() -> Files.move(toDir, fromDir));
                                throw e;
                            }
                        } catch (IOException e) {
                            LOG.warning("Ignoring version " + version.getId() + " because version id does not match folder name " + id + ", and we cannot correct it.", e);
                            return Stream.empty();
                        }
                    }

                    return Stream.of(version);
                }).forEachOrdered(provider::addVersion);
            } catch (IOException e) {
                LOG.warning("Failed to load versions from " + versionsDir, e);
            }
        }

        for (Version version : provider.getVersionMap().values()) {
            try {
                Version resolved = version.resolve(provider);

                if (resolved.appliesToCurrentEnvironment() &&
                        EventBus.EVENT_BUS.fireEvent(new LoadedOneVersionEvent(this, resolved)) != Event.Result.DENY)
                    versions.put(version.getId(), version);
            } catch (VersionNotFoundException e) {
                LOG.warning("Ignoring version " + version.getId() + " because it inherits from a nonexistent version.");
            }
        }

        this.gameVersions.clear();
        this.versions = versions;
    }

    @Override
    public void refreshVersions() {
        if (EventBus.EVENT_BUS.fireEvent(new RefreshingVersionsEvent(this)) == Event.Result.DENY)
            return;

        refreshVersionsImpl();
        EventBus.EVENT_BUS.fireEvent(new RefreshedVersionsEvent(this));
    }

    @Override
    public AssetIndex getAssetIndex(String version, String assetId) throws IOException {
        try {
            return Objects.requireNonNull(JsonUtils.fromJsonFile(getIndexFile(version, assetId), AssetIndex.class));
        } catch (JsonParseException | NullPointerException e) {
            throw new IOException("Asset index file malformed", e);
        }
    }

    @Override
    public Path getActualAssetDirectory(String version, String assetId) {
        try {
            return reconstructAssets(version, assetId);
        } catch (IOException | JsonParseException e) {
            LOG.error("Unable to reconstruct asset directory", e);
            return getAssetDirectory(version, assetId);
        }
    }

    @Override
    public Path getAssetDirectory(String version, String assetId) {
        return getBaseDirectory().resolve("assets");
    }

    @Override
    public Optional<Path> getAssetObject(String version, String assetId, String name) throws IOException {
        try {
            AssetObject assetObject = getAssetIndex(version, assetId).getObjects().get(name);
            if (assetObject == null) return Optional.empty();
            return Optional.of(getAssetObject(version, assetId, assetObject));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unrecognized asset object " + name + " in asset " + assetId + " of version " + version, e);
        }
    }

    @Override
    public Path getAssetObject(String version, String assetId, AssetObject obj) {
        return getAssetObject(version, getAssetDirectory(version, assetId), obj);
    }

    public Path getAssetObject(String version, Path assetDir, AssetObject obj) {
        return assetDir.resolve("objects").resolve(obj.getLocation());
    }

    @Override
    public Path getIndexFile(String version, String assetId) {
        return getAssetDirectory(version, assetId).resolve("indexes").resolve(assetId + ".json");
    }

    @Override
    public Path getLoggingObject(String version, String assetId, LoggingInfo loggingInfo) {
        return getAssetDirectory(version, assetId).resolve("log_configs").resolve(loggingInfo.getFile().getId());
    }

    protected Path reconstructAssets(String version, String assetId) throws IOException, JsonParseException {
        Path assetsDir = getAssetDirectory(version, assetId);
        Path indexFile = getIndexFile(version, assetId);
        Path virtualRoot = assetsDir.resolve("virtual").resolve(assetId);

        if (!Files.isRegularFile(indexFile))
            return assetsDir;

        AssetIndex index = JsonUtils.fromJsonFile(indexFile, AssetIndex.class);

        if (index == null)
            return assetsDir;

        if (index.isVirtual()) {
            Path resourcesDir = getRunDirectory(version).resolve("resources");

            int cnt = 0;
            int tot = index.getObjects().size();
            for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
                Path target = virtualRoot.resolve(entry.getKey());
                Path original = getAssetObject(version, assetsDir, entry.getValue());
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

            // If the scale new format existent file is lower then 0.1, use the old format.
            if (cnt * 10 < tot)
                return assetsDir;
            else
                return virtualRoot;
        }

        return assetsDir;
    }

    public Task<Version> saveAsync(Version version) {
        this.gameVersions.remove(getVersionJar(version));
        if (version.isResolvedPreservingPatches()) {
            return new VersionJsonSaveTask(this, MaintainTask.maintainPreservingPatches(this, version));
        } else {
            return new VersionJsonSaveTask(this, version);
        }
    }

    public boolean isLoaded() {
        return versions != null;
    }

    public Path getModpackConfiguration(String version) {
        return getVersionRoot(version).resolve("modpack.json");
    }

    /**
     * read modpack configuration for a version.
     *
     * @param version version installed as modpack
     * @return modpack configuration object, or null if this version is not a modpack.
     * @throws VersionNotFoundException if version does not exist.
     * @throws IOException              if an i/o error occurs.
     */
    @Nullable
    public ModpackConfiguration<?> readModpackConfiguration(String version) throws IOException, VersionNotFoundException {
        if (!hasVersion(version)) throw new VersionNotFoundException(version);
        Path file = getModpackConfiguration(version);
        if (Files.notExists(file)) return null;
        return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
    }

    public boolean isModpack(String version) {
        return Files.exists(getModpackConfiguration(version));
    }

    public ModManager getModManager(String version) {
        return new ModManager(this, version);
    }

    public Path getSavesDirectory(String id) {
        return getRunDirectory(id).resolve("saves");
    }

    public Path getBackupsDirectory(String id) {
        return getRunDirectory(id).resolve("backups");
    }

    public Path getSchematicsDirectory(String id) {
        return getRunDirectory(id).resolve("schematics");
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("versions", versions == null ? null : versions.keySet())
                .append("baseDirectory", baseDirectory)
                .toString();
    }

    public Path getResourcepacksDirectory(String id) {
        return getRunDirectory(id).resolve("resourcepacks");
    }
}
