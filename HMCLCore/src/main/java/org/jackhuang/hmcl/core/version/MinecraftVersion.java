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

import org.jackhuang.hmcl.api.game.IMinecraftLibrary;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.service.IMinecraftProvider;
import org.jackhuang.hmcl.core.asset.AssetsIndex;
import org.jackhuang.hmcl.util.ArrayUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftVersion implements Cloneable, Comparable<MinecraftVersion> {

    @SerializedName("minecraftArguments")
    public String minecraftArguments;
    @SerializedName("arguments")
    public Arguments arguments;
    @SerializedName("mainClass")
    public String mainClass;
    @SerializedName("time")
    public String time;
    @SerializedName("id")
    public String id;
    @SerializedName("type")
    public String type;
    @SerializedName("processArguments")
    public String processArguments;
    @SerializedName("releaseTime")
    public String releaseTime;
    @SerializedName("jar")
    public String jar;
    @SerializedName("inheritsFrom")
    public String inheritsFrom;
    @SerializedName("runDir")
    public String runDir;
    @SerializedName("assets")
    protected String assets;
    @SerializedName("minimumLauncherVersion")
    public int minimumLauncherVersion;
    @SerializedName("hidden")
    public boolean hidden;
    @SerializedName("assetIndex")
    public AssetIndexDownloadInfo assetIndex;
    @SerializedName("downloads")
    private Map<String, GameDownloadInfo> downloads;
    @SerializedName("libraries")
    public ArrayList<MinecraftLibrary> libraries;
    @SerializedName("logging")
    public Map<String, LoggingInfo> logging;

    public MinecraftVersion() {
    }

    public MinecraftVersion(String minecraftArguments, String mainClass, String time, String id, String type, String processArguments, String releaseTime, String assets, String jar, String inheritsFrom, String runDir, int minimumLauncherVersion, List<MinecraftLibrary> libraries, boolean hidden, Map<String, GameDownloadInfo> downloads, AssetIndexDownloadInfo assetIndexDownloadInfo, Map<String, LoggingInfo> logging) {
        this();
        this.minecraftArguments = minecraftArguments;
        this.mainClass = mainClass;
        this.time = time;
        this.id = id;
        this.type = type;
        this.processArguments = processArguments;
        this.releaseTime = releaseTime;
        this.assets = assets;
        this.jar = jar;
        this.inheritsFrom = inheritsFrom;
        this.minimumLauncherVersion = minimumLauncherVersion;
        this.hidden = hidden;
        this.runDir = runDir;
        if (assetIndexDownloadInfo == null)
            this.assetIndex = null;
        else
            this.assetIndex = (AssetIndexDownloadInfo) assetIndexDownloadInfo.clone();
        if (libraries == null)
            this.libraries = new ArrayList<>();
        else {
            this.libraries = new ArrayList<>(libraries.size());
            for (MinecraftLibrary library : libraries)
                if (library != null)
                    this.libraries.add((MinecraftLibrary) library.clone());
        }
        if (downloads == null)
            this.downloads = null;
        else {
            this.downloads = new HashMap<>(downloads.size());
            for (Map.Entry<String, GameDownloadInfo> entry : downloads.entrySet())
                this.downloads.put(entry.getKey(), (GameDownloadInfo) entry.getValue().clone());
        }
        if (logging == null)
            this.logging = null;
        else {
            this.logging = new HashMap<>(logging.size());
            for (Map.Entry<String, LoggingInfo> entry : logging.entrySet())
                this.logging.put(entry.getKey(), (LoggingInfo) entry.getValue().clone());
        }
    }

    @Override
    public Object clone() {
        try {
            MinecraftVersion mv = (MinecraftVersion) super.clone();
            mv.libraries = (ArrayList<MinecraftLibrary>) mv.libraries.clone();
            return mv;
        } catch (CloneNotSupportedException ex) {
            throw new InternalError();
        }
    }

    public MinecraftVersion resolve(IMinecraftProvider<?> provider) throws GameException {
        return resolve(provider, new HashSet<>());
    }

    protected MinecraftVersion resolve(IMinecraftProvider<?> provider, Set<String> resolvedSoFar) throws GameException {
        if (inheritsFrom == null)
            return this;
        if (!resolvedSoFar.add(id))
            throw new GameException(C.i18n("launch.circular_dependency_versions"));

        MinecraftVersion parent = provider.getVersionById(inheritsFrom);
        if (parent == null) {
            if (!provider.install(inheritsFrom, t -> t.hidden = true))
                return this;
            parent = provider.getVersionById(inheritsFrom);
        }
        parent = parent.resolve(provider, resolvedSoFar);
        MinecraftVersion result = new MinecraftVersion(
                this.minecraftArguments != null ? this.minecraftArguments : parent.minecraftArguments,
                this.mainClass != null ? this.mainClass : parent.mainClass,
                this.time, this.id, this.type, parent.processArguments, this.releaseTime,
                this.assets != null ? this.assets : parent.assets,
                this.jar != null ? this.jar : parent.jar,
                null, this.runDir, parent.minimumLauncherVersion,
                this.libraries != null ? ArrayUtils.merge(this.libraries, parent.libraries) : parent.libraries, this.hidden,
                this.downloads != null ? this.downloads : parent.downloads,
                this.assetIndex != null ? this.assetIndex : parent.assetIndex,
                this.logging != null ? this.logging : parent.logging);

        return result;
    }

    public File getJar(File gameDir) {
        String jarId = this.jar == null ? this.id : this.jar;
        return new File(gameDir, "versions/" + jarId + "/" + jarId + ".jar");
    }

    public File getJar(File gameDir, String suffix) {
        String jarId = this.jar == null ? this.id : this.jar;
        return new File(gameDir, "versions/" + jarId + "/" + jarId + suffix + ".jar");
    }

    public File getNatives(File gameDir) {
        return new File(gameDir, "versions/" + id + "/" + id + "-natives");
    }

    public boolean isAllowedToUnpackNatives() {
        return true;
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        return id.compareTo(o.id);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        return Objects.equals(this.id, ((MinecraftVersion) obj).id);
    }

    public AssetIndexDownloadInfo getAssetsIndex() {
        if (assetIndex == null)
            assetIndex = new AssetIndexDownloadInfo(assets == null ? AssetsIndex.DEFAULT_ASSET_NAME : assets);
        return assetIndex;
    }

    public GameDownloadInfo getClientDownloadInfo() {
        if (downloads == null)
            downloads = new HashMap<>();
        if (!downloads.containsKey("client"))
            downloads.put("client", new GameDownloadInfo());
        GameDownloadInfo i = downloads.get("client");
        if (i.id == null)
            i.id = id;
        return i;
    }

    public List<IMinecraftLibrary> getLibraries() {
        return libraries == null ? new LinkedList<>() : Collections.unmodifiableList(libraries);
    }
}
