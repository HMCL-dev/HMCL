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
package org.jackhuang.hmcl.util.upgrade;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.core.MCUtils;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.task.TaskWindow;
import org.jackhuang.hmcl.util.net.FileDownloadTask;
import org.jackhuang.hmcl.util.ui.MessageBox;
import org.jackhuang.hmcl.util.Utils;
import org.jackhuang.hmcl.api.VersionNumber;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.sys.IOUtils;
import org.jackhuang.hmcl.util.sys.Java;
import org.jackhuang.hmcl.util.sys.OS;

/**
 *
 * @author huangyuhui
 */
public class AppDataUpgrader extends IUpgrader {

    private void launchNewerVersion(List<String> args, File jar) throws IOException, ReflectiveOperationException {
        try (JarFile jarFile = new JarFile(jar)) {
            String mainClass = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClass == null)
                throw new ClassNotFoundException("Main-Class not found in manifest");
            ArrayList<String> al = new ArrayList<>(args);
            al.add("--noupdate");
            ClassLoader pre = Thread.currentThread().getContextClassLoader();
            try {
                ClassLoader now = new URLClassLoader(new URL[] { jar.toURI().toURL() }, ClassLoader.getSystemClassLoader().getParent());
                Thread.currentThread().setContextClassLoader(now);
                now.loadClass(mainClass).getMethod("main", String[].class).invoke(null, new Object[] { al.toArray(new String[0]) });
            } finally {
                Thread.currentThread().setContextClassLoader(pre);
            }
        }
    }

    @Override
    public void parseArguments(VersionNumber nowVersion, List<String> args) {
        File f = AppDataUpgraderPackGzTask.HMCL_VER_FILE;
        if (!args.contains("--noupdate"))
            try {
                if (f.exists()) {
                    Map<String, String> m = C.GSON.fromJson(FileUtils.read(f), new TypeToken<Map<String, String>>() {
                    }.getType());
                    String s = m.get("ver");
                    if (s != null && VersionNumber.asVersion(s).compareTo(nowVersion) > 0) {
                        String j = m.get("loc");
                        if (j != null) {
                            File jar = new File(j);
                            if (jar.exists()) {
                                launchNewerVersion(args, jar);
                                System.exit(0);
                            }
                        }
                    }
                }
            } catch (JsonParseException ex) {
                f.delete();
            } catch (IOException | ReflectiveOperationException t) {
                HMCLog.err("Unable to execute newer version application", t);
                AppDataUpgraderPackGzTask.HMCL_VER_FILE.delete(); // delete version json, let HMCL re-download the newer version.
            }
    }

    @Override
    public void accept(SimpleEvent<VersionNumber> event) {
        final VersionNumber version = event.getValue();
        ((UpdateChecker) event.getSource()).requestDownloadLink().reg(map -> {
            boolean flag = false;
            for (Java java : Java.JAVA)
                if (java.getName().startsWith("1.8") || java.getName().startsWith("9") || java.getName().startsWith("10")) {
                    flag = true;
                    break;
                }
            if (!flag) {
                MessageBox.show("请安装 Java 8");
                try {
                    java.awt.Desktop.getDesktop().browse(new URI("https://java.com"));
                } catch (URISyntaxException | IOException e) {
                    Utils.setClipboard("https://java.com");
                    MessageBox.show(C.i18n("update.no_browser"));
                }
            }

            if (MessageBox.show(C.i18n("update.newest_version") + version.toString() + "\n"
                    + C.i18n("update.should_open_link"),
                    MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (map != null && map.containsKey("pack") && !StrUtils.isBlank(map.get("pack")))
                    try {
                        String hash = null;
                        if (map.containsKey("packsha1"))
                            hash = map.get("packsha1");
                        if (TaskWindow.factory().append(new AppDataUpgraderPackGzTask(map.get("pack"), version.toString(), hash)).execute()) {
                            new ProcessBuilder(new String[] { IOUtils.getJavaDir(), "-jar", AppDataUpgraderPackGzTask.getSelf(version.toString()).getAbsolutePath() }).directory(new File("").getAbsoluteFile()).start();
                            System.exit(0);
                        }
                    } catch (IOException ex) {
                        HMCLog.err("Failed to create upgrader", ex);
                    }
                else if (map != null && map.containsKey("jar") && !StrUtils.isBlank(map.get("jar")))
                    try {
                        String hash = null;
                        if (map.containsKey("jarsha1"))
                            hash = map.get("jarsha1");
                        if (TaskWindow.factory().append(new AppDataUpgraderJarTask(map.get("jar"), version.toString(), hash)).execute()) {
                            new ProcessBuilder(new String[] { IOUtils.getJavaDir(), "-jar", AppDataUpgraderJarTask.getSelf(version.toString()).getAbsolutePath() }).directory(new File("").getAbsoluteFile()).start();
                            System.exit(0);
                        }
                    } catch (IOException ex) {
                        Main.LOGGER.log(Level.SEVERE, "Failed to create upgrader", ex);
                    }
                else {
                    String url = C.URL_PUBLISH;
                    if (map != null)
                        if (map.containsKey(OS.os().checkedName))
                            url = map.get(OS.os().checkedName);
                        else if (map.containsKey(OS.UNKNOWN.checkedName))
                            url = map.get(OS.UNKNOWN.checkedName);
                    if (url == null)
                        url = C.URL_PUBLISH;
                    try {
                        java.awt.Desktop.getDesktop().browse(new URI(url));
                    } catch (URISyntaxException | IOException e) {
                        HMCLog.err("Failed to browse uri: " + url, e);
                        Utils.setClipboard(url);
                        MessageBox.show(C.i18n("update.no_browser"));
                    }
                }
        }).execute();
    }

    ;

    public static class AppDataUpgraderPackGzTask extends Task {

        public static final File BASE_FOLDER = MCUtils.getWorkingDirectory("hmcl");
        public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

        public static File getSelf(String ver) {
            return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
        }

        private final String downloadLink, newestVersion, expectedHash;
        File tempFile;

        public AppDataUpgraderPackGzTask(String downloadLink, String newestVersion, String hash) throws IOException {
            this.downloadLink = downloadLink;
            this.newestVersion = newestVersion;
            this.expectedHash = hash;
            tempFile = File.createTempFile("hmcl", ".pack.gz");
        }

        @Override
        public Collection<Task> getDependTasks() {
            return Collections.singleton(new FileDownloadTask(downloadLink, tempFile, expectedHash));
        }

        @Override
        public void executeTask(boolean areDependTasksSucceeded) throws Exception {
            if (!areDependTasksSucceeded) {
                tempFile.delete();
                return;
            }
            HashMap<String, String> json = new HashMap<>();
            File f = getSelf(newestVersion);
            if (!FileUtils.makeDirectory(f.getParentFile()))
                throw new IOException("Failed to make directories: " + f.getParent());

            for (int i = 0; f.exists(); i++)
                f = new File(BASE_FOLDER, "HMCL-" + newestVersion + (i > 0 ? "-" + i : "") + ".jar");
            if (!f.createNewFile())
                throw new IOException("Failed to create new file: " + f);

            try (GZIPInputStream in = new GZIPInputStream(FileUtils.openInputStream(tempFile));
                    JarOutputStream jos = new JarOutputStream(FileUtils.openOutputStream(f))) {
                Pack200.newUnpacker().unpack(in, jos);
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

    public static class AppDataUpgraderJarTask extends Task {

        public static final File BASE_FOLDER = MCUtils.getWorkingDirectory("hmcl");
        public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

        public static File getSelf(String ver) {
            return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
        }

        private final String downloadLink, newestVersion, expectedHash;
        File tempFile;

        public AppDataUpgraderJarTask(String downloadLink, String newestVersion, String hash) throws IOException {
            this.downloadLink = downloadLink;
            this.newestVersion = newestVersion;
            this.expectedHash = hash;
            tempFile = File.createTempFile("hmcl", ".jar");
        }

        @Override
        public Collection<Task> getDependTasks() {
            return Collections.singleton(new FileDownloadTask(downloadLink, tempFile, expectedHash));
        }

        @Override
        public void executeTask(boolean areDependTasksSucceeded) throws Exception {
            if (!areDependTasksSucceeded) {
                tempFile.delete();
                return;
            }
            HashMap<String, String> json = new HashMap<>();
            File f = getSelf(newestVersion);
            FileUtils.copyFile(tempFile, f);
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
