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

import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.util.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.api.EventHandler;
import org.jackhuang.hellominecraft.api.PropertyChangedEvent;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.Java;
import org.jackhuang.hellominecraft.util.sys.JdkVersion;
import org.jackhuang.hellominecraft.util.sys.OS;

/**
 *
 * @author huangyuhui
 */
public class VersionSetting {

    public transient String id;
    public transient boolean isGlobal = false;

    @SerializedName("usesGlobal")
    private boolean usesGlobal;
    @SerializedName("javaArgs")
    private String javaArgs;
    @SerializedName("minecraftArgs")
    private String minecraftArgs;
    @SerializedName("maxMemory")
    private String maxMemory;
    @SerializedName("permSize")
    private String permSize;
    @SerializedName("width")
    private String width;
    @SerializedName("height")
    private String height;
    @SerializedName("javaDir")
    private String javaDir;
    @SerializedName("precalledCommand")
    private String precalledCommand;
    @SerializedName("serverIp")
    private String serverIp;
    @SerializedName("java")
    private String java;
    @SerializedName("wrapper")
    private String wrapper;
    @SerializedName("fullscreen")
    private boolean fullscreen;
    @SerializedName("noJVMArgs")
    private boolean noJVMArgs;
    @SerializedName("notCheckGame")
    private boolean notCheckGame;

    /**
     * 0 - Close the launcher when the game starts.<br/>
     * 1 - Hide the launcher when the game starts.<br/>
     * 2 - Keep the launcher open.<br/>
     */
    @SerializedName("launcherVisibility")
    private int launcherVisibility;

    /**
     * 0 - .minecraft<br/>
     * 1 - .minecraft/versions/&lt;version&gt;/<br/>
     */
    @SerializedName("gameDirType")
    private int gameDirType;

    public transient final EventHandler<PropertyChangedEvent> propertyChanged = new EventHandler<>();

    public VersionSetting() {
        fullscreen = usesGlobal = false;
        launcherVisibility = 1;
        gameDirType = 0;
        javaDir = java = minecraftArgs = serverIp = precalledCommand = wrapper = "";
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
        PropertyChangedEvent event = new PropertyChangedEvent(this, "javaDir", this.javaDir, javaDir);
        this.javaDir = javaDir;
        propertyChanged.fire(event);
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
        PropertyChangedEvent event = new PropertyChangedEvent(this, "java", this.java, java);
        if (java == null)
            this.java = Java.JAVA.get(0).getName();
        else {
            int idx = Java.JAVA.indexOf(java);
            if (idx == -1)
                return;
            this.java = java.getName();
        }
        propertyChanged.fire(event);
    }

    public String getJavaArgs() {
        if (StrUtils.isBlank(javaArgs))
            return "";
        return javaArgs;
    }

    public void setJavaArgs(String javaArgs) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "javaArgs", this.javaArgs, javaArgs);
        this.javaArgs = javaArgs;
        propertyChanged.fire(event);
    }

    public String getMaxMemory() {
        if (StrUtils.isBlank(maxMemory))
            return String.valueOf(OS.getSuggestedMemorySize());
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "maxMemory", this.maxMemory, maxMemory);
        this.maxMemory = maxMemory;
        propertyChanged.fire(event);
    }

    public String getWidth() {
        if (StrUtils.isBlank(width))
            return "854";
        return width;
    }

    public void setWidth(String width) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "width", this.width, width);
        this.width = width;
        propertyChanged.fire(event);
    }

    public String getHeight() {
        if (StrUtils.isBlank(height))
            return "480";
        return height;
    }

    public void setHeight(String height) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "height", this.height, height);
        this.height = height;
        propertyChanged.fire(event);
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "fullscreen", this.fullscreen, fullscreen);
        this.fullscreen = fullscreen;
        propertyChanged.fire(event);
    }

    public LauncherVisibility getLauncherVisibility() {
        return LauncherVisibility.values()[launcherVisibility];
    }

    public void setLauncherVisibility(LauncherVisibility launcherVisibility) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "launcherVisibility", this.launcherVisibility, launcherVisibility);
        this.launcherVisibility = launcherVisibility.ordinal();
        propertyChanged.fire(event);
    }

    public GameDirType getGameDirType() {
        if (gameDirType < 0 || gameDirType > 1)
            setGameDirType(GameDirType.ROOT_FOLDER);
        return GameDirType.values()[gameDirType];
    }

    public void setGameDirType(GameDirType gameDirType) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "gameDirType", this.gameDirType, gameDirType);
        this.gameDirType = gameDirType.ordinal();
        propertyChanged.fire(event);
    }

    public String getPermSize() {
        return permSize;
    }

    public void setPermSize(String permSize) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "permSize", this.permSize, permSize);
        this.permSize = permSize;
        propertyChanged.fire(event);
    }

    public boolean isNoJVMArgs() {
        return noJVMArgs;
    }

    public void setNoJVMArgs(boolean noJVMArgs) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "noJVMArgs", this.noJVMArgs, noJVMArgs);
        this.noJVMArgs = noJVMArgs;
        propertyChanged.fire(event);
    }

    public String getMinecraftArgs() {
        return minecraftArgs;
    }

    public void setMinecraftArgs(String minecraftArgs) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "minecraftArgs", this.minecraftArgs, minecraftArgs);
        this.minecraftArgs = minecraftArgs;
        propertyChanged.fire(event);
    }

    public String getPrecalledCommand() {
        return precalledCommand;
    }

    public void setPrecalledCommand(String precalledCommand) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "precalledCommand", this.precalledCommand, precalledCommand);
        this.precalledCommand = precalledCommand;
        propertyChanged.fire(event);
    }

    public String getWrapper() {
        return wrapper;
    }

    public void setWrapper(String wrapper) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "wrapper", this.wrapper, wrapper);
        this.wrapper = wrapper;
        propertyChanged.fire(event);
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "serverIp", this.serverIp, serverIp);
        this.serverIp = serverIp;
        propertyChanged.fire(event);
    }

    public boolean isNotCheckGame() {
        return notCheckGame;
    }

    public void setNotCheckGame(boolean notCheckGame) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "notCheckGame", this.notCheckGame, notCheckGame);
        this.notCheckGame = notCheckGame;
        propertyChanged.fire(event);
    }

    public boolean isUsesGlobal() {
        return usesGlobal;
    }

    public void setUsesGlobal(boolean usesGlobal) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "usesGlobal", this.usesGlobal, usesGlobal);
        this.usesGlobal = usesGlobal;
        propertyChanged.fire(event);
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
        x.setNotCheckGame(isNotCheckGame());
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
