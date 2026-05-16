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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
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
import java.lang.reflect.Type;
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

        /// Setting property names overridden by this instance.
        @SerializedName("overrideProperties")
        private final ObservableSet<String> overrideProperties = FXCollections.observableSet();

        /// Returns the overridden setting property names.
        public ObservableSet<String> getOverrideProperties() {
            return overrideProperties;
        }

        /// JSON adapter for instance settings.
        public static final class Adapter extends ObservableSetting.Adapter<Instance> {
            @Override
            protected Instance createInstance() {
                return new Instance();
            }

            @Override
            public Instance deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                    throws JsonParseException {
                Instance setting = super.deserialize(json, typeOfT, context);
                migrateLegacyRenderer(setting);
                return setting;
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

            @Override
            public Global deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                    throws JsonParseException {
                Global setting = super.deserialize(json, typeOfT, context);
                migrateLegacyRenderer(setting);
                return setting;
            }
        }
    }

    /// Creates a new setting property without a default value.
    protected final <T> SettingProperty<T> newSettingProperty(String name) {
        return new SimpleSettingProperty<>(this, name);
    }

    /// Creates a new setting property with the given default value.
    protected final <T> SettingProperty<T> newSettingProperty(String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, name, defaultValue);
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

    /// Property name for customized JVM options.
    public static final String PROPERTY_JVM_OPTIONS = "jvmOptions";

    /// User customized JVM options.
    @SerializedName(PROPERTY_JVM_OPTIONS)
    private final SettingProperty<String> jvmOptions = newSettingProperty(PROPERTY_JVM_OPTIONS, "");

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

    /// Property name for automatic memory allocation.
    public static final String PROPERTY_AUTO_MEMORY = "autoMemory";

    /// If `true`, HMCL will automatically adjust memory allocation.
    @SerializedName(PROPERTY_AUTO_MEMORY)
    private final SettingProperty<Boolean> autoMemory = newSettingProperty(PROPERTY_AUTO_MEMORY, true);

    /// Returns the automatic memory allocation property.
    public SettingProperty<Boolean> autoMemoryProperty() {
        return autoMemory;
    }

    /// Property name for the minimum heap memory.
    public static final String PROPERTY_MIN_MEMORY = "minMemory";

    /// The minimum heap memory in MiB.
    @SerializedName(PROPERTY_MIN_MEMORY)
    private final SettingProperty<@Nullable Integer> minMemory = newSettingProperty(PROPERTY_MIN_MEMORY);

    /// Returns the minimum heap memory property.
    public SettingProperty<@Nullable Integer> minMemoryProperty() {
        return minMemory;
    }

    /// Property name for the maximum heap memory.
    public static final String PROPERTY_MAX_MEMORY = "maxMemory";

    /// The maximum heap memory in MiB.
    @SerializedName(PROPERTY_MAX_MEMORY)
    private final SettingProperty<Integer> maxMemory = newSettingProperty(PROPERTY_MAX_MEMORY, SUGGESTED_MEMORY);

    /// Returns the maximum heap memory property.
    public SettingProperty<Integer> maxMemoryProperty() {
        return maxMemory;
    }

    /// Property name for the permanent generation or metaspace size.
    public static final String PROPERTY_PERM_SIZE = "permSize";

    /// The permanent generation or metaspace size in MiB.
    @SerializedName(PROPERTY_PERM_SIZE)
    private final SettingProperty<String> permSize = newSettingProperty(PROPERTY_PERM_SIZE, "");

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

    /// Property name for customized game arguments.
    public static final String PROPERTY_GAME_ARGS = "gameArgs";

    /// User customized arguments passed to the game.
    @SerializedName(PROPERTY_GAME_ARGS)
    private final SettingProperty<String> gameArgs = newSettingProperty(PROPERTY_GAME_ARGS, "");

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

    /// Legacy property name for the renderer used by the game before OpenGL and Vulkan settings were split.
    private static final String PROPERTY_LEGACY_RENDERER = "renderer";

    /// Property name for the OpenGL renderer.
    public static final String PROPERTY_OPENGL_RENDERER = "openGLRenderer";

    /// The OpenGL renderer used by the game.
    @SerializedName(PROPERTY_OPENGL_RENDERER)
    private final InheritableProperty<Renderer> openGLRenderer =
            newInheritableProperty(PROPERTY_OPENGL_RENDERER, Renderer.DEFAULT);

    /// Returns the OpenGL renderer property.
    public InheritableProperty<Renderer> openGLRendererProperty() {
        return openGLRenderer;
    }

    /// Property name for the Vulkan renderer.
    public static final String PROPERTY_VULKAN_RENDERER = "vulkanRenderer";

    /// The Vulkan renderer used by the game.
    @SerializedName(PROPERTY_VULKAN_RENDERER)
    private final InheritableProperty<Renderer> vulkanRenderer =
            newInheritableProperty(PROPERTY_VULKAN_RENDERER, Renderer.DEFAULT);

    /// Returns the Vulkan renderer property.
    public InheritableProperty<Renderer> vulkanRendererProperty() {
        return vulkanRenderer;
    }

    /// Property name for customized environment variables.
    public static final String PROPERTY_ENVIRONMENT_VARIABLES = "environmentVariables";

    /// User customized environment variables passed to the game.
    @SerializedName(PROPERTY_ENVIRONMENT_VARIABLES)
    private final SettingProperty<String> environmentVariables = newSettingProperty(PROPERTY_ENVIRONMENT_VARIABLES, "");

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

    /// Property name for disabling native library patching.
    public static final String PROPERTY_NOT_PATCH_NATIVES = "notPatchNatives";

    /// If `true`, HMCL does not patch native libraries.
    @SerializedName(PROPERTY_NOT_PATCH_NATIVES)
    private final SettingProperty<Boolean> notPatchNatives = newSettingProperty(PROPERTY_NOT_PATCH_NATIVES, false);

    /// Returns the native library patching property.
    public SettingProperty<Boolean> notPatchNativesProperty() {
        return notPatchNatives;
    }

    /// Property name for the native library directory mode.
    public static final String PROPERTY_NATIVES_DIR_TYPE = "nativesDirType";

    /// The native library directory mode.
    @SerializedName(PROPERTY_NATIVES_DIR_TYPE)
    private final SettingProperty<NativesDirectoryType> nativesDirType = newSettingProperty(PROPERTY_NATIVES_DIR_TYPE, NativesDirectoryType.VERSION_FOLDER);

    /// Returns the native library directory mode property.
    public SettingProperty<NativesDirectoryType> nativesDirTypeProperty() {
        return nativesDirType;
    }

    /// Property name for the native library directory path.
    public static final String PROPERTY_NATIVES_DIR = "nativesDir";

    /// The path to the native library directory.
    @SerializedName(PROPERTY_NATIVES_DIR)
    private final SettingProperty<String> nativesDir = newSettingProperty(PROPERTY_NATIVES_DIR, "");

    /// Returns the native library directory property.
    public SettingProperty<String> nativesDirProperty() {
        return nativesDir;
    }

    /// Property name for using native GLFW.
    public static final String PROPERTY_USE_NATIVE_GLFW = "useNativeGLFW";

    /// If `true`, HMCL will use native GLFW.
    @SerializedName(PROPERTY_USE_NATIVE_GLFW)
    private final SettingProperty<Boolean> useNativeGLFW = newSettingProperty(PROPERTY_USE_NATIVE_GLFW, false);

    /// Returns the native GLFW property.
    public SettingProperty<Boolean> useNativeGLFWProperty() {
        return useNativeGLFW;
    }

    /// Property name for using native OpenAL.
    public static final String PROPERTY_USE_NATIVE_OPENAL = "useNativeOpenAL";

    /// If `true`, HMCL will use native OpenAL.
    @SerializedName(PROPERTY_USE_NATIVE_OPENAL)
    private final SettingProperty<Boolean> useNativeOpenAL = newSettingProperty(PROPERTY_USE_NATIVE_OPENAL, false);

    /// Returns the native OpenAL property.
    public SettingProperty<Boolean> useNativeOpenALProperty() {
        return useNativeOpenAL;
    }

    /// Converts a legacy setting into a named global game setting.
    public static Global fromVersionSetting(String name, VersionSetting source) {
        Global target = new Global();
        target.nameProperty().setValue(name);
        if (source.getGameDirType() == GameDirectoryType.VERSION_FOLDER) {
            target.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);
        }
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
            target.getOverrideProperties().addAll(java.util.List.of(
                    PROPERTY_JVM_OPTIONS,
                    PROPERTY_AUTO_MEMORY,
                    PROPERTY_MIN_MEMORY,
                    PROPERTY_MAX_MEMORY,
                    PROPERTY_PERM_SIZE,
                    PROPERTY_GAME_ARGS,
                    PROPERTY_ENVIRONMENT_VARIABLES,
                    PROPERTY_NOT_PATCH_NATIVES,
                    PROPERTY_NATIVES_DIR_TYPE,
                    PROPERTY_NATIVES_DIR,
                    PROPERTY_USE_NATIVE_GLFW,
                    PROPERTY_USE_NATIVE_OPENAL
            ));
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
        target.gameDirTypeProperty().setValue(source.getGameDirType() == GameDirectoryType.VERSION_FOLDER
                ? GameDirectoryType.ROOT_FOLDER
                : source.getGameDirType());
        target.runningDirProperty().setValue(empty(source.getGameDir()));

        target.processPriorityProperty().setValue(source.getProcessPriority());
        target.launcherVisibilityProperty().setValue(source.getLauncherVisibility());
        target.gameArgsProperty().setValue(empty(source.getMinecraftArgs()));
        target.graphicsBackendProperty().setValue(source.getGraphicsBackend());
        setRendererForApi(target, source.getRenderer(), source.getGraphicsBackend());
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

    private static void migrateLegacyRenderer(@Nullable GameSetting setting) {
        if (setting == null) {
            return;
        }

        JsonElement legacyRenderer = setting.unknownFields.remove(PROPERTY_LEGACY_RENDERER);
        if (legacyRenderer == null || legacyRenderer.isJsonNull()) {
            return;
        }

        Renderer renderer = parseLegacyRenderer(legacyRenderer);
        if (renderer != null) {
            setRendererForApi(setting, renderer, setting.graphicsBackendProperty().getValue());
        }
    }

    private static @Nullable Renderer parseLegacyRenderer(JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return Renderer.of(primitive.getAsString());
        }

        if (element.isJsonObject()) {
            JsonElement name = element.getAsJsonObject().get("name");
            if (name instanceof JsonPrimitive primitive && primitive.isString()) {
                return Renderer.of(primitive.getAsString());
            }
        }

        return null;
    }

    private static void setRendererForApi(GameSetting setting, Renderer renderer, @Nullable GraphicsAPI fallbackApi) {
        if (renderer instanceof Renderer.Driver driver) {
            rendererPropertyForApi(setting, driver.api()).setValue(renderer);
            if (fallbackApi == null || fallbackApi == GraphicsAPI.DEFAULT) {
                setting.graphicsBackendProperty().setValue(driver.api());
            }
            return;
        }

        if (fallbackApi == GraphicsAPI.OPENGL || fallbackApi == GraphicsAPI.VULKAN) {
            rendererPropertyForApi(setting, fallbackApi).setValue(renderer);
        } else {
            setting.openGLRendererProperty().setValue(renderer);
            setting.vulkanRendererProperty().setValue(renderer);
        }
    }

    private static InheritableProperty<Renderer> rendererPropertyForApi(GameSetting setting, GraphicsAPI api) {
        return switch (api) {
            case OPENGL -> setting.openGLRendererProperty();
            case VULKAN -> setting.vulkanRendererProperty();
            case DEFAULT -> throw new IllegalArgumentException("The default graphics API has no renderer property");
        };
    }

    private static Renderer selectRenderer(GraphicsAPI api, @Nullable Renderer renderer) {
        if (renderer instanceof Renderer.Driver driver && driver.api() != api) {
            return Renderer.DEFAULT;
        }

        return renderer != null ? renderer : Renderer.DEFAULT;
    }

    /// Resolves a global setting and an optional instance setting into launch-time values.
    public static Effective resolve(Global global, @Nullable Instance instance) {
        return new Effective(global, instance);
    }

    private static String empty(@Nullable String value) {
        return value != null ? value : "";
    }

    private static <T> T direct(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
    }

    private static <T> T inherited(Global global, @Nullable Instance instance, Function<GameSetting, SettingProperty<T>> propertyGetter) {
        if (instance != null) {
            SettingProperty<T> property = propertyGetter.apply(instance);
            if (instance.getOverrideProperties().contains(property.getName())) {
                return direct(property);
            }
        }
        return direct(propertyGetter.apply(global));
    }

    private static <T> T inheritable(Global global, @Nullable Instance instance, Function<GameSetting, InheritableProperty<T>> propertyGetter) {
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
            return inheritable(global, instance, GameSetting::javaTypeProperty);
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
            return empty(inherited(global, instance, GameSetting::jvmOptionsProperty));
        }

        /// Returns whether generated JVM options are disabled.
        public boolean isNoJVMOptions() {
            return inheritable(global, instance, GameSetting::noJVMOptionsProperty);
        }

        /// Returns whether generated optimizing JVM options are disabled.
        public boolean isNoOptimizingJVMOptions() {
            return inheritable(global, instance, GameSetting::noOptimizingJVMOptionsProperty);
        }

        /// Returns whether JVM validity checks are disabled.
        public boolean isNotCheckJVM() {
            return inheritable(global, instance, GameSetting::notCheckJVMProperty);
        }

        /// Returns whether game completeness checks are disabled.
        public boolean isNotCheckGame() {
            return inheritable(global, instance, GameSetting::notCheckGameProperty);
        }

        /// Returns whether automatic memory allocation is enabled.
        public boolean isAutoMemory() {
            return inherited(global, instance, GameSetting::autoMemoryProperty);
        }

        /// Returns the effective minimum heap memory in MiB.
        public @Nullable Integer getMinMemory() {
            return inherited(global, instance, GameSetting::minMemoryProperty);
        }

        /// Returns the effective maximum heap memory in MiB.
        public int getMaxMemory() {
            Integer value = inherited(global, instance, GameSetting::maxMemoryProperty);
            return value != null && value > 0 ? value : SUGGESTED_MEMORY;
        }

        /// Returns the effective permanent generation or metaspace size text.
        public String getPermSize() {
            return empty(inherited(global, instance, GameSetting::permSizeProperty));
        }

        /// Returns the effective game window mode.
        public GameWindowType getWindowType() {
            return inheritable(global, instance, GameSetting::windowTypeProperty);
        }

        /// Returns the effective game window width.
        public int getWidth() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(inheritable(global, instance, GameSetting::widthProperty))), 0));
        }

        /// Returns the effective game window height.
        public int getHeight() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(inheritable(global, instance, GameSetting::heightProperty))), 0));
        }

        /// Returns the effective game directory mode.
        public GameDirectoryType getGameDirType() {
            return inheritable(global, instance, GameSetting::gameDirTypeProperty);
        }

        /// Returns the effective custom run directory.
        public String getRunningDir() {
            return empty(inheritable(global, instance, GameSetting::runningDirProperty));
        }

        /// Returns the effective process priority.
        public ProcessPriority getProcessPriority() {
            return inheritable(global, instance, GameSetting::processPriorityProperty);
        }

        /// Returns the effective launcher visibility.
        public LauncherVisibility getLauncherVisibility() {
            return inheritable(global, instance, GameSetting::launcherVisibilityProperty);
        }

        /// Returns the effective game arguments.
        public String getGameArgs() {
            return empty(inherited(global, instance, GameSetting::gameArgsProperty));
        }

        /// Returns the effective graphics API.
        public GraphicsAPI getGraphicsBackend() {
            return inheritable(global, instance, GameSetting::graphicsBackendProperty);
        }

        /// Returns the effective OpenGL renderer.
        public Renderer getOpenGLRenderer() {
            return selectRenderer(GraphicsAPI.OPENGL, inheritable(global, instance, GameSetting::openGLRendererProperty));
        }

        /// Returns the effective Vulkan renderer.
        public Renderer getVulkanRenderer() {
            return selectRenderer(GraphicsAPI.VULKAN, inheritable(global, instance, GameSetting::vulkanRendererProperty));
        }

        /// Returns the effective renderer.
        public Renderer getRenderer() {
            return switch (getGraphicsBackend()) {
                case OPENGL -> getOpenGLRenderer();
                case VULKAN -> getVulkanRenderer();
                case DEFAULT -> Renderer.DEFAULT;
            };
        }

        /// Returns the effective environment variables.
        public String getEnvironmentVariables() {
            return empty(inherited(global, instance, GameSetting::environmentVariablesProperty));
        }

        /// Returns the effective command wrapper.
        public String getCommandWrapper() {
            return empty(inheritable(global, instance, GameSetting::commandWrapperProperty));
        }

        /// Returns the effective pre-launch command.
        public String getPreLaunchCommand() {
            return empty(inheritable(global, instance, GameSetting::preLaunchCommandProperty));
        }

        /// Returns the effective post-exit command.
        public String getPostExitCommand() {
            return empty(inheritable(global, instance, GameSetting::postExitCommandProperty));
        }

        /// Returns the effective quick play type.
        public QuickPlayType getQuickPlay() {
            return inheritable(global, instance, GameSetting::quickPlayProperty);
        }

        /// Returns the effective quick play option.
        public @Nullable QuickPlayOption getQuickPlayOption() {
            return switch (getQuickPlay()) {
                case NONE -> null;
                case MULTIPLAYER -> {
                    String server = empty(inheritable(global, instance, GameSetting::quickPlayMultiplayerProperty));
                    yield StringUtils.isBlank(server) ? null : new QuickPlayOption.MultiPlayer(server);
                }
                case SINGLEPLAYER -> {
                    String world = empty(inheritable(global, instance, GameSetting::quickPlaySingleplayerProperty));
                    yield StringUtils.isBlank(world) ? null : new QuickPlayOption.SinglePlayer(world);
                }
                case REALMS -> {
                    String realm = empty(inheritable(global, instance, GameSetting::quickPlayRealmsProperty));
                    yield StringUtils.isBlank(realm) ? null : new QuickPlayOption.Realm(realm);
                }
            };
        }

        /// Returns whether logs should be shown after launch.
        public boolean isShowLogs() {
            return inheritable(global, instance, GameSetting::showLogsProperty);
        }

        /// Returns whether debug log output is enabled.
        public boolean isEnableDebugLogOutput() {
            return inheritable(global, instance, GameSetting::enableDebugLogOutputProperty);
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
