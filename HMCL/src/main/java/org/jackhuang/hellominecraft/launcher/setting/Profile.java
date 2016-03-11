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
package org.jackhuang.hellominecraft.launcher.setting;

import org.jackhuang.hellominecraft.launcher.util.HMCLGameLauncher;
import org.jackhuang.hellominecraft.launcher.util.HMCLMinecraftService;
import java.io.File;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;
import org.jackhuang.hellominecraft.launcher.core.MCUtils;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.EventHandler;
import org.jackhuang.hellominecraft.util.system.OS;

/**
 *
 * @author huangyuhui
 */
public final class Profile {

    private String name, selectedMinecraftVersion = "", gameDir;

    private transient IMinecraftService service;
    private transient HMCLGameLauncher launcher = new HMCLGameLauncher(this);
    public transient final EventHandler<String> propertyChanged = new EventHandler<>(this);

    public Profile() {
        this("Default");
    }

    public Profile(String name) {
        this(name, IOUtils.currentDir().getPath());
    }

    public Profile(String name, String gameDir) {
        this.name = name;
        this.gameDir = gameDir;
    }

    public Profile(String name, Profile v) {
        this();
        if (v == null)
            return;
        this.name = name;
        gameDir = v.gameDir;
    }

    public IMinecraftService service() {
        if (service == null)
            service = PluginManager.plugin().provideMinecraftService(this);
        return service;
    }

    public HMCLGameLauncher launcher() {
        return launcher;
    }

    private transient final VersionSetting defaultVersionSetting = new VersionSetting();

    public VersionSetting getSelectedVersionSetting() {
        VersionSetting vs = getVersionSetting(getSelectedVersion());
        if (vs == null)
            vs = defaultVersionSetting;
        return vs;
    }

    public VersionSetting getVersionSetting(String id) {
        return ((HMCLMinecraftService) service()).getVersionSetting(id);
    }

    public String getSettingsSelectedMinecraftVersion() {
        return selectedMinecraftVersion;
    }

    public String getSelectedVersion() {
        String v = selectedMinecraftVersion;
        if (StrUtils.isBlank(v) || service().version().getVersionById(v) == null || service().version().getVersionById(v).hidden) {
            MinecraftVersion mv = service().version().getOneVersion(t -> !t.hidden);
            if (mv != null)
                v = mv.id;
            if (StrUtils.isNotBlank(v))
                setSelectedMinecraftVersion(v);
        }
        return StrUtils.isBlank(v) ? null : v;
    }

    public transient final EventHandler<String> selectedVersionChangedEvent = new EventHandler<>(this);

    public void setSelectedMinecraftVersion(String selectedMinecraftVersion) {
        this.selectedMinecraftVersion = selectedMinecraftVersion;
        propertyChanged.execute("selectedMinecraftVersion");
        selectedVersionChangedEvent.execute(selectedMinecraftVersion);
    }

    public String getGameDir() {
        if (StrUtils.isBlank(gameDir))
            gameDir = MCUtils.getInitGameDir().getPath();
        return IOUtils.addSeparator(gameDir);
    }

    public String getCanonicalGameDir() {
        return IOUtils.tryGetCanonicalFolderPath(getGameDirFile());
    }

    public File getCanonicalGameDirFile() {
        return IOUtils.tryGetCanonicalFile(getGameDirFile());
    }

    public File getGameDirFile() {
        return new File(getGameDir());
    }

    public Profile setGameDir(String gameDir) {
        this.gameDir = gameDir;
        service().version().refreshVersions();
        propertyChanged.execute("gameDir");
        return this;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
        propertyChanged.execute("name");
    }

    public void checkFormat() {
        gameDir = gameDir.replace('/', OS.os().fileSeparator).replace('\\', OS.os().fileSeparator);
    }

    public void onSelected() {
        service().version().refreshVersions();
    }
}
