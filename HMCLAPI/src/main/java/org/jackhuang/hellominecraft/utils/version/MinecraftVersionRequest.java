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
package org.jackhuang.hellominecraft.utils.version;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.utils.C;
import org.jackhuang.hellominecraft.utils.HMCLog;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 * @author huangyuhui
 */
public class MinecraftVersionRequest {

    public static final int UNKOWN = 0, INVALID = 1, INVALID_JAR = 2,
        MODIFIED = 3, OK = 4, NOT_FOUND = 5, UNREADABLE = 6, NOT_FILE = 7;
    public int type;
    public String version;

    public static String getResponse(MinecraftVersionRequest minecraftVersion) {
        String text = "";
        switch (minecraftVersion.type) {
        case MinecraftVersionRequest.INVALID:
            text = C.i18n("minecraft.invalid");
            break;
        case MinecraftVersionRequest.INVALID_JAR:
            text = C.i18n("minecraft.invalid_jar");
            break;
        case MinecraftVersionRequest.NOT_FILE:
            text = C.i18n("minecraft.not_a_file");
            break;
        case MinecraftVersionRequest.NOT_FOUND:
            text = C.i18n("minecraft.not_found");
            break;
        case MinecraftVersionRequest.UNREADABLE:
            text = C.i18n("minecraft.not_readable");
            break;
        case MinecraftVersionRequest.MODIFIED:
            text = C.i18n("minecraft.modified") + " ";
        case MinecraftVersionRequest.OK:
            text += minecraftVersion.version;
            break;
        case MinecraftVersionRequest.UNKOWN:
        default:
            text = "???";
            break;
        }
        return text;
    }

    private static int lessThan32(byte[] b, int x) {
        for (; x < b.length; x++)
            if (b[x] < 32)
                return x;
        return -1;
    }

    private static MinecraftVersionRequest getVersionOfOldMinecraft(ZipFile file, ZipEntry entry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(file.getInputStream(entry));

        byte[] bytes = "Minecraft Minecraft ".getBytes("ASCII");
        int j;
        if ((j = ArrayUtils.matchArray(tmp, bytes)) < 0) {
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
        byte[] tmp = NetUtils.getBytesFromStream(file.getInputStream(entry));

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
            while (tmp[k] >= 48 && tmp[k] <= 57 || tmp[k] == 46)
                k--;
            k++;
            r.version = new String(tmp, k, i - k + 1);
        }
        r.type = file.getEntry("META-INF/MANIFEST.MF") == null
                 ? MinecraftVersionRequest.MODIFIED : MinecraftVersionRequest.OK;
        return r;
    }

    public static MinecraftVersionRequest minecraftVersion(File file) {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        if (!file.exists()) {
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
        ZipFile localZipFile = null;
        try {
            localZipFile = new ZipFile(file);
            ZipEntry minecraft = localZipFile
                .getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null)
                return getVersionOfOldMinecraft(localZipFile, minecraft);
            ZipEntry main = localZipFile.getEntry("net/minecraft/client/main/Main.class");
            ZipEntry minecraftserver = localZipFile.getEntry("net/minecraft/server/MinecraftServer.class");
            if ((main != null) && (minecraftserver != null))
                return getVersionOfNewMinecraft(localZipFile, minecraftserver);
            r.type = MinecraftVersionRequest.INVALID;
            return r;
        } catch (IOException localException) {
            HMCLog.warn("Zip file is invalid", localException);
            r.type = MinecraftVersionRequest.INVALID_JAR;
            return r;
        } finally {
            if (localZipFile != null)
                try {
                    localZipFile.close();
                } catch (IOException ex) {
                    HMCLog.warn("Failed to close zip file", ex);
                }
        }
    }
}
