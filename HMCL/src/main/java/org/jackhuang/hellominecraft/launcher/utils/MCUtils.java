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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsIndex;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsObject;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersions;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.version.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.system.OS;

/**
 *
 * @author huang
 */
public final class MCUtils {

    public static File getAssetObject(Gson gson, String dir, String assetVersion, String name) throws IOException {
        File assetsDir = new File(dir, "assets");
        File indexDir = new File(assetsDir, "indexes");
        File objectsDir = new File(assetsDir, "objects");
        File indexFile = new File(indexDir, assetVersion + ".json");
        try {
            AssetsIndex index = (AssetsIndex) gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

            String hash = ((AssetsObject) index.getFileMap().get(name)).getHash();
            return new File(objectsDir, hash.substring(0, 2) + "/" + hash);
        } catch (JsonSyntaxException e) {
            throw new IOException("Assets file format malformed.", e);
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
        byte[] tmp = NetUtils.getBytesFromStream(file.getInputStream(entry));

        byte[] bytes = "Minecraft Minecraft ".getBytes("ASCII");
        int j;
        if ((j = ArrayUtils.matchArray(tmp, bytes)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + bytes.length;

        if ((j = lessThan32(tmp, i)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        String ver = new String(tmp, i, j - i, "ASCII");
        r.version = ver;

        r.type = file.getEntry("META-INF/MANIFEST.MF") == null
                ? MinecraftVersionRequest.Modified : MinecraftVersionRequest.OK;
        return r;
    }

    private static MinecraftVersionRequest getVersionOfNewMinecraft(ZipFile file, ZipEntry entry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(file.getInputStream(entry));

        byte[] str = "-server.txt".getBytes("ASCII");
        int j = ArrayUtils.matchArray(tmp, str);
        if (j < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + str.length;
        i += 11;
        j = lessThan32(tmp, i);
        if (j < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        r.version = new String(tmp, i, j - i, "ASCII");

        char ch = r.version.charAt(0);
        // 1.8.1+
        if (ch < '0' || ch > '9') {
            str = "Can't keep up! Did the system time change, or is the server overloaded?".getBytes("ASCII");
            j = ArrayUtils.matchArray(tmp, str);
            if (j < 0) {
                r.type = MinecraftVersionRequest.Unkown;
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
                r.type = MinecraftVersionRequest.Unkown;
                return r;
            }
            int k = i;
            while (tmp[k] >= 48 && tmp[k] <= 57 || tmp[k] == 46) k--;
            k++;
            r.version = new String(tmp, k, i - k + 1);
        }
        r.type = file.getEntry("META-INF/MANIFEST.MF") == null
                ? MinecraftVersionRequest.Modified : MinecraftVersionRequest.OK;
        return r;
    }

    public static MinecraftVersionRequest minecraftVersion(File file) {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        if (!file.exists()) {
            r.type = MinecraftVersionRequest.NotFound;
            return r;
        }
        if (!file.isFile()) {
            r.type = MinecraftVersionRequest.NotAFile;
            return r;
        }
        if (!file.canRead()) {
            r.type = MinecraftVersionRequest.NotReadable;
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
            r.type = MinecraftVersionRequest.Invaild;
            return r;
        } catch (IOException localException) {
            HMCLog.warn("Zip file is invalid", localException);
            r.type = MinecraftVersionRequest.InvaildJar;
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

    public static File getWorkingDirectory(String baseName) {
        String userhome = System.getProperty("user.home", ".");
        File file;
        switch (OS.os()) {
            case LINUX:
                file = new File(userhome, '.' + baseName + '/');
                break;
            case WINDOWS:
                String appdata = System.getenv("APPDATA");
                if (appdata != null)
                    file = new File(appdata, "." + baseName + '/');
                else
                    file = new File(userhome, '.' + baseName + '/');
                break;
            case OSX:
                file = new File(userhome, "Library/Application Support/" + baseName);
                break;
            default:
                file = new File(userhome, baseName + '/');
        }
        return file;
    }

    public static File getLocation() {
        return getWorkingDirectory("minecraft");
    }

    public static boolean is16Folder(String path) {
        path = IOUtils.addSeparator(path);
        return new File(path, "versions").exists();
    }

    public static String minecraft() {
        if (OS.os() == OS.OSX) return "minecraft";
        return ".minecraft";
    }

    public static File getInitGameDir() {
        File gameDir = IOUtils.currentDir();
        if (gameDir.exists()) {
            gameDir = new File(gameDir, MCUtils.minecraft());
            if (!gameDir.exists()) {
                File newFile = MCUtils.getLocation();
                if (newFile.exists()) gameDir = newFile;
            }
        }
        return gameDir;
    }

    public static MinecraftVersion downloadMinecraft(File gameDir, String id, DownloadType sourceType) {
        String vurl = sourceType.getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(gameDir, "versions/" + id);
        File mvt = new File(vpath, id + ".json");
        File mvj = new File(vpath, id + ".jar");
        vpath.mkdirs();
        mvt.delete();
        mvj.delete();

        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
                .addTask(new FileDownloadTask(vurl + id + ".jar", IOUtils.tryGetCanonicalFile(mvj)).setTag(id + ".jar"))
                .start()) {
            MinecraftVersion mv;
            try {
                mv = C.gson.fromJson(FileUtils.readFileToStringQuietly(mvt), MinecraftVersion.class);
            } catch(JsonSyntaxException ex) {
                HMCLog.err("Failed to parse minecraft version json.", ex);
                mv = null;
            }
            return mv;
        }
        return null;
    }

    public static boolean downloadMinecraftJar(File gameDir, String id, DownloadType sourceType) {
        String vurl = sourceType.getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(gameDir, "versions/" + id);
        File mvv = new File(vpath, id + ".jar"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.jar");
            mvv.renameTo(moved);
        }
        File mvt = new File(vpath, id + ".jar");
        vpath.mkdirs();
        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(vurl + id + ".jar", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".jar"))
                .start()) {
            if (moved != null)
                moved.delete();
            return true;
        } else {
            mvt.delete();
            if (moved != null)
                moved.renameTo(mvt);
            return false;
        }
    }

    public static boolean downloadMinecraftVersionJson(File gameDir, String id, DownloadType sourceType) {
        String vurl = sourceType.getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(gameDir, "versions/" + id);
        File mvv = new File(vpath, id + ".json"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.json");
            mvv.renameTo(moved);
        }
        File mvt = new File(vpath, id + ".json");
        vpath.mkdirs();
        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
                .start()) {
            if (moved != null)
                moved.delete();
            return true;
        } else {
            mvt.delete();
            if (moved != null)
                moved.renameTo(mvt);
            return false;
        }
    }

    public static boolean downloadMinecraftAssetsIndex(File assetsLocation, String assetsId, DownloadType sourceType) {
        String aurl = sourceType.getProvider().getIndexesDownloadURL();

        assetsLocation.mkdirs();
        File assetsIndex = new File(assetsLocation, "indexes/" + assetsId + ".json");
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assetsId + "-renamed.json");
            assetsIndex.renameTo(renamed);
        }
        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(aurl + assetsId + ".json", IOUtils.tryGetCanonicalFile(assetsIndex)).setTag(assetsId + ".json"))
                .start()) {
            if (renamed != null)
                renamed.delete();
            return true;
        }
        if (renamed != null)
            renamed.renameTo(assetsIndex);
        return false;
    }

    public static MinecraftRemoteVersions getRemoteMinecraftVersions(DownloadType sourceType) throws IOException {
        String result = NetUtils.doGet(sourceType.getProvider().getVersionsListDownloadURL());
        return MinecraftRemoteVersions.fromJson(result);
    }

    public static String profile = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";

    public static void tryWriteProfile(File gameDir) throws IOException {
        File file = new File(gameDir, "launcher_profiles.json");
        if (!file.exists())
            FileUtils.writeStringToFile(file, profile);
    }
}
