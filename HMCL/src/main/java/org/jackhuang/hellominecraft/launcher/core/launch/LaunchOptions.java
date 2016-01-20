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
package org.jackhuang.hellominecraft.launcher.core.launch;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.system.JdkVersion;

/**
 *
 * @author huangyuhui
 */
public class LaunchOptions {

    private String name, versionName, javaArgs, minecraftArgs, maxMemory, permSize, width, height, userProperties, serverIp;
    private String proxyHost, proxyPort, proxyUser, proxyPass, javaDir, launchVersion;
    private boolean fullscreen, debug, noJVMArgs, canceledWrapper;
    private JdkVersion java;
    private File gameDir;
    private GameDirType gameDirType;

    protected transient IMinecraftService service;

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public File getGameDir() {
        return gameDir;
    }

    public void setGameDir(File gameDir) {
        this.gameDir = gameDir;
    }

    public void setJavaDir(String javaDir) {
        this.javaDir = javaDir;
    }

    public String getJavaDir() {
        return javaDir;
    }

    public JdkVersion getJava() {
        return java;
    }

    public void setJava(JdkVersion java) {
        this.java = java;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaArgs() {
        return javaArgs;
    }

    public void setJavaArgs(String javaArgs) {
        this.javaArgs = javaArgs;
    }

    public boolean hasJavaArgs() {
        return StrUtils.isNotBlank(getJavaArgs().trim());
    }

    public String getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getUserProperties() {
        if (userProperties == null)
            return "";
        return userProperties;
    }

    public void setUserProperties(String userProperties) {
        this.userProperties = userProperties;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public GameDirType getGameDirType() {
        return gameDirType;
    }

    public void setGameDirType(GameDirType gameDirType) {
        this.gameDirType = gameDirType;
    }

    public String getPermSize() {
        return permSize;
    }

    public void setPermSize(String permSize) {
        this.permSize = permSize;
    }

    public boolean isNoJVMArgs() {
        return noJVMArgs;
    }

    public void setNoJVMArgs(boolean noJVMArgs) {
        this.noJVMArgs = noJVMArgs;
    }

    public String getMinecraftArgs() {
        return minecraftArgs;
    }

    public void setMinecraftArgs(String minecraftArgs) {
        this.minecraftArgs = minecraftArgs;
    }

    public boolean isCanceledWrapper() {
        return canceledWrapper;
    }

    public void setCanceledWrapper(boolean canceledWrapper) {
        this.canceledWrapper = canceledWrapper;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPass() {
        return proxyPass;
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass = proxyPass;
    }

    private String precalledCommand;

    public String getPrecalledCommand() {
        return precalledCommand;
    }

    public void setPrecalledCommand(String precalledCommand) {
        this.precalledCommand = precalledCommand;
    }

    public String getLaunchVersion() {
        return launchVersion;
    }

    public void setLaunchVersion(String launchVersion) {
        this.launchVersion = launchVersion;
    }
}
