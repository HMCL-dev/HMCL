/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.utils.system.Platform;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLibrary extends IMinecraftLibrary {

    public ArrayList<Rules> rules;
    public String url, formatted = null;
    //public boolean serverreq=true, clientreq=true;
    public String[] checksums;
    public Natives natives;
    public Extract extract;

    public MinecraftLibrary(String name) {
        super(name);
    }

    public MinecraftLibrary(ArrayList<Rules> rules, String url, String[] checksums, Natives natives, String name, Extract extract) {
        super(name);
        this.rules = rules == null ? null : (ArrayList<Rules>) rules.clone();
        this.url = url;
        this.checksums = checksums == null ? null : Arrays.copyOf(checksums, checksums.length);
        this.natives = natives == null ? null : (Natives) natives.clone();
        this.extract = extract == null ? null : (Extract) extract.clone();
    }

    @Override
    public Object clone() {
        return new MinecraftLibrary(rules, url, checksums, natives, name, extract);
    }

    /**
     * is the library allowed to load.
     *
     * @return
     */
    @Override
    public boolean allow() {
        boolean flag = false;
        if (rules == null || rules.isEmpty())
            flag = true;
        else
            for (Rules r : rules)
                if (r.action.equals("disallow")) {
                    if (r.os != null && (StrUtils.isBlank(r.os.name) || r.os.name.equalsIgnoreCase(OS.os().toString()))) {
                        flag = false;
                        break;
                    }
                } else if (r.os == null || (r.os != null && (StrUtils.isBlank(r.os.name) || r.os.name.equalsIgnoreCase(OS.os().toString()))))
                    flag = true;
        return flag;
    }

    private String formatArch(String nati) {
        return nati == null ? "" : nati.replace("${arch}", Platform.getPlatform().getBit());
    }

    private String getNative() {
        OS os = OS.os();
        if (os == OS.WINDOWS)
            return formatArch(natives.windows);
        else if (os == OS.OSX)
            return formatArch(natives.osx);
        else
            return formatArch(natives.linux);
    }

    @Override
    public boolean isRequiredToUnzip() {
        return natives != null && allow();
    }

    @Override
    public void init() {
        String str = name;
        String[] s = str.split(":");
        str = s[0];
        str = str.replace('.', File.separatorChar);
        if (natives == null)
            str += File.separator + s[1] + File.separator + s[2]
                   + File.separator + s[1] + '-' + s[2] + ".jar";
        else {
            str += File.separator + s[1] + File.separator + s[2]
                   + File.separator + s[1] + '-' + s[2] + '-';
            str += getNative();
            str += ".jar";
        }
        formatted = str;
    }

    @Override
    public File getFilePath(File gameDir) {
        return new File(gameDir, "libraries" + File.separatorChar + formatted);
    }

    @Override
    public String getDownloadURL(String urlBase, DownloadType downloadType) {
        if (StrUtils.isNotBlank(url) && downloadType.getProvider().isAllowedToUseSelfURL())
            urlBase = this.url;
        if (urlBase.endsWith(".jar"))
            return urlBase;
        return urlBase + formatted.replace('\\', '/');
    }

    @Override
    public String[] getDecompressExtractRules() {
        return extract == null || extract.exclude == null ? new String[0] : extract.exclude;
    }
}
