/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.ProxyManager;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.Logging.LOG;

public class HMCLGameRepository extends DefaultGameRepository {
    private final Profile profile;

    // local version settings
    private final Map<String, VersionSetting> localVersionSettings = new HashMap<>();
    private final Set<String> beingModpackVersions = new HashSet<>();

    public final EventManager<Event> onVersionIconChanged = new EventManager<>();

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

    public Stream<Version> getDisplayVersions() {
        return getVersions().stream()
                .filter(v -> !v.isHidden())
                .sorted(Comparator.comparing((Version v) -> v.getReleaseTime() == null ? new Date(0L) : v.getReleaseTime())
                        .thenComparing(v -> VersionNumber.asVersion(v.getId())));
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
            LOG.log(Level.WARNING, "Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }

        // https://github.com/huanghongxun/HMCL/issues/938
        System.gc();
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
        Path srcDir = getVersionRoot(srcId).toPath();
        Path dstDir = getVersionRoot(dstId).toPath();

        Version fromVersion = getVersion(srcId);

        if (Files.exists(dstDir)) throw new IOException("Version exists");
        FileUtils.copyDirectory(srcDir, dstDir);

        Path fromJson = dstDir.resolve(srcId + ".json");
        Path fromJar = dstDir.resolve(srcId + ".jar");
        Path toJson = dstDir.resolve(dstId + ".json");
        Path toJar = dstDir.resolve(dstId + ".jar");

        if (Files.exists(fromJar)) {
            Files.move(fromJar, toJar);
        }
        Files.move(fromJson, toJson);

        FileUtils.writeText(toJson.toFile(), JsonUtils.GSON.toJson(fromVersion.setId(dstId)));
        
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
    @Nullable
    public VersionSetting getLocalVersionSetting(String id) {
        if (!localVersionSettings.containsKey(id))
            loadLocalVersionSetting(id);
        VersionSetting setting = localVersionSettings.get(id);
        if (setting != null && isModpack(id))
            setting.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        return setting;
    }

    @Nullable
    public VersionSetting getLocalVersionSettingOrCreate(String id) {
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs == null) {
            vs = createLocalVersionSetting(id);
        }
        return vs;
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

        VersionSetting vs = getLocalVersionSettingOrCreate(id);
        VersionIconType iconType = Optional.ofNullable(vs).map(VersionSetting::getVersionIcon).orElse(VersionIconType.DEFAULT);

        if (iconType == VersionIconType.DEFAULT) {
            Version version = getVersion(id).resolve(this);
            File iconFile = getVersionIconFile(id);
            if (iconFile.exists())
                return new Image("file:" + iconFile.getAbsolutePath());
            else if (LibraryAnalyzer.isModded(this, version))
                return newImage("/assets/img/furnace.png");
            else
                return newImage("/assets/img/grass.png");
        } else {
            return newImage(iconType.getResourceUrl());
        }
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
            LOG.log(Level.SEVERE, "Unable to save version setting of " + id, e);
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

    public LaunchOptions getLaunchOptions(String version, JavaVersion javaVersion, File gameDir, List<String> javaAgents, boolean makeLaunchScript) {
        VersionSetting vs = getVersionSetting(version);

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionType(Metadata.TITLE)
                .setVersionName(version)
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.getMinecraftArgs()))
                .setJavaArguments(StringUtils.tokenize(vs.getJavaArgs()))
                .setMaxMemory(vs.isNoJVMArgs() && vs.isAutoMemory() ? null : (int)(getAllocatedMemory(
                        vs.getMaxMemory() * 1024L * 1024L,
                        OperatingSystem.getPhysicalMemoryStatus().orElse(OperatingSystem.PhysicalMemoryStatus.INVALID).getAvailable(),
                        vs.isAutoMemory()
                ) / 1024 / 1024))
                .setMinMemory(vs.getMinMemory())
                .setMetaspace(Lang.toIntOrNull(vs.getPermSize()))
                .setWidth(vs.getWidth())
                .setHeight(vs.getHeight())
                .setFullscreen(vs.isFullscreen())
                .setServerIp(vs.getServerIp())
                .setWrapper(vs.getWrapper())
                .setPreLaunchCommand(vs.getPreLaunchCommand())
                .setPostExitCommand(vs.getPostExitCommand())
                .setNoGeneratedJVMArgs(vs.isNoJVMArgs())
                .setNativesDirType(vs.getNativesDirType())
                .setNativesDir(vs.getNativesDir())
                .setProcessPriority(vs.getProcessPriority())
                .setUseSoftwareRenderer(vs.isUseSoftwareRenderer())
                .setUseNativeGLFW(vs.isUseNativeGLFW())
                .setUseNativeOpenAL(vs.isUseNativeOpenAL())
                .setDaemon(!makeLaunchScript && vs.getLauncherVisibility().isDaemon())
                .setJavaAgents(javaAgents);
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
                ModpackProvider provider = ModpackHelper.getProviderByType(modpackConfiguration.getType());
                if (provider != null) provider.injectLaunchOptions(jsonText, builder);
            } catch (IOException | JsonParseException e) {
                e.printStackTrace();
            }
        }

        if (vs.isAutoMemory() && builder.getJavaArguments().stream().anyMatch(it -> it.startsWith("-Xmx")))
            builder.setMaxMemory(null);

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

    private static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";


    // These version ids are forbidden because they may conflict with modpack configuration filenames
    private static final Set<String> FORBIDDEN_VERSION_IDS = new HashSet<>(Arrays.asList(
            "modpack", "minecraftinstance", "manifest"));

    public static boolean isValidVersionId(String id) {
        if (FORBIDDEN_VERSION_IDS.contains(id))
            return false;

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS &&
                FORBIDDEN_VERSION_IDS.contains(id.toLowerCase()))
            return false;

        return OperatingSystem.isNameValid(id);
    }

    /**
     * Returns true if the given version id conflicts with an existing version.
     */
    public boolean versionIdConflicts(String id) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            // on Windows, filenames are case-insensitive
            for (String existingId : versions.keySet()) {
                if (existingId.equalsIgnoreCase(id)) {
                    return true;
                }
            }
            return false;
        } else {
            return versions.containsKey(id);
        }
    }

    public static long getAllocatedMemory(long minimum, long available, boolean auto) {
        if (auto) {
            available -= 384 * 1024 * 1024; // Reserve 384MiB memory for off-heap memory and HMCL itself
            if (available <= 0) {
                return minimum;
            }

            final long threshold = 8L * 1024 * 1024 * 1024;
            final long suggested = Math.min(available <= threshold
                            ? (long) (available * 0.8)
                            : (long) (threshold * 0.8 + (available - threshold) * 0.2),
                    16384L * 1024 * 1024);
            return Math.max(minimum, suggested);
        } else {
            return minimum;
        }
    }
}
