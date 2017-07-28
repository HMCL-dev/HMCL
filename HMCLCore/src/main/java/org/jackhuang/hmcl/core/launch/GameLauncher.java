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
package org.jackhuang.hmcl.core.launch;

import org.jackhuang.hmcl.api.game.LaunchOptions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.game.DecompressLibraryJob;
import org.jackhuang.hmcl.api.event.launch.LaunchEvent;
import org.jackhuang.hmcl.api.event.launch.LaunchSucceededEvent;
import org.jackhuang.hmcl.api.event.launch.LaunchingState;
import org.jackhuang.hmcl.api.event.launch.LaunchingStateChangedEvent;
import org.jackhuang.hmcl.api.event.launch.ProcessingLoginResultEvent;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.RuntimeGameException;
import org.jackhuang.hmcl.api.auth.AuthenticationException;
import org.jackhuang.hmcl.api.auth.LoginInfo;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.core.service.IMinecraftLoader;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.code.Charsets;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.api.Wrapper;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.sys.JavaProcess;
import org.jackhuang.hmcl.util.sys.OS;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.core.download.DownloadLibraryJob;

public abstract class GameLauncher {

    LaunchOptions options;
    IMinecraftService service;
    LoginInfo info;
    UserProfileProvider result;
    IAuthenticator login;

    public GameLauncher(LaunchOptions options, IMinecraftService version, LoginInfo info, IAuthenticator lg) {
        this.options = options;
        this.service = version;
        this.info = info;
        this.login = lg;
    }

    public LaunchOptions getOptions() {
        return options;
    }

    public IMinecraftService getService() {
        return service;
    }

    public LoginInfo getInfo() {
        return info;
    }

    public UserProfileProvider getLoginResult() {
        return result;
    }

    private Object tag;

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    /**
     * Generates the launch command.
     *
     * @throws AuthenticationException having trouble logging in.
     * @throws GameException having trouble completing the game or making lanch command.
     * @throws RuntimeGameException will be thrown when someone processing login result.
     * @see LaunchingStateChangedEvent
     * @see DecompressLibrariesEvent
     * @see LaunchSucceededEvent
     * @see DownloadLibrariesEvent
     * @see ProcessingLoginResultEvent
     */
    public void makeLaunchCommand() throws AuthenticationException, GameException, RuntimeGameException {
        HMCLog.log("Building process");
        HMCLog.log("Logging in...");
        HMCLApi.EVENT_BUS.fireChannel(new LaunchingStateChangedEvent(this, LaunchingState.LoggingIn));
        IMinecraftLoader loader;
        if (info != null)
            result = login.login(info);
        else
            result = login.loginBySettings();
        if (result == null)
            throw new AuthenticationException("Result can not be null.");
        Wrapper<UserProfileProvider> loginInfo = new Wrapper<>(result);
        HMCLApi.EVENT_BUS.fireChannel(new ProcessingLoginResultEvent(this, loginInfo));
        result = loginInfo.getValue();

        HMCLApi.EVENT_BUS.fireChannel(new LaunchingStateChangedEvent(this, LaunchingState.GeneratingLaunchingCodes));
        loader = service.launch(options, result);

        File file = service.version().getDecompressNativesToLocation(loader.getMinecraftVersion());
        if (file != null)
            FileUtils.cleanDirectoryQuietly(file);

        if (!options.isNotCheckGame()) {
            HMCLog.log("Detecting libraries...");
            HMCLApi.EVENT_BUS.fireChannel(new LaunchingStateChangedEvent(this, LaunchingState.DownloadingLibraries));
            if (!downloadLibraries(service.download().getDownloadLibraries(loader.getMinecraftVersion())))
                throw new GameException("Failed to download libraries");
        }

        HMCLog.log("Unpacking natives...");
        HMCLApi.EVENT_BUS.fireChannel(new LaunchingStateChangedEvent(this, LaunchingState.DecompressingNatives));
        DecompressLibraryJob job = service.version().getDecompressLibraries(loader.getMinecraftVersion());
        if (!decompressLibraries(job))
            throw new GameException("Failed to decompress natives");

        HMCLApi.EVENT_BUS.fireChannel(new LaunchSucceededEvent(this, loader.makeLaunchingCommand()));
    }
    
    public abstract boolean downloadLibraries(List<DownloadLibraryJob> jobs);
    
    public abstract boolean decompressLibraries(DecompressLibraryJob job);

    /**
     * Launch the game "as soon as possible".
     *
     * @param str launch command
     * @throws IOException failed creating process
     */
    public void launch(List<String> str) throws IOException {
        if (!service.version().onLaunch(options.getLaunchVersion()))
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
        String s = StrUtils.makeCommand(str);
        s = s.replace(result.getAccessToken(), "<access token>");
        s = s.replace(result.getSession(), "<session>");
        s = s.replace(result.getUserId(), "<uuid>");
        HMCLog.log(s);
        ProcessBuilder builder = new ProcessBuilder(str);
        if (options.getLaunchVersion() == null || service.baseDirectory() == null)
            throw new Error("Fucking bug!");
        builder.directory(service.version().getRunDirectory(options.getLaunchVersion()))
                .environment().put("APPDATA", service.baseDirectory().getAbsolutePath());
        JavaProcess jp = new JavaProcess(str, builder.start());
        HMCLog.log("Have started the process");
        HMCLApi.EVENT_BUS.fireChannel(new LaunchEvent(this, jp));
    }

    /**
     * According to the name...
     *
     * @param launcherName the name of launch bat/sh
     * @param str launch command
     *
     * @return launcher location
     *
     * @throws java.io.IOException write contents failed.
     */
    public File makeLauncher(String launcherName, List<String> str) throws IOException {
        HMCLog.log("Making shell launcher...");
        service.version().onLaunch(options.getLaunchVersion());
        boolean isWin = OS.os() == OS.WINDOWS;
        File f = new File(launcherName + (isWin ? ".bat" : ".sh"));
        if (!f.exists() && !f.createNewFile())
            throw new IOException("Script file " + f.getAbsolutePath() + " does not exist but cannot be created.");
        BufferedWriter writer;
        try (FileOutputStream fos = FileUtils.openOutputStream(f)) {
            writer = new BufferedWriter(new OutputStreamWriter(fos, Charsets.toCharset()));
            if (isWin) {
                writer.write("@echo off");
                writer.newLine();
                writer.write("set appdata=" + service.baseDirectory().getAbsolutePath());
                writer.newLine();
                writer.write("cd /D %appdata%");
                writer.newLine();
            }
            if (StrUtils.isNotBlank(options.getPrecalledCommand())) {
                writer.write(options.getPrecalledCommand());
                writer.newLine();
            }
            writer.write(StrUtils.makeCommand(str));
            writer.close();
        }
        if (!f.setExecutable(true))
            throw new IOException(C.i18n("launch.failed_sh_permission"));

        HMCLog.log("Command: " + StrUtils.parseParams("", str, " "));
        return f;
    }

}
