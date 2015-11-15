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
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.JavaProcess;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
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
    DownloadType downloadType;

    public GameLauncher(Profile version, LoginInfo info, IAuthenticator lg) {
        this(version, info, lg, DownloadType.Mojang);
    }

    public GameLauncher(Profile version, LoginInfo info, IAuthenticator lg, DownloadType downloadType) {
        this.get = version;
        this.provider = get.getMinecraftProvider();
        this.info = info;
        this.login = lg;
        this.downloadType = downloadType;
    }

    public Profile getProfile() {
        return get;
    }

    public IMinecraftLoader makeLaunchCommand() {
        IMinecraftLoader loader;
        try {
            if (info != null)
                result = login.login(info);
            else
                result = login.loginBySettings();
        } catch (Exception e) {
            HMCLog.err("An exception has thrown when logging in.", e);
            result = new UserProfileProvider();
            result.setSuccess(false);
            result.setErrorReason(e.getLocalizedMessage());
        }
        if (result == null || result.isSuccessful() == false) {
            String error;
            if (result == null || result.getErrorReason() == null)
                error = C.i18n("login.failed");
            else {
                error = C.i18n("login.failed") + result.getErrorReason();
                HMCLog.warn("Login failed by method: " + login.getName() + ", state: " + result.isSuccessful() + ", error reason: " + result.getErrorReason());
            }
            failEvent.execute(error);
            return null;
        }

        try {
            loader = provider.provideMinecraftLoader(result, downloadType);
        } catch (IllegalStateException e) {
            HMCLog.err("Failed to get minecraft loader", e);
            failEvent.execute(C.i18n("launch.circular_dependency_versions"));
            return null;
        }

        File file = provider.getDecompressNativesToLocation();
        if (file != null)
            FileUtils.cleanDirectoryQuietly(file);

        if (!downloadLibrariesEvent.execute(provider.getDownloadLibraries(downloadType))) {
            failEvent.execute(C.i18n("launch.failed"));
            return null;
        }
        if (!decompressNativesEvent.execute(provider.getDecompressLibraries())) {
            failEvent.execute(C.i18n("launch.failed"));
            return null;
        }
        successEvent.execute(loader.makeLaunchingCommand());
        return loader;
    }

    /**
     * Launch the game "as soon as possible".
     *
     * @param str launch command
     */
    public void launch(List str) {
        try {
            provider.onLaunch();
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
            if (get == null || get.getSelectedMinecraftVersion() == null || StrUtils.isBlank(get.getCanonicalGameDir()))
                throw new NullPointerException("Fucking bug!");
            builder.directory(provider.getRunDirectory(get.getSelectedMinecraftVersion().id))
            .environment().put("APPDATA", get.getCanonicalGameDir());
            JavaProcess jp = new JavaProcess(str, builder.start(), PROCESS_MANAGER);
            launchEvent.execute(jp);
        } catch (IOException e) {
            failEvent.execute(C.i18n("launch.failed_creating_process") + "\n" + e.getMessage());
            HMCLog.err("Failed to launch when creating a new process.", e);
        }
    }

    /**
     * According to the name...
     *
     * @param launcherName the name of launch bat/sh
     * @param str launch command
     * @return launcher location
     * @throws java.io.IOException write contents failed.
     */
    public File makeLauncher(String launcherName, List str) throws IOException {
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
