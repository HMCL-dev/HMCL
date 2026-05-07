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
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.setting.property.SettingGroup;
import org.jackhuang.hmcl.setting.property.SettingProperty;
import org.jackhuang.hmcl.setting.property.SimpleInheritableProperty;
import org.jackhuang.hmcl.setting.property.SimpleSettingProperty;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Game launch settings shared by global presets and instance-specific overrides.
///
/// @author Glavo
@NotNullByDefault
public sealed abstract class GameSetting extends ObservableSetting {
    /// Memory settings are overridden as one group.
    public static final SettingGroup MEMORY_SETTINGS = new SettingGroup("memory");

    /// JVM option text can inherit and merge with the parent setting.
    public static final SettingGroup JVM_OPTIONS = new SettingGroup("jvmOptions");

    /// Game argument text can inherit and merge with the parent setting.
    public static final SettingGroup GAME_ARGUMENTS = new SettingGroup("gameArguments");

    /// Environment variable text can inherit and merge with the parent setting.
    public static final SettingGroup ENVIRONMENT_VARIABLES = new SettingGroup("environmentVariables");

    private static final int SUGGESTED_MEMORY;

    static {
        double totalMemoryMB = MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
        SUGGESTED_MEMORY = totalMemoryMB >= 32768
                ? 8192
                : Integer.max((int) (Math.round(totalMemoryMB / 4.0 / 128.0) * 128), 256);
    }

    /// Instance-specific game setting.
    @JsonAdapter(Instance.Adapter.class)
    public static final class Instance extends GameSetting {
        /// Creates an empty instance setting.
        public Instance() {
            register();
        }

        /// The parent global game setting ID.
        @SerializedName("parent")
        private final SettingProperty<@Nullable UUID> parent = newSettingProperty("parent");

        /// Returns the parent global game setting ID property.
        public SettingProperty<@Nullable UUID> parentProperty() {
            return parent;
        }

        /// The icon of the instance.
        @SerializedName("icon")
        private final SettingProperty<VersionIconType> icon = newSettingProperty("icon", VersionIconType.DEFAULT);

        /// Returns the instance icon property.
        public SettingProperty<VersionIconType> iconProperty() {
            return icon;
        }

        /// Whether to isolate the instance from other instances.
        @SerializedName("isolation")
        private final SettingProperty<Boolean> isolation = newSettingProperty("isolation", false);

        /// Returns the instance isolation property.
        public SettingProperty<Boolean> isolationProperty() {
            return isolation;
        }

        /// Setting groups overridden by this instance instead of merged or inherited.
        @SerializedName("overrideGroups")
        private final javafx.collections.ObservableSet<SettingGroup> overrideGroups = javafx.collections.FXCollections.observableSet();

        /// Returns the overridden setting groups.
        public javafx.collections.ObservableSet<SettingGroup> getOverrideGroups() {
            return overrideGroups;
        }

        /// JSON adapter for instance settings.
        public static final class Adapter extends ObservableSetting.Adapter<Instance> {
            @Override
            protected Instance createInstance() {
                return new Instance();
            }
        }
    }

    /// Reusable global game setting.
    @JsonAdapter(Global.Adapter.class)
    public static final class Global extends GameSetting {
        /// Creates a global setting with generated identity.
        public Global() {
            register();
            if (id.getValue() == null) {
                id.setValue(UUID.randomUUID());
            }
        }

        /// The stable global setting ID.
        @SerializedName("id")
        private final SettingProperty<UUID> id = newSettingProperty("id");

        /// Returns the global setting ID property.
        public SettingProperty<UUID> idProperty() {
            return id;
        }

        /// The display name of this global setting.
        @SerializedName("name")
        private final SettingProperty<String> name = newSettingProperty("name", "");

        /// Returns the display name property.
        public SettingProperty<String> nameProperty() {
            return name;
        }

        /// Whether to enable the version isolation strategy when installing a new instance.
        @SerializedName("defaultIsolationType")
        private final SettingProperty<DefaultIsolationType> defaultIsolationType = newSettingProperty("defaultIsolationType", DefaultIsolationType.MODED);

        /// Returns the default isolation type property.
        public SettingProperty<DefaultIsolationType> defaultIsolationTypeProperty() {
            return defaultIsolationType;
        }

        /// JSON adapter for global settings.
        public static final class Adapter extends ObservableSetting.Adapter<Global> {
            @Override
            protected Global createInstance() {
                return new Global();
            }
        }
    }

    /// Creates a new setting property without a default value.
    protected final <T> SettingProperty<T> newSettingProperty(String name) {
        return new SimpleSettingProperty<>(this, null, name);
    }

    /// Creates a new setting property with the given default value.
    protected final <T> SettingProperty<T> newSettingProperty(String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, null, name, defaultValue);
    }

    /// Creates a new group-scoped setting property without a default value.
    protected final <T> SettingProperty<T> newSettingProperty(SettingGroup group, String name) {
        return new SimpleSettingProperty<>(this, group, name);
    }

    /// Creates a new group-scoped setting property with the given default value.
    protected final <T> SettingProperty<T> newSettingProperty(SettingGroup group, String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, group, name, defaultValue);
    }

    /// Creates a new inheritable property.
    protected final <T> InheritableProperty<T> newInheritableProperty(String name, T defaultValue) {
        return new SimpleInheritableProperty<>(this, name, defaultValue);
    }

    /// The Java selection mode.
    @SerializedName("javaType")
    private final InheritableProperty<JavaVersionType> javaType = newInheritableProperty("javaType", JavaVersionType.AUTO);

    /// Returns the Java selection mode property.
    public InheritableProperty<JavaVersionType> javaTypeProperty() {
        return javaType;
    }

    /// The custom or detected Java version string.
    @SerializedName("javaVersion")
    private final SettingProperty<String> javaVersion = newSettingProperty("javaVersion", "");

    /// Returns the Java version property.
    public SettingProperty<String> javaVersionProperty() {
        return javaVersion;
    }

    /// User customized Java executable path.
    @SerializedName("customJavaPath")
    private final SettingProperty<String> customJavaPath = newSettingProperty("customJavaPath", "");

    /// Returns the custom Java executable path property.
    public SettingProperty<String> customJavaPathProperty() {
        return customJavaPath;
    }

    /// Resolved Java executable path used to disambiguate detected Java runtimes.
    @SerializedName("defaultJavaPath")
    private final SettingProperty<String> defaultJavaPath = newSettingProperty("defaultJavaPath", "");

    /// Returns the default Java executable path property.
    public SettingProperty<String> defaultJavaPathProperty() {
        return defaultJavaPath;
    }

    /// Finds Java using this setting without resolving a parent setting.
    public @Nullable JavaRuntime getJava(@Nullable GameVersionNumber gameVersion, @Nullable Version version) throws InterruptedException {
        return resolve(this instanceof Global global ? global : new Global(), this instanceof Instance instance ? instance : null)
                .getJava(gameVersion, version);
    }

    /// User customized JVM options.
    @SerializedName("jvmOptions")
    private final SettingProperty<String> jvmOptions = newSettingProperty(JVM_OPTIONS, "jvmOptions", "");

    /// Returns the user customized JVM options property.
    public SettingProperty<String> jvmOptionsProperty() {
        return jvmOptions;
    }

    /// If `true`, HMCL will not use default JVM arguments.
    @SerializedName("noJVMOptions")
    private final InheritableProperty<Boolean> noJVMOptions = newInheritableProperty("noJVMOptions", false);

    /// Returns the no generated JVM options property.
    public InheritableProperty<Boolean> noJVMOptionsProperty() {
        return noJVMOptions;
    }

    /// If `true`, HMCL will not use the default optimizing JVM options.
    @SerializedName("noOptimizingJVMOptions")
    private final InheritableProperty<Boolean> noOptimizingJVMOptions = newInheritableProperty("noOptimizingJVMOptions", false);

    /// Returns the no optimizing JVM options property.
    public InheritableProperty<Boolean> noOptimizingJVMOptionsProperty() {
        return noOptimizingJVMOptions;
    }

    /// If `true`, HMCL does not check JVM validity.
    @SerializedName("notCheckJVM")
    private final InheritableProperty<Boolean> notCheckJVM = newInheritableProperty("notCheckJVM", false);

    /// Returns the JVM validity check property.
    public InheritableProperty<Boolean> notCheckJVMProperty() {
        return notCheckJVM;
    }

    /// If `true`, HMCL does not check game completeness.
    @SerializedName("notCheckGame")
    private final InheritableProperty<Boolean> notCheckGame = newInheritableProperty("notCheckGame", false);

    /// Returns the game completeness check property.
    public InheritableProperty<Boolean> notCheckGameProperty() {
        return notCheckGame;
    }

    /// If `true`, HMCL will automatically adjust memory allocation.
    @SerializedName("autoMemory")
    private final SettingProperty<Boolean> autoMemory = newSettingProperty(MEMORY_SETTINGS, "autoMemory", true);

    /// Returns the automatic memory allocation property.
    public SettingProperty<Boolean> autoMemoryProperty() {
        return autoMemory;
    }

    /// The minimum heap memory in MiB.
    @SerializedName("minMemory")
    private final SettingProperty<@Nullable Integer> minMemory = newSettingProperty(MEMORY_SETTINGS, "minMemory");

    /// Returns the minimum heap memory property.
    public SettingProperty<@Nullable Integer> minMemoryProperty() {
        return minMemory;
    }

    /// The maximum heap memory in MiB.
    @SerializedName("maxMemory")
    private final SettingProperty<Integer> maxMemory = newSettingProperty(MEMORY_SETTINGS, "maxMemory", SUGGESTED_MEMORY);

    /// Returns the maximum heap memory property.
    public SettingProperty<Integer> maxMemoryProperty() {
        return maxMemory;
    }

    /// The permanent generation or metaspace size in MiB.
    @SerializedName("permSize")
    private final SettingProperty<String> permSize = newSettingProperty(MEMORY_SETTINGS, "permSize", "");

    /// Returns the permanent generation or metaspace size property.
    public SettingProperty<String> permSizeProperty() {
        return permSize;
    }

    /// The initial game window mode.
    @SerializedName("windowType")
    private final InheritableProperty<GameWindowType> windowType = newInheritableProperty("windowType", GameWindowType.WINDOWED);

    /// Returns the game window mode property.
    public InheritableProperty<GameWindowType> windowTypeProperty() {
        return windowType;
    }

    /// The width of the game window.
    @SerializedName("width")
    private final InheritableProperty<Double> width = newInheritableProperty("width", 854.0);

    /// Returns the game window width property.
    public InheritableProperty<Double> widthProperty() {
        return width;
    }

    /// The height of the game window.
    @SerializedName("height")
    private final InheritableProperty<Double> height = newInheritableProperty("height", 480.0);

    /// Returns the game window height property.
    public InheritableProperty<Double> heightProperty() {
        return height;
    }

    /// The game directory mode.
    @SerializedName("gameDirType")
    private final InheritableProperty<GameDirectoryType> gameDirType = newInheritableProperty("gameDirType", GameDirectoryType.ROOT_FOLDER);

    /// Returns the game directory mode property.
    public InheritableProperty<GameDirectoryType> gameDirTypeProperty() {
        return gameDirType;
    }

    /// The custom run directory.
    @SerializedName("runningDir")
    private final InheritableProperty<String> runningDir = newInheritableProperty("runningDir", "");

    /// Returns the custom run directory property.
    public InheritableProperty<String> runningDirProperty() {
        return runningDir;
    }

    /// The process priority of the game.
    @SerializedName("processPriority")
    private final InheritableProperty<ProcessPriority> processPriority = newInheritableProperty("processPriority", ProcessPriority.NORMAL);

    /// Returns the process priority property.
    public InheritableProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }

    /// Launcher behavior after the game starts.
    @SerializedName("launcherVisibility")
    private final InheritableProperty<LauncherVisibility> launcherVisibility = newInheritableProperty("launcherVisibility", LauncherVisibility.KEEP);

    /// Returns the launcher visibility property.
    public InheritableProperty<LauncherVisibility> launcherVisibilityProperty() {
        return launcherVisibility;
    }

    /// User customized arguments passed to the game.
    @SerializedName("gameArgs")
    private final SettingProperty<String> gameArgs = newSettingProperty(GAME_ARGUMENTS, "gameArgs", "");

    /// Returns the customized game arguments property.
    public SettingProperty<String> gameArgsProperty() {
        return gameArgs;
    }

    /// Graphics API requested by the launcher.
    @SerializedName("graphicsBackend")
    private final InheritableProperty<GraphicsAPI> graphicsBackend = newInheritableProperty("graphicsBackend", GraphicsAPI.DEFAULT);

    /// Returns the graphics API property.
    public InheritableProperty<GraphicsAPI> graphicsBackendProperty() {
        return graphicsBackend;
    }

    /// The renderer used by the game.
    @SerializedName("renderer")
    private final InheritableProperty<Renderer> renderer = newInheritableProperty("renderer", Renderer.DEFAULT);

    /// Returns the renderer property.
    public InheritableProperty<Renderer> rendererProperty() {
        return renderer;
    }

    /// User customized environment variables passed to the game.
    @SerializedName("environmentVariables")
    private final SettingProperty<String> environmentVariables = newSettingProperty(ENVIRONMENT_VARIABLES, "environmentVariables", "");

    /// Returns the customized environment variables property.
    public SettingProperty<String> environmentVariablesProperty() {
        return environmentVariables;
    }

    /// The command wrapper for launching Minecraft.
    @SerializedName("commandWrapper")
    private final InheritableProperty<String> commandWrapper = newInheritableProperty("commandWrapper", "");

    /// Returns the command wrapper property.
    public InheritableProperty<String> commandWrapperProperty() {
        return commandWrapper;
    }

    /// The command that will be executed before launching the game.
    @SerializedName("preLaunchCommand")
    private final InheritableProperty<String> preLaunchCommand = newInheritableProperty("preLaunchCommand", "");

    /// Returns the pre-launch command property.
    public InheritableProperty<String> preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// The command that will be executed after the game exits.
    @SerializedName("postExitCommand")
    private final InheritableProperty<String> postExitCommand = newInheritableProperty("postExitCommand", "");

    /// Returns the post-exit command property.
    public InheritableProperty<String> postExitCommandProperty() {
        return postExitCommand;
    }

    /// The quick play target type.
    @SerializedName("quickPlay")
    private final InheritableProperty<QuickPlayType> quickPlay = newInheritableProperty("quickPlay", QuickPlayType.NONE);

    /// Returns the quick play target type property.
    public InheritableProperty<QuickPlayType> quickPlayProperty() {
        return quickPlay;
    }

    /// The server address for multiplayer quick play.
    @SerializedName("quickPlayMultiplayer")
    private final InheritableProperty<String> quickPlayMultiplayer = newInheritableProperty("quickPlayMultiplayer", "");

    /// Returns the multiplayer quick play target property.
    public InheritableProperty<String> quickPlayMultiplayerProperty() {
        return quickPlayMultiplayer;
    }

    /// The world folder name for singleplayer quick play.
    @SerializedName("quickPlaySingleplayer")
    private final InheritableProperty<String> quickPlaySingleplayer = newInheritableProperty("quickPlaySingleplayer", "");

    /// Returns the singleplayer quick play target property.
    public InheritableProperty<String> quickPlaySingleplayerProperty() {
        return quickPlaySingleplayer;
    }

    /// The realm ID for Realms quick play.
    @SerializedName("quickPlayRealms")
    private final InheritableProperty<String> quickPlayRealms = newInheritableProperty("quickPlayRealms", "");

    /// Returns the Realms quick play target property.
    public InheritableProperty<String> quickPlayRealmsProperty() {
        return quickPlayRealms;
    }

    /// If `true`, show the logs after game launched.
    @SerializedName("showLogs")
    private final InheritableProperty<Boolean> showLogs = newInheritableProperty("showLogs", false);

    /// Returns the show logs property.
    public InheritableProperty<Boolean> showLogsProperty() {
        return showLogs;
    }

    /// If `true`, enable debug log output.
    @SerializedName("enableDebugLogOutput")
    private final InheritableProperty<Boolean> enableDebugLogOutput = newInheritableProperty("enableDebugLogOutput", false);

    /// Returns the debug log output property.
    public InheritableProperty<Boolean> enableDebugLogOutputProperty() {
        return enableDebugLogOutput;
    }

    /// If `true`, HMCL does not patch native libraries.
    @SerializedName("notPatchNatives")
    private final InheritableProperty<Boolean> notPatchNatives = newInheritableProperty("notPatchNatives", false);

    /// Returns the native library patching property.
    public InheritableProperty<Boolean> notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// The native library directory mode.
    @SerializedName("nativesDirType")
    private final InheritableProperty<NativesDirectoryType> nativesDirType = newInheritableProperty("nativesDirType", NativesDirectoryType.VERSION_FOLDER);

    /// Returns the native library directory mode property.
    public InheritableProperty<NativesDirectoryType> nativesDirTypeProperty() {
        return nativesDirType;
    }

    /// The path to the native library directory.
    @SerializedName("nativesDir")
    private final InheritableProperty<String> nativesDir = newInheritableProperty("nativesDir", "");

    /// Returns the native library directory property.
    public InheritableProperty<String> nativesDirProperty() {
        return nativesDir;
    }

    /// If `true`, HMCL will use native GLFW.
    @SerializedName("useNativeGLFW")
    private final InheritableProperty<Boolean> useNativeGLFW = newInheritableProperty("useNativeGLFW", false);

    /// Returns the native GLFW property.
    public InheritableProperty<Boolean> useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    /// If `true`, HMCL will use native OpenAL.
    @SerializedName("useNativeOpenAL")
    private final InheritableProperty<Boolean> useNativeOpenAL = newInheritableProperty("useNativeOpenAL", false);

    /// Returns the native OpenAL property.
    public InheritableProperty<Boolean> useNativeOpenALProperty() {
        return useNativeOpenAL;
    }

    /// Converts a legacy setting into a named global game setting.
    public static Global fromVersionSetting(String name, VersionSetting source) {
        Global target = new Global();
        target.nameProperty().setValue(name);
        copyCommonProperties(source, target);
        return target;
    }

    /// Converts a legacy setting into an instance game setting.
    public static Instance fromVersionSetting(@Nullable UUID parent, VersionSetting source, boolean copyValues) {
        Instance target = new Instance();
        target.parentProperty().setValue(parent);
        target.iconProperty().setValue(source.getVersionIcon());
        target.isolationProperty().setValue(source.getGameDirType() == GameDirectoryType.VERSION_FOLDER);
        if (copyValues) {
            copyCommonProperties(source, target);
            target.getOverrideGroups().add(MEMORY_SETTINGS);
            target.getOverrideGroups().add(JVM_OPTIONS);
            target.getOverrideGroups().add(GAME_ARGUMENTS);
            target.getOverrideGroups().add(ENVIRONMENT_VARIABLES);
        }
        return target;
    }

    private static void copyCommonProperties(VersionSetting source, GameSetting target) {
        target.javaTypeProperty().setValue(source.getJavaVersionType());
        target.javaVersionProperty().setValue(empty(source.getJavaVersion()));
        target.customJavaPathProperty().setValue(empty(source.getJavaDir()));
        target.defaultJavaPathProperty().setValue(empty(source.getDefaultJavaPath()));

        target.jvmOptionsProperty().setValue(empty(source.getJavaArgs()));
        target.noJVMOptionsProperty().setValue(source.isNoJVMArgs());
        target.noOptimizingJVMOptionsProperty().setValue(source.isNoOptimizingJVMArgs());
        target.notCheckJVMProperty().setValue(source.isNotCheckJVM());
        target.notCheckGameProperty().setValue(source.isNotCheckGame());

        target.autoMemoryProperty().setValue(source.isAutoMemory());
        target.minMemoryProperty().setValue(source.getMinMemory());
        target.maxMemoryProperty().setValue(source.getMaxMemory());
        target.permSizeProperty().setValue(empty(source.getPermSize()));

        target.windowTypeProperty().setValue(source.isFullscreen() ? GameWindowType.FULLSCREEN : GameWindowType.WINDOWED);
        target.widthProperty().setValue((double) source.getWidth());
        target.heightProperty().setValue((double) source.getHeight());
        target.gameDirTypeProperty().setValue(source.getGameDirType());
        target.runningDirProperty().setValue(empty(source.getGameDir()));

        target.processPriorityProperty().setValue(source.getProcessPriority());
        target.launcherVisibilityProperty().setValue(source.getLauncherVisibility());
        target.gameArgsProperty().setValue(empty(source.getMinecraftArgs()));
        target.graphicsBackendProperty().setValue(source.getGraphicsBackend());
        target.rendererProperty().setValue(source.getRenderer());
        target.environmentVariablesProperty().setValue(empty(source.getEnvironmentVariables()));
        target.commandWrapperProperty().setValue(empty(source.getWrapper()));
        target.preLaunchCommandProperty().setValue(empty(source.getPreLaunchCommand()));
        target.postExitCommandProperty().setValue(empty(source.getPostExitCommand()));

        if (StringUtils.isBlank(source.getServerIp())) {
            target.quickPlayProperty().setValue(QuickPlayType.NONE);
        } else {
            target.quickPlayProperty().setValue(QuickPlayType.MULTIPLAYER);
            target.quickPlayMultiplayerProperty().setValue(source.getServerIp());
        }

        target.showLogsProperty().setValue(source.isShowLogs());
        target.enableDebugLogOutputProperty().setValue(source.isEnableDebugLogOutput());
        target.notPatchNativesProperty().setValue(source.isNotPatchNatives());
        target.nativesDirTypeProperty().setValue(source.getNativesDirType());
        target.nativesDirProperty().setValue(empty(source.getNativesDir()));
        target.useNativeGLFWProperty().setValue(source.isUseNativeGLFW());
        target.useNativeOpenALProperty().setValue(source.isUseNativeOpenAL());
    }

    /// Resolves a global setting and an optional instance setting into launch-time values.
    public static Effective resolve(Global global, @Nullable Instance instance) {
        return new Effective(global, instance);
    }

    private static boolean overrides(@Nullable Instance instance, SettingGroup group) {
        return instance != null && instance.getOverrideGroups().contains(group);
    }

    private static String empty(@Nullable String value) {
        return value != null ? value : "";
    }

    private static <T> T direct(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
    }

    private static <T> T grouped(Global global, @Nullable Instance instance, SettingGroup group, Function<GameSetting, SettingProperty<T>> propertyGetter) {
        if (overrides(instance, group)) {
            T value = propertyGetter.apply(instance).getValue();
            if (value != null) {
                return value;
            }
        }
        return direct(propertyGetter.apply(global));
    }

    private static <T> T inherited(Global global, @Nullable Instance instance, Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        if (instance != null) {
            T value = propertyGetter.apply(instance).getValue();
            if (value != null) {
                return value;
            }
        }

        InheritableProperty<T> globalProperty = propertyGetter.apply(global);
        T value = globalProperty.getValue();
        return value != null ? value : globalProperty.defaultValue();
    }

    private static String merged(Global global, @Nullable Instance instance, SettingGroup group, Function<GameSetting, SettingProperty<String>> propertyGetter) {
        String globalValue = empty(propertyGetter.apply(global).getValue());
        if (instance == null) {
            return globalValue;
        }

        String instanceValue = empty(propertyGetter.apply(instance).getValue());
        if (overrides(instance, group)) {
            return instanceValue;
        }
        if (StringUtils.isBlank(globalValue)) {
            return instanceValue;
        }
        if (StringUtils.isBlank(instanceValue)) {
            return globalValue;
        }
        return globalValue + " " + instanceValue;
    }

    /// Launch-time effective game setting.
    public static final class Effective {
        private final Global global;
        private final @Nullable Instance instance;

        private Effective(Global global, @Nullable Instance instance) {
            this.global = Objects.requireNonNull(global);
            this.instance = instance;
        }

        /// Returns the parent global setting.
        public Global getGlobal() {
            return global;
        }

        /// Returns the instance setting, if one exists.
        public @Nullable Instance getInstance() {
            return instance;
        }

        /// Returns the effective Java selection mode.
        public JavaVersionType getJavaVersionType() {
            return inherited(global, instance, GameSetting::javaTypeProperty);
        }

        /// Returns the effective Java version text.
        public String getJavaVersion() {
            if (instance != null && instance.javaTypeProperty().getValue() != null) {
                return empty(instance.javaVersionProperty().getValue());
            }
            return empty(global.javaVersionProperty().getValue());
        }

        /// Returns the effective custom Java executable path.
        public String getCustomJavaPath() {
            if (instance != null && instance.javaTypeProperty().getValue() != null) {
                return empty(instance.customJavaPathProperty().getValue());
            }
            return empty(global.customJavaPathProperty().getValue());
        }

        /// Returns the effective default Java executable path.
        public String getDefaultJavaPath() {
            if (instance != null && instance.javaTypeProperty().getValue() != null) {
                return empty(instance.defaultJavaPathProperty().getValue());
            }
            return empty(global.defaultJavaPathProperty().getValue());
        }

        /// Switches the effective Java selection back to automatic mode.
        public void setJavaAutoSelected() {
            GameSetting target = instance != null && instance.javaTypeProperty().getValue() != null ? instance : global;
            target.javaTypeProperty().setValue(JavaVersionType.AUTO);
            target.javaVersionProperty().setValue("");
            target.customJavaPathProperty().setValue("");
            target.defaultJavaPathProperty().setValue("");
        }

        /// Finds the effective Java runtime.
        public @Nullable JavaRuntime getJava(@Nullable GameVersionNumber gameVersion, @Nullable Version version) throws InterruptedException {
            switch (getJavaVersionType()) {
                case DEFAULT:
                    return JavaRuntime.getDefault();
                case AUTO:
                    return JavaManager.findSuitableJava(gameVersion, version);
                case CUSTOM:
                    try {
                        return JavaManager.getJava(Path.of(getCustomJavaPath()));
                    } catch (IOException | InvalidPathException e) {
                        return null;
                    }
                case VERSION: {
                    String javaVersion = getJavaVersion();
                    if (StringUtils.isBlank(javaVersion)) {
                        return JavaManager.findSuitableJava(gameVersion, version);
                    }

                    int majorVersion = -1;
                    try {
                        majorVersion = Integer.parseInt(javaVersion);
                    } catch (NumberFormatException ignored) {
                    }

                    if (majorVersion < 0) {
                        LOG.warning("Invalid Java version: " + javaVersion);
                        return null;
                    }

                    final int finalMajorVersion = majorVersion;
                    Collection<JavaRuntime> allJava = JavaManager.getAllJava().stream()
                            .filter(it -> it.getParsedVersion() == finalMajorVersion)
                            .collect(Collectors.toList());
                    return JavaManager.findSuitableJava(allJava, gameVersion, version);
                }
                case DETECTED: {
                    String javaVersion = getJavaVersion();
                    if (StringUtils.isBlank(javaVersion)) {
                        return JavaManager.findSuitableJava(gameVersion, version);
                    }

                    try {
                        String defaultJavaPath = getDefaultJavaPath();
                        if (StringUtils.isNotBlank(defaultJavaPath)) {
                            JavaRuntime java = JavaManager.getJava(Path.of(defaultJavaPath).toRealPath());
                            if (java.getVersion().equals(javaVersion)) {
                                return java;
                            }
                        }
                    } catch (IOException | InvalidPathException ignored) {
                    }

                    for (JavaRuntime java : JavaManager.getAllJava()) {
                        if (java.getVersion().equals(javaVersion)) {
                            return java;
                        }
                    }

                    return null;
                }
                default:
                    throw new AssertionError("Java Type: " + getJavaVersionType());
            }
        }

        /// Returns the effective JVM option text.
        public String getJVMOptions() {
            return merged(global, instance, JVM_OPTIONS, GameSetting::jvmOptionsProperty);
        }

        /// Returns whether generated JVM options are disabled.
        public boolean isNoJVMOptions() {
            return inherited(global, instance, GameSetting::noJVMOptionsProperty);
        }

        /// Returns whether generated optimizing JVM options are disabled.
        public boolean isNoOptimizingJVMOptions() {
            return inherited(global, instance, GameSetting::noOptimizingJVMOptionsProperty);
        }

        /// Returns whether JVM validity checks are disabled.
        public boolean isNotCheckJVM() {
            return inherited(global, instance, GameSetting::notCheckJVMProperty);
        }

        /// Returns whether game completeness checks are disabled.
        public boolean isNotCheckGame() {
            return inherited(global, instance, GameSetting::notCheckGameProperty);
        }

        /// Returns whether automatic memory allocation is enabled.
        public boolean isAutoMemory() {
            return grouped(global, instance, MEMORY_SETTINGS, GameSetting::autoMemoryProperty);
        }

        /// Returns the effective minimum heap memory in MiB.
        public @Nullable Integer getMinMemory() {
            return grouped(global, instance, MEMORY_SETTINGS, GameSetting::minMemoryProperty);
        }

        /// Returns the effective maximum heap memory in MiB.
        public int getMaxMemory() {
            Integer value = grouped(global, instance, MEMORY_SETTINGS, GameSetting::maxMemoryProperty);
            return value != null && value > 0 ? value : SUGGESTED_MEMORY;
        }

        /// Returns the effective permanent generation or metaspace size text.
        public String getPermSize() {
            return empty(grouped(global, instance, MEMORY_SETTINGS, GameSetting::permSizeProperty));
        }

        /// Returns the effective game window mode.
        public GameWindowType getWindowType() {
            return inherited(global, instance, GameSetting::windowTypeProperty);
        }

        /// Returns the effective game window width.
        public int getWidth() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(inherited(global, instance, GameSetting::widthProperty))), 0));
        }

        /// Returns the effective game window height.
        public int getHeight() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(inherited(global, instance, GameSetting::heightProperty))), 0));
        }

        /// Returns the effective game directory mode.
        public GameDirectoryType getGameDirType() {
            return inherited(global, instance, GameSetting::gameDirTypeProperty);
        }

        /// Returns the effective custom run directory.
        public String getRunningDir() {
            return empty(inherited(global, instance, GameSetting::runningDirProperty));
        }

        /// Returns the effective process priority.
        public ProcessPriority getProcessPriority() {
            return inherited(global, instance, GameSetting::processPriorityProperty);
        }

        /// Returns the effective launcher visibility.
        public LauncherVisibility getLauncherVisibility() {
            return inherited(global, instance, GameSetting::launcherVisibilityProperty);
        }

        /// Returns the effective game arguments.
        public String getGameArgs() {
            return merged(global, instance, GAME_ARGUMENTS, GameSetting::gameArgsProperty);
        }

        /// Returns the effective graphics API.
        public GraphicsAPI getGraphicsBackend() {
            return inherited(global, instance, GameSetting::graphicsBackendProperty);
        }

        /// Returns the effective renderer.
        public Renderer getRenderer() {
            return inherited(global, instance, GameSetting::rendererProperty);
        }

        /// Returns the effective environment variables.
        public String getEnvironmentVariables() {
            return merged(global, instance, ENVIRONMENT_VARIABLES, GameSetting::environmentVariablesProperty);
        }

        /// Returns the effective command wrapper.
        public String getCommandWrapper() {
            return empty(inherited(global, instance, GameSetting::commandWrapperProperty));
        }

        /// Returns the effective pre-launch command.
        public String getPreLaunchCommand() {
            return empty(inherited(global, instance, GameSetting::preLaunchCommandProperty));
        }

        /// Returns the effective post-exit command.
        public String getPostExitCommand() {
            return empty(inherited(global, instance, GameSetting::postExitCommandProperty));
        }

        /// Returns the effective quick play type.
        public QuickPlayType getQuickPlay() {
            return inherited(global, instance, GameSetting::quickPlayProperty);
        }

        /// Returns the effective quick play option.
        public @Nullable QuickPlayOption getQuickPlayOption() {
            return switch (getQuickPlay()) {
                case NONE -> null;
                case MULTIPLAYER -> {
                    String server = empty(inherited(global, instance, GameSetting::quickPlayMultiplayerProperty));
                    yield StringUtils.isBlank(server) ? null : new QuickPlayOption.MultiPlayer(server);
                }
                case SINGLEPLAYER -> {
                    String world = empty(inherited(global, instance, GameSetting::quickPlaySingleplayerProperty));
                    yield StringUtils.isBlank(world) ? null : new QuickPlayOption.SinglePlayer(world);
                }
                case REALMS -> {
                    String realm = empty(inherited(global, instance, GameSetting::quickPlayRealmsProperty));
                    yield StringUtils.isBlank(realm) ? null : new QuickPlayOption.Realm(realm);
                }
            };
        }

        /// Returns whether logs should be shown after launch.
        public boolean isShowLogs() {
            return inherited(global, instance, GameSetting::showLogsProperty);
        }

        /// Returns whether debug log output is enabled.
        public boolean isEnableDebugLogOutput() {
            return inherited(global, instance, GameSetting::enableDebugLogOutputProperty);
        }

        /// Returns whether native library patching is disabled.
        public boolean isNotPatchNatives() {
            return inherited(global, instance, GameSetting::notPatchNativesProperty);
        }

        /// Returns the effective native directory mode.
        public NativesDirectoryType getNativesDirType() {
            return inherited(global, instance, GameSetting::nativesDirTypeProperty);
        }

        /// Returns the effective native directory.
        public String getNativesDir() {
            return empty(inherited(global, instance, GameSetting::nativesDirProperty));
        }

        /// Returns whether native GLFW should be used.
        public boolean isUseNativeGLFW() {
            return inherited(global, instance, GameSetting::useNativeGLFWProperty);
        }

        /// Returns whether native OpenAL should be used.
        public boolean isUseNativeOpenAL() {
            return inherited(global, instance, GameSetting::useNativeOpenALProperty);
        }
    }
}
