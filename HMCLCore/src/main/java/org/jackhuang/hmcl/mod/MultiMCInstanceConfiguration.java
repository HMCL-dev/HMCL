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
package org.jackhuang.hmcl.mod;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 *
 * @author huangyuhui
 */
public final class MultiMCInstanceConfiguration {

    private final String name; // name
    private final String gameVersion; // IntendedVersion
    private final Integer permGen; // PermGen
    private final String wrapperCommand; // WrapperCommand
    private final String preLaunchCommand; // PreLaunchCommand
    private final String postExitCommand; // PostExitCommand
    private final String notes; // notes
    private final String javaPath; // JavaPath
    private final String jvmArgs; // JvmArgs
    private final boolean fullscreen; // LaunchMaximized
    private final Integer width; // MinecraftWinWidth
    private final Integer height; // MinecraftWinHeight
    private final Integer maxMemory; // MaxMemAlloc
    private final Integer minMemory; // MinMemAlloc
    private final boolean showConsole; // ShowConsole
    private final boolean showConsoleOnError; // ShowConsoleOnError
    private final boolean autoCloseConsole; // AutoCloseConsole
    private final boolean overrideMemory; // OverrideMemory
    private final boolean overrideJavaLocation; // OverrideJavaLocation
    private final boolean overrideJavaArgs; // OverrideJavaArgs
    private final boolean overrideConsole; // OverrideConsole
    private final boolean overrideCommands; // OverrideCommands
    private final boolean overrideWindow; // OverrideWindow

    public MultiMCInstanceConfiguration(String defaultName, InputStream contentStream) throws IOException {
        Properties p = new Properties();
        p.load(contentStream);

        autoCloseConsole = Boolean.parseBoolean(p.getProperty("AutoCloseConsole"));
        gameVersion = p.getProperty("IntendedVersion");
        javaPath = p.getProperty("JavaPath");
        jvmArgs = p.getProperty("JvmArgs");
        fullscreen = Boolean.parseBoolean(p.getProperty("LaunchMaximized"));
        maxMemory = StringUtils.parseInt(p.getProperty("MaxMemAlloc"));
        minMemory = StringUtils.parseInt(p.getProperty("MinMemAlloc"));
        height = StringUtils.parseInt(p.getProperty("MinecraftWinHeight"));
        width = StringUtils.parseInt(p.getProperty("MinecraftWinWidth"));
        overrideCommands = Boolean.parseBoolean(p.getProperty("OverrideCommands"));
        overrideConsole = Boolean.parseBoolean(p.getProperty("OverrideConsole"));
        overrideJavaArgs = Boolean.parseBoolean(p.getProperty("OverrideJavaArgs"));
        overrideJavaLocation = Boolean.parseBoolean(p.getProperty("OverrideJavaLocation"));
        overrideMemory = Boolean.parseBoolean(p.getProperty("OverrideMemory"));
        overrideWindow = Boolean.parseBoolean(p.getProperty("OverrideWindow"));
        permGen = StringUtils.parseInt(p.getProperty("PermGen"));
        postExitCommand = p.getProperty("PostExitCommand");
        preLaunchCommand = p.getProperty("PreLaunchCommand");
        showConsole = Boolean.parseBoolean(p.getProperty("ShowConsole"));
        showConsoleOnError = Boolean.parseBoolean(p.getProperty("ShowConsoleOnError"));
        wrapperCommand = p.getProperty("WrapperCommand");
        name = defaultName;
        notes = Optional.ofNullable(p.getProperty("notes")).orElse("");
    }

    /**
     * The instance's name.
     */
    public String getName() {
        return name;
    }

    /**
     * The game version of the instance.
     */
    public String getGameVersion() {
        return gameVersion;
    }

    /**
     * The permanent generation size of JVM.
     * Nullable.
     */
    public Integer getPermGen() {
        return permGen;
    }

    /**
     * The command to launch JVM.
     */
    public String getWrapperCommand() {
        return wrapperCommand;
    }

    /**
     * The command that will be executed before game launches.
     */
    public String getPreLaunchCommand() {
        return preLaunchCommand;
    }

    /**
     * The command that will be executed after game exits.
     */
    public String getPostExitCommand() {
        return postExitCommand;
    }

    /**
     * The description of the instance.
     */
    public String getNotes() {
        return notes;
    }

    /**
     * JVM installation location.
     */
    public String getJavaPath() {
        return javaPath;
    }

    /**
     * The JVM's arguments
     */
    public String getJvmArgs() {
        return jvmArgs;
    }

    /**
     * True if Minecraft will start in fullscreen mode.
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * The initial width of the game window.
     * Nullable.
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * The initial height of the game window.
     * Nullable.
     */
    public Integer getHeight() {
        return height;
    }

    /**
     * The maximum memory that JVM can allocate.
     * Nullable.
     */
    public Integer getMaxMemory() {
        return maxMemory;
    }

    /**
     * The minimum memory that JVM can allocate.
     * Nullable.
     */
    public Integer getMinMemory() {
        return minMemory;
    }

    /**
     * True if show the console window when game launches.
     */
    public boolean isShowConsole() {
        return showConsole;
    }

    /**
     * True if show the console window when game crashes.
     */
    public boolean isShowConsoleOnError() {
        return showConsoleOnError;
    }

    /**
     * True if closes the console window when game stops.
     */
    public boolean isAutoCloseConsole() {
        return autoCloseConsole;
    }

    /**
     * True if {@link #getMaxMemory}, {@link #getMinMemory}, {@link #getPermGen} will come info force.
     */
    public boolean isOverrideMemory() {
        return overrideMemory;
    }

    /**
     * True if {@link #getJavaPath} will come info force.
     */
    public boolean isOverrideJavaLocation() {
        return overrideJavaLocation;
    }

    /**
     * True if {@link #getJvmArgs} will come info force.
     */
    public boolean isOverrideJavaArgs() {
        return overrideJavaArgs;
    }

    /**
     * True if {@link #isShowConsole}, {@link #isShowConsoleOnError}, {@link #isAutoCloseConsole} will come into force.
     */
    public boolean isOverrideConsole() {
        return overrideConsole;
    }

    /**
     * True if {@link #getPreLaunchCommand}, {@link #getPostExitCommand}, {@link #getWrapperCommand} will come into force.
     */
    public boolean isOverrideCommands() {
        return overrideCommands;
    }

    /**
     * True if {@link #getHeight}, {@link #getWidth}, {@link #isFullscreen} will come into force.
     */
    public boolean isOverrideWindow() {
        return overrideWindow;
    }

    public static Modpack readMultiMCModpackManifest(File f) throws IOException {
        try (ZipFile zipFile = new ZipFile(f)) {
            ZipArchiveEntry firstEntry = zipFile.getEntries().nextElement();
            String name = StringUtils.substringBefore(firstEntry.getName(), '/');
            ZipArchiveEntry entry = zipFile.getEntry(name + "/instance.cfg");
            if (entry == null)
                throw new IOException("`instance.cfg` not found, " + f + " is not a valid MultiMC modpack.");
            MultiMCInstanceConfiguration cfg = new MultiMCInstanceConfiguration(name, zipFile.getInputStream(entry));
            return new Modpack(cfg.getName(), "", "", cfg.getGameVersion(), cfg.getNotes(), cfg);
        }
    }
}
