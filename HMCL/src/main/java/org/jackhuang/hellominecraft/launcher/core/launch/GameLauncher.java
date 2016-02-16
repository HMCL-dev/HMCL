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
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.auth.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.core.version.DecompressLibraryJob;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.system.JavaProcess;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.system.OS;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.system.ProcessManager;

public class GameLauncher {

    public static final ProcessManager PROCESS_MANAGER = new ProcessManager();
    LaunchOptions options;
    IMinecraftService service;
    LoginInfo info;
    UserProfileProvider result;
    IAuthenticator login;
    public final EventHandler<List<DownloadLibraryJob>> downloadLibrariesEvent = new EventHandler(this);
    public final EventHandler<List<String>> successEvent = new EventHandler(this);
    public final EventHandler<JavaProcess> launchEvent = new EventHandler(this);
    public final EventHandler<DecompressLibraryJob> decompressNativesEvent = new EventHandler(this);

    public GameLauncher(LaunchOptions options, IMinecraftService version, LoginInfo info, IAuthenticator lg) {
        this.options = options;
        this.service = version;
        this.info = info;
        this.login = lg;
    }

    private Object tag;

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
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
        PluginManager.plugin().onProcessingLoginResult(result);

        loader = service.launch(options, result);

        File file = service.version().getDecompressNativesToLocation(loader.getMinecraftVersion());
        if (file != null)
            FileUtils.cleanDirectoryQuietly(file);

        HMCLog.log("Detecting libraries...");
        if (!downloadLibrariesEvent.execute(service.download().getDownloadLibraries(loader.getMinecraftVersion())))
            throw new GameException("Failed to download libraries");

        HMCLog.log("Unpacking natives...");
        DecompressLibraryJob job = service.version().getDecompressLibraries(loader.getMinecraftVersion());
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
        if (!service.version().onLaunch())
            return;
        if (StrUtils.isNotBlank(options.getPrecalledCommand())) {
            Process p = Runtime.getRuntime().exec(options.getPrecalledCommand());
            try {
                if (p.isAlive())
                    p.waitFor();
            } catch (InterruptedException ex) {
                HMCLog.warn("Failed to invoke precalled command", ex);
            }
        }
        HMCLog.log("Starting process");
        ProcessBuilder builder = new ProcessBuilder(str);
        if (options.getLaunchVersion() == null || service.baseDirectory() == null)
            throw new Error("Fucking bug!");
        builder.redirectErrorStream(true).directory(service.version().getRunDirectory(options.getLaunchVersion()))
            .environment().put("APPDATA", service.baseDirectory().getAbsolutePath());
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
        service.version().onLaunch();
        boolean isWin = OS.os() == OS.WINDOWS;
        File f = new File(launcherName + (isWin ? ".bat" : ".sh"));
        if (!f.exists() && !f.createNewFile())
            HMCLog.warn("Failed to create " + f);
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
            String appdata = IOUtils.tryGetCanonicalFilePath(service.baseDirectory());
            if (appdata != null) {
                writer.write("set appdata=" + appdata);
                writer.newLine();
            }
        }
        if (StrUtils.isNotBlank(options.getPrecalledCommand())) {
            writer.write(options.getPrecalledCommand());
            writer.newLine();
        }
        writer.write(StrUtils.makeCommand(str));
        writer.close();
        if (!f.setExecutable(true)) {
            HMCLog.warn("Failed to give launcher permission.");
            MessageBox.Show(C.i18n("launch.failed_sh_permission"));
        }

        HMCLog.log("Command: " + StrUtils.parseParams("", str, " "));
        return f;
    }

}
