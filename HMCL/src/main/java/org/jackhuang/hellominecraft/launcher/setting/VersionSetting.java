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
package org.jackhuang.hellominecraft.launcher.setting;

import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.util.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.Utils;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.system.Java;
import org.jackhuang.hellominecraft.util.system.JdkVersion;

/**
 *
 * @author huangyuhui
 */
public class VersionSetting {

    public transient String id;

    private String javaArgs, minecraftArgs, maxMemory, permSize, width, height;
    private String javaDir, precalledCommand, serverIp, java, wrapper;
    private boolean fullscreen, noJVMArgs;

    /**
     * 0 - Close the launcher when the game starts.<br/>
     * 1 - Hide the launcher when the game starts.<br/>
     * 2 - Keep the launcher open.<br/>
     */
    private int launcherVisibility;

    /**
     * 0 - .minecraft<br/>
     * 1 - .minecraft/versions/&lt;version&gt;/<br/>
     */
    private int gameDirType;

    public transient final EventHandler<String> propertyChanged = new EventHandler<>(this);

    public VersionSetting() {
        fullscreen = false;
        launcherVisibility = 1;
        gameDirType = 0;
        javaDir = java = minecraftArgs = serverIp = precalledCommand = wrapper = "";
    }

    public VersionSetting(VersionSetting v) {
        this();
        if (v == null)
            return;
        maxMemory = v.maxMemory;
        width = v.width;
        height = v.height;
        java = v.java;
        fullscreen = v.fullscreen;
        javaArgs = v.javaArgs;
        javaDir = v.javaDir;
        minecraftArgs = v.minecraftArgs;
        permSize = v.permSize;
        gameDirType = v.gameDirType;
        noJVMArgs = v.noJVMArgs;
        launcherVisibility = v.launcherVisibility;
        precalledCommand = v.precalledCommand;
        wrapper = v.wrapper;
        serverIp = v.serverIp;
    }

    public String getJavaDir() {
        Java j = getJava();
        if (j.getHome() == null)
            return javaDir;
        else
            return j.getJava();
    }

    public String getSettingsJavaDir() {
        return javaDir;
    }

    public File getJavaDirFile() {
        return new File(getJavaDir());
    }

    public void setJavaDir(String javaDir) {
        this.javaDir = javaDir;
        propertyChanged.execute("javaDir");
    }

    public Java getJava() {
        return Java.JAVA.get(getJavaIndexInAllJavas());
    }

    public int getJavaIndexInAllJavas() {
        if (StrUtils.isBlank(java) && StrUtils.isNotBlank(javaDir))
            java = "Custom";
        int idx = Java.JAVA.indexOf(new Java(java, null));
        if (idx == -1) {
            java = Java.suggestedJava().getName();
            idx = 0;
        }
        return idx;
    }

    public void setJava(Java java) {
        if (java == null)
            this.java = Java.JAVA.get(0).getName();
        else {
            int idx = Java.JAVA.indexOf(java);
            if (idx == -1)
                return;
            this.java = java.getName();
        }
        propertyChanged.execute("java");
    }

    public String getJavaArgs() {
        if (StrUtils.isBlank(javaArgs))
            return "";
        return javaArgs;
    }

    public void setJavaArgs(String javaArgs) {
        this.javaArgs = javaArgs;
        propertyChanged.execute("javaArgs");
    }

    public boolean hasJavaArgs() {
        return StrUtils.isNotBlank(getJavaArgs().trim());
    }

    public String getMaxMemory() {
        if (StrUtils.isBlank(maxMemory))
            return String.valueOf(Utils.getSuggestedMemorySize());
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
        propertyChanged.execute("maxMemory");
    }

    public String getWidth() {
        if (StrUtils.isBlank(width))
            return "854";
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
        propertyChanged.execute("width");
    }

    public String getHeight() {
        if (StrUtils.isBlank(height))
            return "480";
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
        propertyChanged.execute("height");
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        propertyChanged.execute("fullscreen");
    }

    public LauncherVisibility getLauncherVisibility() {
        return LauncherVisibility.values()[launcherVisibility];
    }

    public void setLauncherVisibility(LauncherVisibility launcherVisibility) {
        this.launcherVisibility = launcherVisibility.ordinal();
        propertyChanged.execute("launcherVisibility");
    }

    public GameDirType getGameDirType() {
        if (gameDirType < 0 || gameDirType > 1)
            setGameDirType(GameDirType.ROOT_FOLDER);
        return GameDirType.values()[gameDirType];
    }

    public void setGameDirType(GameDirType gameDirType) {
        this.gameDirType = gameDirType.ordinal();
        propertyChanged.execute("gameDirType");
    }

    public String getPermSize() {
        return permSize;
    }

    public void setPermSize(String permSize) {
        this.permSize = permSize;
        propertyChanged.execute("permSize");
    }

    public boolean isNoJVMArgs() {
        return noJVMArgs;
    }

    public void setNoJVMArgs(boolean noJVMArgs) {
        this.noJVMArgs = noJVMArgs;
        propertyChanged.execute("noJVMArgs");
    }

    public String getMinecraftArgs() {
        return minecraftArgs;
    }

    public void setMinecraftArgs(String minecraftArgs) {
        this.minecraftArgs = minecraftArgs;
        propertyChanged.execute("minecraftArgs");
    }

    public String getPrecalledCommand() {
        return precalledCommand;
    }

    public void setPrecalledCommand(String precalledCommand) {
        this.precalledCommand = precalledCommand;
        propertyChanged.execute("precalledCommand");
    }

    public String getWrapper() {
        return wrapper;
    }

    public void setWrapper(String wrapper) {
        this.wrapper = wrapper;
        propertyChanged.execute("wrapper");
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
        propertyChanged.execute("serverIp");
    }

    public LaunchOptions createLaunchOptions(File gameDir) {
        LaunchOptions x = new LaunchOptions();
        x.setFullscreen(isFullscreen());
        x.setWrapper(getWrapper());
        x.setGameDir(gameDir);
        x.setGameDirType(getGameDirType());
        x.setHeight(getHeight());
        x.setJavaArgs(getJavaArgs());
        x.setLaunchVersion(id);
        x.setMaxMemory(getMaxMemory());
        x.setMinecraftArgs(getMinecraftArgs());
        x.setName(Main.shortTitle());
        x.setType(Main.shortTitle());
        x.setVersionName(Main.shortTitle());
        x.setNoJVMArgs(isNoJVMArgs());
        x.setPermSize(getPermSize());
        x.setPrecalledCommand(getPrecalledCommand());
        x.setProxyHost(Settings.getInstance().getProxyHost());
        x.setProxyPort(Settings.getInstance().getProxyPort());
        x.setProxyUser(Settings.getInstance().getProxyUserName());
        x.setProxyPass(Settings.getInstance().getProxyPassword());
        x.setServerIp(getServerIp());
        x.setWidth(getWidth());

        String str = getJavaDir();
        if (!getJavaDirFile().exists()) {
            HMCLog.err(C.i18n("launch.wrong_javadir"));
            setJava(null);
            str = getJavaDir();
        }
        JdkVersion jv = new JdkVersion(str);
        if (Settings.getInstance().getJava().contains(jv))
            jv = Settings.getInstance().getJava().get(Settings.getInstance().getJava().indexOf(jv));
        else
            try {
                jv = JdkVersion.getJavaVersionFromExecutable(str);
                Settings.getInstance().getJava().add(jv);
                Settings.save();
            } catch (IOException ex) {
                HMCLog.warn("Failed to get java version", ex);
                jv = null;
            }
        x.setJava(jv);
        x.setJavaDir(str);
        return x;
    }
}
