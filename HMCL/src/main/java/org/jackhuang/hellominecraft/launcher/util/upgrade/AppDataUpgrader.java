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
package org.jackhuang.hellominecraft.launcher.util.upgrade;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;
import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.MCUtils;
import org.jackhuang.hellominecraft.util.task.Task;
import org.jackhuang.hellominecraft.util.task.TaskWindow;
import org.jackhuang.hellominecraft.util.net.FileDownloadTask;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.UpdateChecker;
import org.jackhuang.hellominecraft.util.Utils;
import org.jackhuang.hellominecraft.util.VersionNumber;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.sys.IOUtils;
import org.jackhuang.hellominecraft.util.sys.OS;

/**
 *
 * @author huangyuhui
 */
public class AppDataUpgrader extends IUpgrader {

    private boolean launchNewerVersion(String[] args, File jar) throws Exception {
        try (JarFile jarFile = new JarFile(jar)) {
            String mainClass = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass != null) {
                ArrayList<String> al = new ArrayList<>(Arrays.asList(args));
                al.add("--noupdate");
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    new URLClassLoader(new URL[] { jar.toURI().toURL() },
                                       URLClassLoader.getSystemClassLoader().getParent()).loadClass(mainClass)
                        .getMethod("main", String[].class).invoke(null, new Object[] { al.toArray(new String[0]) });
                    return null;
                });
                return true;
            }
        }
        return false;
    }

    @Override
    public void parseArguments(VersionNumber nowVersion, String[] args) {
        if (!ArrayUtils.contains(args, "--noupdate"))
            try {
                File f = AppDataUpgraderTask.HMCL_VER_FILE;
                if (f.exists()) {
                    Map<String, String> m = C.GSON.fromJson(FileUtils.read(f), Map.class);
                    String s = m.get("ver");
                    if (s != null && VersionNumber.check(s).compareTo(nowVersion) > 0) {
                        String j = m.get("loc");
                        if (j != null) {
                            File jar = new File(j);
                            if (jar.exists() && launchNewerVersion(args, jar))
                                System.exit(0);
                        }
                    }
                }
            } catch (Throwable t) {
                HMCLog.err("Failed to execute newer version application", t);
            }
    }

    @Override
    public void accept(SimpleEvent<VersionNumber> event) {
        final VersionNumber version = event.getValue();
        ((UpdateChecker) event.getSource()).requestDownloadLink().reg(map -> {
            if (MessageBox.show(C.i18n("update.newest_version") + version.firstVer + "." + version.secondVer + "." + version.thirdVer + "\n"
                                + C.i18n("update.should_open_link"),
                                MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (map != null && map.containsKey("pack"))
                    try {
                        String hash = null;
                        if (map.containsKey("packsha1"))
                            hash = map.get("packsha1");
                        if (TaskWindow.factory().append(new AppDataUpgraderTask(map.get("pack"), version.version, hash)).execute()) {
                            new ProcessBuilder(new String[] { IOUtils.getJavaDir(), "-jar", AppDataUpgraderTask.getSelf(version.version).getAbsolutePath() }).directory(new File(".")).start();
                            System.exit(0);
                        }
                    } catch (IOException ex) {
                        HMCLog.err("Failed to create upgrader", ex);
                    }
                else {
                    String url = C.URL_PUBLISH;
                    if (map != null)
                        if (map.containsKey(OS.os().checked_name))
                            url = map.get(OS.os().checked_name);
                        else if (map.containsKey(OS.UNKOWN.checked_name))
                            url = map.get(OS.UNKOWN.checked_name);
                    if (url == null)
                        url = C.URL_PUBLISH;
                    try {
                        java.awt.Desktop.getDesktop().browse(new URI(url));
                    } catch (URISyntaxException | IOException e) {
                        HMCLog.warn("Failed to browse uri: " + url, e);
                        Utils.setClipborad(url);
                        MessageBox.show(C.i18n("update.no_browser"));
                    }
                }
        }).execute();
    }

    public static class AppDataUpgraderTask extends Task {

        public static final File BASE_FOLDER = MCUtils.getWorkingDirectory("hmcl");
        public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

        public static File getSelf(String ver) {
            return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
        }

        private final String downloadLink, newestVersion, expectedHash;
        File tempFile;

        public AppDataUpgraderTask(String downloadLink, String newestVersion, String hash) throws IOException {
            this.downloadLink = downloadLink;
            this.newestVersion = newestVersion;
            this.expectedHash = hash;
            tempFile = File.createTempFile("hmcl", ".pack.gz");
        }

        @Override
        public Collection<Task> getDependTasks() {
            return Arrays.asList(new FileDownloadTask(downloadLink, tempFile, expectedHash));
        }

        @Override
        public void executeTask(boolean areDependTasksSucceeded) throws Exception {
            HashMap<String, String> json = new HashMap<>();
            File f = getSelf(newestVersion);
            if (!FileUtils.makeDirectory(f.getParentFile()))
                HMCLog.warn("Failed to make directories: " + f.getParent());

            for (int i = 0; f.exists(); i++)
                f = new File(BASE_FOLDER, "HMCL-" + newestVersion + (i > 0 ? "-" + i : "") + ".jar");
            if (!f.createNewFile())
                HMCLog.warn("Failed to create new file: " + f);

            try (JarOutputStream jos = new JarOutputStream(FileUtils.openOutputStream(f))) {
                Pack200.newUnpacker().unpack(new GZIPInputStream(FileUtils.openInputStream(tempFile)), jos);
            }
            json.put("ver", newestVersion);
            json.put("loc", f.getAbsolutePath());
            String result = C.GSON.toJson(json);
            FileUtils.write(HMCL_VER_FILE, result);
        }

        @Override
        public String getInfo() {
            return "Upgrade";
        }

    }
}
