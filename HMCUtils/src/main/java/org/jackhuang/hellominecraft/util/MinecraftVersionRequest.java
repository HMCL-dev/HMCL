/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 * @author huangyuhui
 */
public class MinecraftVersionRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;

    public static final int UNKOWN = 0, INVALID = 1, INVALID_JAR = 2,
        MODIFIED = 3, OK = 4, NOT_FOUND = 5, UNREADABLE = 6, NOT_FILE = 7;
    public int type;
    public String version;

    public static String getResponse(MinecraftVersionRequest minecraftVersion) {
        switch (minecraftVersion.type) {
        case MinecraftVersionRequest.INVALID:
            return C.i18n("minecraft.invalid");
        case MinecraftVersionRequest.INVALID_JAR:
            return C.i18n("minecraft.invalid_jar");
        case MinecraftVersionRequest.NOT_FILE:
            return C.i18n("minecraft.not_a_file");
        case MinecraftVersionRequest.NOT_FOUND:
            return C.i18n("minecraft.not_found");
        case MinecraftVersionRequest.UNREADABLE:
            return C.i18n("minecraft.not_readable");
        case MinecraftVersionRequest.MODIFIED:
            return C.i18n("minecraft.modified") + ' ' + minecraftVersion.version;
        case MinecraftVersionRequest.OK:
            return minecraftVersion.version;
        case MinecraftVersionRequest.UNKOWN:
        default:
            return "???";
        }
    }

    private static int lessThan32(byte[] b, int x) {
        for (; x < b.length; x++)
            if (b[x] < 32)
                return x;
        return -1;
    }

    private static MinecraftVersionRequest getVersionOfOldMinecraft(ZipFile file, ZipEntry entry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = IOUtils.toByteArray(file.getInputStream(entry));

        byte[] bytes = "Minecraft Minecraft ".getBytes("ASCII");
        int j = ArrayUtils.matchArray(tmp, bytes);
        if (j < 0) {
            r.type = MinecraftVersionRequest.UNKOWN;
            return r;
        }
        int i = j + bytes.length;

        if ((j = lessThan32(tmp, i)) < 0) {
            r.type = MinecraftVersionRequest.UNKOWN;
            return r;
        }
        String ver = new String(tmp, i, j - i, "ASCII");
        r.version = ver;

        r.type = file.getEntry("META-INF/MANIFEST.MF") == null
                 ? MinecraftVersionRequest.MODIFIED : MinecraftVersionRequest.OK;
        return r;
    }

    private static MinecraftVersionRequest getVersionOfNewMinecraft(ZipFile file, ZipEntry entry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = IOUtils.toByteArray(file.getInputStream(entry));

        byte[] str = "-server.txt".getBytes("ASCII");
        int j = ArrayUtils.matchArray(tmp, str);
        if (j < 0) {
            r.type = MinecraftVersionRequest.UNKOWN;
            return r;
        }
        int i = j + str.length;
        i += 11;
        j = lessThan32(tmp, i);
        if (j < 0) {
            r.type = MinecraftVersionRequest.UNKOWN;
            return r;
        }
        r.version = new String(tmp, i, j - i, "ASCII");

        char ch = r.version.charAt(0);
        // 1.8.1+
        if (ch < '0' || ch > '9') {
            str = "Can't keep up! Did the system time change, or is the server overloaded?".getBytes("ASCII");
            j = ArrayUtils.matchArray(tmp, str);
            if (j < 0) {
                r.type = MinecraftVersionRequest.UNKOWN;
                return r;
            }
            i = -1;
            while (j > 0) {
                if (tmp[j] >= 48 && tmp[j] <= 57) {
                    i = j;
                    break;
                }
                j--;
            }
            if (i == -1) {
                r.type = MinecraftVersionRequest.UNKOWN;
                return r;
            }
            int k = i;
            if (tmp[i + 1] >= (int) 'a' && tmp[i + 1] <= (int) 'z')
                i++;
            while (tmp[k] >= 48 && tmp[k] <= 57 || tmp[k] == (int) '-' || tmp[k] == (int) '.' || tmp[k] >= 97 && tmp[k] <= (int) 'z')
                k--;
            k++;
            r.version = new String(tmp, k, i - k + 1, "ASCII");
        }
        r.type = file.getEntry("META-INF/MANIFEST.MF") == null
                 ? MinecraftVersionRequest.MODIFIED : MinecraftVersionRequest.OK;
        return r;
    }

    public static MinecraftVersionRequest minecraftVersion(File file) {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        if (file == null || !file.exists()) {
            r.type = MinecraftVersionRequest.NOT_FOUND;
            return r;
        }
        if (!file.isFile()) {
            r.type = MinecraftVersionRequest.NOT_FILE;
            return r;
        }
        if (!file.canRead()) {
            r.type = MinecraftVersionRequest.UNREADABLE;
            return r;
        }
        ZipFile f = null;
        try {
            f = new ZipFile(file);
            ZipEntry minecraft = f
                .getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null)
                return getVersionOfOldMinecraft(f, minecraft);
            ZipEntry main = f.getEntry("net/minecraft/client/main/Main.class");
            ZipEntry minecraftserver = f.getEntry("net/minecraft/server/MinecraftServer.class");
            if ((main != null) && (minecraftserver != null))
                return getVersionOfNewMinecraft(f, minecraftserver);
            r.type = MinecraftVersionRequest.INVALID;
            return r;
        } catch (IOException e) {
            HMCLog.warn("Zip file is invalid", e);
            r.type = MinecraftVersionRequest.INVALID_JAR;
            return r;
        } finally {
            IOUtils.closeQuietly(f);
        }
    }
}
