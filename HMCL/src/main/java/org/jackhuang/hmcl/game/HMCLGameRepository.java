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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackLocalInstallTask;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.ProxyManager;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;

public class HMCLGameRepository extends DefaultGameRepository {
    private final Profile profile;

    // local version settings
    private final Map<String, VersionSetting> localVersionSettings = new HashMap<>();
    private final Set<String> beingModpackVersions = new HashSet<>();

    public HMCLGameRepository(Profile profile, File baseDirectory) {
        super(baseDirectory);
        this.profile = profile;
    }

    public Profile getProfile() {
        return profile;
    }

    @Override
    public GameDirectoryType getGameDirectoryType(String id) {
        if (beingModpackVersions.contains(id) || isModpack(id)) {
            return GameDirectoryType.VERSION_FOLDER;
        } else {
            return getVersionSetting(id).getGameDirType();
        }
    }

    @Override
    public File getRunDirectory(String id) {
        switch (getGameDirectoryType(id)) {
            case VERSION_FOLDER:
                return getVersionRoot(id);
            case ROOT_FOLDER:
                return super.getRunDirectory(id);
            case CUSTOM:
                File dir = new File(getVersionSetting(id).getGameDir());
                if (!FileUtils.isValidPath(dir)) return getVersionRoot(id);
                return dir;
            default:
                throw new Error();
        }
    }

    @Override
    protected void refreshVersionsImpl() {
        localVersionSettings.clear();
        super.refreshVersionsImpl();
        versions.keySet().forEach(this::loadLocalVersionSetting);
        versions.keySet().forEach(version -> {
            if (isModpack(version)) {
                specializeVersionSetting(version);
            }
        });

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

    public void duplicateVersion(String srcId, String dstId, boolean copySaves) throws IOException {
        File srcDir = getVersionRoot(srcId);
        File dstDir = getVersionRoot(dstId);

        if (dstDir.exists()) throw new IOException("Version exists");
        FileUtils.copyDirectory(srcDir.toPath(), dstDir.toPath());
        Files.move(dstDir.toPath().resolve(srcId + ".jar"), dstDir.toPath().resolve(dstId + ".jar"));
        Files.move(dstDir.toPath().resolve(srcId + ".json"), dstDir.toPath().resolve(dstId + ".json"));
        VersionSetting oldVersionSetting = getVersionSetting(srcId).clone();
        GameDirectoryType originalGameDirType = oldVersionSetting.getGameDirType();
        oldVersionSetting.setUsesGlobal(false);
        oldVersionSetting.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        VersionSetting newVersionSetting = initLocalVersionSetting(dstId, oldVersionSetting);
        saveVersionSetting(dstId);

        File srcGameDir = getRunDirectory(srcId);
        File dstGameDir = getRunDirectory(dstId);

        List<String> blackList = new ArrayList<>(Arrays.asList(
                "regex:(.*?)\\.log",
                "usernamecache.json", "usercache.json", // Minecraft
                "launcher_profiles.json", "launcher.pack.lzma", // Minecraft Launcher
                "backup", "pack.json", "launcher.jar", "cache", // HMCL
                ".curseclient", // Curse
                ".fabric", ".mixin.out", // Fabric
                "jars", "logs", "versions", "assets", "libraries", "crash-reports", "NVIDIA", "AMD", "screenshots", "natives", "native", "$native", "server-resource-packs", // Minecraft
                "downloads", // Curse
                "asm", "backups", "TCNodeTracker", "CustomDISkins", "data", "CustomSkinLoader/caches" // Mods
        ));
        blackList.add(srcId + ".jar");
        blackList.add(srcId + ".json");
        if (!copySaves)
            blackList.add("saves");

        if (originalGameDirType != GameDirectoryType.VERSION_FOLDER)
            FileUtils.copyDirectory(srcGameDir.toPath(), dstGameDir.toPath(), path -> Modpack.acceptFile(path, blackList, null));
    }

    private File getLocalVersionSettingFile(String id) {
        return new File(getVersionRoot(id), "hmclversion.cfg");
    }

    private void loadLocalVersionSetting(String id) {
        File file = getLocalVersionSettingFile(id);
        if (file.exists())
            try {
                VersionSetting versionSetting = GSON.fromJson(FileUtils.readText(file), VersionSetting.class);
                initLocalVersionSetting(id, versionSetting);
            } catch (Exception ex) {
                // If [JsonParseException], [IOException] or [NullPointerException] happens, the json file is malformed and needed to be recreated.
                initLocalVersionSetting(id, new VersionSetting());
            }
    }

    /**
     * Create new version setting if version id has no version setting.
     * @param id the version id.
     * @return new version setting, null if given version does not exist.
     */
    public VersionSetting createLocalVersionSetting(String id) {
        if (!hasVersion(id))
            return null;
        if (localVersionSettings.containsKey(id))
            return getLocalVersionSetting(id);
        else
            return initLocalVersionSetting(id, new VersionSetting());
    }

    private VersionSetting initLocalVersionSetting(String id, VersionSetting vs) {
        localVersionSettings.put(id, vs);
        vs.addPropertyChangedListener(a -> saveVersionSetting(id));
        return vs;
    }

    /**
     * Get the version setting for version id.
     *
     * @param id version id
     *
     * @return corresponding version setting, null if the version has no its own version setting.
     */
    public VersionSetting getLocalVersionSetting(String id) {
        if (!localVersionSettings.containsKey(id))
            loadLocalVersionSetting(id);
        VersionSetting setting = localVersionSettings.get(id);
        if (setting != null && isModpack(id))
            setting.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        return setting;
    }

    public VersionSetting getVersionSetting(String id) {
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs == null || vs.isUsesGlobal()) {
            profile.getGlobal().setGlobal(true); // always keep global.isGlobal = true
            profile.getGlobal().setUsesGlobal(true);
            return profile.getGlobal();
        } else
            return vs;
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
                (LibraryAnalyzer.LAUNCH_WRAPPER_MAIN.equals(version.getMainClass())
                        || version.getMainClass().startsWith("net.fabricmc")
                        || LibraryAnalyzer.MOD_LAUNCHER_MAIN.equals(version.getMainClass())))
            return newImage("/assets/img/furnace.png");
        else
            return newImage("/assets/img/grass.png");
    }

    public boolean saveVersionSetting(String id) {
        if (!localVersionSettings.containsKey(id))
            return false;
        File file = getLocalVersionSettingFile(id);
        if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
            return false;

        try {
            FileUtils.writeText(file, GSON.toJson(localVersionSettings.get(id)));
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
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs == null)
            vs = createLocalVersionSetting(id);
        if (vs == null)
            return null;
        vs.setUsesGlobal(false);
        return vs;
    }

    public void globalizeVersionSetting(String id) {
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs != null)
            vs.setUsesGlobal(true);
    }

    public boolean forbidsVersion(String id) {
        return FORBIDDEN.contains(id);
    }

    public LaunchOptions getLaunchOptions(String version, File gameDir, boolean checkJava) throws InterruptedException {
        VersionSetting vs = getVersionSetting(version);

        JavaVersion javaVersion = Optional.ofNullable(vs.getJavaVersion(checkJava)).orElse(JavaVersion.fromCurrentEnvironment());
        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionName(Metadata.TITLE)
                .setVersionType(Metadata.TITLE)
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.getMinecraftArgs()))
                .setJavaArguments(StringUtils.tokenize(vs.getJavaArgs()))
                .setMaxMemory(vs.getMaxMemory())
                .setMinMemory(vs.getMinMemory())
                .setMetaspace(Lang.toIntOrNull(vs.getPermSize()))
                .setWidth(vs.getWidth())
                .setHeight(vs.getHeight())
                .setFullscreen(vs.isFullscreen())
                .setServerIp(vs.getServerIp())
                .setWrapper(vs.getWrapper())
                .setPrecalledCommand(vs.getPreLaunchCommand())
                .setNoGeneratedJVMArgs(vs.isNoJVMArgs())
                .setNativesDirType(vs.getNativesDirType())
                .setNativesDir(vs.getNativesDir());
        if (config().hasProxy()) {
            builder.setProxy(ProxyManager.getProxy());
            if (config().hasProxyAuth()) {
                builder.setProxyUser(config().getProxyUser());
                builder.setProxyPass(config().getProxyPass());
            }
        }

        File json = getModpackConfiguration(version);
        if (json.exists()) {
            try {
                String jsonText = FileUtils.readText(json);
                ModpackConfiguration<?> modpackConfiguration = JsonUtils.GSON.fromJson(jsonText, ModpackConfiguration.class);
                if (McbbsModpackLocalInstallTask.MODPACK_TYPE.equals(modpackConfiguration.getType())) {
                    ModpackConfiguration<McbbsModpackManifest> config = JsonUtils.GSON.fromJson(FileUtils.readText(json), new TypeToken<ModpackConfiguration<McbbsModpackManifest>>() {
                    }.getType());
                    config.getManifest().injectLaunchOptions(builder);
                }
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
            }
        }

        return builder.create();
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
