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
package org.jackhuang.hellominecraft.launcher.core.version;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.core.assets.AssetsIndex;
import org.jackhuang.hellominecraft.utils.ArrayUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftVersion implements Cloneable, Comparable<MinecraftVersion> {

    public String minecraftArguments, mainClass, time, id, type, processArguments,
        releaseTime, assets, jar, inheritsFrom;
    public int minimumLauncherVersion;
    public boolean hidden;

    public List<MinecraftLibrary> libraries;

    public MinecraftVersion() {
    }

    public MinecraftVersion(String minecraftArguments, String mainClass, String time, String id, String type, String processArguments, String releaseTime, String assets, String jar, String inheritsFrom, int minimumLauncherVersion, List<MinecraftLibrary> libraries, boolean hidden) {
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
        if (libraries == null)
            this.libraries = new ArrayList<>();
        else {
            this.libraries = new ArrayList<>(libraries.size());
            for (IMinecraftLibrary library : libraries)
                this.libraries.add((MinecraftLibrary) library.clone());
        }
    }

    @Override
    public Object clone() {
        return new MinecraftVersion(minecraftArguments, mainClass, time, id, type, processArguments, releaseTime, assets, jar, inheritsFrom, minimumLauncherVersion, libraries, hidden);
    }

    public MinecraftVersion resolve(IMinecraftProvider provider) throws GameException {
        return resolve(provider, new HashSet<>());
    }

    protected MinecraftVersion resolve(IMinecraftProvider provider, Set<String> resolvedSoFar) throws GameException {
        if (inheritsFrom == null)
            return this;
        if (!resolvedSoFar.add(id))
            throw new GameException(C.i18n("launch.circular_dependency_versions"));

        MinecraftVersion parent = provider.getVersionById(inheritsFrom);
        if (parent == null) {
            if (!provider.install(inheritsFrom))
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
            null, parent.minimumLauncherVersion,
            this.libraries != null ? ArrayUtils.merge(this.libraries, parent.libraries) : parent.libraries, this.hidden);

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
        return new File(gameDir, "versions/" + id + "/" + id
                                 + "-natives");
    }

    public boolean isAllowedToUnpackNatives() {
        return true;
    }

    public String getAssets() {
        return assets == null ? AssetsIndex.DEFAULT_ASSET_NAME : assets;
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        return id.compareTo(((MinecraftVersion) o).id);
    }
}
