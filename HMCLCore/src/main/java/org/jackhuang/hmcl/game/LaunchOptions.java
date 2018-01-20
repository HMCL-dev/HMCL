/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.JavaVersion;

import java.io.File;
import java.io.Serializable;

/**
 *
 * @author huangyuhui
 */
public class LaunchOptions implements Serializable {

    private File gameDir;
    private JavaVersion java;
    private String versionName;
    private String profileName;
    private String minecraftArgs;
    private String javaArgs;
    private Integer minMemory;
    private Integer maxMemory;
    private Integer metaspace;
    private Integer width;
    private Integer height;
    private boolean fullscreen;
    private String serverIp;
    private String wrapper;
    private String proxyHost;
    private String proxyPort;
    private String proxyUser;
    private String proxyPass;
    private boolean noGeneratedJVMArgs;
    private String precalledCommand;

    /**
     * The game directory
     */
    public File getGameDir() {
        return gameDir;
    }

    /**
     * The Java Environment that Minecraft runs on.
     */
    public JavaVersion getJava() {
        return java;
    }

    /**
     * Will shown in the left bottom corner of the main menu of Minecraft.
     * null if use the id of launch version.
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     * Don't know what the hell this is.
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * User custom additional minecraft command line arguments.
     */
    public String getMinecraftArgs() {
        return minecraftArgs;
    }

    /**
     * User custom additional java virtual machine command line arguments.
     */
    public String getJavaArgs() {
        return javaArgs;
    }

    /**
     * The minimum memory that the JVM can allocate.
     */
    public Integer getMinMemory() {
        return minMemory;
    }

    /**
     * The maximum memory that the JVM can allocate.
     */
    public Integer getMaxMemory() {
        return maxMemory;
    }

    /**
     * The maximum metaspace memory that the JVM can allocate.
     * For Java 7 -XX:PermSize and Java 8 -XX:MetaspaceSize
     * Containing class instances.
     */
    public Integer getMetaspace() {
        return metaspace;
    }

    /**
     * The initial game window width
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * The initial game window height
     */
    public Integer getHeight() {
        return height;
    }

    /**
     * Is inital game window fullscreen.
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * The server ip that will connect to when enter game main menu.
     */
    public String getServerIp() {
        return serverIp;
    }

    /**
     * i.e. optirun
     */
    public String getWrapper() {
        return wrapper;
    }

    /**
     * The host of the proxy address
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * the port of the proxy address.
     */
    public String getProxyPort() {
        return proxyPort;
    }

    /**
     * The user name of the proxy, optional.
     */
    public String getProxyUser() {
        return proxyUser;
    }

    /**
     * The password of the proxy, optional
     */
    public String getProxyPass() {
        return proxyPass;
    }

    /**
     * Prevent game launcher from generating default JVM arguments like max memory.
     */
    public boolean isNoGeneratedJVMArgs() {
        return noGeneratedJVMArgs;
    }

    /**
     * Called command line before launching the game.
     */
    public String getPrecalledCommand() {
        return precalledCommand;
    }

    public static class Builder {

        LaunchOptions options = new LaunchOptions();

        public LaunchOptions create() {
            return options;
        }

        public Builder setGameDir(File gameDir) {
            options.gameDir = gameDir;
            return this;
        }

        public Builder setJava(JavaVersion java) {
            options.java = java;
            return this;
        }

        public Builder setVersionName(String versionName) {
            options.versionName = versionName;
            return this;
        }

        public Builder setProfileName(String profileName) {
            options.profileName = profileName;
            return this;
        }

        public Builder setMinecraftArgs(String minecraftArgs) {
            options.minecraftArgs = minecraftArgs;
            return this;
        }

        public Builder setJavaArgs(String javaArgs) {
            options.javaArgs = javaArgs;
            return this;
        }

        public Builder setMinMemory(Integer minMemory) {
            options.minMemory = minMemory;
            return this;
        }

        public Builder setMaxMemory(Integer maxMemory) {
            options.maxMemory = maxMemory;
            return this;
        }

        public Builder setMetaspace(Integer metaspace) {
            options.metaspace = metaspace;
            return this;
        }

        public Builder setWidth(Integer width) {
            options.width = width;
            return this;
        }

        public Builder setHeight(Integer height) {
            options.height = height;
            return this;
        }

        public Builder setFullscreen(boolean fullscreen) {
            options.fullscreen = fullscreen;
            return this;
        }

        public Builder setServerIp(String serverIp) {
            options.serverIp = serverIp;
            return this;
        }

        public Builder setWrapper(String wrapper) {
            options.wrapper = wrapper;
            return this;
        }

        public Builder setProxyHost(String proxyHost) {
            options.proxyHost = proxyHost;
            return this;
        }

        public Builder setProxyPort(String proxyPort) {
            options.proxyPort = proxyPort;
            return this;
        }

        public Builder setProxyUser(String proxyUser) {
            options.proxyUser = proxyUser;
            return this;
        }

        public Builder setProxyPass(String proxyPass) {
            options.proxyPass = proxyPass;
            return this;
        }

        public Builder setNoGeneratedJVMArgs(boolean noGeneratedJVMArgs) {
            options.noGeneratedJVMArgs = noGeneratedJVMArgs;
            return this;
        }

        public Builder setPrecalledCommand(String precalledCommand) {
            options.precalledCommand = precalledCommand;
            return this;
        }

    }
}
