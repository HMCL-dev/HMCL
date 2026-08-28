/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectPropertyBase;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// HMCL-specific game instance that owns instance-local settings and run-directory policy.
@NotNullByDefault
public class HMCLGameInstance extends DefaultGameInstance {

    /// Whether the instance-local game settings file has already been inspected.
    private boolean gameSettingsLoaded;

    /// Whether the instance-local game settings file cannot be overwritten safely.
    private boolean gameSettingsReadOnly;

    /// Cached instance-local game settings, or `null` when none exist after loading.
    private GameSettings.@Nullable Instance gameSettings;

    /// Creates a registered instance bound to the given repository snapshot.
    ///
    /// @param snapshot the repository snapshot that owns this instance
    /// @param id       the instance id
    /// @param manifest the stored instance manifest
    protected HMCLGameInstance(DefaultGameRepositorySnapshot snapshot, GameInstanceID id, GameInstanceManifest manifest) {
        this(snapshot, id, manifest, (Path) null);
    }

    /// Creates a registered instance with an optional non-conventional manifest path.
    ///
    /// @param snapshot     the repository snapshot that owns this instance
    /// @param id           the instance id
    /// @param manifest     the stored instance manifest
    /// @param manifestFile the actual manifest JSON path, or `null` for the layout default
    protected HMCLGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            @Nullable Path manifestFile) {
        super(snapshot, id, manifest, manifestFile);
    }

    /// Creates an instance that shares mutable instance-local state with another instance.
    ///
    /// Used when the repository clones a snapshot so that settings and the icon property remain
    /// available on the new wrapper.
    private HMCLGameInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            HMCLGameInstance shareState) {
        super(snapshot, id, manifest, shareState);
        this.gameSettingsLoaded = shareState.gameSettingsLoaded;
        this.gameSettingsReadOnly = shareState.gameSettingsReadOnly;
        this.gameSettings = shareState.gameSettings;
    }

    @Override
    protected HMCLGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
        return new HMCLGameInstance(newSnapshot, id, manifest, this);
    }

    @Override
    protected HMCLGameInstance withManifest(DefaultGameRepositorySnapshot newSnapshot, GameInstanceManifest manifest) {
        return new HMCLGameInstance(newSnapshot, id, manifest, this);
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @Override
    public HMCLGameRepositoryLayout getLayout() {
        return (HMCLGameRepositoryLayout) super.getLayout();
    }

    /// Returns the HMCL modpack configuration file for this instance.
    ///
    /// @return the `modpack.cfg` path in the instance root
    @Override
    public Path getModpackConfigurationFile() {
        return getLayout().getModpackConfigurationFile(getId());
    }

    /// Returns whether this instance has an HMCL modpack configuration file.
    ///
    /// @return whether [#getModpackConfigurationFile()] exists
    public boolean isModpack() {
        return Files.exists(getModpackConfigurationFile());
    }

    /// Reads this instance's HMCL modpack configuration.
    ///
    /// @return the parsed configuration, or `null` when the file does not exist
    /// @throws IOException if the configuration cannot be read
    public @Nullable ModpackConfiguration<?> readModpackConfiguration() throws IOException {
        Path file = getModpackConfigurationFile();
        if (Files.notExists(file)) {
            return null;
        }
        try {
            return JsonUtils.fromJsonFile(file, ModpackConfiguration.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed modpack configuration: " + file, e);
        }
    }

    @Override
    public Path getRunDirectory() {
        return getRepository().computeRunDirectory(getId(), isModpack(), getSettings());
    }

    /// Returns the loaded instance-local game settings, loading them while this instance is current.
    ///
    /// @return the settings, or `null` when no local settings exist after loading
    public @Nullable GameSettings.Instance getSettings() {
        ensureGameSettingsLoaded();
        return gameSettings;
    }

    /// Returns the instance-local game settings, creating an empty settings object when absent.
    ///
    /// @return the settings, or `null` when the settings file is read-only and no settings are loaded
    public @Nullable GameSettings.Instance getSettingsOrCreate() {
        GameSettings.Instance setting = getSettings();
        if (setting == null) {
            setting = createSettings();
        }
        return setting;
    }

    /// Resolves this instance's effective settings against its selected parent preset.
    ///
    /// @return the effective settings
    public GameSettings.Effective getEffectiveSettings() {
        @Nullable GameSettings.Instance setting = getSettings();
        return GameSettings.resolve(getRepository().getParentGameSettings(setting), setting);
    }

    /// Creates empty instance-local game settings when none are loaded.
    ///
    /// @return the settings, or `null` when settings are read-only or already present in a non-creatable state
    public @Nullable GameSettings.Instance createSettings() {
        ensureGameSettingsLoaded();
        if (gameSettingsReadOnly) {
            return null;
        }
        if (gameSettings != null) {
            return gameSettings;
        }
        return initSettings(new GameSettings.Instance(), true);
    }

    /// Returns whether the instance-local game settings file cannot be overwritten safely.
    ///
    /// @return whether the settings are loaded in read-only mode
    public boolean isSettingsReadOnly() {
        ensureGameSettingsLoaded();
        return gameSettingsReadOnly;
    }

    /// Enables instance-local running-directory selection for this instance.
    ///
    /// A blank local running directory resolves to the instance root. This operation is idempotent
    /// and schedules a settings save only when it adds the override. It leaves the instance
    /// unchanged when its local settings cannot be written safely.
    public void enableIsolation() {
        if (isSettingsReadOnly()) {
            return;
        }

        @Nullable GameSettings.Instance setting = getSettingsOrCreate();
        if (setting != null
                && setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
            saveSettings();
        }
    }

    /// Backs up and overwrites the settings file when this instance still owns its settings.
    public void forceOverwriteSettings() {
        ensureGameSettingsLoaded();
        if (!ownsGameSettings()) {
            return;
        }

        GameSettings.Instance setting = gameSettings;
        if (setting == null) {
            setting = new GameSettings.Instance();
            gameSettings = setting;
            gameSettingsLoaded = true;
        }

        boolean installAutoSave = !setting.isSavable();
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        SettingFileUtils.backupInvalidConfig(file);
        setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
        setting.setSavable(true);
        setting.setBackupOnNextSave(false);
        gameSettingsReadOnly = false;
        saveSettings();
        if (installAutoSave) {
            setting.addListener(a -> saveSettings());
        }
    }

    /// Saves the settings asynchronously when writable and still owned by the current instance.
    public void saveSettings() {
        if (gameSettings == null || gameSettingsReadOnly || !ownsGameSettings()) {
            return;
        }

        GameSettings.Instance setting = gameSettings;
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOG.warning("Failed to create directory: " + file.getParent(), e);
        }

        if (setting.isBackupOnNextSave()) {
            setting.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileSaver.save(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Saves the settings synchronously when writable and still owned by the current instance.
    ///
    /// @throws IOException if saving the file fails
    public void saveSettingsSync() throws IOException {
        if (gameSettings == null || gameSettingsReadOnly || !ownsGameSettings()) {
            return;
        }

        GameSettings.Instance setting = gameSettings;
        Path file = getGameSettingsFile().toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        if (setting.isBackupOnNextSave()) {
            setting.setBackupOnNextSave(false);
            SettingFileUtils.backupInvalidConfig(file);
        }
        FileUtils.saveSafely(file, LauncherSettings.SETTINGS_GSON.toJson(setting));
    }

    /// Initializes this instance with the given settings object.
    ///
    /// @param setting   the settings to install
    /// @param allowSave whether the settings may be written back to disk
    /// @return the installed settings
    public GameSettings.Instance initSettings(GameSettings.Instance setting, boolean allowSave) {
        normalizeRunningDirectoryOverride(setting);
        setting.setSavable(allowSave);
        gameSettingsLoaded = true;
        gameSettings = setting;
        setting.iconProperty().addListener(observable -> invalidateIconImage());
        if (allowSave) {
            gameSettingsReadOnly = false;
            setting.addListener(a -> saveSettings());
        } else {
            gameSettingsReadOnly = true;
        }
        return setting;
    }

    /// Returns a deep copy of the currently loaded settings, or a new settings object that inherits
    /// the effective parent preset when no local settings exist.
    ///
    /// @return a detached copy suitable for installing into another instance
    public GameSettings.Instance copySettings() {
        @Nullable GameSettings.Instance setting = getSettings();
        if (setting != null) {
            return JsonUtils.clone(LauncherSettings.SETTINGS_GSON, setting, TypeToken.get(GameSettings.Instance.class));
        }

        GameSettings.Instance copied = new GameSettings.Instance();
        copied.parentProperty().setValue(
                getEffectiveSettings().getPreset().idProperty().getValue());
        return copied;
    }

    /// Returns the first custom icon file found in this instance's root directory.
    ///
    /// @return the icon file, or empty when no supported icon file exists
    public @Nullable Path getIconFile() {
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = getInstanceRoot().resolve("icon." + extension);
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    /// Replaces this instance's custom icon file.
    ///
    /// Existing supported icon files are removed before `iconFile` is copied.
    ///
    /// @param iconFile the source icon file
    /// @throws IOException              if the icon cannot be copied
    /// @throws IllegalArgumentException if the file extension is unsupported
    public void setIconFile(Path iconFile) throws IOException {
        String extension = FileUtils.getExtension(iconFile).toLowerCase(Locale.ROOT);
        if (!FXUtils.IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported icon file: " + extension);
        }

        clearIconFiles();
        FileUtils.copyFile(iconFile, getInstanceRoot().resolve("icon." + extension));
        invalidateIconImage();
    }

    /// Deletes all supported custom icon files for this instance.
    ///
    /// Individual deletion failures are logged and do not stop later files from being attempted.
    public void deleteIconFile() {
        clearIconFiles();
        invalidateIconImage();
    }

    private void clearIconFiles() {
        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            Path file = getInstanceRoot().resolve("icon." + extension);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete instance icon file: " + file, e);
            }
        }
    }

    /// Soft-cached icon image for this instance id.
    ///
    /// Shared across COW snapshot wrappers. The computed [Image] is retained only via a
    /// [WeakReference], so it can be reclaimed under memory pressure when nothing else holds it.
    private @Nullable WeakCachedIconImageProperty iconImage;

    /// Returns the observable icon image for this instance.
    ///
    /// The image is stored in a [WeakReference] cache: when nothing else strongly references it
    /// (for example no UI node is displaying it), the JVM may reclaim the [Image] under memory
    /// pressure. The next [#getIconImage] reloads it.
    ///
    /// @return the icon image property
    public ReadOnlyObjectProperty<Image> iconImageProperty() {
        if (iconImage == null) {
            iconImage = new WeakCachedIconImageProperty();
        }
        return iconImage;
    }

    /// Returns the icon image selected for this instance.
    ///
    /// Equivalent to [ReadOnlyObjectProperty#get()] on [#iconImageProperty].
    ///
    /// @return the selected or derived icon image
    public Image getIconImage() {
        return iconImageProperty().get();
    }

    /// Drops the soft-cached icon image and notifies observers.
    public void invalidateIconImage() {
        ((WeakCachedIconImageProperty) iconImageProperty()).invalidate();
    }

    /// Soft-cached read-only icon property compatible with JavaFX versions before 19.
    private final class WeakCachedIconImageProperty extends ReadOnlyObjectPropertyBase<Image> {
        private @Nullable WeakReference<Image> cache;

        @Override
        public Object getBean() {
            return HMCLGameInstance.this;
        }

        @Override
        public String getName() {
            return "iconImage";
        }

        @Override
        public Image get() {
            WeakReference<Image> current = cache;
            Image image = current != null ? current.get() : null;
            if (image != null) {
                return image;
            }

            image = computeIconImage();
            cache = new WeakReference<>(image);
            return image;
        }

        /// Computes the icon image from settings, custom files, and the launch manifest.
        ///
        /// @return the selected or derived icon image
        private Image computeIconImage() {
            @Nullable GameSettings.Instance setting = getSettings();
            GameInstanceIconType iconType = setting != null
                    ? Lang.requireNonNullElse(setting.iconProperty().getValue(), GameInstanceIconType.DEFAULT)
                    : GameInstanceIconType.DEFAULT;
            if (iconType != GameInstanceIconType.DEFAULT) {
                return iconType.getIcon();
            }

            @Nullable Path iconFile = getIconFile();
            if (iconFile != null) {
                try {
                    return FXUtils.loadImage(iconFile, 64, 64, true, true);
                } catch (Exception e) {
                    LOG.warning("Failed to load instance icon for " + getId(), e);
                }
            }

            for (ModLoaderType modLoader : getModLoaders()) {
                return GameInstanceIconType.getIconType(modLoader).getIcon();
            }

            if (hasComponent(GameComponentType.OPTIFINE))
                return GameInstanceIconType.OPTIFINE.getIcon();

            GameVersionNumber version = getVersion();
            if (version.isAprilFools())
                return GameInstanceIconType.APRIL_FOOLS.getIcon();
            else if (version instanceof GameVersionNumber.LegacySnapshot)
                return GameInstanceIconType.COMMAND.getIcon();
            else if (version instanceof GameVersionNumber.Old)
                return GameInstanceIconType.CRAFT_TABLE.getIcon();
            else
                return GameInstanceIconType.GRASS.getIcon();
        }

        /// Clears the weak cache and notifies listeners.
        void invalidate() {
            cache = null;
            fireValueChangedEvent();
        }
    }

    /// Creates the marker indicating that the most recent launch ended abnormally.
    public void markLaunchedAbnormally() {
        try {
            Files.createFile(getInstanceRoot().resolve(".abnormal"));
        } catch (IOException ignored) {
        }
    }

    /// Deletes the abnormal-launch marker when present.
    ///
    /// @return whether a regular marker file was present
    public boolean unmarkLaunchedAbnormally() {
        Path file = getInstanceRoot().resolve(".abnormal");
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try {
            Files.delete(file);
        } catch (IOException e) {
            LOG.warning("Failed to delete abnormal launch marker: " + file, e);
        }
        return true;
    }

    /// Loads settings once while this object is the repository's current instance.
    private void ensureGameSettingsLoaded() {
        if (!gameSettingsLoaded && getRepository().findInstance(id) == this) {
            loadGameSettings();
        }
    }

    /// Returns whether this instance still owns the settings object used by the current snapshot.
    private boolean ownsGameSettings() {
        @Nullable HMCLGameInstance current = getRepository().findInstance(id);
        boolean owns = current == this
                || current != null && gameSettings != null && current.gameSettings == gameSettings;
        if (!owns && gameSettings != null) {
            gameSettings.setSavable(false);
        }
        return owns;
    }

    private void loadGameSettings() {
        gameSettingsLoaded = true;
        LoadResult result = loadGameSettingsFile(getGameSettingsFile());
        if (result.setting() != null) {
            initSettings(result.setting(), result.allowSave());
            return;
        }
        if (!result.allowSave()) {
            gameSettingsReadOnly = true;
            return;
        }

        @Nullable GameSettingsPresetID legacyParent = getRepository().getGameDirectory().getLegacyGameSettings();
        if (SettingsManager.getGameSettings(legacyParent) == null) {
            legacyParent = null;
        }

        LegacyGameSettingsMigrator.InstanceMigrationResult migrationResult =
                LegacyGameSettingsMigrator.migrateInstanceGameSettings(
                        getRepository(), id, legacyParent);
        if (migrationResult != null) {
            initSettings(migrationResult.setting(), true);
            try {
                saveSettingsSync();
                migrationResult.saveReceipt();
            } catch (IOException e) {
                LOG.warning("Failed to save migrated instance game settings for " + id, e);
            }
        }
    }

    private Path getGameSettingsFile() {
        return getLayout().getInstanceGameSettingsFile(id);
    }

    public LaunchOptions.Builder getLaunchOptions(JavaRuntime javaVersion, Path gameDir, List<String> javaAgents, List<String> javaArguments, boolean makeLaunchScript) {
        GameSettings.Effective vs = getEffectiveSettings();
        boolean noJVMOptions = vs.getInheritable(GameSettings::noJVMOptionsProperty);
        boolean autoMemory = vs.getInheritable(GameSettings::autoMemoryProperty);
        GameVersionNumber gameVersionNumber = getVersion();

        @Nullable Integer maxMemory;
        if (autoMemory) {
            maxMemory = noJVMOptions
                    ? null
                    : Math.toIntExact(HMCLGameRepository.getAutoAllocatedMemory(
                            SystemInfo.getPhysicalMemoryStatus().available(),
                            javaVersion.getPlatform()) / 1024L / 1024L);
        } else {
            maxMemory = vs.getMaxMemory();
        }

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setInstanceId(getId())
                .setGameDir(gameDir)
                .setJava(javaVersion)
                .setVersionType(Metadata.TITLE)
                .setVersionName(getId().id())
                .setProfileName(Metadata.TITLE)
                .setGameArguments(StringUtils.tokenize(vs.getInheritable(GameSettings::gameArgumentsProperty)))
                .setOverrideJavaArguments(StringUtils.tokenize(vs.getInheritable(GameSettings::jvmOptionsProperty)))
                .setMaxMemory(maxMemory)
                .setMinMemory(vs.getInheritable(GameSettings::minMemoryProperty))
                .setMetaspace(Lang.toIntOrNull(vs.getInheritable(GameSettings::permSizeProperty)))
                .setEnvironmentVariables(
                        Lang.mapOf(StringUtils.tokenize(vs.getInheritable(GameSettings::environmentVariablesProperty))
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
                .setFullscreen(vs.getInheritable(GameSettings::windowTypeProperty) == GameWindowType.FULLSCREEN)
                .setWrapper(vs.getInheritable(GameSettings::commandWrapperProperty))
                .setProxyOption(getProxyOption())
                .setPreLaunchCommand(vs.getInheritable(GameSettings::preLaunchCommandProperty))
                .setPostExitCommand(vs.getInheritable(GameSettings::postExitCommandProperty))
                .setNoGeneratedJVMArgs(noJVMOptions)
                .setNoGeneratedOptimizingJVMArgs(vs.getInheritable(GameSettings::noOptimizingJVMOptionsProperty))
                .setUseCustomNatives(vs.getInheritable(GameSettings::useCustomNativesProperty))
                .setNativesDir(vs.getInheritable(GameSettings::nativesDirectoryProperty))
                .setProcessPriority(vs.getInheritable(GameSettings::processPriorityProperty))
                .setGraphicsBackend(vs.getInheritable(GameSettings::graphicsBackendProperty))
                .setRenderer(vs.getRenderer(gameVersionNumber))
                .setEnableDebugLogOutput(vs.getInheritable(GameSettings::enableDebugLogOutputProperty))
                .setAllowAutoAgent(vs.getInheritable(GameSettings::allowAutoAgentProperty))
                .setDisableAutoGameOptions(vs.getInheritable(GameSettings::disableAutoGameOptionsProperty))
                .setUseNativeGLFWorSDL(vs.getInheritable(GameSettings::useNativeGLFWorSDLProperty))
                .setUseNativeOpenAL(vs.getInheritable(GameSettings::useNativeOpenALProperty))
                .setUseHighPerformanceGPU(vs.getInheritable(GameSettings::highPerformanceProperty))
                .setDaemon(!makeLaunchScript && vs.getInheritable(GameSettings::launcherVisibilityProperty).isDaemon())
                .setJavaAgents(javaAgents)
                .setJavaArguments(javaArguments);

        QuickPlayOption quickPlayOption = vs.getQuickPlayOption();
        if (quickPlayOption != null) {
            builder.setQuickPlayOption(quickPlayOption);
        }

        Path json = getModpackConfigurationFile();
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

        if (autoMemory && builder.getJavaArguments().stream().anyMatch(it -> it.startsWith("-Xmx")))
            builder.setMaxMemory(null);

        return builder;
    }

    private static ProxyOption getProxyOption() {
        return switch (settings().proxyTypeProperty().get()) {
            case SYSTEM -> ProxyOption.Default.INSTANCE;
            case DIRECT -> ProxyOption.Direct.INSTANCE;
            case HTTP, SOCKS -> {
                String proxyHost = settings().proxyHostProperty().get();
                int proxyPort = settings().proxyPortProperty().get();

                if (StringUtils.isBlank(proxyHost) || proxyPort < 0 || proxyPort > 0xFFFF) {
                    yield ProxyOption.Default.INSTANCE;
                }

                String proxyUser = settings().proxyUserProperty().get();
                String proxyPass = settings().proxyPasswordProperty().get();

                if (StringUtils.isBlank(proxyUser)) {
                    proxyUser = null;
                    proxyPass = null;
                } else if (proxyPass == null) {
                    proxyPass = "";
                }

                if (settings().proxyTypeProperty().get() == ProxyType.HTTP) {
                    yield new ProxyOption.Http(proxyHost, proxyPort, proxyUser, proxyPass);
                } else {
                    yield new ProxyOption.Socks(proxyHost, proxyPort, proxyUser, proxyPass);
                }
            }
        };
    }

    /// Loads a new-format instance game settings file.
    private static LoadResult loadGameSettingsFile(Path file) {
        if (!Files.exists(file)) {
            return new LoadResult(null, true);
        }

        try {
            JsonObject jsonObject = JsonUtils.fromJsonFile(LauncherSettings.SETTINGS_GSON, file, JsonObject.class);
            if (jsonObject == null) {
                LOG.warning("Instance game settings are empty: " + file);
                GameSettings.Instance fallback = new GameSettings.Instance();
                return new LoadResult(fallback, true);
            }

            JsonSchema.CompatibilityResult schemaResult =
                    JsonSchema.check(jsonObject, GameSettings.Instance.CURRENT_SCHEMA);
            switch (schemaResult.status()) {
                case MISSING -> LOG.warning("Missing schema in instance game settings: " + file);
                case INVALID -> LOG.warning("Invalid schema in instance game settings: %s, Actual: %s".formatted(file, schemaResult.invalidValue()));
                case UNPARSEABLE -> LOG.warning("Unparseable schema in instance game settings: %s, Actual: %s".formatted(file, schemaResult.actual()));
                case UNEXPECTED_ID -> LOG.warning("Unexpected instance game settings schema. Expected: %s, Actual: %s".formatted(GameSettings.Instance.CURRENT_SCHEMA, schemaResult.actual()));
                case UNSUPPORTED_MAJOR, READ_ONLY_PRESERVE_SCHEMA -> LOG.warning("Unsupported instance game settings schema. Expected: %s, Actual: %s".formatted(GameSettings.Instance.CURRENT_SCHEMA, schemaResult.actual()));
                case READ_WRITE, READ_WRITE_PRESERVE_SCHEMA -> {
                }
            }
            if (!schemaResult.readable()) {
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setSavable(false);
                return new LoadResult(fallback, false);
            }

            GameSettings.@Nullable Instance setting =
                    LauncherSettings.SETTINGS_GSON.fromJson(jsonObject, GameSettings.Instance.class);
            if (setting == null) {
                LOG.warning("Instance game settings deserialized to null: " + file);
                GameSettings.Instance fallback = new GameSettings.Instance();
                fallback.setBackupOnNextSave(true);
                return new LoadResult(fallback, true);
            }
            if (!schemaResult.preserveSchema() && !GameSettings.Instance.CURRENT_SCHEMA.equals(setting.getSchema())) {
                setting.setSchema(GameSettings.Instance.CURRENT_SCHEMA);
            }
            return new LoadResult(setting, schemaResult.allowSave());
        } catch (JsonParseException ex) {
            LOG.warning("Failed to parse game setting " + file, ex);
            GameSettings.Instance fallback = new GameSettings.Instance();
            fallback.setBackupOnNextSave(true);
            return new LoadResult(fallback, true);
        } catch (Exception ex) {
            LOG.warning("Failed to load game setting " + file, ex);
            return new LoadResult(null, false);
        }
    }

    /// Keeps old local custom running directories effective under the new source-selection model.
    private static void normalizeRunningDirectoryOverride(GameSettings.Instance setting) {
        if (StringUtils.isNotBlank(setting.runningDirectoryProperty().getValue())) {
            setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
        }
    }

    /// Result of loading an instance-specific game settings file.
    ///
    /// @param setting   the loaded instance settings, or `null` when unavailable
    /// @param allowSave whether the file may be overwritten
    private record LoadResult(@Nullable GameSettings.Instance setting, boolean allowSave) {
    }

    /// Optional reference to an HMCL game instance bound to a repository.
    ///
    /// Replaces the former `(repository, instanceId)` pair for UI and service context that may or
    /// may not have a selected instance. When present, [#instance()] is a snapshot member and may
    /// become stale after the repository publishes a new snapshot; call [#refreshed()] to re-resolve
    /// from the current snapshot while preserving repository context.
    @NotNullByDefault
    public static final class Optional {
        private final HMCLGameRepository repository;
        private final @Nullable HMCLGameInstance instance;

        /// Creates an empty optional bound only to a repository.
        ///
        /// @param repository the repository
        public Optional(HMCLGameRepository repository) {
            this.repository = Objects.requireNonNull(repository);
            this.instance = null;
        }

        /// Creates an optional that holds the given instance.
        ///
        /// @param instance the instance
        public Optional(HMCLGameInstance instance) {
            this.repository = instance.getRepository();
            this.instance = instance;
        }

        /// Creates an optional by resolving `instanceId` from the repository's current snapshot.
        ///
        /// @param repository the repository
        /// @param instanceId the instance id, or `null` for an empty optional
        /// @return an optional that is empty when `instanceId` is null or not registered
        public static Optional of(HMCLGameRepository repository, @Nullable GameInstanceID instanceId) {
            if (instanceId == null) {
                return new Optional(repository);
            }
            HMCLGameInstance instance = repository.findInstance(instanceId);
            return instance != null ? new Optional(instance) : new Optional(repository);
        }

        /// Creates an optional that holds the given instance.
        ///
        /// @param instance the instance
        /// @return the optional
        public static Optional of(HMCLGameInstance instance) {
            return new Optional(instance);
        }

        /// Creates an empty optional bound only to a repository.
        ///
        /// @param repository the repository
        /// @return the empty optional
        public static Optional empty(HMCLGameRepository repository) {
            return new Optional(repository);
        }

        /// Returns the repository associated with this optional.
        ///
        /// @return the repository
        public HMCLGameRepository repository() {
            return repository;
        }

        /// Returns the held instance, if any.
        ///
        /// @return the instance, or `null` when empty
        @Contract(pure = true)
        public @Nullable HMCLGameInstance instance() {
            return instance;
        }

        /// Returns the held instance id, if any.
        ///
        /// @return the instance id, or `null` when empty
        @Contract(pure = true)
        public @Nullable GameInstanceID instanceId() {
            return instance != null ? instance.getId() : null;
        }

        /// Returns whether an instance is present.
        ///
        /// @return whether [#instance()] is non-null
        public boolean isPresent() {
            return instance != null;
        }

        /// Returns whether no instance is present.
        ///
        /// @return whether [#instance()] is null
        public boolean isEmpty() {
            return instance == null;
        }

        /// Re-resolves the held instance id from the repository's current snapshot.
        ///
        /// @return this optional when empty; otherwise a fresh optional for the same id
        public Optional refreshed() {
            if (instance == null) {
                return this;
            }
            return of(repository, instance.getId());
        }
    }
}
