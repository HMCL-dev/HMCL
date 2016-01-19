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
package org.jackhuang.hellominecraft.launcher.core.launch;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftLoader;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
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
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.Profile;
import org.jackhuang.hellominecraft.launcher.core.auth.AuthenticationException;
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
    IMinecraftService provider;
    LoginInfo info;
    UserProfileProvider result;
    IAuthenticator login;
    public final EventHandler<List<DownloadLibraryJob>> downloadLibrariesEvent = new EventHandler(this);
    public final EventHandler<List<String>> successEvent = new EventHandler(this);
    public final EventHandler<JavaProcess> launchEvent = new EventHandler(this);
    public final EventHandler<DecompressLibraryJob> decompressNativesEvent = new EventHandler(this);

    public GameLauncher(Profile version, LoginInfo info, IAuthenticator lg) {
        this.get = version;
        this.provider = get.service();
        this.info = info;
        this.login = lg;
    }

    public Profile getProfile() {
        return get;
    }

    public IMinecraftLoader makeLaunchCommand() throws AuthenticationException, GameException {
        HMCLog.log("Logging in...");
        IMinecraftLoader loader;
        if (info != null)
            result = login.login(info);
        else
            result = login.loginBySettings();
        if (result == null)
            throw new AuthenticationException("Result can not be null.");
        PluginManager.NOW_PLUGIN.onProcessingLoginResult(result);

        loader = provider.version().provideMinecraftLoader(result);

        File file = provider.version().getDecompressNativesToLocation(loader.getMinecraftVersion());
        if (file != null)
            FileUtils.cleanDirectoryQuietly(file);

        HMCLog.log("Detecting libraries...");
        if (!downloadLibrariesEvent.execute(provider.download().getDownloadLibraries(loader.getMinecraftVersion())))
            throw new GameException("Failed to download libraries");

        HMCLog.log("Unpacking natives...");
        DecompressLibraryJob job = provider.version().getDecompressLibraries(loader.getMinecraftVersion());
        if (!decompressNativesEvent.execute(job))
            throw new GameException("Failed to decompress natives");

        successEvent.execute(loader.makeLaunchingCommand());
        return loader;
    }

    /**
     * Launch the game "as soon as possible".
     *
     * @param str launch command
     *
     * @throws IOException failed creating process
     */
    public void launch(List str) throws IOException {
        if (!provider.version().onLaunch())
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
        if (get == null || provider.version().getSelectedVersion() == null || StrUtils.isBlank(get.getCanonicalGameDir()))
            throw new Error("Fucking bug!");
        builder.directory(provider.version().getRunDirectory(provider.version().getSelectedVersion().id))
            .environment().put("APPDATA", get.getCanonicalGameDir());
        JavaProcess jp = new JavaProcess(str, builder.start(), PROCESS_MANAGER);
        HMCLog.log("The game process have been started");
        launchEvent.execute(jp);
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
        provider.version().onLaunch();
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
