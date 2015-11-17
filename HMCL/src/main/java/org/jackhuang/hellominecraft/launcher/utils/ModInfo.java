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
package org.jackhuang.hellominecraft.launcher.utils;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.system.FileUtils;

/**
 *
 * @author huangyuhui
 */
public class ModInfo implements Comparable<ModInfo> {

    public File location;
    public String modid, name, description, version, mcversion, url, updateUrl, credits;
    public String[] authorList;

    public boolean isActive() {
        return !location.getName().endsWith(".disabled");
    }

    @Override
    public int compareTo(ModInfo o) {
        return getFileName().toLowerCase().compareTo(o.getFileName().toLowerCase());
    }

    public String getName() {
        return name == null ? FileUtils.removeExtension(location.getName()) : name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ModInfo && (((ModInfo) obj).location == location || ((ModInfo) obj).location.equals(location));
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    public String getFileName() {
        String n = location.getName();
        return FileUtils.removeExtension(isActive() ? n : n.substring(0, n.length() - ".disabled".length()));
    }

    public static boolean isFileMod(File file) {
        if (file == null)
            return false;
        String name = file.getName();
        boolean disabled = name.endsWith(".disabled");
        if (disabled)
            name = name.substring(0, name.length() - ".disabled".length());
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    public static ModInfo readModInfo(File f) {
        ModInfo i = new ModInfo();
        i.location = f;
        try {
            ZipFile jar = new ZipFile(f);
            ZipEntry entry = jar.getEntry("mcmod.info");
            if (entry == null)
                return i;
            else {
                List<ModInfo> m = C.gson.fromJson(new InputStreamReader(jar.getInputStream(entry)), new TypeToken<List<ModInfo>>() {
                                                  }.getType());
                if (m != null && m.size() > 0) {
                    i = m.get(0);
                    i.location = f;
                }
            }
            jar.close();
            return i;
        } catch (IOException ex) {
            HMCLog.warn("File " + f + " is not a jar.", ex);
            return i;
        } catch (JsonSyntaxException e) {
            return i;
        }
    }
}
