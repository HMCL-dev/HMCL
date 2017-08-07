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
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.core.service.IProfile;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.HMCLGameLauncher;
import org.jackhuang.hmcl.util.HMCLMinecraftService;
import java.io.File;
import org.jackhuang.hmcl.core.MCUtils;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.event.EventHandler;
import org.jackhuang.hmcl.api.event.PropertyChangedEvent;

/**
 *
 * @author huangyuhui
 */
public final class Profile implements IProfile {

    @SerializedName("selectedMinecraftVersion")
    private String selectedVersion = "";
    @SerializedName("gameDir")
    private String gameDir;
    @SerializedName("noCommon")
    private boolean noCommon;
    @SerializedName("global")
    private VersionSetting global;

    private transient String name;
    private transient HMCLMinecraftService service = new HMCLMinecraftService(this);
    private transient HMCLGameLauncher launcher = new HMCLGameLauncher(this);
    public transient final EventHandler<PropertyChangedEvent<String>> propertyChanged = new EventHandler<>();

    public Profile() {
        this("Default");
    }

    public Profile(String name) {
        this(name, ".minecraft");
    }

    public Profile(String name, String gameDir) {
        this.name = name;
        this.gameDir = gameDir;
        this.global = new VersionSetting();
    }

    public Profile(String name, Profile v) {
        this();
        if (v == null)
            return;
        this.name = name;
        gameDir = v.gameDir;
    }

    @Override
    public HMCLMinecraftService service() {
        return service;
    }

    public HMCLGameLauncher launcher() {
        return launcher;
    }

    public VersionSetting getSelectedVersionSetting() {
        return getVersionSetting(getSelectedVersion());
    }

    public VersionSetting getVersionSetting(String id) {
        VersionSetting vs = service().getVersionSetting(id);
        if (vs == null || vs.isUsesGlobal()) {
            global.isGlobal = true;
            global.id = id;
            return global;
        } else
            return vs;
    }
    
    public boolean isVersionSettingGlobe(String id) {
        VersionSetting vs = service().getVersionSetting(id);
        return vs == null || vs.isUsesGlobal();
    }

    public void makeVersionSettingSpecial(String id) {
        VersionSetting vs = service().getVersionSetting(id);
        if (vs == null) {
            service().createVersionSetting(id);
            vs = service().getVersionSetting(id);
            if (vs == null)
                return;
            vs.setUsesGlobal(false);
        } else
            vs.setUsesGlobal(false);
        propertyChanged.fire(new PropertyChangedEvent<>(this, "selectedVersion", selectedVersion, selectedVersion));
    }

    public void makeVersionSettingGlobal(String id) {
        VersionSetting vs = service().getVersionSetting(id);
        if (vs == null)
            return;
        vs.setUsesGlobal(true);
        propertyChanged.fire(new PropertyChangedEvent<>(this, "selectedVersion", selectedVersion, selectedVersion));
    }

    @Override
    public String getSelectedVersion() {
        String v = selectedVersion;
        if (StrUtils.isBlank(v) || service().version().getVersionById(v) == null || service().version().getVersionById(v).hidden) {
            MinecraftVersion mv = service().version().getOneVersion(t -> !t.hidden);
            if (mv != null)
                v = mv.id;
            if (StrUtils.isNotBlank(v))
                setSelectedVersion(v);
        }
        return StrUtils.isBlank(v) ? null : v;
    }

    @Override
    public void setSelectedVersion(String newVersion) {
        PropertyChangedEvent event = new PropertyChangedEvent<>(this, "selectedVersion", this.selectedVersion, newVersion);
        this.selectedVersion = newVersion;
        propertyChanged.fire(event);
    }

    @Override
    public File getGameDir() {
        if (StrUtils.isBlank(gameDir))
            gameDir = MCUtils.getInitGameDir().getPath();
        return new File(gameDir.replace('\\', '/'));
    }

    @Override
    public void setGameDir(File gameDir) {
        PropertyChangedEvent event = new PropertyChangedEvent<>(this, "gameDir", this.gameDir, gameDir);
        this.gameDir = gameDir.getPath();
        service().version().refreshVersions();
        propertyChanged.fire(event);
    }

    public boolean isNoCommon() {
        return noCommon;
    }

    public void setNoCommon(boolean noCommon) {
        PropertyChangedEvent event = new PropertyChangedEvent(this, "noCommon", this.noCommon, noCommon);
        this.noCommon = noCommon;
        propertyChanged.fire(event);
    }

    @Override
    public String getName() {
        return name;
    }

    void setName(String name) {
        PropertyChangedEvent event = new PropertyChangedEvent<>(this, "name", this.name, name);
        this.name = name;
        propertyChanged.fire(event);
    }

    @Override
    public void onSelected() {
        service().version().refreshVersions();
    }
}
