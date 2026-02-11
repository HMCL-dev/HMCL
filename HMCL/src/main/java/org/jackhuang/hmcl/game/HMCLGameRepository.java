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
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.mod.ModAdviser;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class HMCLGameRepository extends DefaultGameRepository {
    private final Profile profile;

    // local version settings
    private final Map<String, VersionSetting> localVersionSettings = new HashMap<>();
    private final Set<String> beingModpackVersions = new HashSet<>();

    public final EventManager<Event> onVersionIconChanged = new EventManager<>();

    public HMCLGameRepository(Profile profile, Path baseDirectory) {
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
    public Path getRunDirectory(String id) {
        switch (getGameDirectoryType(id)) {
            case VERSION_FOLDER:
                return getVersionRoot(id);
            case ROOT_FOLDER:
                return super.getRunDirectory(id);
            case CUSTOM:
                try {
                    return Path.of(getVersionSetting(id).getGameDir());
                } catch (InvalidPathException ignored) {
                    return getVersionRoot(id);
                }
            default:
                throw new AssertionError("Unreachable");
        }
    }

    public Stream<Version> getDisplayVersions() {
        return getVersions().stream()
                .filter(v -> !v.isHidden())
                .sorted(Comparator.comparing((Version v) -> Lang.requireNonNullElse(v.getReleaseTime(), Instant.EPOCH))
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
            Path file = getBaseDirectory().resolve("launcher_profiles.json");
            if (!Files.exists(file) && !versions.isEmpty()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, PROFILE);
            }
        } catch (IOException ex) {
            LOG.warning("Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex);
        }
    }

    public void changeDirectory(Path newDirectory) {
        setBaseDirectory(newDirectory);
        refreshVersionsAsync().start();
    }

    private void clean(Path directory) throws IOException {
        FileUtils.deleteDirectory(directory.resolve("crash-reports"));
        FileUtils.deleteDirectory(directory.resolve("logs"));
    }

    public void clean(String id) throws IOException {
        clean(getBaseDirectory());
        clean(getRunDirectory(id));
    }

    public void duplicateVersion(String srcId, String dstId, boolean copySaves) throws IOException {
        Path srcDir = getVersionRoot(srcId);
        Path dstDir = getVersionRoot(dstId);

        Version fromVersion = getVersion(srcId);

        List<String> blackList = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        blackList.add(srcId + ".jar");
        blackList.add(srcId + ".json");
        if (!copySaves)
            blackList.add("saves");

        if (Files.exists(dstDir)) throw new IOException("Version exists");

        Files.createDirectories(dstDir);
        FileUtils.copyDirectory(srcDir, dstDir, path -> Modpack.acceptFile(path, blackList, null));

        Path fromJson = srcDir.resolve(srcId + ".json");
        Path fromJar = srcDir.resolve(srcId + ".jar");
        Path toJson = dstDir.resolve(dstId + ".json");
        Path toJar = dstDir.resolve(dstId + ".jar");

        if (Files.exists(fromJar)) {
            Files.copy(fromJar, toJar);
        }
        Files.copy(fromJson, toJson);

        JsonUtils.writeToJsonFile(toJson, fromVersion.setId(dstId).setJar(dstId));

        VersionSetting oldVersionSetting = getVersionSetting(srcId).clone();
        GameDirectoryType originalGameDirType = oldVersionSetting.getGameDirType();
        oldVersionSetting.setUsesGlobal(false);
        oldVersionSetting.setGameDirType(GameDirectoryType.VERSION_FOLDER);
        VersionSetting newVersionSetting = initLocalVersionSetting(dstId, oldVersionSetting);
        saveVersionSetting(dstId);

        Path srcGameDir = getRunDirectory(srcId);
        Path dstGameDir = getRunDirectory(dstId);

        if (originalGameDirType != GameDirectoryType.VERSION_FOLDER)
            FileUtils.copyDirectory(srcGameDir, dstGameDir, path -> Modpack.acceptFile(path, blackList, null));
    }

    private Path getLocalVersionSettingFile(String id) {
        return getVersionRoot(id).resolve("hmclversion.cfg");
    }

    private void loadLocalVersionSetting(String id) {
        Path file = getLocalVersionSettingFile(id);
        if (Files.exists(file))
            try {
                VersionSetting versionSetting = JsonUtils.fromJsonFile(file, VersionSetting.class);
                initLocalVersionSetting(id, versionSetting);
            } catch (Exception ex) {
                // If [JsonParseException], [IOException] or [NullPointerException] happens, the json file is malformed and needed to be recreated.
                initLocalVersionSetting(id, new VersionSetting());
            }
    }

    /**
     * Create new version setting if version id has no version setting.
     *
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
        vs.addListener(a -> saveVersionSetting(id));
        return vs;
    }

    /**
     * Get the version setting for version id.
     *
     * @param id version id
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
            profile.getGlobal().setUsesGlobal(true);
            return profile.getGlobal();
        } else
            return vs;
    }

    public Optional<Path> getVersionIconFile(String id) {
        Path root = getVersionRoot(id);

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = root.resolve("icon." + extension);
            if (Files.exists(file)) {
                return Optional.of(file);
            }
        }

        return Optional.empty();
    }

    public void setVersionIconFile(String id, Path iconFile) throws IOException {
        String ext = FileUtils.getExtension(iconFile).toLowerCase(Locale.ROOT);
        if (!FXUtils.IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported icon file: " + ext);
        }

        deleteIconFile(id);

        FileUtils.copyFile(iconFile, getVersionRoot(id).resolve("icon." + ext));
    }

    public void deleteIconFile(String id) {
        Path root = getVersionRoot(id);
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = root.resolve("icon." + extension);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete icon file: " + file, e);
            }
        }
    }

    public Image getVersionIconImage(String id) {
        if (id == null || !isLoaded())
            return VersionIconType.DEFAULT.getIcon();

        VersionSetting vs = getLocalVersionSettingOrCreate(id);
        VersionIconType iconType = vs != null ? Lang.requireNonNullElse(vs.getVersionIcon(), VersionIconType.DEFAULT) : VersionIconType.DEFAULT;

        if (iconType == VersionIconType.DEFAULT) {
            Version version = getVersion(id).resolve(this);
            Optional<Path> iconFile = getVersionIconFile(id);
            if (iconFile.isPresent()) {
                try {
                    return FXUtils.loadImage(iconFile.get());
                } catch (Exception e) {
                    LOG.warning("Failed to load version icon of " + id, e);
                }
            }

            if (LibraryAnalyzer.isModded(this, version)) {
                LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version, null);
                if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.FABRIC))
                    return VersionIconType.FABRIC.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.QUILT))
                    return VersionIconType.QUILT.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.LEGACY_FABRIC))
                    return VersionIconType.LEGACY_FABRIC.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE))
                    return VersionIconType.NEO_FORGE.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.FORGE))
                    return VersionIconType.FORGE.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.CLEANROOM))
                    return VersionIconType.CLEANROOM.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.LITELOADER))
                    return VersionIconType.CHICKEN.getIcon();
                else if (libraryAnalyzer.has(LibraryAnalyzer.LibraryType.OPTIFINE))
                    return VersionIconType.OPTIFINE.getIcon();
            }

            String gameVersion = getGameVersion(version).orElse(null);
            if (gameVersion != null) {
                GameVersionNumber versionNumber = GameVersionNumber.asGameVersion(gameVersion);
                if (versionNumber.isAprilFools()) {
                    return VersionIconType.APRIL_FOOLS.getIcon();
                } else if (versionNumber instanceof GameVersionNumber.LegacySnapshot) {
                    return VersionIconType.COMMAND.getIcon();
                } else if (versionNumber instanceof GameVersionNumber.Old) {
                    return VersionIconType.CRAFT_TABLE.getIcon();
                }
            }
            return VersionIconType.GRASS.getIcon();
        } else {
            return iconType.getIcon();
        }
    }

    public void saveVersionSetting(String id) {
        if (!localVersionSettings.containsKey(id))
            return;
        Path file = getLocalVersionSettingFile(id).toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.warning("Failed to create directory: " + file.getParent(), e);
        }

        FileSaver.save(file, GSON.toJson(localVersionSettings.get(id)));
    }

    /**
     * Make version use self version settings instead of the global one.
     *
     * @param id the version id.
     * @return specialized version setting, null if given version does not exist.
     */
    public VersionSetting specializeVersionSetting(String id) {
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs == null)
            vs = createLocalVersionSetting(id);
        if (vs == null)
            return null;
        if (vs.isUsesGlobal()) {
            vs.setUsesGlobal(false);
        }
        return vs;
    }

    public void globalizeVersionSetting(String id) {
        VersionSetting vs = getLocalVersionSetting(id);
        if (vs != null)
            vs.setUsesGlobal(true);
    }

    public LaunchOptions.Builder getLaunchOptions(String version, JavaRuntime javaVersion, Path gameDir, List<String> javaAgents, List<String> javaArguments, boolean makeLaunchScript) {
        VersionSetting vs = getVersionSetting(version);

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionType(Metadata.TITLE)
                .setVersionName(version)
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.getMinecraftArgs()))
                .setOverrideJavaArguments(StringUtils.tokenize(vs.getJavaArgs()))
                .setMaxMemory(vs.isNoJVMArgs() && vs.isAutoMemory() ? null : (int) (getAllocatedMemory(
                        vs.getMaxMemory() * 1024L * 1024L,
                        SystemInfo.getPhysicalMemoryStatus().getAvailable(),
                        vs.isAutoMemory()
                ) / 1024 / 1024))
                .setMinMemory(vs.getMinMemory())
                .setMetaspace(Lang.toIntOrNull(vs.getPermSize()))
                .setEnvironmentVariables(
                        Lang.mapOf(StringUtils.tokenize(vs.getEnvironmentVariables())
                                .stream()
                                .map(it -> {
                                    int idx = it.indexOf('=');
                                    return idx >= 0 ? pair(it.substring(0, idx), it.substring(idx + 1)) : pair(it, "");
                                })
                                .collect(Collectors.toList())
                        )
                )
                .setWidth(vs.getWidth())
                .setHeight(vs.getHeight())
                .setFullscreen(vs.isFullscreen())
                .setWrapper(vs.getWrapper())
                .setProxyOption(getProxyOption())
                .setPreLaunchCommand(vs.getPreLaunchCommand())
                .setPostExitCommand(vs.getPostExitCommand())
                .setNoGeneratedJVMArgs(vs.isNoJVMArgs())
                .setNoGeneratedOptimizingJVMArgs(vs.isNoOptimizingJVMArgs())
                .setNativesDirType(vs.getNativesDirType())
                .setNativesDir(vs.getNativesDir())
                .setProcessPriority(vs.getProcessPriority())
                .setRenderer(vs.getRenderer())
                .setEnableDebugLogOutput(vs.isEnableDebugLogOutput())
                .setUseNativeGLFW(vs.isUseNativeGLFW())
                .setUseNativeOpenAL(vs.isUseNativeOpenAL())
                .setDaemon(!makeLaunchScript && vs.getLauncherVisibility().isDaemon())
                .setJavaAgents(javaAgents)
                .setJavaArguments(javaArguments);

        if (StringUtils.isNotBlank(vs.getServerIp())) {
            builder.setQuickPlayOption(new QuickPlayOption.MultiPlayer(vs.getServerIp()));
        }

        Path json = getModpackConfiguration(version);
        if (Files.exists(json)) {
            try {
                String jsonText = Files.readString(json);
                ModpackConfiguration<?> modpackConfiguration = JsonUtils.GSON.fromJson(jsonText, ModpackConfiguration.class);
                ModpackProvider provider = ModpackHelper.getProviderByType(modpackConfiguration.getType());
                if (provider != null) provider.injectLaunchOptions(jsonText, builder);
            } catch (IOException | JsonParseException e) {
                LOG.warning("Failed to parse modpack configuration file " + json, e);
            }
        }

        if (vs.isAutoMemory() && builder.getJavaArguments().stream().anyMatch(it -> it.startsWith("-Xmx")))
            builder.setMaxMemory(null);

        return builder;
    }

    @Override
    public Path getModpackConfiguration(String version) {
        return getVersionRoot(version).resolve("modpack.cfg");
    }

    public void markVersionAsModpack(String id) {
        beingModpackVersions.add(id);
    }

    public void undoMark(String id) {
        beingModpackVersions.remove(id);
    }

    public void markVersionLaunchedAbnormally(String id) {
        try {
            Files.createFile(getVersionRoot(id).resolve(".abnormal"));
        } catch (IOException ignored) {
        }
    }

    public boolean unmarkVersionLaunchedAbnormally(String id) {
        Path file = getVersionRoot(id).resolve(".abnormal");
        if (Files.isRegularFile(file)) {
            try {
                Files.delete(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete abnormal mark file: " + file, e);
            }

            return true;
        } else {
            return false;
        }
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
                FORBIDDEN_VERSION_IDS.contains(id.toLowerCase(Locale.ROOT)))
            return false;

        return FileUtils.isNameValid(id);
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
            available -= 512 * 1024 * 1024; // Reserve 512 MiB memory for off-heap memory and HMCL itself
            if (available <= 0) {
                return minimum;
            }

            final long threshold = 8L * 1024 * 1024 * 1024; // 8 GiB
            final long suggested = Math.min(available <= threshold
                            ? (long) (available * 0.8)
                            : (long) (threshold * 0.8 + (available - threshold) * 0.2),
                    16L * 1024 * 1024 * 1024);
            return Math.max(minimum, suggested);
        } else {
            return minimum;
        }
    }

    public static ProxyOption getProxyOption() {
        if (!config().hasProxy() || config().getProxyType() == null) {
            return ProxyOption.Default.INSTANCE;
        }

        return switch (config().getProxyType()) {
            case DIRECT -> ProxyOption.Direct.INSTANCE;
            case HTTP, SOCKS -> {
                String proxyHost = config().getProxyHost();
                int proxyPort = config().getProxyPort();

                if (StringUtils.isBlank(proxyHost) || proxyPort < 0 || proxyPort > 0xFFFF) {
                    yield ProxyOption.Default.INSTANCE;
                }

                String proxyUser = config().getProxyUser();
                String proxyPass = config().getProxyPass();

                if (StringUtils.isBlank(proxyUser)) {
                    proxyUser = null;
                    proxyPass = null;
                } else if (proxyPass == null) {
                    proxyPass = "";
                }

                if (config().getProxyType() == Proxy.Type.HTTP) {
                    yield new ProxyOption.Http(proxyHost, proxyPort, proxyUser, proxyPass);
                } else {
                    yield new ProxyOption.Socks(proxyHost, proxyPort, proxyUser, proxyPass);
                }
            }
            default -> ProxyOption.Default.INSTANCE;
        };
    }
}
