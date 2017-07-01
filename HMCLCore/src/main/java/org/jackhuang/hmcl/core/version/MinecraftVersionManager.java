/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.version;

import org.jackhuang.hmcl.api.game.Extract;
import org.jackhuang.hmcl.api.game.IMinecraftLibrary;
import org.jackhuang.hmcl.api.game.DecompressLibraryJob;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.version.LoadedOneVersionEvent;
import org.jackhuang.hmcl.api.event.version.RefreshedVersionsEvent;
import org.jackhuang.hmcl.api.event.version.RefreshingVersionsEvent;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.service.IMinecraftProvider;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.core.MCUtils;
import org.jackhuang.hmcl.util.task.TaskWindow;
import org.jackhuang.hmcl.util.MessageBox;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.func.Consumer;
import org.jackhuang.hmcl.api.func.Predicate;
import org.jackhuang.hmcl.util.sys.IOUtils;
import org.jackhuang.hmcl.util.ui.SwingUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftVersionManager<T extends IMinecraftService> extends IMinecraftProvider<T> {

    final Map<String, MinecraftVersion> versions = new TreeMap<>();

    /**
     *
     * @param p
     */
    public MinecraftVersionManager(T p) {
        super(p);
    }

    @Override
    public Collection<MinecraftVersion> getVersions() {
        return versions.values();
    }

    @Override
    public int getVersionCount() {
        return versions.size();
    }

    @Override
    public synchronized void refreshVersions() {
        HMCLApi.EVENT_BUS.fireChannel(new RefreshingVersionsEvent(this));

        try {
            MCUtils.tryWriteProfile(baseDirectory());
        } catch (IOException ex) {
            HMCLog.warn("Failed to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }

        versions.clear();
        File oldDir = new File(baseDirectory(), "bin");
        if (oldDir.exists()) {
            MinecraftClassicVersion v = new MinecraftClassicVersion();
            versions.put(v.id, v);
        }

        File version = new File(baseDirectory(), "versions");
        File[] files = version.listFiles();
        if (files != null && files.length > 0)
            for (File dir : files) {
                String id = dir.getName();
                File jsonFile = new File(dir, id + ".json");

                if (!dir.isDirectory())
                    continue;
                boolean ask = false;
                File[] jsons = null;
                if (!jsonFile.exists()) {
                    jsons = FileUtils.searchSuffix(dir, "json");
                    if (jsons.length == 1)
                        ask = true;
                }
                if (ask) {
                    HMCLog.warn("Found not matched filenames version: " + id + ", json: " + jsons[0].getName());
                    if (MessageBox.show(String.format(C.i18n("launcher.versions_json_not_matched"), id, jsons[0].getName()), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                        if (!jsons[0].renameTo(new File(jsons[0].getParent(), id + ".json")))
                            HMCLog.warn("Failed to rename version json " + jsons[0]);
                }
                if (!jsonFile.exists()) {
                    if (MessageBox.show(C.i18n("launcher.versions_json_not_matched_cannot_auto_completion", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                        FileUtils.deleteDirectoryQuietly(dir);
                    continue;
                }
                MinecraftVersion mcVersion;
                try {
                    mcVersion = readJson(jsonFile);
                    if (mcVersion == null)
                        throw new JsonSyntaxException("Wrong json format, got null.");
                } catch (JsonSyntaxException | IOException e) {
                    HMCLog.warn("Found wrong format json, try to fix it.", e);
                    if (MessageBox.show(C.i18n("launcher.versions_json_not_formatted", id), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                        TaskWindow.factory().execute(service.download().downloadMinecraftVersionJson(id));
                        try {
                            mcVersion = readJson(jsonFile);
                            if (mcVersion == null)
                                throw new JsonSyntaxException("Wrong json format, got null.");
                        } catch (IOException | JsonSyntaxException ex) {
                            HMCLog.warn("Ignoring: " + dir + ", the json of this Minecraft is malformed.", ex);
                            continue;
                        }
                    } else
                        continue;
                }
                try {
                    if (!id.equals(mcVersion.id)) {
                        HMCLog.warn("Found: " + dir + ", it contains id: " + mcVersion.id + ", expected: " + id + ", this app will fix this problem.");
                        mcVersion.id = id;
                        FileUtils.writeQuietly(jsonFile, C.GSON.toJson(mcVersion));
                    }

                    versions.put(id, mcVersion);
                    HMCLApi.EVENT_BUS.fireChannel(new LoadedOneVersionEvent(this, id));
                } catch (Exception e) {
                    HMCLog.warn("Ignoring: " + dir + ", the json of this Minecraft is malformed.", e);
                }
            }
        HMCLApi.EVENT_BUS.fireChannel(new RefreshedVersionsEvent(this));
    }

    @Override
    public File versionRoot(String id) {
        return new File(baseDirectory(), "versions/" + id);
    }

    @Override
    public boolean removeVersionFromDisk(String name) {
        File version = versionRoot(name);
        if (!version.exists())
            return true;

        versions.remove(name);
        return FileUtils.deleteDirectoryQuietly(version);
    }
    
    /**
     * 
     * @param id version id
     * @return null if json syntax is wrong or cannot read the json file.
     */
    public MinecraftVersion readJson(String id) {
        try {
            return readJson(new File(versionRoot(id), id + ".json"));
        } catch(IOException | JsonSyntaxException e) {
            return null;
        }
    }
    
    public MinecraftVersion readJson(File file) throws IOException {
        return C.GSON.fromJson(FileUtils.read(file, IOUtils.DEFAULT_CHARSET), MinecraftVersion.class);
    }
    
    @Override
    public boolean renameVersion(String from, String to) {
        try {
            File toDir = versionRoot(to);
            if (!versionRoot(from).renameTo(toDir))
                return false;
            File toJson = new File(toDir, to + ".json");
            File toJar = new File(toDir, to + ".jar");
            if (!new File(toDir, from + ".json").renameTo(toJson))
                HMCLog.warn("MinecraftVersionManager.RenameVersion: Failed to rename json");
            MinecraftVersion mcVersion = readJson(toJson);
            mcVersion.id = to;
            FileUtils.writeQuietly(toJson, C.GSON.toJson(mcVersion));
            File oldJar = new File(toDir, from + ".jar");
            if (oldJar.exists() && !oldJar.renameTo(toJar))
                HMCLog.warn("Failed to rename pre jar " + oldJar + " to new jar " + toJar);
            return true;
        } catch (IOException | JsonSyntaxException e) {
            HMCLog.warn("Failed to rename " + from + " to " + to + ", the json of this Minecraft is malformed.", e);
            return false;
        }
    }

    @Override
    public File getRunDirectory(String id) {
        if (getVersionById(id) != null)
            if ("version".equals(getVersionById(id).runDir))
                return versionRoot(id);
        return baseDirectory();
    }

    @Override
    public boolean install(String id, Consumer<MinecraftVersion> callback) {
        if (!TaskWindow.factory().append(service.download().downloadMinecraft(id)).execute())
            return false;
        if (callback != null) {
            File mvt = new File(versionRoot(id), id + ".json");
            MinecraftVersion v = readJson(id);
            if (v == null)
                return false;
            callback.accept(v);
            FileUtils.writeQuietly(mvt, C.GSON.toJson(v));
        }
        refreshVersions();
        return true;
    }

    @Override
    public void open(String mv, String name) {
        SwingUtils.openFolder((name == null) ? getRunDirectory(mv) : new File(getRunDirectory(mv), name));
    }

    @Override
    public File getLibraryFile(MinecraftVersion version, IMinecraftLibrary lib) {
        return lib.getFilePath(baseDirectory());
    }

    @Override
    public DecompressLibraryJob getDecompressLibraries(MinecraftVersion v) throws GameException {
        if (v.libraries == null)
            throw new GameException("Wrong format: minecraft.json");
        ArrayList<File> unzippings = new ArrayList<>();
        ArrayList<Extract> extractRules = new ArrayList<>();
        for (IMinecraftLibrary l : v.libraries)
            if (l.isNative() && v.isAllowedToUnpackNatives()) {
                unzippings.add(getLibraryFile(v, l));
                extractRules.add(l.getDecompressExtractRules());
            }
        return new DecompressLibraryJob(unzippings.toArray(new File[unzippings.size()]), extractRules.toArray(new Extract[extractRules.size()]), getDecompressNativesToLocation(v));
    }

    @Override
    public File getDecompressNativesToLocation(MinecraftVersion v) {
        return v == null ? null : v.getNatives(baseDirectory());
    }

    @Override
    public File getMinecraftJar(String id) {
        if (versions.containsKey(id))
            return versions.get(id).getJar(baseDirectory());
        else
            return null;
    }

    @Override
    public MinecraftVersion getOneVersion(Predicate<MinecraftVersion> pred) {
        for (MinecraftVersion v : versions.values())
            if (pred == null || pred.apply(v))
                return v;
        return null;
    }

    @Override
    public MinecraftVersion getVersionById(String id) {
        return StrUtils.isBlank(id) ? null : versions.get(id);
    }

    @Override
    public boolean onLaunch(String id) {
        File resourcePacks = new File(getRunDirectory(id), "resourcepacks");
        if (!FileUtils.makeDirectory(resourcePacks))
            HMCLog.warn("Failed to make resourcePacks: " + resourcePacks);
        return true;
    }

    @Override
    public void cleanFolder() {
        for (MinecraftVersion s : getVersions()) {
            FileUtils.deleteDirectoryQuietly(new File(versionRoot(s.id), s.id + "-natives"));
            File f = getRunDirectory(s.id);
            String[] dir = { "natives", "native", "$native", "AMD", "crash-reports", "logs", "asm", "NVIDIA", "server-resource-packs", "natives", "native" };
            for (String str : dir)
                FileUtils.deleteDirectoryQuietly(new File(f, str));
            String[] files = { "output-client.log", "usercache.json", "usernamecache.json", "hmclmc.log" };
            for (String str : files)
                if (!new File(f, str).delete())
                    HMCLog.warn("Failed to delete " + str);
        }
    }

    @Override
    public void initializeMiencraft() {
    }
}
