/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;

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

    public void changeDirectory(File newDirectory) {
        setBaseDirectory(newDirectory);
        refreshVersionsAsync().start();
    }

    private void clean(File directory) throws IOException {
        FileUtils.deleteDirectory(new File(directory, "crash-reports"));
        FileUtils.deleteDirectory(new File(directory, "logs"));
    }

    public void clean(String id) throws IOException {
        clean(getBaseDirectory());
        clean(getRunDirectory(id));
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

    public File getVersionIconFile(String id) {
        return new File(getVersionRoot(id), "icon.png");
    }

    public Image getVersionIconImage(String id) {
        if (id == null || !isLoaded())
            return newImage("/assets/img/grass.png");

        Version version = getVersion(id).resolve(this);
        File iconFile = getVersionIconFile(id);
        if (iconFile.exists())
            return new Image("file:" + iconFile.getAbsolutePath());
        else if (version.getMainClass() != null &&
                ("net.minecraft.launchwrapper.Launch".equals(version.getMainClass())
                        || version.getMainClass().startsWith("net.fabricmc")
                        || "cpw.mods.modlauncher.Launcher".equals(version.getMainClass())))
            return newImage("/assets/img/furnace.png");
        else
            return newImage("/assets/img/grass.png");
    }

    public boolean saveVersionSetting(String id) {
        if (!versionSettings.containsKey(id))
            return false;
        File file = getVersionSettingFile(id);
        if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
            return false;

        try {
            FileUtils.writeText(file, GSON.toJson(versionSettings.get(id)));
            return true;
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Unable to save version setting of " + id, e);
            return false;
        }
    }

    /**
     * Make version use self version settings instead of the global one.
     * @param id the version id.
     * @return specialized version setting, null if given version does not exist.
     */
    public VersionSetting specializeVersionSetting(String id) {
        VersionSetting vs = getVersionSetting(id);
        if (vs == null)
            vs = createVersionSetting(id);
        if (vs == null)
            return null;
        vs.setUsesGlobal(false);
        return vs;
    }

    public void globalizeVersionSetting(String id) {
        VersionSetting vs = getVersionSetting(id);
        if (vs != null)
            vs.setUsesGlobal(true);
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

    public void markVersionLaunchedAbnormally(String id) {
        try {
            Files.createFile(getVersionRoot(id).toPath().resolve(".abnormal"));
        } catch (IOException ignored) {
        }
    }

    public boolean unmarkVersionLaunchedAbnormally(String id) {
        File file = new File(getVersionRoot(id), ".abnormal");
        boolean result = file.isFile();
        file.delete();
        return result;
    }

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final HashSet<String> FORBIDDEN = new HashSet<>(Arrays.asList("modpack", "minecraftinstance", "manifest"));

    private static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";
}
