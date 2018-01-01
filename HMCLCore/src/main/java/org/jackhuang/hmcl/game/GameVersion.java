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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.util.Charsets;
import org.jackhuang.hmcl.util.IOUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author huangyuhui
 */
public final class GameVersion {
    private static int lessThan32(byte[] b, int x) {
        for (; x < b.length; x++)
            if (b[x] < 32)
                return x;
        return -1;
    }

    public static int matchArray(byte[] a, byte[] b) {
        for (int i = 0; i < a.length - b.length; i++) {
            int j = 1;
            for (int k = 0; k < b.length; k++) {
                if (b[k] == a[(i + k)])
                    continue;
                j = 0;
                break;
            }
            if (j != 0)
                return i;
        }
        return -1;
    }

    private static String getVersionOfOldMinecraft(ZipFile file, ZipArchiveEntry entry) throws IOException {
        byte[] tmp = IOUtils.readFullyAsByteArray(file.getInputStream(entry));

        byte[] bytes = "Minecraft Minecraft ".getBytes(Charsets.US_ASCII);
        int j = matchArray(tmp, bytes);
        if (j < 0)
            return null;
        int i = j + bytes.length;

        if ((j = lessThan32(tmp, i)) < 0)
            return null;

        return new String(tmp, i, j - i, Charsets.US_ASCII);
    }

    private static String getVersionOfNewMinecraft(ZipFile file, ZipArchiveEntry entry) throws IOException {
        byte[] tmp = IOUtils.readFullyAsByteArray(file.getInputStream(entry));

        byte[] str = "-server.txt".getBytes(Charsets.US_ASCII);
        int j = matchArray(tmp, str);
        if (j < 0) return null;
        int i = j + str.length;
        i += 11;
        j = lessThan32(tmp, i);
        if (j < 0) return null;
        String result = new String(tmp, i, j - i, Charsets.US_ASCII);

        char ch = result.charAt(0);
        // 1.8.1+
        if (ch < '0' || ch > '9') {
            str = "Can't keep up! Did the system time change, or is the server overloaded?".getBytes(Charsets.US_ASCII);
            j = matchArray(tmp, str);
            if (j < 0) return null;
            i = -1;
            while (j > 0) {
                if (tmp[j] >= 48 && tmp[j] <= 57) {
                    i = j;
                    break;
                }
                j--;
            }
            if (i == -1) return null;
            int k = i;
            if (tmp[i + 1] >= (int) 'a' && tmp[i + 1] <= (int) 'z')
                i++;
            while (tmp[k] >= 48 && tmp[k] <= 57 || tmp[k] == (int) '-' || tmp[k] == (int) '.' || tmp[k] >= 97 && tmp[k] <= (int) 'z')
                k--;
            k++;
            return new String(tmp, k, i - k + 1, Charsets.US_ASCII);
        }
        return result;
    }

    public static String minecraftVersion(File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canRead())
            return null;

        ZipFile f = null;
        try {
            f = new ZipFile(file);
            ZipArchiveEntry minecraft = f
                    .getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null)
                return getVersionOfOldMinecraft(f, minecraft);
            ZipArchiveEntry main = f.getEntry("net/minecraft/client/main/Main.class");
            ZipArchiveEntry minecraftserver = f.getEntry("net/minecraft/server/MinecraftServer.class");
            if ((main != null) && (minecraftserver != null))
                return getVersionOfNewMinecraft(f, minecraftserver);
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(f);
        }
    }
}
