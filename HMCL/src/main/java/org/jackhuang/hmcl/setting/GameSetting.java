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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
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
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnknownNullability;

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
    /// Suggested maximum heap memory in MiB.
    static final int SUGGESTED_MEMORY;

    static {
        double totalMemoryMB = MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
        SUGGESTED_MEMORY = totalMemoryMB >= 32768
                ? 8192
                : Integer.max((int) (Math.round(totalMemoryMB / 4.0 / 128.0) * 128), 256);
    }

    /// Instance-specific game setting.
    @JsonAdapter(Instance.Adapter.class)
    @JsonSerializable
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
            public JsonElement serialize(Instance setting, Type typeOfSrc, JsonSerializationContext context) {
                JsonElement serialized = super.serialize(setting, typeOfSrc, context);
                if (serialized instanceof JsonObject object) {
                    for (String propertyName : OVERRIDABLE_PROPERTY_NAMES) {
                        if (!setting.getOverrideProperties().contains(propertyName)) {
                            object.remove(propertyName);
                        }
                    }
                    if (!setting.getOverrideProperties().contains(PROPERTY_JAVA_TYPE)) {
                        object.remove("javaVersion");
                        object.remove("customJavaPath");
                        object.remove("defaultJavaPath");
                    }
                }
                return serialized;
            }
        }
    }

    /// Reusable global game setting.
    @JsonAdapter(Global.Adapter.class)
    @JsonSerializable
    public static final class Global extends GameSetting {
        /// Creates a global setting with generated identity.
        public Global() {
            this(UUID.randomUUID());
        }

        /// Creates a global setting with the given identity.
        public Global(UUID id) {
            register();
            this.id.setValue(id);
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

        /// The profile name that this global setting was migrated from.
        @SerializedName("legacyProfile")
        private final SettingProperty<@Nullable String> legacyProfile = newSettingProperty("legacyProfile");

        /// Returns the migrated profile name property.
        public SettingProperty<@Nullable String> legacyProfileProperty() {
            return legacyProfile;
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
    protected final <T extends @UnknownNullability Object> SettingProperty<T> newSettingProperty(String name) {
        return new SimpleSettingProperty<>(this, name, null);
    }

    /// Creates a new setting property with the given default value.
    protected final <T extends @UnknownNullability Object> SettingProperty<T> newSettingProperty(String name, T defaultValue) {
        return new SimpleSettingProperty<>(this, name, defaultValue);
    }

    /// Creates a new inheritable property.
    protected final <T extends @UnknownNullability Object> InheritableProperty<T> newInheritableProperty(String name, T defaultValue) {
        return new SimpleInheritableProperty<>(this, name, defaultValue);
    }

    /// Property name for the Java selection mode.
    public static final String PROPERTY_JAVA_TYPE = "javaType";

    /// The Java selection mode.
    @SerializedName(PROPERTY_JAVA_TYPE)
    private final InheritableProperty<JavaVersionType> javaType = newInheritableProperty(PROPERTY_JAVA_TYPE, JavaVersionType.AUTO);

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

    /// Property name for disabling generated JVM options.
    public static final String PROPERTY_NO_JVM_OPTIONS = "noJVMOptions";

    /// If `true`, HMCL will not use default JVM arguments.
    @SerializedName(PROPERTY_NO_JVM_OPTIONS)
    private final InheritableProperty<Boolean> noJVMOptions = newInheritableProperty(PROPERTY_NO_JVM_OPTIONS, false);

    /// Returns the no generated JVM options property.
    public InheritableProperty<Boolean> noJVMOptionsProperty() {
        return noJVMOptions;
    }

    /// Property name for disabling generated optimizing JVM options.
    public static final String PROPERTY_NO_OPTIMIZING_JVM_OPTIONS = "noOptimizingJVMOptions";

    /// If `true`, HMCL will not use the default optimizing JVM options.
    @SerializedName(PROPERTY_NO_OPTIMIZING_JVM_OPTIONS)
    private final InheritableProperty<Boolean> noOptimizingJVMOptions = newInheritableProperty(PROPERTY_NO_OPTIMIZING_JVM_OPTIONS, false);

    /// Returns the no optimizing JVM options property.
    public InheritableProperty<Boolean> noOptimizingJVMOptionsProperty() {
        return noOptimizingJVMOptions;
    }

    /// Property name for disabling JVM validity checks.
    public static final String PROPERTY_NOT_CHECK_JVM = "notCheckJVM";

    /// If `true`, HMCL does not check JVM validity.
    @SerializedName(PROPERTY_NOT_CHECK_JVM)
    private final InheritableProperty<Boolean> notCheckJVM = newInheritableProperty(PROPERTY_NOT_CHECK_JVM, false);

    /// Returns the JVM validity check property.
    public InheritableProperty<Boolean> notCheckJVMProperty() {
        return notCheckJVM;
    }

    /// Property name for disabling game completeness checks.
    public static final String PROPERTY_NOT_CHECK_GAME = "notCheckGame";

    /// If `true`, HMCL does not check game completeness.
    @SerializedName(PROPERTY_NOT_CHECK_GAME)
    private final InheritableProperty<Boolean> notCheckGame = newInheritableProperty(PROPERTY_NOT_CHECK_GAME, false);

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
    private final SettingProperty<@Nullable Integer> maxMemory = newSettingProperty(PROPERTY_MAX_MEMORY, SUGGESTED_MEMORY);

    /// Returns the maximum heap memory property.
    public SettingProperty<@Nullable Integer> maxMemoryProperty() {
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

    /// Property name for the initial game window mode.
    public static final String PROPERTY_WINDOW_TYPE = "windowType";

    /// The initial game window mode.
    @SerializedName(PROPERTY_WINDOW_TYPE)
    private final InheritableProperty<GameWindowType> windowType = newInheritableProperty(PROPERTY_WINDOW_TYPE, GameWindowType.WINDOWED);

    /// Returns the game window mode property.
    public InheritableProperty<GameWindowType> windowTypeProperty() {
        return windowType;
    }

    /// Property name for the game window width.
    public static final String PROPERTY_WIDTH = "width";

    /// The width of the game window.
    @SerializedName(PROPERTY_WIDTH)
    private final InheritableProperty<Double> width = newInheritableProperty(PROPERTY_WIDTH, 854.0);

    /// Returns the game window width property.
    public InheritableProperty<Double> widthProperty() {
        return width;
    }

    /// Property name for the game window height.
    public static final String PROPERTY_HEIGHT = "height";

    /// The height of the game window.
    @SerializedName(PROPERTY_HEIGHT)
    private final InheritableProperty<Double> height = newInheritableProperty(PROPERTY_HEIGHT, 480.0);

    /// Returns the game window height property.
    public InheritableProperty<Double> heightProperty() {
        return height;
    }

    /// Property name for the custom run directory.
    public static final String PROPERTY_RUNNING_DIR = "runningDir";

    /// The custom run directory.
    @SerializedName(PROPERTY_RUNNING_DIR)
    private final InheritableProperty<String> runningDir = newInheritableProperty(PROPERTY_RUNNING_DIR, "");

    /// Returns the custom run directory property.
    public InheritableProperty<String> runningDirProperty() {
        return runningDir;
    }

    /// Property name for the process priority.
    public static final String PROPERTY_PROCESS_PRIORITY = "processPriority";

    /// The process priority of the game.
    @SerializedName(PROPERTY_PROCESS_PRIORITY)
    private final InheritableProperty<ProcessPriority> processPriority = newInheritableProperty(PROPERTY_PROCESS_PRIORITY, ProcessPriority.NORMAL);

    /// Returns the process priority property.
    public InheritableProperty<ProcessPriority> processPriorityProperty() {
        return processPriority;
    }

    /// Property name for launcher behavior after game start.
    public static final String PROPERTY_LAUNCHER_VISIBILITY = "launcherVisibility";

    /// Launcher behavior after the game starts.
    @SerializedName(PROPERTY_LAUNCHER_VISIBILITY)
    private final InheritableProperty<LauncherVisibility> launcherVisibility = newInheritableProperty(PROPERTY_LAUNCHER_VISIBILITY, LauncherVisibility.KEEP);

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

    /// Property name for the requested graphics API.
    public static final String PROPERTY_GRAPHICS_BACKEND = "graphicsBackend";

    /// Graphics API requested by the launcher.
    @SerializedName(PROPERTY_GRAPHICS_BACKEND)
    private final InheritableProperty<GraphicsAPI> graphicsBackend = newInheritableProperty(PROPERTY_GRAPHICS_BACKEND, GraphicsAPI.DEFAULT);

    /// Returns the graphics API property.
    public InheritableProperty<GraphicsAPI> graphicsBackendProperty() {
        return graphicsBackend;
    }

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

    /// Property name for the command wrapper.
    public static final String PROPERTY_COMMAND_WRAPPER = "commandWrapper";

    /// The command wrapper for launching Minecraft.
    @SerializedName(PROPERTY_COMMAND_WRAPPER)
    private final InheritableProperty<String> commandWrapper = newInheritableProperty(PROPERTY_COMMAND_WRAPPER, "");

    /// Returns the command wrapper property.
    public InheritableProperty<String> commandWrapperProperty() {
        return commandWrapper;
    }

    /// Property name for the pre-launch command.
    public static final String PROPERTY_PRE_LAUNCH_COMMAND = "preLaunchCommand";

    /// The command that will be executed before launching the game.
    @SerializedName(PROPERTY_PRE_LAUNCH_COMMAND)
    private final InheritableProperty<String> preLaunchCommand = newInheritableProperty(PROPERTY_PRE_LAUNCH_COMMAND, "");

    /// Returns the pre-launch command property.
    public InheritableProperty<String> preLaunchCommandProperty() {
        return preLaunchCommand;
    }

    /// Property name for the post-exit command.
    public static final String PROPERTY_POST_EXIT_COMMAND = "postExitCommand";

    /// The command that will be executed after the game exits.
    @SerializedName(PROPERTY_POST_EXIT_COMMAND)
    private final InheritableProperty<String> postExitCommand = newInheritableProperty(PROPERTY_POST_EXIT_COMMAND, "");

    /// Returns the post-exit command property.
    public InheritableProperty<String> postExitCommandProperty() {
        return postExitCommand;
    }

    /// Property name for the quick play target type.
    public static final String PROPERTY_QUICK_PLAY = "quickPlay";

    /// The quick play target type.
    @SerializedName(PROPERTY_QUICK_PLAY)
    private final InheritableProperty<QuickPlayType> quickPlay = newInheritableProperty(PROPERTY_QUICK_PLAY, QuickPlayType.NONE);

    /// Returns the quick play target type property.
    public InheritableProperty<QuickPlayType> quickPlayProperty() {
        return quickPlay;
    }

    /// Property name for the multiplayer quick play target.
    public static final String PROPERTY_QUICK_PLAY_MULTIPLAYER = "quickPlayMultiplayer";

    /// The server address for multiplayer quick play.
    @SerializedName(PROPERTY_QUICK_PLAY_MULTIPLAYER)
    private final InheritableProperty<String> quickPlayMultiplayer = newInheritableProperty(PROPERTY_QUICK_PLAY_MULTIPLAYER, "");

    /// Returns the multiplayer quick play target property.
    public InheritableProperty<String> quickPlayMultiplayerProperty() {
        return quickPlayMultiplayer;
    }

    /// Property name for the singleplayer quick play target.
    public static final String PROPERTY_QUICK_PLAY_SINGLEPLAYER = "quickPlaySingleplayer";

    /// The world folder name for singleplayer quick play.
    @SerializedName(PROPERTY_QUICK_PLAY_SINGLEPLAYER)
    private final InheritableProperty<String> quickPlaySingleplayer = newInheritableProperty(PROPERTY_QUICK_PLAY_SINGLEPLAYER, "");

    /// Returns the singleplayer quick play target property.
    public InheritableProperty<String> quickPlaySingleplayerProperty() {
        return quickPlaySingleplayer;
    }

    /// Property name for the Realms quick play target.
    public static final String PROPERTY_QUICK_PLAY_REALMS = "quickPlayRealms";

    /// The realm ID for Realms quick play.
    @SerializedName(PROPERTY_QUICK_PLAY_REALMS)
    private final InheritableProperty<String> quickPlayRealms = newInheritableProperty(PROPERTY_QUICK_PLAY_REALMS, "");

    /// Returns the Realms quick play target property.
    public InheritableProperty<String> quickPlayRealmsProperty() {
        return quickPlayRealms;
    }

    /// Property name for showing logs after game start.
    public static final String PROPERTY_SHOW_LOGS = "showLogs";

    /// If `true`, show the logs after game launched.
    @SerializedName(PROPERTY_SHOW_LOGS)
    private final InheritableProperty<Boolean> showLogs = newInheritableProperty(PROPERTY_SHOW_LOGS, false);

    /// Returns the show logs property.
    public InheritableProperty<Boolean> showLogsProperty() {
        return showLogs;
    }

    /// Property name for enabling debug log output.
    public static final String PROPERTY_ENABLE_DEBUG_LOG_OUTPUT = "enableDebugLogOutput";

    /// If `true`, enable debug log output.
    @SerializedName(PROPERTY_ENABLE_DEBUG_LOG_OUTPUT)
    private final InheritableProperty<Boolean> enableDebugLogOutput = newInheritableProperty(PROPERTY_ENABLE_DEBUG_LOG_OUTPUT, false);

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

    /// Property names whose instance override state is stored in `Instance.overrideProperties`.
    private static final String @Unmodifiable [] OVERRIDABLE_PROPERTY_NAMES = {
            PROPERTY_JAVA_TYPE,
            PROPERTY_JVM_OPTIONS,
            PROPERTY_NO_JVM_OPTIONS,
            PROPERTY_NO_OPTIMIZING_JVM_OPTIONS,
            PROPERTY_NOT_CHECK_JVM,
            PROPERTY_NOT_CHECK_GAME,
            PROPERTY_AUTO_MEMORY,
            PROPERTY_MIN_MEMORY,
            PROPERTY_MAX_MEMORY,
            PROPERTY_PERM_SIZE,
            PROPERTY_WINDOW_TYPE,
            PROPERTY_WIDTH,
            PROPERTY_HEIGHT,
            PROPERTY_RUNNING_DIR,
            PROPERTY_PROCESS_PRIORITY,
            PROPERTY_LAUNCHER_VISIBILITY,
            PROPERTY_GAME_ARGS,
            PROPERTY_GRAPHICS_BACKEND,
            PROPERTY_OPENGL_RENDERER,
            PROPERTY_VULKAN_RENDERER,
            PROPERTY_ENVIRONMENT_VARIABLES,
            PROPERTY_COMMAND_WRAPPER,
            PROPERTY_PRE_LAUNCH_COMMAND,
            PROPERTY_POST_EXIT_COMMAND,
            PROPERTY_QUICK_PLAY,
            PROPERTY_QUICK_PLAY_MULTIPLAYER,
            PROPERTY_QUICK_PLAY_SINGLEPLAYER,
            PROPERTY_QUICK_PLAY_REALMS,
            PROPERTY_SHOW_LOGS,
            PROPERTY_ENABLE_DEBUG_LOG_OUTPUT,
            PROPERTY_NOT_PATCH_NATIVES,
            PROPERTY_NATIVES_DIR_TYPE,
            PROPERTY_NATIVES_DIR,
            PROPERTY_USE_NATIVE_GLFW,
            PROPERTY_USE_NATIVE_OPENAL
    };

    static void setRendererForApi(GameSetting setting, Renderer renderer, @Nullable GraphicsAPI fallbackApi) {
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

    private static <T extends @UnknownNullability Object> T inherited(Global global, @Nullable Instance instance, Function<GameSetting, SettingProperty<T>> propertyGetter) {
        if (instance != null) {
            SettingProperty<T> property = propertyGetter.apply(instance);
            if (isOverridden(instance, property)) {
                return property.getValue();
            }
        }
        return propertyGetter.apply(global).getValue();
    }

    private static <T extends @UnknownNullability Object> T inheritable(Global global, @Nullable Instance instance, Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        if (instance != null) {
            InheritableProperty<T> property = propertyGetter.apply(instance);
            if (isOverridden(instance, property)) {
                return getDirectValue(property);
            }
        }

        return getDirectValue(propertyGetter.apply(global));
    }

    private static boolean isOverridden(Instance instance, SettingProperty<?> property) {
        return instance.getOverrideProperties().contains(property.getName());
    }

    private static <T extends @UnknownNullability Object> T getDirectValue(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
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
            if (instance != null && isOverridden(instance, instance.javaTypeProperty())) {
                return instance.javaVersionProperty().getValue();
            }
            return global.javaVersionProperty().getValue();
        }

        /// Returns the effective custom Java executable path.
        public String getCustomJavaPath() {
            if (instance != null && isOverridden(instance, instance.javaTypeProperty())) {
                return Objects.requireNonNullElse(instance.customJavaPathProperty().getValue(), "");
            }
            return Objects.requireNonNullElse(global.customJavaPathProperty().getValue(), "");
        }

        /// Returns the effective default Java executable path.
        public String getDefaultJavaPath() {
            if (instance != null && isOverridden(instance, instance.javaTypeProperty())) {
                return Objects.requireNonNullElse(instance.defaultJavaPathProperty().getValue(), "");
            }
            return Objects.requireNonNullElse(global.defaultJavaPathProperty().getValue(), "");
        }

        /// Switches the effective Java selection back to automatic mode.
        public void setJavaAutoSelected() {
            GameSetting target = instance != null && isOverridden(instance, instance.javaTypeProperty()) ? instance : global;
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
            return Objects.requireNonNullElse(inherited(global, instance, GameSetting::jvmOptionsProperty), "");
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
            return Objects.requireNonNullElse(inherited(global, instance, GameSetting::permSizeProperty), "");
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

        /// Returns the effective custom run directory.
        public String getRunningDir() {
            return Objects.requireNonNullElse(inheritable(global, instance, GameSetting::runningDirProperty), "");
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
            return Objects.requireNonNullElse(inherited(global, instance, GameSetting::gameArgsProperty), "");
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
            return Objects.requireNonNullElse(inherited(global, instance, GameSetting::environmentVariablesProperty), "");
        }

        /// Returns the effective command wrapper.
        public String getCommandWrapper() {
            return Objects.requireNonNullElse(inheritable(global, instance, GameSetting::commandWrapperProperty), "");
        }

        /// Returns the effective pre-launch command.
        public String getPreLaunchCommand() {
            return Objects.requireNonNullElse(inheritable(global, instance, GameSetting::preLaunchCommandProperty), "");
        }

        /// Returns the effective post-exit command.
        public String getPostExitCommand() {
            return Objects.requireNonNullElse(inheritable(global, instance, GameSetting::postExitCommandProperty), "");
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
                    String server = Objects.requireNonNullElse(inheritable(global, instance, GameSetting::quickPlayMultiplayerProperty), "");
                    yield StringUtils.isBlank(server) ? null : new QuickPlayOption.MultiPlayer(server);
                }
                case SINGLEPLAYER -> {
                    String world = Objects.requireNonNullElse(inheritable(global, instance, GameSetting::quickPlaySingleplayerProperty), "");
                    yield StringUtils.isBlank(world) ? null : new QuickPlayOption.SinglePlayer(world);
                }
                case REALMS -> {
                    String realm = Objects.requireNonNullElse(inheritable(global, instance, GameSetting::quickPlayRealmsProperty), "");
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
            return Objects.requireNonNullElse(inherited(global, instance, GameSetting::nativesDirProperty), "");
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
