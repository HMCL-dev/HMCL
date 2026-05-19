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
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.GraphicsAPI;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.QuickPlayType;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Locale;
import java.util.UUID;

/// Converts legacy `VersionSetting` JSON into `GameSetting` models.
@NotNullByDefault
public final class LegacyGameSettingMigrator {
    /// Legacy `VersionIconType` ordinal order used by `VersionSetting`.
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

    /// Prevents instantiation.
    private LegacyGameSettingMigrator() {
    }

    /// Converts a legacy `VersionSetting` JSON object into a named global game setting.
    public static GameSetting.Global toGlobal(String name, @Nullable JsonObject source) {
        GameSetting.Global target = new GameSetting.Global();
        target.nameProperty().setValue(name);
        if (getGameDirType(source, GameDirectoryType.ROOT_FOLDER) == GameDirectoryType.VERSION_FOLDER) {
            target.defaultIsolationTypeProperty().setValue(DefaultIsolationType.ALWAYS);
        }
        if (source != null) {
            copyCommonProperties(source, target);
        }
        return target;
    }

    /// Converts a legacy `VersionSetting` JSON object into an instance game setting.
    public static GameSetting.Instance toInstance(@Nullable UUID parent, @Nullable JsonObject source, boolean copyValues) {
        GameSetting.Instance target = new GameSetting.Instance();
        target.parentProperty().setValue(parent);
        target.iconProperty().setValue(parseLegacyVersionIconType(source));
        target.isolationProperty().setValue(getGameDirType(source, GameDirectoryType.ROOT_FOLDER) == GameDirectoryType.VERSION_FOLDER);
        if (source != null && copyValues) {
            copyCommonProperties(source, target);
            target.getOverrideProperties().addAll(java.util.List.of(
                    GameSetting.PROPERTY_JVM_OPTIONS,
                    GameSetting.PROPERTY_AUTO_MEMORY,
                    GameSetting.PROPERTY_MIN_MEMORY,
                    GameSetting.PROPERTY_MAX_MEMORY,
                    GameSetting.PROPERTY_PERM_SIZE,
                    GameSetting.PROPERTY_GAME_ARGS,
                    GameSetting.PROPERTY_ENVIRONMENT_VARIABLES,
                    GameSetting.PROPERTY_NOT_PATCH_NATIVES,
                    GameSetting.PROPERTY_NATIVES_DIR_TYPE,
                    GameSetting.PROPERTY_NATIVES_DIR,
                    GameSetting.PROPERTY_USE_NATIVE_GLFW,
                    GameSetting.PROPERTY_USE_NATIVE_OPENAL
            ));
        }
        return target;
    }

    /// Returns the legacy `usesGlobal` flag from a `VersionSetting` JSON object.
    public static boolean isUsesGlobal(@Nullable JsonObject source) {
        return readBoolean(source, "usesGlobal", false);
    }

    /// Returns the legacy game directory type from a `VersionSetting` JSON object.
    public static GameDirectoryType getGameDirType(@Nullable JsonObject source, GameDirectoryType defaultValue) {
        return parseEnum(source, "gameDirType", GameDirectoryType.class, defaultValue);
    }

    /// Copies shared legacy properties into the target setting.
    private static void copyCommonProperties(JsonObject source, GameSetting target) {
        JavaVersionType javaVersionType = parseLegacyJavaVersionType(source);
        target.javaTypeProperty().setValue(javaVersionType);
        target.javaVersionProperty().setValue(empty(parseLegacyJavaVersion(source)));
        target.customJavaPathProperty().setValue(readString(source, "javaDir", ""));
        target.defaultJavaPathProperty().setValue(readString(source, "defaultJavaPath", ""));

        target.jvmOptionsProperty().setValue(readString(source, "javaArgs", ""));
        target.noJVMOptionsProperty().setValue(readBoolean(source, "noJVMArgs", false));
        target.noOptimizingJVMOptionsProperty().setValue(readBoolean(source, "noOptimizingJVMArgs", false));
        target.notCheckJVMProperty().setValue(readBoolean(source, "notCheckJVM", false));
        target.notCheckGameProperty().setValue(readBoolean(source, "notCheckGame", false));

        int maxMemory = readInt(source, "maxMemory", GameSetting.SUGGESTED_MEMORY);
        target.autoMemoryProperty().setValue(readBoolean(source, "autoMemory", true));
        target.minMemoryProperty().setValue(readNullableInt(source, "minMemory"));
        target.maxMemoryProperty().setValue(maxMemory > 0 ? maxMemory : GameSetting.SUGGESTED_MEMORY);
        target.permSizeProperty().setValue(readString(source, "permSize", ""));

        target.windowTypeProperty().setValue(readBoolean(source, "fullscreen", false) ? GameWindowType.FULLSCREEN : GameWindowType.WINDOWED);
        target.widthProperty().setValue((double) readInt(source, "width", 0));
        target.heightProperty().setValue((double) readInt(source, "height", 0));
        GameDirectoryType gameDirType = getGameDirType(source, GameDirectoryType.ROOT_FOLDER);
        target.gameDirTypeProperty().setValue(gameDirType == GameDirectoryType.VERSION_FOLDER
                ? GameDirectoryType.ROOT_FOLDER
                : gameDirType);
        target.runningDirProperty().setValue(readString(source, "gameDir", ""));

        target.processPriorityProperty().setValue(parseEnum(source, "processPriority", ProcessPriority.class, ProcessPriority.NORMAL));
        target.launcherVisibilityProperty().setValue(parseEnum(source, "launcherVisibility", LauncherVisibility.class, LauncherVisibility.HIDE));
        target.gameArgsProperty().setValue(readString(source, "minecraftArgs", ""));
        Renderer renderer = parseLegacyRenderer(source);
        GraphicsAPI graphicsBackend = parseGraphicsBackend(source, renderer);
        target.graphicsBackendProperty().setValue(graphicsBackend);
        GameSetting.setRendererForApi(target, renderer, graphicsBackend);
        target.environmentVariablesProperty().setValue(readString(source, "environmentVariables", ""));
        target.commandWrapperProperty().setValue(readString(source, "wrapper", ""));
        target.preLaunchCommandProperty().setValue(readString(source, "precalledCommand", ""));
        target.postExitCommandProperty().setValue(readString(source, "postExitCommand", ""));

        String serverIp = readString(source, "serverIp", "");
        if (StringUtils.isBlank(serverIp)) {
            target.quickPlayProperty().setValue(QuickPlayType.NONE);
        } else {
            target.quickPlayProperty().setValue(QuickPlayType.MULTIPLAYER);
            target.quickPlayMultiplayerProperty().setValue(serverIp);
        }

        target.showLogsProperty().setValue(readBoolean(source, "showLogs", false));
        target.enableDebugLogOutputProperty().setValue(readBoolean(source, "enableDebugLogOutput", false));
        target.notPatchNativesProperty().setValue(readBoolean(source, "notPatchNatives", false));
        target.nativesDirTypeProperty().setValue(parseEnum(source, "nativesDirType", NativesDirectoryType.class, NativesDirectoryType.VERSION_FOLDER));
        target.nativesDirProperty().setValue(readString(source, "nativesDir", ""));
        target.useNativeGLFWProperty().setValue(readBoolean(source, "useNativeGLFW", false));
        target.useNativeOpenALProperty().setValue(readBoolean(source, "useNativeOpenAL", false));
    }

    /// Parses the legacy Java selection mode.
    private static JavaVersionType parseLegacyJavaVersionType(JsonObject source) {
        if (source.has("javaVersionType")) {
            return parseEnum(source, "javaVersionType", JavaVersionType.class, JavaVersionType.AUTO);
        }

        return switch (readString(source, "java", "")) {
            case "Default" -> JavaVersionType.DEFAULT;
            case "Auto" -> JavaVersionType.AUTO;
            case "Custom" -> JavaVersionType.CUSTOM;
            default -> JavaVersionType.AUTO;
        };
    }

    /// Parses the legacy Java version value.
    private static @Nullable String parseLegacyJavaVersion(JsonObject source) {
        if (source.has("javaVersionType")) {
            return readNullableString(source, "java");
        }

        String java = readString(source, "java", "");
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

        return readBoolean(source, "useSoftwareRenderer", false) ? Renderer.OpenGL.LLVMPIPE : Renderer.DEFAULT;
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
        String name = readNullableString(source, "graphicsBackend");
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
        JsonPrimitive primitive = getPrimitive(source, "versionIcon");
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

    /// Returns a JSON primitive property, or `null` when the property is absent or not primitive.
    private static @Nullable JsonPrimitive getPrimitive(@Nullable JsonObject source, String name) {
        if (source == null) {
            return null;
        }

        JsonElement element = source.get(name);
        return element instanceof JsonPrimitive primitive ? primitive : null;
    }

    /// Reads a nullable string property.
    private static @Nullable String readNullableString(@Nullable JsonObject source, String name) {
        JsonPrimitive primitive = getPrimitive(source, name);
        if (primitive == null) {
            return null;
        }

        try {
            return primitive.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /// Reads a string property.
    private static String readString(@Nullable JsonObject source, String name, String defaultValue) {
        return Lang.requireNonNullElse(readNullableString(source, name), defaultValue);
    }

    /// Reads a boolean property.
    private static boolean readBoolean(@Nullable JsonObject source, String name, boolean defaultValue) {
        JsonPrimitive primitive = getPrimitive(source, name);
        if (primitive == null) {
            return defaultValue;
        }

        try {
            return primitive.getAsBoolean();
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    /// Reads an integer property.
    private static int readInt(@Nullable JsonObject source, String name, int defaultValue) {
        JsonPrimitive primitive = getPrimitive(source, name);
        if (primitive == null) {
            return defaultValue;
        }

        try {
            return primitive.isNumber() ? primitive.getAsInt() : Lang.parseInt(primitive.getAsString(), defaultValue);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    /// Reads a nullable integer property.
    private static @Nullable Integer readNullableInt(@Nullable JsonObject source, String name) {
        JsonPrimitive primitive = getPrimitive(source, name);
        if (primitive == null) {
            return null;
        }

        try {
            return primitive.isNumber() ? primitive.getAsInt() : Lang.toIntOrNull(primitive.getAsString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /// Reads an enum property from either ordinal or name.
    private static <E extends Enum<E>> E parseEnum(@Nullable JsonObject source, String name, Class<E> type, E defaultValue) {
        JsonPrimitive primitive = getPrimitive(source, name);
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

    /// Returns an empty string for `null` values.
    private static String empty(@Nullable String value) {
        return value != null ? value : "";
    }
}
