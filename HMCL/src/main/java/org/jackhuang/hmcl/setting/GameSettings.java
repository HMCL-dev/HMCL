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
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import org.jackhuang.hmcl.util.gson.JsonSchema;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Game launch settings shared by presets and instance-specific overrides.
///
/// @author Glavo
@NotNullByDefault
public sealed abstract class GameSettings extends ObservableSetting {
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
    public static final class Instance extends GameSettings implements JsonSchemaSetting {
        /// The JSON schema supported by instance-specific game settings.
        public static final JsonSchema CURRENT_SCHEMA =
                new JsonSchema("instance-game-settings", new JsonSchema.Version(1, 0, 0));

        /// Creates an empty instance setting.
        public Instance() {
            tracker.markDirty(schema);
            register();
        }

        /// The schema used by this instance game settings file.
        @SerializedName(JsonSchema.PROPERTY_SCHEMA)
        private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

        /// Returns the schema property.
        public ObjectProperty<JsonSchema> schemaProperty() {
            return schema;
        }

        /// Returns the schema used by this instance game settings file.
        @Override
        public JsonSchema getSchema() {
            return schema.get();
        }

        /// Sets the schema used by this instance game settings file.
        @Override
        public void setSchema(JsonSchema schema) {
            this.schema.set(Objects.requireNonNull(schema));
        }

        /// Whether this instance setting may be saved back to its JSON file.
        private transient boolean savable = true;

        /// Returns whether this instance setting may be saved back to its JSON file.
        @Override
        public boolean isSavable() {
            return savable;
        }

        /// Sets whether this instance setting may be saved back to its JSON file.
        @Override
        public void setSavable(boolean savable) {
            this.savable = savable;
        }

        /// The parent preset ID.
        @SerializedName("parent")
        private final SettingProperty<@Nullable SettingId> parent = newSettingProperty("parent");

        /// Returns the parent preset ID property.
        public SettingProperty<@Nullable SettingId> parentProperty() {
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
        }
    }

    /// Reusable game setting preset.
    @JsonAdapter(Preset.Adapter.class)
    @JsonSerializable
    public static final class Preset extends GameSettings {
        /// Creates a preset with the given identity.
        public Preset(SettingId id) {
            register();
            this.id.setValue(Objects.requireNonNull(id));
        }

        /// Creates a preset with the given identity.
        private Preset() {
            register();
        }

        /// The stable preset ID.
        @SerializedName("id")
        private final SettingProperty<SettingId> id = newSettingProperty("id", SettingId.NIL);

        /// Returns the preset ID property.
        public SettingProperty<SettingId> idProperty() {
            return id;
        }

        /// The custom localized display name of this preset.
        @SerializedName("name")
        private final SettingProperty<@Nullable LocalizedText> name = newSettingProperty("name");

        /// Returns the custom localized display name property.
        public SettingProperty<@Nullable LocalizedText> nameProperty() {
            return name;
        }

        /// The automatic display name number of this preset.
        @SerializedName("autoNameNumber")
        private final SettingProperty<@Nullable Integer> autoNameNumber = newSettingProperty("autoNameNumber");

        /// Returns the automatic display name number property.
        public SettingProperty<@Nullable Integer> autoNameNumberProperty() {
            return autoNameNumber;
        }

        /// Whether to enable the version isolation strategy when installing a new instance.
        @SerializedName("defaultIsolationType")
        private final SettingProperty<DefaultIsolationType> defaultIsolationType = newSettingProperty("defaultIsolationType", DefaultIsolationType.MODED);

        /// Returns the default isolation type property.
        public SettingProperty<DefaultIsolationType> defaultIsolationTypeProperty() {
            return defaultIsolationType;
        }

        /// JSON adapter for presets.
        public static final class Adapter extends ObservableSetting.Adapter<@Nullable Preset> {
            @Override
            protected Preset createInstance() {
                return new Preset();
            }

            @Override
            public @Nullable Preset deserialize(
                    JsonElement json,
                    Type typeOfT,
                    JsonDeserializationContext context) throws JsonParseException {
                @Nullable Preset result = super.deserialize(json, typeOfT, context);
                if (result != null && SettingId.NIL.equals(result.idProperty().getValue())) {
                    throw new JsonParseException("Preset ID cannot be nil");
                }
                return result;
            }
        }
    }

    /// Creates a new setting property without a default value.
    protected final <T extends @Nullable Object> SettingProperty<@Nullable T> newSettingProperty(String name) {
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
    private final InheritableProperty<LauncherVisibility> launcherVisibility = newInheritableProperty(PROPERTY_LAUNCHER_VISIBILITY, LauncherVisibility.HIDE);

    /// Returns the launcher visibility property.
    public InheritableProperty<LauncherVisibility> launcherVisibilityProperty() {
        return launcherVisibility;
    }

    /// Property name for allowing HMCL to modify the game with Java agents.
    public static final String PROPERTY_ALLOW_AUTO_AGENT = "allowAutoAgent";

    /// If `true`, HMCL may attach Java agents to improve the game experience.
    @SerializedName(PROPERTY_ALLOW_AUTO_AGENT)
    private final InheritableProperty<Boolean> allowAutoAgent = newInheritableProperty(PROPERTY_ALLOW_AUTO_AGENT, false);

    /// Returns the automatic Java agent permission property.
    public InheritableProperty<Boolean> allowAutoAgentProperty() {
        return allowAutoAgent;
    }

    /// Property name for disabling automatic game options generation.
    public static final String PROPERTY_DISABLE_AUTO_GAME_OPTIONS = "disableAutoGameOptions";

    /// If `true`, HMCL will not generate game options automatically.
    @SerializedName(PROPERTY_DISABLE_AUTO_GAME_OPTIONS)
    private final InheritableProperty<Boolean> disableAutoGameOptions =
            newInheritableProperty(PROPERTY_DISABLE_AUTO_GAME_OPTIONS, false);

    /// Returns the automatic game options generation disable property.
    public InheritableProperty<Boolean> disableAutoGameOptionsProperty() {
        return disableAutoGameOptions;
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

    private static Renderer selectRenderer(GraphicsAPI api, @Nullable Renderer renderer) {
        if (renderer instanceof Renderer.Driver driver && driver.api() != api) {
            return Renderer.DEFAULT;
        }

        return renderer != null ? renderer : Renderer.DEFAULT;
    }

    /// Resolves a preset and an optional instance setting into launch-time values.
    public static Effective resolve(Preset preset, @Nullable Instance instance) {
        return new Effective(preset, instance);
    }

    /// Returns the effective value for a property whose override state is tracked by the property itself.
    private static <T extends @UnknownNullability Object> T inherited(Preset preset, @Nullable Instance instance, Function<GameSettings, SettingProperty<T>> propertyGetter) {
        if (instance != null) {
            SettingProperty<T> property = propertyGetter.apply(instance);
            if (isOverridden(instance, property)) {
                return getDirectValue(property);
            }
        }
        return getDirectValue(propertyGetter.apply(preset));
    }

    /// Returns the effective value for a property whose override state is tracked by another property.
    private static <T extends @UnknownNullability Object> T inherited(
            Preset preset,
            @Nullable Instance instance,
            Function<GameSettings, SettingProperty<T>> propertyGetter,
            Function<GameSettings, ? extends SettingProperty<?>> overridePropertyGetter) {
        if (instance != null && isOverridden(instance, overridePropertyGetter.apply(instance))) {
            return getDirectValue(propertyGetter.apply(instance));
        }

        return getDirectValue(propertyGetter.apply(preset));
    }

    /// Returns the effective value for an inheritable property.
    private static <T extends @UnknownNullability Object> T inheritable(Preset preset, @Nullable Instance instance, Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        if (instance != null) {
            InheritableProperty<T> property = propertyGetter.apply(instance);
            if (isOverridden(instance, property)) {
                return getDirectValue(property);
            }
        }

        return getDirectValue(propertyGetter.apply(preset));
    }

    /// Returns whether an instance overrides the given property.
    private static boolean isOverridden(Instance instance, SettingProperty<?> property) {
        return instance.getOverrideProperties().contains(property.getName());
    }

    /// Returns the property's direct value, or its default value when the direct value is `null`.
    private static <T extends @UnknownNullability Object> T getDirectValue(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
    }

    /// Launch-time effective game setting.
    public static final class Effective {
        private final Preset preset;
        private final @Nullable Instance instance;

        private Effective(Preset preset, @Nullable Instance instance) {
            this.preset = Objects.requireNonNull(preset);
            this.instance = instance;
        }

        /// Returns the parent preset.
        public Preset getPreset() {
            return preset;
        }

        /// Returns the instance setting, if one exists.
        public @Nullable Instance getInstance() {
            return instance;
        }

        /// Returns the effective value for a property whose override state is tracked by the property itself.
        public <T extends @UnknownNullability Object> T get(Function<GameSettings, SettingProperty<T>> propertyGetter) {
            return inherited(preset, instance, propertyGetter);
        }

        /// Returns the effective value for a property whose override state is tracked by another property.
        public <T extends @UnknownNullability Object> T get(
                Function<GameSettings, SettingProperty<T>> propertyGetter,
                Function<GameSettings, ? extends SettingProperty<?>> overridePropertyGetter) {
            return inherited(preset, instance, propertyGetter, overridePropertyGetter);
        }

        /// Returns the effective value for an inheritable property.
        public <T extends @UnknownNullability Object> T getInheritable(
                Function<GameSettings, InheritableProperty<T>> propertyGetter) {
            return inheritable(preset, instance, propertyGetter);
        }

        /// Switches the effective Java selection back to automatic mode.
        public void setJavaAutoSelected() {
            GameSettings target = instance != null && isOverridden(instance, instance.javaTypeProperty()) ? instance : preset;
            target.javaTypeProperty().setValue(JavaVersionType.AUTO);
            target.javaVersionProperty().setValue("");
            target.customJavaPathProperty().setValue("");
            target.defaultJavaPathProperty().setValue("");
        }

        /// Finds the effective Java runtime.
        public @Nullable JavaRuntime getJava(@Nullable GameVersionNumber gameVersion, @Nullable Version version) throws InterruptedException {
            JavaVersionType javaVersionType = getInheritable(GameSettings::javaTypeProperty);
            switch (javaVersionType) {
                case DEFAULT:
                    return JavaRuntime.getDefault();
                case AUTO:
                    return JavaManager.findSuitableJava(gameVersion, version);
                case CUSTOM:
                    try {
                        return JavaManager.getJava(Path.of(get(GameSettings::customJavaPathProperty, GameSettings::javaTypeProperty)));
                    } catch (IOException | InvalidPathException e) {
                        return null;
                    }
                case VERSION: {
                    String javaVersion = get(GameSettings::javaVersionProperty, GameSettings::javaTypeProperty);
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
                    String javaVersion = get(GameSettings::javaVersionProperty, GameSettings::javaTypeProperty);
                    if (StringUtils.isBlank(javaVersion)) {
                        return JavaManager.findSuitableJava(gameVersion, version);
                    }

                    try {
                        String defaultJavaPath = get(GameSettings::defaultJavaPathProperty, GameSettings::javaTypeProperty);
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
                    throw new AssertionError("Java Type: " + javaVersionType);
            }
        }

        /// Returns the effective maximum heap memory in MiB.
        public int getMaxMemory() {
            Integer value = get(GameSettings::maxMemoryProperty);
            return value != null && value > 0 ? value : SUGGESTED_MEMORY;
        }

        /// Returns the effective game window width.
        public int getWidth() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(getInheritable(GameSettings::widthProperty))), 0));
        }

        /// Returns the effective game window height.
        public int getHeight() {
            return Math.max(0, Lang.parseInt(String.valueOf(Math.round(getInheritable(GameSettings::heightProperty))), 0));
        }

        /// Returns the effective renderer.
        public Renderer getRenderer() {
            return switch (getInheritable(GameSettings::graphicsBackendProperty)) {
                case OPENGL -> selectRenderer(GraphicsAPI.OPENGL, getInheritable(GameSettings::openGLRendererProperty));
                case VULKAN -> selectRenderer(GraphicsAPI.VULKAN, getInheritable(GameSettings::vulkanRendererProperty));
                case DEFAULT -> Renderer.DEFAULT;
            };
        }

        /// Returns the effective quick play option.
        public @Nullable QuickPlayOption getQuickPlayOption() {
            return switch (getInheritable(GameSettings::quickPlayProperty)) {
                case NONE -> null;
                case MULTIPLAYER -> {
                    String server = getInheritable(GameSettings::quickPlayMultiplayerProperty);
                    yield StringUtils.isBlank(server) ? null : new QuickPlayOption.MultiPlayer(server);
                }
                case SINGLEPLAYER -> {
                    String world = getInheritable(GameSettings::quickPlaySingleplayerProperty);
                    yield StringUtils.isBlank(world) ? null : new QuickPlayOption.SinglePlayer(world);
                }
                case REALMS -> {
                    String realm = getInheritable(GameSettings::quickPlayRealmsProperty);
                    yield StringUtils.isBlank(realm) ? null : new QuickPlayOption.Realm(realm);
                }
            };
        }
    }
}
