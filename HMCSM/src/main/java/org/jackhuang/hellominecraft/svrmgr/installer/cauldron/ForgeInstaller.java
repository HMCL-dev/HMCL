/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.installer.cauldron;

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
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
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
	String content = NetUtils.getStreamContent(zipFile.getInputStream(entry));
	InstallProfile profile = gson.fromJson(content, InstallProfile.class);

	HMCLog.log("Extracting cauldron server pack..." + profile.install.filePath);

	entry = zipFile.getEntry(profile.install.filePath);
	InputStream is = zipFile.getInputStream(entry);

	//MinecraftLibrary forge = new MinecraftLibrary(profile.install.path);
	//forge.format();
	File file = new File(gameDir, profile.install.filePath);
	file.getParentFile().mkdirs();
	FileOutputStream fos = new FileOutputStream(file);
	BufferedOutputStream bos = new BufferedOutputStream(fos);
	int c;
	while ((c = is.read()) != -1) {
	    bos.write((byte) c);
	}
	bos.close();
	fos.close();

	File minecraftserver = new File(gameDir, "minecraft_server." + profile.install.minecraft + ".jar");
	TaskWindow tw = TaskWindow.getInstance();
	if(minecraftserver.exists() && JOptionPane.showConfirmDialog(null, "已发现官方服务端文件，是否要重新下载？") == JOptionPane.YES_OPTION) {
            tw.clean();
	    if(!tw.addTask(new FileDownloadTask("https://s3.amazonaws.com/Minecraft.Download/versions/{MCVER}/minecraft_server.{MCVER}.jar".replace("{MCVER}", profile.install.minecraft),
		    minecraftserver).setTag("minecraft_server")).start())
		MessageBox.Show("Minecraft官方服务端下载失败！");
	}
        tw.clean();
	for (MinecraftLibrary library : profile.versionInfo.libraries) {
	    library.init();
	    File lib = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
	    String libURL = "https://libraries.minecraft.net/";
	    if (StrUtils.isNotBlank(library.url)) {
		libURL = library.url;
	    }
	    tw.addTask(new FileDownloadTask(libURL + library.formatted.replace("\\", "/"), lib).setTag(library.name));
	}
	tw.start();
	if(!tw.areTasksFinished())
	    MessageBox.Show("压缩库下载失败！");
        tw.clean();
	for (MinecraftLibrary library : profile.versionInfo.libraries) {
	    File packxz = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
	    if(packxz.exists()) return;
	    File lib = new File(gameDir, "libraries" + File.separator + library.formatted);
	    lib.getParentFile().mkdirs();
	    String libURL = "https://libraries.minecraft.net/";
	    if (StrUtils.isNotBlank(library.url)) {
		libURL = library.url;
	    }
	    tw.addTask(new FileDownloadTask(libURL + library.formatted.replace("\\", "/"), lib).setTag(library.name));
	}
	tw.start();
	if(!tw.areTasksFinished())
	    MessageBox.Show("库下载失败！");
	tw.clean();
	ArrayList<String> badLibs = new ArrayList<String>();
	for (MinecraftLibrary library : profile.versionInfo.libraries) {
	    File lib = new File(gameDir, "libraries" + File.separator + library.formatted);
	    File packFile = new File(gameDir, "libraries" + File.separator + library.formatted + ".pack.xz");
	    if (packFile.exists() && packFile.isFile()) {
		try {
		    unpackLibrary(lib.getParentFile(), NetUtils.getBytesFromStream(FileUtils.openInputStream(packFile)));
		    if(!checksumValid(lib, Arrays.asList(library.checksums)))
			badLibs.add(library.name);
		} catch (IOException e) {
		    HMCLog.warn("Failed to unpack library: " + library.name);
		    badLibs.add(library.name);
		}
	    }
	}
	if (badLibs.size() > 0) {
	    MessageBox.Show("这些库在解压的时候出现了问题" + badLibs.toString());
	}
    }

    public static void unpackLibrary(File output, byte[] data)
	    throws IOException {
	if (output.exists()) {
	    output.delete();
	}

	byte[] decompressed = IOUtils.readFully(new XZInputStream(new ByteArrayInputStream(data)));

	String end = new String(decompressed, decompressed.length - 4, 4);
	if (!end.equals("SIGN")) {
	    HMCLog.warn("Unpacking failed, signature missing " + end);
	    return;
	}

	int x = decompressed.length;
	int len = decompressed[(x - 8)] & 0xFF | (decompressed[(x - 7)] & 0xFF) << 8 | (decompressed[(x - 6)] & 0xFF) << 16 | (decompressed[(x - 5)] & 0xFF) << 24;

	byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

	FileOutputStream jarBytes = new FileOutputStream(output);
	JarOutputStream jos = new JarOutputStream(jarBytes);

	Pack200.newUnpacker().unpack(new ByteArrayInputStream(decompressed), jos);

	jos.putNextEntry(new JarEntry("checksums.sha1"));
	jos.write(checksums);
	jos.closeEntry();

	jos.close();
	jarBytes.close();
    }

    private static boolean checksumValid(File libPath, List<String> checksums) {
	try {
	    byte[] fileData = NetUtils.getBytesFromStream(FileUtils.openInputStream(libPath));
	    boolean valid = (checksums == null) || (checksums.isEmpty()) || (checksums.contains(DigestUtils.sha1Hex(fileData)));
	    if ((!valid) && (libPath.getName().endsWith(".jar"))) {
		valid = validateJar(libPath, fileData, checksums);
	    }
	    return valid;
	} catch (IOException e) {
	    HMCLog.warn("Failed to checksum valid: " + libPath, e);
	}
	return false;
    }

    private static boolean validateJar(File libPath, byte[] data, List<String> checksums) throws IOException {
	System.out.println("Checking \"" + libPath.getAbsolutePath() + "\" internal checksums");

	HashMap<String, String> files = new HashMap<String, String>();
	String[] hashes = null;
	JarInputStream jar = new JarInputStream(new ByteArrayInputStream(data));
	JarEntry entry = jar.getNextJarEntry();
	while (entry != null) {
	    byte[] eData = IOUtils.readFully(jar);

	    if (entry.getName().equals("checksums.sha1")) {
		hashes = new String(eData, Charset.forName("UTF-8")).split("\n");
	    }

	    if (!entry.isDirectory()) {
		files.put(entry.getName(), DigestUtils.sha1Hex(eData));
	    }
	    entry = jar.getNextJarEntry();
	}
	jar.close();

	if (hashes != null) {
	    boolean failed = !checksums.contains(files.get("checksums.sha1"));
	    if (failed) {
		System.out.println("    checksums.sha1 failed validation");
	    } else {
		System.out.println("    checksums.sha1 validated successfully");
		for (String hash : hashes) {
		    if ((!hash.trim().equals("")) && (hash.contains(" "))) {
			String[] e = hash.split(" ");
			String validChecksum = e[0];
			String target = e[1];
			String checksum = (String) files.get(target);

			if ((!files.containsKey(target)) || (checksum == null)) {
			    System.out.println("    " + target + " : missing");
			    failed = true;
			} else {
			    if (checksum.equals(validChecksum)) {
				continue;
			    }
			    System.out.println("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
			    failed = true;
			}
		    }
		}
	    }
	    if (!failed) {
		System.out.println("    Jar contents validated successfully");
	    }

	    return !failed;
	}

	System.out.println("    checksums.sha1 was not found, validation failed");
	return false;
    }
}
