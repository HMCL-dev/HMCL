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
package org.jackhuang.hellominecraft.launcher.core;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.system.FileUtils;

/**
 *
 * @author huangyuhui
 */
public class ModInfo implements Comparable<ModInfo> {

    public File location;
    public String modid, name, description, author, version, mcversion, url, updateUrl, credits;
    public String[] authorList;

    public boolean isActive() {
        return !location.getName().endsWith(".disabled");
    }

    public void reverseModState() {
        File f = location, newf;
        if (f.getName().endsWith(".disabled"))
            newf = new File(f.getParentFile(), f.getName().substring(0, f.getName().length() - ".disabled".length()));
        else
            newf = new File(f.getParentFile(), f.getName() + ".disabled");
        if (f.renameTo(newf))
            location = newf;
    }

    @Override
    public int compareTo(ModInfo o) {
        return getFileName().toLowerCase().compareTo(o.getFileName().toLowerCase());
    }

    public String getName() {
        return name == null ? FileUtils.removeExtension(location.getName()) : name;
    }

    public String getAuthor() {
        if (authorList != null && authorList.length > 0)
            return StrUtils.parseParams("", authorList, ", ");
        else if (StrUtils.isNotBlank(author))
            return author;
        else
            return "Unknown";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ModInfo && (((ModInfo) obj).location == location || ((ModInfo) obj).location.equals(location));
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    @Override
    public String toString() {
        return getFileName();
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
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith("litemod");
    }

    private static ModInfo getForgeModInfo(File f, ZipFile jar, ZipEntry entry) throws IOException {
        ModInfo i = new ModInfo();
        i.location = f;
        List<ModInfo> m = C.gson.fromJson(new InputStreamReader(jar.getInputStream(entry)),
                                          new TypeToken<List<ModInfo>>() {
                                      }.getType());
        if (m != null && m.size() > 0) {
            i = m.get(0);
            i.location = f;
        }
        return i;
    }

    private static ModInfo getLiteLoaderModInfo(File f, ZipFile jar, ZipEntry entry) throws IOException {
        ModInfo m = C.gson.fromJson(new InputStreamReader(jar.getInputStream(entry)),
                                    ModInfo.class);
        if (m == null)
            m = new ModInfo();
        m.location = f;
        return m;
    }

    public static ModInfo readModInfo(File f) {
        ModInfo i = new ModInfo();
        i.location = f;
        try {
            try (ZipFile jar = new ZipFile(f)) {
                ZipEntry entry = jar.getEntry("mcmod.info");
                if (entry != null)
                    return getForgeModInfo(f, jar, entry);
                entry = jar.getEntry("litemod.json");
                if (entry != null)
                    return getLiteLoaderModInfo(f, jar, entry);
                return i;
            }
        } catch (IOException ex) {
            HMCLog.warn("File " + f + " is not a jar.", ex);
        } catch (JsonSyntaxException ignore) {
        }
        return i;
    }
}
