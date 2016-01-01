/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 *
 * @author huang
 */
public class MCUtils {
    

    private static int a(byte[] paramArrayOfByte1, byte[] paramArrayOfByte2) {
        for (int i = 0; i < paramArrayOfByte1.length - paramArrayOfByte2.length; i++) {
            int j = 1;
            for (int k = 0; k < paramArrayOfByte2.length; k++) {
                if (paramArrayOfByte2[k] == paramArrayOfByte1[(i + k)]) {
                    continue;
                }
                j = 0;
                break;
            }
            if (j != 0) {
                return i;
            }
        }
        return -1;
    }

    private static int a(byte[] paramArrayOfByte, int paramInt) {
        for (; paramInt < paramArrayOfByte.length; paramInt++) {
            if (paramArrayOfByte[paramInt] < 32) {
                return paramInt;
            }
        }
        return -1;
    }

    private static MinecraftVersionRequest getVersionOfOldMinecraft(ZipFile paramZipFile, ZipEntry paramZipEntry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(paramZipFile.getInputStream(paramZipEntry));

        byte[] arrayOfByte = "Minecraft Minecraft ".getBytes("ASCII");
        int j;
        if ((j = a(tmp, arrayOfByte)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + arrayOfByte.length;

        if ((j = a(tmp, i)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        String ver = new String(tmp, i, j - i, "ASCII");
        r.version = ver;

        if (paramZipFile.getEntry("META-INF/MANIFEST.MF") == null) {
            r.type = MinecraftVersionRequest.Modified;
        } else {
            r.type = MinecraftVersionRequest.OK;
        }
        return r;
    }

    private static MinecraftVersionRequest getVersionOfNewMinecraft(ZipFile paramZipFile, ZipEntry paramZipEntry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(paramZipFile.getInputStream(paramZipEntry));

        byte[] arrayOfByte = "-server.txt".getBytes("ASCII");
        int j;
        if ((j = a(tmp, arrayOfByte)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + arrayOfByte.length;

        i += 11;

        if ((j = a(tmp, i)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        String ver = new String(tmp, i, j - i, "ASCII");
        r.version = ver;

        if (paramZipFile.getEntry("META-INF/MANIFEST.MF") == null) {
            r.type = MinecraftVersionRequest.Modified;
        } else {
            r.type = MinecraftVersionRequest.OK;
        }
        return r;
    }

    public static MinecraftVersionRequest minecraftVersion(File paramFile) {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        if (!paramFile.exists()) {
            r.type = MinecraftVersionRequest.NotFound;
            return r;
        }
        if (!paramFile.isFile()) {
            r.type = MinecraftVersionRequest.NotAFile;
            return r;
        }
        if (!paramFile.canRead()) {
            r.type = MinecraftVersionRequest.NotReadable;
            return r;
        }
        ZipFile localZipFile = null;
        try {
            int k;
            localZipFile = new ZipFile(paramFile);
            ZipEntry minecraft = localZipFile
                    .getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null) {
                return getVersionOfOldMinecraft(localZipFile, minecraft);
            }
            ZipEntry main = localZipFile.getEntry("net/minecraft/client/main/Main.class");
            ZipEntry minecraftserver = localZipFile.getEntry("net/minecraft/server/MinecraftServer.class");
            if ((main != null) && (minecraftserver != null)) {
                return getVersionOfNewMinecraft(localZipFile, minecraftserver);
            }
            r.type = MinecraftVersionRequest.Invaild;
            return r;
        } catch (Exception localException) {
            r.type = MinecraftVersionRequest.InvaildJar;
            return r;
        } finally {
            if (localZipFile != null) {
                try {
                    localZipFile.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public static String getGameDir(Version version, String defaultGameDir) {
        String path = version.gameDir;
        if (org.apache.commons.lang3.StringUtils.isBlank(path)) {
            path = defaultGameDir;
        }
        path = IOUtils.addSeparator(path);
        return path;
    }

    public static String getPath(Version version, String lastFolder, String defaultGameDir) {
        String path = getGameDir(version, defaultGameDir);
        File file = new File((new StringBuilder()).append(path).append("versions").append(File.separator).append(version.name).append(File.separator).append(lastFolder).toString());
        if (file.exists() && version.isVer16) {
            return file.getPath();
        }
        file = new File((new StringBuilder()).append(path).append(lastFolder).toString());
        if (file.exists()) {
            return file.getPath();
        } else {
            return null;
        }
    }

    public static String try2GetPath(Version version, String lastFolder, String defaultGameDir) {
        String path = getGameDir(version, defaultGameDir);
        if (version.isVer16) {
            return (new StringBuilder()).append(path).append("versions").append(File.separator).append(version.name).append(File.separator).append(lastFolder).toString();
        } else {
            return (new StringBuilder()).append(path).append(lastFolder).toString();
        }
    }

    public static MinecraftVersion getMinecraftVersion(Version get, String defaultGameDir) {
        try {
            String name = get.name;
            String pa = IOUtils.addSeparator(getGameDir(get, defaultGameDir))
                    + "versions" + File.separator + name + File.separator + name
                    + ".json";
            File file = new File(pa);
            String s = FileUtils.readFileToString(file);
            return new Gson().fromJson(s, MinecraftVersion.class);
        } catch (IOException ex) {
            HMCLLog.err("Failed to get Minecraft Version: " + get, ex);
            return null;
        }
    }

    public static File getLocation() {
        //if (gameDir == null) {
        String localObject = "minecraft";
        String str1 = System.getProperty("user.home", ".");
        File file;
        OS os = OS.os();
        if (os == OS.LINUX) {
            file = new File(str1, '.' + (String) localObject + '/');
        } else if (os == OS.WINDOWS) {
            String str2;
            if ((str2 = System.getenv("APPDATA")) != null) {
                file = new File(str2, "." + (String) localObject + '/');
            } else {
                file = new File(str1, '.' + (String) localObject + '/');
            }
        } else if (os == OS.OSX) {
            file = new File(str1, "Library/Application Support/" + localObject);
        } else {
            file = new File(str1, localObject + '/');
        }
        if (!file.exists() && !file.mkdirs()) {
            throw new RuntimeException("The working directory could not be created: " + localObject);
        }
        return file;
    }

    public static boolean is16Folder(String path) {
        path = IOUtils.addSeparator(path);
        if (new File(path + "versions").exists()) {
            return true;
        }
        return false;
    }

    public static String minecraft() {
        String os = System.getProperty("os.name").trim().toLowerCase();
        if (os.indexOf("mac") != -1) {
            return "minecraft";
        }
        return ".minecraft";
    }
}
