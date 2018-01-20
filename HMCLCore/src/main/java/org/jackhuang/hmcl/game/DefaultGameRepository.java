/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * An implementation of classic Minecraft game repository.
 *
 * @author huangyuhui
 */
public class DefaultGameRepository implements GameRepository {

    private File baseDirectory;
    protected final Map<String, Version> versions = new TreeMap<>();
    protected boolean loaded = false;

    public DefaultGameRepository(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public File getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public boolean hasVersion(String id) {
        return versions.containsKey(id);
    }

    @Override
    public Version getVersion(String id) {
        if (!hasVersion(id))
            throw new VersionNotFoundException("Version '" + id + "' does not exist.");
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
    public File getLibraryFile(Version version, Library lib) {
        return new File(getBaseDirectory(), "libraries/" + lib.getPath());
    }

    @Override
    public File getRunDirectory(String id) {
        return getBaseDirectory();
    }

    @Override
    public File getVersionJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return new File(getVersionRoot(id), id + ".jar");
    }

    @Override
    public File getNativeDirectory(String id) {
        return new File(getVersionRoot(id), id + "-natives");
    }

    @Override
    public File getVersionRoot(String id) {
        return new File(getBaseDirectory(), "versions/" + id);
    }

    public File getVersionJson(String id) {
        return new File(getVersionRoot(id), id + ".json");
    }

    public Version readVersionJson(String id) throws IOException, JsonSyntaxException {
        return readVersionJson(getVersionJson(id));
    }

    public Version readVersionJson(File file) throws IOException, JsonSyntaxException {
        return Constants.GSON.fromJson(FileUtils.readText(file), Version.class);
    }

    @Override
    public boolean renameVersion(String from, String to) {
        try {
            Version fromVersion = getVersion(from);
            File fromDir = getVersionRoot(from);
            File toDir = getVersionRoot(to);
            if (!fromDir.renameTo(toDir))
                return false;

            File toJson = new File(toDir, to + ".json");
            File toJar = new File(toDir, to + ".jar");

            if (!new File(toDir, from + ".json").renameTo(toJson)
                    || !new File(toDir, from + ".jar").renameTo(toJar)) {
                // recovery
                toJson.renameTo(new File(toDir, from + ".json"));
                toJar.renameTo(new File(toDir, from + ".jar"));
                toDir.renameTo(fromDir);
                return false;
            }

            FileUtils.writeText(toJson, Constants.GSON.toJson(fromVersion.setId(to)));
            return true;
        } catch (IOException | JsonSyntaxException | VersionNotFoundException e) {
            return false;
        }
    }

    public boolean removeVersionFromDisk(String id) {
        File file = getVersionRoot(id);
        if (!file.exists())
            return true;
        versions.remove(id);
        return FileUtils.deleteDirectoryQuietly(file);
    }

    protected void refreshVersionsImpl() {
        versions.clear();

        if (ClassicVersion.hasClassicVersion(getBaseDirectory())) {
            Version version = new ClassicVersion();
            versions.put(version.getId(), version);
        }

        File[] files = new File(getBaseDirectory(), "versions").listFiles();
        if (files != null)
            for (File dir : files)
                if (dir.isDirectory()) {
                    String id = dir.getName();
                    File json = new File(dir, id + ".json");

                    // If user renamed the json file by mistake or created the json file in a wrong name,
                    // we will find the only json and rename it to correct name.
                    if (!json.exists()) {
                        List<File> jsons = FileUtils.listFilesByExtension(dir, "json");
                        if (jsons.size() == 1)
                            jsons.get(0).renameTo(json);
                    }

                    Version version;
                    try {
                        version = Objects.requireNonNull(readVersionJson(json));
                    } catch (Exception e) {
                        // JsonSyntaxException or IOException or NullPointerException(!!)
                        if (EventBus.EVENT_BUS.fireEvent(new GameJsonParseFailedEvent(this, json, id)) != Event.Result.ALLOW)
                            continue;

                        try {
                            version = Objects.requireNonNull(readVersionJson(json));
                        } catch (Exception e2) {
                            Logging.LOG.log(Level.SEVERE, "User corrected version json is still malformed");
                            continue;
                        }
                    }

                    if (!id.equals(version.getId())) {
                        version = version.setId(id);
                        try {
                            FileUtils.writeText(json, Constants.GSON.toJson(version));
                        } catch (Exception e) {
                            Logging.LOG.log(Level.WARNING, "Ignoring version {0} because wrong id {1} is set and cannot correct it.", new Object[] { id, version.getId() });
                            continue;
                        }
                    }

                    if (EventBus.EVENT_BUS.fireEvent(new LoadedOneVersionEvent(this, version)) != Event.Result.DENY)
                        versions.put(id, version);
                }

        loaded = true;
    }

    @Override
    public void refreshVersions() {
        EventBus.EVENT_BUS.fireEvent(new RefreshingVersionsEvent(this));
        Schedulers.newThread().schedule(() -> {
            refreshVersionsImpl();
            EventBus.EVENT_BUS.fireEvent(new RefreshedVersionsEvent(this));
        });
    }

    @Override
    public AssetIndex getAssetIndex(String version, String assetId) throws IOException {
        try {
            return Objects.requireNonNull(Constants.GSON.fromJson(FileUtils.readText(getIndexFile(version, assetId)), AssetIndex.class));
        } catch (JsonParseException | NullPointerException e) {
            throw new IOException("Asset index file malformed", e);
        }
    }

    @Override
    public File getActualAssetDirectory(String version, String assetId) {
        try {
            return reconstructAssets(version, assetId);
        } catch (IOException | JsonParseException e) {
            Logging.LOG.log(Level.SEVERE, "Unable to reconstruct asset directory", e);
            return getAssetDirectory(version, assetId);
        }
    }

    @Override
    public File getAssetDirectory(String version, String assetId) {
        return new File(getBaseDirectory(), "assets");
    }

    @Override
    public File getAssetObject(String version, String assetId, String name) throws IOException {
        try {
            return getAssetObject(version, assetId, getAssetIndex(version, assetId).getObjects().get(name));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unrecognized asset object " + name + " in asset " + assetId + " of version " + version, e);
        }
    }

    @Override
    public File getAssetObject(String version, String assetId, AssetObject obj) {
        return getAssetObject(version, getAssetDirectory(version, assetId), obj);
    }

    public File getAssetObject(String version, File assetDir, AssetObject obj) {
        return new File(assetDir, "objects/" + obj.getLocation());
    }

    @Override
    public File getIndexFile(String version, String assetId) {
        return new File(getAssetDirectory(version, assetId), "indexes/" + assetId + ".json");
    }

    @Override
    public File getLoggingObject(String version, String assetId, LoggingInfo loggingInfo) {
        return new File(getAssetDirectory(version, assetId), "log_configs/" + loggingInfo.getFile().getId());
    }

    protected File reconstructAssets(String version, String assetId) throws IOException, JsonParseException {
        File assetsDir = getAssetDirectory(version, assetId);
        File indexFile = getIndexFile(version, assetId);
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetId);

        if (!indexFile.isFile())
            return assetsDir;

        String assetIndexContent = FileUtils.readText(indexFile);
        AssetIndex index = Constants.GSON.fromJson(assetIndexContent, AssetIndex.class);

        if (index == null)
            return assetsDir;

        if (index.isVirtual()) {
            int cnt = 0;
            int tot = index.getObjects().entrySet().size();
            for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
                File target = new File(virtualRoot, entry.getKey());
                File original = getAssetObject(version, assetsDir, entry.getValue());
                if (original.exists()) {
                    cnt++;
                    if (!target.isFile())
                        FileUtils.copyFile(original, target);
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

    public boolean isLoaded() {
        return loaded;
    }

}
