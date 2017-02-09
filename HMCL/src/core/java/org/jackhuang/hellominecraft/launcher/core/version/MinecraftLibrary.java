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

import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.jackhuang.hellominecraft.util.sys.OS;
import org.jackhuang.hellominecraft.util.sys.Platform;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLibrary extends IMinecraftLibrary {

    @SerializedName("rules")
    public ArrayList<Rules> rules;
    @SerializedName("url")
    public String url;
    @SerializedName("natives")
    public Natives natives;
    @SerializedName("extract")
    public Extract extract;
    @SerializedName("downloads")
    public LibrariesDownloadInfo downloads;

    public MinecraftLibrary(String name) {
        super(name);
    }

    /**
     * is the library allowed to load.
     *
     * @return
     */
    @Override
    public boolean allow() {
        if (rules != null) {
            boolean flag = false;
            for (Rules r : rules)
                if ("disallow".equals(r.action()))
                    return false;
                else if ("allow".equals(r.action()))
                    flag = true;
            return flag;
        } else
            return true;
    }

    private String formatArch(String nati) {
        return nati == null ? "" : nati.replace("${arch}", Platform.getPlatform().getBit());
    }

    private String getNative() {
        if (natives == null)
            return "";
        switch (OS.os()) {
            case WINDOWS:
                return formatArch(natives.windows);
            case OSX:
                return formatArch(natives.osx);
            default:
                return formatArch(natives.linux);
        }
    }

    @Override
    public boolean isRequiredToUnzip() {
        return natives != null && allow();
    }

    public String formatName() {
        if (name == null)
            return null;
        String[] s = name.split(":");
        if (s.length < 3)
            return null;
        StringBuilder sb = new StringBuilder(s[0].replace('.', '/')).append('/').append(s[1]).append('/').append(s[2]).append('/').append(s[1]).append('-').append(s[2]);
        if (natives != null)
            sb.append('-').append(getNative());
        return sb.append(".jar").toString();
    }

    @Override
    public File getFilePath(File gameDir) {
        LibraryDownloadInfo info = getDownloadInfo();
        if (info == null)
            return null;
        return new File(gameDir, "libraries/" + info.path);
    }

    @Override
    public Extract getDecompressExtractRules() {
        return extract == null ? new Extract() : extract;
    }

    @Override
    public LibraryDownloadInfo getDownloadInfo() {
        if (name == null)
            return null;
        if (downloads == null)
            downloads = new LibrariesDownloadInfo();
        LibraryDownloadInfo info;
        if (natives != null) {
            if (downloads.classifiers == null)
                downloads.classifiers = new HashMap<>();
            if (!downloads.classifiers.containsKey(getNative()))
                downloads.classifiers.put(getNative(), info = new LibraryDownloadInfo());
            else {
                info = downloads.classifiers.get(getNative());
                if (info == null)
                    info = new LibraryDownloadInfo();
            }
        } else {
            if (downloads.artifact == null)
                downloads.artifact = new LibraryDownloadInfo();
            info = downloads.artifact;
        }
        if (StrUtils.isBlank(info.path)) {
            info.path = formatName();
            if (info.path == null)
                return null;
        }
        info.forgeURL = this.url;
        return info;
    }
}
