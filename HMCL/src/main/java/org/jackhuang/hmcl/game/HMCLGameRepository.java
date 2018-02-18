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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class HMCLGameRepository extends DefaultGameRepository {
    private final Profile profile;
    private final Map<String, VersionSetting> versionSettings = new HashMap<>();
    private final Set<String> beingModpackVersions = new HashSet<>();

    public boolean checkedModpack = false, checkingModpack = false;

    public HMCLGameRepository(Profile profile, File baseDirectory) {
        super(baseDirectory);
        this.profile = profile;
    }

    public Profile getProfile() {
        return profile;
    }

    private boolean useSelf(String version, String assetId) {
        VersionSetting vs = profile.getVersionSetting(version);
        return new File(getBaseDirectory(), "assets/indexes/" + assetId + ".json").exists() || vs.isNoCommon();
    }

    @Override
    public File getAssetDirectory(String version, String assetId) {
        if (useSelf(version, assetId))
            return super.getAssetDirectory(version, assetId);
        else
            return new File(Settings.INSTANCE.getCommonPath(), "assets");
    }

    @Override
    public File getRunDirectory(String id) {
        if (beingModpackVersions.contains(id) || isModpack(id))
            return getVersionRoot(id);
        else {
            VersionSetting vs = profile.getVersionSetting(id);
            switch (vs.getGameDirType()) {
                case VERSION_FOLDER: return getVersionRoot(id);
                case ROOT_FOLDER: return super.getRunDirectory(id);
                case CUSTOM: return new File(vs.getGameDir());
                default: throw new Error();
            }
        }
    }

    @Override
    public File getLibraryFile(Version version, Library lib) {
        VersionSetting vs = profile.getVersionSetting(version.getId());
        File self = super.getLibraryFile(version, lib);
        if (self.exists() || vs.isNoCommon())
            return self;
        else
            return new File(Settings.INSTANCE.getCommonPath(), "libraries/" + lib.getPath());
    }


    @Override
    protected void refreshVersionsImpl() {
        versionSettings.clear();
        super.refreshVersionsImpl();
        versions.keySet().forEach(this::loadVersionSetting);

        try {
            File file = new File(getBaseDirectory(), "launcher_profiles.json");
            if (!file.exists() && !versions.isEmpty())
                FileUtils.writeText(file, PROFILE);
        } catch (IOException ex) {
            Logging.LOG.log(Level.WARNING, "Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }
    }

    @Override
    public void refreshVersions() {
        EventBus.EVENT_BUS.fireEvent(new RefreshingVersionsEvent(this));
        refreshVersionsImpl();
        EventBus.EVENT_BUS.fireEvent(new RefreshedVersionsEvent(this));
    }

    public void changeDirectory(File newDirectory) {
        setBaseDirectory(newDirectory);
        refreshVersionsAsync().start();
    }

    private File getVersionSettingFile(String id) {
        return new File(getVersionRoot(id), "hmclversion.cfg");
    }

    private void loadVersionSetting(String id) {
        File file = getVersionSettingFile(id);
        if (file.exists())
            try {
                VersionSetting versionSetting = GSON.fromJson(FileUtils.readText(file), VersionSetting.class);
                initVersionSetting(id, versionSetting);
            } catch (Exception ex) {
                // If [JsonParseException], [IOException] or [NullPointerException] happens, the json file is malformed and needed to be recreated.
                initVersionSetting(id, new VersionSetting());
            }
    }

    /**
     * Create new version setting if version id has no version setting.
     * @param id the version id.
     * @return new version setting, null if given version does not exist.
     */
    public VersionSetting createVersionSetting(String id) {
        if (!hasVersion(id))
            return null;
        if (versionSettings.containsKey(id))
            return getVersionSetting(id);
        else
            return initVersionSetting(id, new VersionSetting());
    }

    private VersionSetting initVersionSetting(String id, VersionSetting vs) {
        vs.addPropertyChangedListener(a -> saveVersionSetting(id));
        versionSettings.put(id, vs);
        return vs;
    }

    /**
     * Get the version setting for version id.
     *
     * @param id version id
     *
     * @return may return null if the id not exists
     */
    public VersionSetting getVersionSetting(String id) {
        if (!versionSettings.containsKey(id))
            loadVersionSetting(id);
        VersionSetting setting = versionSettings.get(id);
        if (setting != null && isModpack(id))
            setting.setGameDirType(EnumGameDirectory.VERSION_FOLDER);
        return setting;
    }

    public File getVersionIcon(String id) {
        return new File(getVersionRoot(id), "icon.png");
    }

    public void saveVersionSetting(String id) {
        if (!versionSettings.containsKey(id))
            return;
        Lang.invoke(() -> FileUtils.writeText(getVersionSettingFile(id), GSON.toJson(versionSettings.get(id))));
    }

    public boolean forbidsVersion(String id) {
        return FORBIDDEN.contains(id);
    }

    @Override
    public File getModpackConfiguration(String version) {
        return new File(getVersionRoot(version), "modpack.cfg");
    }

    public void markVersionAsModpack(String id) {
        beingModpackVersions.add(id);
    }

    public void undoMark(String id) {
        beingModpackVersions.remove(id);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(VersionSetting.class, VersionSetting.Serializer.INSTANCE)
            .create();

    private static final HashSet<String> FORBIDDEN = new HashSet<>(Arrays.asList("modpack", "minecraftinstance", "manifest"));

    private static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";
}
