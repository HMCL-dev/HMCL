/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.jfoenix.concurrency.JFXUtilities;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.util.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

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
                Logging.stop();
                ClassLoader now = new URLClassLoader(new URL[]{jar.toURI().toURL()}, ClassLoader.getSystemClassLoader().getParent());
                Thread.currentThread().setContextClassLoader(now);
                now.loadClass(mainClass).getMethod("main", String[].class).invoke(null, new Object[]{al.toArray(new String[0])});
            } finally {
                Logging.start(Launcher.LOG_DIRECTORY);
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
                    Map<String, String> m = Constants.GSON.fromJson(FileUtils.readText(f), new TypeToken<Map<String, String>>() {
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
                Logging.LOG.log(Level.SEVERE, "Unable to execute newer version application", t);
                AppDataUpgraderPackGzTask.HMCL_VER_FILE.delete(); // delete version json, let HMCL re-download the newer version.
            }
    }

    @Override
    public void download(UpdateChecker checker, VersionNumber ver) {
        if (!(ver instanceof IntVersionNumber))
            return;
        IntVersionNumber version = (IntVersionNumber) ver;
        checker.requestDownloadLink().then(Task.of(variables -> {
            Map<String, String> map = variables.get(UpdateChecker.REQUEST_DOWNLOAD_LINK_ID);

            if (map != null && map.containsKey("jar") && !StringUtils.isBlank(map.get("jar")))
                try {
                    String hash = null;
                    if (map.containsKey("jarsha1"))
                        hash = map.get("jarsha1");
                    Task task = new AppDataUpgraderJarTask(NetworkUtils.toURL(map.get("jar")), version.toString(), hash);
                    TaskExecutor executor = task.executor();
                    AtomicReference<Region> region = new AtomicReference<>();
                    JFXUtilities.runInFX(() -> region.set(Controllers.taskDialog(executor, i18n("message.downloading"), "", null)));
                    if (executor.test()) {
                        new ProcessBuilder(JavaVersion.fromCurrentEnvironment().getBinary().getAbsolutePath(), "-jar", AppDataUpgraderJarTask.getSelf(version.toString()).getAbsolutePath())
                                .directory(new File("").getAbsoluteFile()).start();
                        System.exit(0);
                    }
                    JFXUtilities.runInFX(() -> region.get().fireEvent(new DialogCloseEvent()));
                } catch (IOException ex) {
                    Logging.LOG.log(Level.SEVERE, "Failed to create upgrader", ex);
                }
            else if (map != null && map.containsKey("pack") && !StringUtils.isBlank(map.get("pack")))
                try {
                    String hash = null;
                    if (map.containsKey("packsha1"))
                        hash = map.get("packsha1");
                    Task task = new AppDataUpgraderPackGzTask(NetworkUtils.toURL(map.get("pack")), version.toString(), hash);
                    TaskExecutor executor = task.executor();
                    AtomicReference<Region> region = new AtomicReference<>();
                    JFXUtilities.runInFX(() -> region.set(Controllers.taskDialog(executor, i18n("message.downloading"), "", null)));
                    if (executor.test()) {
                        new ProcessBuilder(JavaVersion.fromCurrentEnvironment().getBinary().getAbsolutePath(), "-jar", AppDataUpgraderPackGzTask.getSelf(version.toString()).getAbsolutePath())
                                .directory(new File("").getAbsoluteFile()).start();
                        System.exit(0);
                    }
                    JFXUtilities.runInFX(() -> region.get().fireEvent(new DialogCloseEvent()));
                } catch (IOException ex) {
                    Logging.LOG.log(Level.SEVERE, "Failed to create upgrader", ex);
                }
            else {
                String url = Launcher.PUBLISH;
                if (map != null)
                    if (map.containsKey(OperatingSystem.CURRENT_OS.getCheckedName()))
                        url = map.get(OperatingSystem.CURRENT_OS.getCheckedName());
                    else if (map.containsKey(OperatingSystem.UNKNOWN.getCheckedName()))
                        url = map.get(OperatingSystem.UNKNOWN.getCheckedName());
                try {
                    java.awt.Desktop.getDesktop().browse(new URI(url));
                } catch (URISyntaxException | IOException e) {
                    Logging.LOG.log(Level.SEVERE, "Failed to browse uri: " + url, e);
                    OperatingSystem.setClipboard(url);
                    MessageBox.show(i18n("update.no_browser"));
                }
            }
        })).start();
    }

    public static class AppDataUpgraderPackGzTask extends Task {

        public static final File BASE_FOLDER = Launcher.HMCL_DIRECTORY;
        public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

        public static File getSelf(String ver) {
            return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
        }

        private final URL downloadLink;
        private final String newestVersion, hash;
        File tempFile;

        public AppDataUpgraderPackGzTask(URL downloadLink, String newestVersion, String hash) throws IOException {
            this.downloadLink = downloadLink;
            this.newestVersion = newestVersion;
            this.hash = hash;
            tempFile = File.createTempFile("hmcl", ".pack.gz");

            setName("Upgrade");
        }

        @Override
        public Collection<Task> getDependents() {
            return Collections.singleton(new FileDownloadTask(downloadLink, tempFile, Proxy.NO_PROXY, new IntegrityCheck("SHA-1", hash)));
        }

        @Override
        public void execute() throws Exception {
            HashMap<String, String> json = new HashMap<>();
            File f = getSelf(newestVersion);
            if (!FileUtils.makeDirectory(f.getParentFile()))
                throw new IOException("Failed to make directories: " + f.getParent());

            for (int i = 0; f.exists() && !f.delete(); i++)
                f = new File(BASE_FOLDER, "HMCL-" + newestVersion + (i > 0 ? "-" + i : "") + ".jar");
            if (!f.createNewFile())
                throw new IOException("Failed to create new file: " + f);

            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(f))) {
                Pack200.newUnpacker().unpack(new GZIPInputStream(new FileInputStream(tempFile)), jos);
            }
            json.put("ver", newestVersion);
            json.put("loc", f.getAbsolutePath());
            String result = Constants.GSON.toJson(json);
            FileUtils.writeText(HMCL_VER_FILE, result);
        }

    }

    public static class AppDataUpgraderJarTask extends Task {

        public static final File BASE_FOLDER = OperatingSystem.getWorkingDirectory("hmcl");
        public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

        public static File getSelf(String ver) {
            return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
        }

        private final URL downloadLink;
        private final String newestVersion, hash;
        File tempFile;

        public AppDataUpgraderJarTask(URL downloadLink, String newestVersion, String hash) throws IOException {
            this.downloadLink = downloadLink;
            this.newestVersion = newestVersion;
            this.hash = hash;
            tempFile = File.createTempFile("hmcl", ".jar");

            setName("Upgrade");
        }

        @Override
        public Collection<Task> getDependents() {
            return Collections.singleton(new FileDownloadTask(downloadLink, tempFile, Proxy.NO_PROXY, new IntegrityCheck("SHA-1", hash)));
        }

        @Override
        public void execute() throws Exception {
            HashMap<String, String> json = new HashMap<>();
            File f = getSelf(newestVersion);
            FileUtils.copyFile(tempFile, f);
            json.put("ver", newestVersion);
            json.put("loc", f.getAbsolutePath());
            String result = Constants.GSON.toJson(json);
            FileUtils.writeText(HMCL_VER_FILE, result);
        }

    }
}
