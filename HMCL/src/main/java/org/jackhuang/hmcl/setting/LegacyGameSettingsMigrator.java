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
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Converts legacy game settings JSON into `GameSettings` models.
@NotNullByDefault
public final class LegacyGameSettingsMigrator {
    /// Legacy file name used by old per-version `VersionSetting` data.
    private static final String LEGACY_INSTANCE_SETTINGS_FILENAME = "hmclversion.cfg";

    /// Receipt file name used to record successful legacy per-version settings migration.
    private static final String LEGACY_INSTANCE_SETTINGS_MIGRATION_RECEIPT_FILENAME =
            "instance-game-settings.migration-receipt.json";

    /// Legacy game directory modes stored by old configuration files.
    private enum GameDirectoryType {
        /// Use the root `.minecraft` folder.
        ROOT_FOLDER,

        /// Use the version-specific folder.
        VERSION_FOLDER,

        /// Use a custom game directory.
        CUSTOM
    }

    /// Legacy `VersionIconType` ordinal order used by old local settings.
    private static final VersionIconType @Unmodifiable [] LEGACY_VERSION_ICON_TYPES = {
            VersionIconType.DEFAULT,
            VersionIconType.GRASS,
            VersionIconType.CHEST,
            VersionIconType.CHICKEN,
            VersionIconType.COMMAND,
            VersionIconType.OPTIFINE,
            VersionIconType.CRAFT_TABLE,
            VersionIconType.FABRIC,
            VersionIconType.FORGE,
            VersionIconType.NEO_FORGE,
            VersionIconType.FURNACE,
            VersionIconType.QUILT,
            VersionIconType.APRIL_FOOLS,
            VersionIconType.CLEANROOM,
            VersionIconType.LEGACY_FABRIC
    };

    /// Legacy `JavaVersionType` ordinal order used by old game settings.
    private static final JavaVersionType @Unmodifiable [] LEGACY_JAVA_VERSION_TYPES = {
            // Legacy ordinal 0 was DEFAULT and ordinal 1 was AUTO; both now migrate to  AUTO.
            JavaVersionType.AUTO,
            JavaVersionType.AUTO,
            JavaVersionType.VERSION,
            JavaVersionType.DETECTED,
            JavaVersionType.CUSTOM
    };

    /// Legacy native library mode ordinal order used by old game settings.
    private static final String @Unmodifiable [] LEGACY_NATIVES_DIRECTORY_TYPES = {
            "VERSION_FOLDER",
            "CUSTOM"
    };

    /// Prevents instantiation.
    private LegacyGameSettingsMigrator() {
    }

    /// Converts a legacy profile-level setting JSON object into a preset with the given ID.
    public static GameSettings.Preset toPreset(SettingID id, String name, @Nullable JsonObject source) {
        GameSettings.Preset target = new GameSettings.Preset(id);
        target.nameProperty().setValue(LocalizedText.plain(name));
        if (getLegacyGameDirType(source, GameDirectoryType.ROOT_FOLDER) == GameDirectoryType.VERSION_FOLDER) {
            target.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);
        }
        if (source != null) {
            copyCommonProperties(source, target, GameSettings.DetectedJava::ofLegacyPath);
        }
        return target;
    }

    /// Migrates a legacy per-version game setting file into an instance setting.
    ///
    /// @param parent the migrated parent preset ID for the profile
    /// @return the migrated instance setting, or `null` when no legacy file can be migrated
    public static @Nullable InstanceMigrationResult migrateInstanceGameSettings(
            HMCLGameRepository repository,
            String instanceId,
            @Nullable SettingID parent) {
        Path instanceRoot = repository.getVersionRoot(instanceId);
        Path file = instanceRoot.resolve(LEGACY_INSTANCE_SETTINGS_FILENAME);
        if (!Files.exists(file)) {
            return null;
        }
        Path receiptLocation = repository.getInstanceMetadataDirectory(instanceId)
                .resolve(LEGACY_INSTANCE_SETTINGS_MIGRATION_RECEIPT_FILENAME);
        if (MigrationReceipt.matches(receiptLocation, file)) {
            LOG.info("Skipping already migrated legacy version setting " + file);
            return null;
        }

        try {
            JsonObject legacySettingJson = JsonUtils.fromJsonFile(file, JsonObject.class);
            if (legacySettingJson == null) {
                return null;
            }

            boolean inheritsLegacyParent = JsonUtils.getBoolean(legacySettingJson, "usesGlobal", false);
            GameSettings.Instance setting = toInstance(parent, legacySettingJson, inheritsLegacyParent);
            if (!inheritsLegacyParent) {
                Path baseDirectory = repository.getBaseDirectory();
                GameSettings.Preset parentSetting = SettingsManager.getGameSettings(parent);
                if (parentSetting != null
                        && getLegacyGameDirType(legacySettingJson, GameDirectoryType.ROOT_FOLDER) == GameDirectoryType.ROOT_FOLDER
                        && StringUtils.isNotBlank(parentSetting.runningDirectoryProperty().getValue())) {
                    setting.runningDirectoryProperty().setValue(baseDirectory.toString());
                    setting.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                }
            }
            return new InstanceMigrationResult(file, receiptLocation, setting);
        } catch (Exception ex) {
            LOG.warning("Failed to migrate legacy version setting " + file, ex);
            return null;
        }
    }

    /// Converts a legacy local setting JSON object into an instance game setting.
    ///
    /// @param parent the migrated parent preset ID for the instance
    /// @param source the legacy local setting JSON object
    /// @param inheritsLegacyParent whether the legacy instance inherits its parent preset instead of
    ///                             carrying its own copied values
    public static GameSettings.Instance toInstance(
            @Nullable SettingID parent,
            @Nullable JsonObject source,
            boolean inheritsLegacyParent) {
        return toInstance(parent, source, inheritsLegacyParent, GameSettings.DetectedJava::ofLegacyPath);
    }

    /// Converts a legacy local setting JSON object into an instance game setting with a custom
    /// detected Java converter.
    ///
    /// @param parent the migrated parent preset ID for the instance
    /// @param source the legacy local setting JSON object
    /// @param inheritsLegacyParent whether the legacy instance inherits its parent preset instead of
    ///                             carrying its own copied values
    /// @param detectedJavaFactory converts legacy detected Java version and path fields into the new
    ///                            detected Java reference
    @VisibleForTesting
    static GameSettings.Instance toInstance(
            @Nullable SettingID parent,
            @Nullable JsonObject source,
            boolean inheritsLegacyParent,
            BiFunction<String, String, GameSettings.DetectedJava> detectedJavaFactory) {
        GameSettings.Instance target = new GameSettings.Instance();
        target.parentProperty().setValue(parent);
        target.iconProperty().setValue(parseLegacyVersionIconType(source));
        if (source != null && !inheritsLegacyParent) {
            copyCommonProperties(source, target, detectedJavaFactory);
            if (getLegacyGameDirType(source, GameDirectoryType.ROOT_FOLDER) != GameDirectoryType.ROOT_FOLDER) {
                target.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
            }
            target.getOverrideProperties().addAll(List.of(
                    GameSettings.PROPERTY_JAVA_TYPE,
                    GameSettings.PROPERTY_JVM_OPTIONS,
                    GameSettings.PROPERTY_NO_JVM_OPTIONS,
                    GameSettings.PROPERTY_NO_OPTIMIZING_JVM_OPTIONS,
                    GameSettings.PROPERTY_NOT_CHECK_JVM,
                    GameSettings.PROPERTY_NOT_CHECK_GAME,
                    GameSettings.PROPERTY_AUTO_MEMORY,
                    GameSettings.PROPERTY_MIN_MEMORY,
                    GameSettings.PROPERTY_MAX_MEMORY,
                    GameSettings.PROPERTY_PERM_SIZE,
                    GameSettings.PROPERTY_WINDOW_TYPE,
                    GameSettings.PROPERTY_WIDTH,
                    GameSettings.PROPERTY_HEIGHT,
                    GameSettings.PROPERTY_PROCESS_PRIORITY,
                    GameSettings.PROPERTY_LAUNCHER_VISIBILITY,
                    GameSettings.PROPERTY_GAME_ARGS,
                    GameSettings.PROPERTY_GRAPHICS_BACKEND,
                    GameSettings.PROPERTY_OPENGL_RENDERER,
                    GameSettings.PROPERTY_VULKAN_RENDERER,
                    GameSettings.PROPERTY_ENVIRONMENT_VARIABLES,
                    GameSettings.PROPERTY_COMMAND_WRAPPER,
                    GameSettings.PROPERTY_PRE_LAUNCH_COMMAND,
                    GameSettings.PROPERTY_POST_EXIT_COMMAND,
                    GameSettings.PROPERTY_QUICK_PLAY,
                    GameSettings.PROPERTY_QUICK_PLAY_MULTIPLAYER,
                    GameSettings.PROPERTY_QUICK_PLAY_SINGLEPLAYER,
                    GameSettings.PROPERTY_QUICK_PLAY_REALMS,
                    GameSettings.PROPERTY_SHOW_LOGS,
                    GameSettings.PROPERTY_ENABLE_DEBUG_LOG_OUTPUT,
                    GameSettings.PROPERTY_NOT_PATCH_NATIVES,
                    GameSettings.PROPERTY_USE_CUSTOM_NATIVES,
                    GameSettings.PROPERTY_NATIVES_DIRECTORY,
                    GameSettings.PROPERTY_USE_NATIVE_GLFW,
                    GameSettings.PROPERTY_USE_NATIVE_OPENAL
            ));
        }
        return target;
    }

    /// Returns the legacy game directory type from a setting JSON object.
    private static GameDirectoryType getLegacyGameDirType(@Nullable JsonObject source, GameDirectoryType defaultValue) {
        return parseEnum(source, "gameDirType", GameDirectoryType.class, defaultValue);
    }

    /// Copies shared legacy properties into the target setting.
    private static void copyCommonProperties(
            JsonObject source,
            GameSettings target,
            BiFunction<String, String, GameSettings.DetectedJava> detectedJavaFactory) {
        JavaVersionType javaVersionType = parseLegacyJavaVersionType(source);
        String legacyJavaVersion = Objects.requireNonNullElse(parseLegacyJavaVersion(source), "");
        target.javaTypeProperty().setValue(javaVersionType);
        if (javaVersionType == JavaVersionType.VERSION) {
            target.customJavaVersionProperty().setValue(legacyJavaVersion);
        } else if (javaVersionType == JavaVersionType.DETECTED && StringUtils.isNotBlank(legacyJavaVersion)) {
            target.detectedJavaProperty().setValue(detectedJavaFactory.apply(
                    legacyJavaVersion,
                    JsonUtils.getString(source, "defaultJavaPath", "")));
        }
        target.customJavaPathProperty().setValue(JsonUtils.getString(source, "javaDir", ""));

        target.jvmOptionsProperty().setValue(JsonUtils.getString(source, "javaArgs", ""));
        target.noJVMOptionsProperty().setValue(JsonUtils.getBoolean(source, "noJVMArgs", false));
        target.noOptimizingJVMOptionsProperty().setValue(JsonUtils.getBoolean(source, "noOptimizingJVMArgs", false));
        target.notCheckJVMProperty().setValue(JsonUtils.getBoolean(source, "notCheckJVM", false));
        target.notCheckGameProperty().setValue(JsonUtils.getBoolean(source, "notCheckGame", false));

        target.autoMemoryProperty().setValue(JsonUtils.getBoolean(source, "autoMemory", true));
        target.minMemoryProperty().setValue(JsonUtils.getNullableInt(source, "minMemory"));
        target.maxMemoryProperty().setValue(JsonUtils.getInt(source, "maxMemory", GameSettings.SUGGESTED_MEMORY));
        target.permSizeProperty().setValue(JsonUtils.getString(source, "permSize", ""));

        target.windowTypeProperty().setValue(JsonUtils.getBoolean(source, "fullscreen", false) ? GameWindowType.FULLSCREEN : GameWindowType.WINDOWED);
        target.widthProperty().setValue((double) JsonUtils.getInt(source, "width", 0));
        target.heightProperty().setValue((double) JsonUtils.getInt(source, "height", 0));
        target.runningDirectoryProperty().setValue(getLegacyGameDirType(source, GameDirectoryType.ROOT_FOLDER) == GameDirectoryType.CUSTOM
                ? JsonUtils.getString(source, "gameDir", "")
                : "");

        target.processPriorityProperty().setValue(parseEnum(source, "processPriority", ProcessPriority.class, ProcessPriority.NORMAL));
        target.launcherVisibilityProperty().setValue(parseEnum(source, "launcherVisibility", LauncherVisibility.class, LauncherVisibility.HIDE));
        target.gameArgumentsProperty().setValue(JsonUtils.getString(source, "minecraftArgs", ""));
        migrateLegacyRenderer(source, target);
        target.environmentVariablesProperty().setValue(JsonUtils.getString(source, "environmentVariables", ""));
        target.commandWrapperProperty().setValue(JsonUtils.getString(source, "wrapper", ""));
        target.preLaunchCommandProperty().setValue(JsonUtils.getString(source, "precalledCommand", ""));
        target.postExitCommandProperty().setValue(JsonUtils.getString(source, "postExitCommand", ""));

        String serverIp = JsonUtils.getString(source, "serverIp", "");
        if (StringUtils.isBlank(serverIp)) {
            target.quickPlayProperty().setValue(QuickPlayType.NONE);
        } else {
            target.quickPlayProperty().setValue(QuickPlayType.MULTIPLAYER);
            target.quickPlayMultiplayerProperty().setValue(serverIp);
        }

        target.showLogsProperty().setValue(JsonUtils.getBoolean(source, "showLogs", false));
        target.enableDebugLogOutputProperty().setValue(JsonUtils.getBoolean(source, "enableDebugLogOutput", false));
        target.notPatchNativesProperty().setValue(JsonUtils.getBoolean(source, "notPatchNatives", false));
        boolean useCustomNatives = isLegacyCustomNativesDirectory(source);
        target.useCustomNativesProperty().setValue(useCustomNatives);
        target.nativesDirectoryProperty().setValue(useCustomNatives ? JsonUtils.getString(source, "nativesDir", "") : "");
        target.useNativeGLFWProperty().setValue(JsonUtils.getBoolean(source, "useNativeGLFW", false));
        target.useNativeOpenALProperty().setValue(JsonUtils.getBoolean(source, "useNativeOpenAL", false));
    }

    /// Returns whether old game settings selected user-managed native libraries.
    private static boolean isLegacyCustomNativesDirectory(JsonObject source) {
        JsonPrimitive primitive = JsonUtils.getPrimitive(source, "nativesDirType");
        if (primitive == null) {
            return false;
        }

        try {
            if (primitive.isNumber()) {
                int index = primitive.getAsInt();
                return index >= 0
                        && index < LEGACY_NATIVES_DIRECTORY_TYPES.length
                        && "CUSTOM".equals(LEGACY_NATIVES_DIRECTORY_TYPES[index]);
            }

            return "CUSTOM".equalsIgnoreCase(primitive.getAsString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /// Migrates the legacy renderer fields into current renderer properties.
    private static void migrateLegacyRenderer(JsonObject source, GameSettings setting) {
        Renderer renderer = parseLegacyRenderer(source);
        GraphicsAPI graphicsBackend = parseGraphicsBackend(source, renderer);
        setting.graphicsBackendProperty().setValue(graphicsBackend);

        if (renderer instanceof Renderer.Driver driver) {
            rendererPropertyForApi(setting, driver.api()).setValue(renderer);
            if (graphicsBackend == GraphicsAPI.DEFAULT) {
                setting.graphicsBackendProperty().setValue(driver.api());
            }
            return;
        }

        if (graphicsBackend == GraphicsAPI.OPENGL || graphicsBackend == GraphicsAPI.VULKAN) {
            rendererPropertyForApi(setting, graphicsBackend).setValue(renderer);
        } else {
            setting.openGLRendererProperty().setValue(renderer);
            setting.vulkanRendererProperty().setValue(renderer);
        }
    }

    /// Returns the renderer property for the given non-default graphics API.
    private static InheritableProperty<Renderer> rendererPropertyForApi(GameSettings setting, GraphicsAPI api) {
        return switch (api) {
            case OPENGL -> setting.openGLRendererProperty();
            case VULKAN -> setting.vulkanRendererProperty();
            case DEFAULT -> throw new IllegalArgumentException("The default graphics API has no renderer property");
        };
    }

    /// Parses the legacy Java selection mode.
    private static JavaVersionType parseLegacyJavaVersionType(JsonObject source) {
        JsonPrimitive primitive = JsonUtils.getPrimitive(source, "javaVersionType");
        if (primitive != null) {
            try {
                if (primitive.isNumber()) {
                    int index = primitive.getAsInt();
                    return index >= 0 && index < LEGACY_JAVA_VERSION_TYPES.length
                            ? LEGACY_JAVA_VERSION_TYPES[index]
                            : JavaVersionType.AUTO;
                }

                return switch (primitive.getAsString().toUpperCase(Locale.ROOT)) {
                    case "DEFAULT", "AUTO" -> JavaVersionType.AUTO;
                    case "VERSION" -> JavaVersionType.VERSION;
                    case "DETECTED" -> JavaVersionType.DETECTED;
                    case "CUSTOM" -> JavaVersionType.CUSTOM;
                    default -> JavaVersionType.AUTO;
                };
            } catch (RuntimeException ignored) {
                return JavaVersionType.AUTO;
            }
        }

        return switch (JsonUtils.getString(source, "java", "")) {
            case "Default", "Auto" -> JavaVersionType.AUTO;
            case "Custom" -> JavaVersionType.CUSTOM;
            default -> JavaVersionType.AUTO;
        };
    }

    /// Parses the legacy Java version value.
    private static @Nullable String parseLegacyJavaVersion(JsonObject source) {
        if (source.has("javaVersionType")) {
            return switch (parseLegacyJavaVersionType(source)) {
                case VERSION, DETECTED -> JsonUtils.getString(source, "java", null);
                case AUTO, CUSTOM -> "";
            };
        }

        String java = JsonUtils.getString(source, "java", "");
        return switch (java) {
            case "Default", "Auto", "Custom" -> "";
            default -> java;
        };
    }

    /// Parses the legacy renderer selection.
    private static Renderer parseLegacyRenderer(JsonObject source) {
        JsonElement renderer = source.get("renderer");
        if (renderer != null && !renderer.isJsonNull()) {
            Renderer parsed = parseLegacyRenderer(renderer);
            if (parsed != null) {
                return parsed;
            }
        }

        return JsonUtils.getBoolean(source, "useSoftwareRenderer", false) ? Renderer.OpenGL.LLVMPIPE : Renderer.DEFAULT;
    }

    /// Parses a renderer from a legacy JSON element.
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

    /// Parses the graphics API selection with renderer-derived fallback.
    private static GraphicsAPI parseGraphicsBackend(JsonObject source, Renderer renderer) {
        String name = JsonUtils.getString(source, "graphicsBackend", null);
        if (name != null) {
            try {
                return GraphicsAPI.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return renderer instanceof Renderer.Driver driver ? driver.api() : GraphicsAPI.DEFAULT;
    }

    /// Parses the legacy icon selection with frozen ordinal order.
    private static VersionIconType parseLegacyVersionIconType(@Nullable JsonObject source) {
        JsonPrimitive primitive = JsonUtils.getPrimitive(source, "versionIcon");
        if (primitive == null) {
            return VersionIconType.DEFAULT;
        }

        try {
            if (primitive.isNumber()) {
                int index = primitive.getAsInt();
                return index >= 0 && index < LEGACY_VERSION_ICON_TYPES.length
                        ? LEGACY_VERSION_ICON_TYPES[index]
                        : VersionIconType.DEFAULT;
            }

            String value = primitive.getAsString();
            for (VersionIconType iconType : LEGACY_VERSION_ICON_TYPES) {
                if (iconType.name().equalsIgnoreCase(value)) {
                    return iconType;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return VersionIconType.DEFAULT;
    }

    /// Reads an enum property from either ordinal or name.
    private static <E extends Enum<E>> E parseEnum(@Nullable JsonObject source, String name, Class<E> type, E defaultValue) {
        JsonPrimitive primitive = JsonUtils.getPrimitive(source, name);
        if (primitive == null) {
            return defaultValue;
        }

        E[] constants = type.getEnumConstants();
        try {
            if (primitive.isNumber()) {
                int index = primitive.getAsInt();
                return index >= 0 && index < constants.length ? constants[index] : defaultValue;
            }

            String value = primitive.getAsString();
            for (E constant : constants) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return defaultValue;
    }

    /// Result of migrating a legacy per-version game settings file.
    ///
    /// @param path the legacy per-version game settings path
    /// @param receiptLocation the receipt path to write after saving the migrated settings
    /// @param setting the migrated instance game settings
    public record InstanceMigrationResult(
            Path path,
            Path receiptLocation,
            GameSettings.Instance setting) {
        /// Writes the migration receipt when receipt tracking is enabled.
        public void saveReceipt() {
            MigrationReceipt.save(receiptLocation, path);
        }
    }
}
