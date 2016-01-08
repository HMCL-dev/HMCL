/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.JavaProcess;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.EventHandler;
import org.jackhuang.hellominecraft.utils.system.ProcessManager;

public class GameLauncher {

    public static final ProcessManager PROCESS_MANAGER = new ProcessManager();
    Profile get;
    IMinecraftProvider provider;
    LoginInfo info;
    UserProfileProvider result;
    IAuthenticator login;
    public final EventHandler<String> failEvent = new EventHandler(this);
    public final EventHandler<List<DownloadLibraryJob>> downloadLibrariesEvent = new EventHandler(this);
    public final EventHandler<List<String>> successEvent = new EventHandler(this);
    public final EventHandler<JavaProcess> launchEvent = new EventHandler(this);
    public final EventHandler<DecompressLibraryJob> decompressNativesEvent = new EventHandler(this);

    public GameLauncher(Profile version, LoginInfo info, IAuthenticator lg) {
        this.get = version;
        this.provider = get.getMinecraftProvider();
        this.info = info;
        this.login = lg;
    }

    public Profile getProfile() {
        return get;
    }

    public IMinecraftLoader makeLaunchCommand() {
        HMCLog.log("Logging in...");
        IMinecraftLoader loader;
        try {
            if (info != null)
                result = login.login(info);
            else
                result = login.loginBySettings();
            if (result == null)
                throw new IllegalStateException("Result can not be null.");
            PluginManager.NOW_PLUGIN.onProcessingLoginResult(result);
        } catch (Throwable e) {
            String error = C.i18n("login.failed") + e.getMessage();
            HMCLog.warn("Login failed by method: " + login.getName(), e);
            failEvent.execute(error);
            return null;
        }

        try {
            loader = provider.provideMinecraftLoader(result);
        } catch (IllegalStateException e) {
            HMCLog.err("Failed to get minecraft loader", e);
            failEvent.execute(C.i18n("launch.circular_dependency_versions"));
            return null;
        } catch (Exception e) {
            failEvent.execute(C.i18n("launch.failed"));
            HMCLog.err("Failed to get minecraft loader", e);
            return null;
        }

        File file = provider.getDecompressNativesToLocation(loader.getMinecraftVersion());
        if (file != null)
            FileUtils.cleanDirectoryQuietly(file);

        HMCLog.log("Detecting libraries...");
        if (!downloadLibrariesEvent.execute(provider.getDownloadService().getDownloadLibraries())) {
            failEvent.execute(C.i18n("launch.failed"));
            return null;
        }

        HMCLog.log("Unpacking natives...");
        if (!decompressNativesEvent.execute(provider.getDecompressLibraries(loader.getMinecraftVersion()))) {
            failEvent.execute(C.i18n("launch.failed"));
            return null;
        }

        List<String> lst;
        try {
            lst = loader.makeLaunchingCommand();
        } catch (Exception e) {
            failEvent.execute(C.i18n("launch.failed"));
            HMCLog.err("Failed to launch game", e);
            return null;
        }
        successEvent.execute(lst);
        return loader;
    }

    /**
     * Launch the game "as soon as possible".
     *
     * @param str launch command
     */
    public void launch(List str) {
        try {
            if (!provider.onLaunch())
                return;
            if (StrUtils.isNotBlank(getProfile().getPrecalledCommand())) {
                Process p = Runtime.getRuntime().exec(getProfile().getPrecalledCommand());
                try {
                    if (p != null && p.isAlive())
                        p.waitFor();
                } catch (InterruptedException ex) {
                    HMCLog.warn("Failed to invoke precalled command", ex);
                }
            }
            HMCLog.log("Starting process");
            ProcessBuilder builder = new ProcessBuilder(str);
            if (get == null || provider.getSelectedVersion() == null || StrUtils.isBlank(get.getCanonicalGameDir()))
                throw new Error("Fucking bug!");
            builder.directory(provider.getRunDirectory(provider.getSelectedVersion().id))
                .environment().put("APPDATA", get.getCanonicalGameDir());
            JavaProcess jp = new JavaProcess(str, builder.start(), PROCESS_MANAGER);
            launchEvent.execute(jp);
        } catch (Exception e) {
            failEvent.execute(C.i18n("launch.failed_creating_process") + "\n" + e.getMessage());
            HMCLog.err("Failed to launch when creating a new process.", e);
        }
    }

    /**
     * According to the name...
     *
     * @param launcherName the name of launch bat/sh
     * @param str          launch command
     *
     * @return launcher location
     *
     * @throws java.io.IOException write contents failed.
     */
    public File makeLauncher(String launcherName, List str) throws IOException {
        HMCLog.log("Making shell launcher...");
        provider.onLaunch();
        boolean isWin = OS.os() == OS.WINDOWS;
        File f = new File(launcherName + (isWin ? ".bat" : ".sh"));
        if (!f.exists())
            f.createNewFile();
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), System.getProperty("sun.jnu.encoding", "UTF-8")));
        } catch (UnsupportedEncodingException ex) {
            HMCLog.warn("Failed to create writer, will try again.", ex);
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
        }
        if (isWin) {
            writer.write("@echo off");
            writer.newLine();
            String appdata = IOUtils.tryGetCanonicalFilePath(get.getCanonicalGameDirFile());
            if (appdata != null) {
                writer.write("set appdata=" + appdata);
                writer.newLine();
            }
        }
        if (StrUtils.isNotBlank(getProfile().getPrecalledCommand())) {
            writer.write(getProfile().getPrecalledCommand());
            writer.newLine();
        }
        writer.write(StrUtils.makeCommand(str));
        writer.close();
        if (!isWin)
            try {
                Runtime.getRuntime().exec("chmod +x " + IOUtils.tryGetCanonicalFilePath(f));
            } catch (IOException e) {
                HMCLog.warn("Failed to give sh file permission.", e);
                MessageBox.Show(C.i18n("launch.failed_sh_permission"));
            }

        HMCLog.log("Command: " + StrUtils.parseParams("", str, " "));
        return f;
    }

    public static class DownloadLibraryJob {

        String url, name;
        File path;

        public DownloadLibraryJob(String n, String u, File p) {
            url = u;
            name = n;
            path = IOUtils.tryGetCanonicalFile(p);
        }
    }

    public static class DecompressLibraryJob {

        File[] decompressFiles;
        String[][] extractRules;
        File decompressTo;

        public DecompressLibraryJob(File[] decompressFiles, String[][] extractRules, File decompressTo) {
            this.decompressFiles = decompressFiles;
            this.extractRules = extractRules;
            this.decompressTo = decompressTo;
        }

    }
}
