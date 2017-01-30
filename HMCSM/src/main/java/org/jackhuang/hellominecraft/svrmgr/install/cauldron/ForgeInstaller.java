/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.install.cauldron;

import com.google.gson.Gson;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JOptionPane;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.code.DigestUtils;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.sys.IOUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.task.TaskWindow;
import org.jackhuang.hellominecraft.util.net.FileDownloadTask;
import org.tukaani.xz.XZInputStream;

/**
 *
 * @author huangyuhui
 */
public class ForgeInstaller {

    private final Gson gson = new Gson();
    public File gameDir, gameLibraries;
    public File forgeInstaller;

    public ForgeInstaller(File gameDir, File forgeInstaller) throws IOException {
        this.gameDir = gameDir.getCanonicalFile();
        this.forgeInstaller = forgeInstaller;
    }

    public void install() throws Exception {
        HMCLog.log("Extracting install profiles...");

        ZipFile zipFile = new ZipFile(forgeInstaller);
        ZipEntry entry = zipFile.getEntry("install_profile.json");
        String content = IOUtils.toString(zipFile.getInputStream(entry));
        InstallProfile profile = gson.fromJson(content, InstallProfile.class);

        HMCLog.log("Extracting cauldron server pack..." + profile.install.filePath);

        entry = zipFile.getEntry(profile.install.filePath);
        InputStream is = zipFile.getInputStream(entry);

        //MinecraftLibrary forge = new MinecraftLibrary(profile.install.path);
        //forge.format();
        File file = new File(gameDir, profile.install.filePath);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            int c;
            while ((c = is.read()) != -1)
                bos.write((byte) c);
        }

        File minecraftserver = new File(gameDir, "minecraft_server." + profile.install.minecraft + ".jar");
        if (minecraftserver.exists() && JOptionPane.showConfirmDialog(null, "已发现官方服务端文件，是否要重新下载？") == JOptionPane.YES_OPTION)
            if (!TaskWindow.factory().append(new FileDownloadTask("https://s3.amazonaws.com/Minecraft.Download/versions/{MCVER}/minecraft_server.{MCVER}.jar".replace("{MCVER}", profile.install.minecraft),
                                                                       minecraftserver).setTag("minecraft_server")).execute())
                MessageBox.show("Minecraft官方服务端下载失败！");
        TaskWindow.TaskWindowFactory tw = TaskWindow.factory();
        for (MinecraftLibrary library : profile.versionInfo.libraries) {
            library.init();
            File lib = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
            String libURL = "https://libraries.minecraft.net/";
            if (StrUtils.isNotBlank(library.url))
                libURL = library.url;
            tw.append(new FileDownloadTask(libURL + library.formatted.replace("\\", "/"), lib).setTag(library.name));
        }
        if (!tw.execute())
            MessageBox.show("压缩库下载失败！");

        tw = TaskWindow.factory();
        for (MinecraftLibrary library : profile.versionInfo.libraries) {
            File packxz = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
            if (packxz.exists())
                return;
            File lib = new File(gameDir, "libraries" + File.separator + library.formatted);
            lib.getParentFile().mkdirs();
            String libURL = "https://libraries.minecraft.net/";
            if (StrUtils.isNotBlank(library.url))
                libURL = library.url;
            tw.append(new FileDownloadTask(libURL + library.formatted.replace("\\", "/"), lib).setTag(library.name));
        }
        if (!tw.execute())
            MessageBox.show("库下载失败！");

        ArrayList<String> badLibs = new ArrayList<>();
        for (MinecraftLibrary library : profile.versionInfo.libraries) {
            File lib = new File(gameDir, "libraries" + File.separator + library.formatted);
            File packFile = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
            if (packFile.exists() && packFile.isFile())
                try {
                    unpackLibrary(lib.getParentFile(), IOUtils.toByteArray(FileUtils.openInputStream(packFile)));
                    if (!checksumValid(lib, Arrays.asList(library.checksums)))
                        badLibs.add(library.name);
                } catch (IOException e) {
                    HMCLog.warn("Failed to unpack library: " + library.name);
                    badLibs.add(library.name);
                }
        }
        if (badLibs.size() > 0)
            MessageBox.show("这些库在解压的时候出现了问题" + badLibs.toString());
    }

    public static void unpackLibrary(File output, byte[] data)
        throws IOException {
        if (output.exists())
            output.delete();

        byte[] decompressed = IOUtils.toByteArray(new XZInputStream(new ByteArrayInputStream(data)));

        String end = new String(decompressed, decompressed.length - 4, 4);
        if (!end.equals("SIGN")) {
            HMCLog.warn("Unpacking failed, signature missing " + end);
            return;
        }

        int x = decompressed.length;
        int len = decompressed[(x - 8)] & 0xFF | (decompressed[(x - 7)] & 0xFF) << 8 | (decompressed[(x - 6)] & 0xFF) << 16 | (decompressed[(x - 5)] & 0xFF) << 24;

        byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

        try (FileOutputStream jarBytes = new FileOutputStream(output); JarOutputStream jos = new JarOutputStream(jarBytes)) {

            Pack200.newUnpacker().unpack(new ByteArrayInputStream(decompressed), jos);

            jos.putNextEntry(new JarEntry("checksums.sha1"));
            jos.write(checksums);
            jos.closeEntry();

        }
    }

    private static boolean checksumValid(File libPath, List<String> checksums) {
        try {
            byte[] fileData = IOUtils.toByteArray(FileUtils.openInputStream(libPath));
            boolean valid = (checksums == null) || (checksums.isEmpty()) || (checksums.contains(DigestUtils.sha1Hex(fileData)));
            if ((!valid) && (libPath.getName().endsWith(".jar")))
                valid = validateJar(libPath, fileData, checksums);
            return valid;
        } catch (IOException e) {
            HMCLog.warn("Failed to checksum valid: " + libPath, e);
        }
        return false;
    }

    private static boolean validateJar(File libPath, byte[] data, List<String> checksums) throws IOException {
        System.out.println("Checking \"" + libPath.getAbsolutePath() + "\" internal checksums");

        HashMap<String, String> files = new HashMap<>();
        String[] hashes = null;
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(data))) {
            JarEntry entry = jar.getNextJarEntry();
            while (entry != null) {
                byte[] eData = IOUtils.toByteArray(jar);

                if (entry.getName().equals("checksums.sha1"))
                    hashes = new String(eData, Charset.forName("UTF-8")).split("\n");

                if (!entry.isDirectory())
                    files.put(entry.getName(), DigestUtils.sha1Hex(eData));
                entry = jar.getNextJarEntry();
            }
        }

        if (hashes != null) {
            boolean failed = !checksums.contains(files.get("checksums.sha1"));
            if (failed)
                System.out.println("    checksums.sha1 failed validation");
            else {
                System.out.println("    checksums.sha1 validated successfully");
                for (String hash : hashes)
                    if ((!hash.trim().equals("")) && (hash.contains(" "))) {
                        String[] e = hash.split(" ");
                        String validChecksum = e[0];
                        String target = e[1];
                        String checksum = (String) files.get(target);

                        if ((!files.containsKey(target)) || (checksum == null)) {
                            System.out.println("    " + target + " : missing");
                            failed = true;
                        } else {
                            if (checksum.equals(validChecksum))
                                continue;
                            System.out.println("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
                            failed = true;
                        }
                    }
            }
            if (!failed)
                System.out.println("    Jar contents validated successfully");

            return !failed;
        }

        System.out.println("    checksums.sha1 was not found, validation failed");
        return false;
    }
}
