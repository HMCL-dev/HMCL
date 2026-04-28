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
package org.jackhuang.hmcl.theme2;

import com.google.gson.*;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Represents a resolved or unresolved theme configuration for HMCL.
///
/// A theme describes visual settings such as brightness, accent color, color style,
/// background image and opacity, and contrast level. It may also carry a list of
/// conditional [overrides] that are merged on top of the base values when
/// [resolve(Map)] is called.
///
/// Themes are typically loaded from a JSON object via [fromJson(JsonObject)].
/// After loading, call [resolve(Map)] with the current feature map to obtain
/// a fully resolved [Theme] whose [overrides] list is empty.
///
/// @param version           The schema version string declared in the JSON source, or `null` if absent.
/// @param brightness        The preferred brightness (light or dark), or `null` to use the system default.
/// @param color             The accent color of the theme, or `null` to use the default color.
/// @param colorStyle        The color generation style, or `null` to use the default style.
/// @param background        The background configuration, or `null` if no custom background is set.
/// @param backgroundOpacity The opacity of the background image in the range `[0.0, 1.0]`, or `null` if not specified.
/// @param contrast          The contrast level preference, or `null` to use the system default.
/// @param rules             The compatibility rules that guard this theme entry. An empty list means the theme always applies.
/// @param overrides         The overrides that are merged on top of the base values when [resolve(Map)] is called.
/// @author Glavo
public record Theme(
        @Nullable String version,
        @Nullable Brightness brightness,
        @Nullable ThemeColor2 color,
        @Nullable ColorStyle colorStyle,
        @Nullable ThemeBackground background,
        @Nullable Double backgroundOpacity,
        @Nullable Contrast contrast,
        @NotNull List<CompatibilityRule> rules,
        @NotNull List<Theme> overrides
) {
    /// The current default (and maximum supported) theme schema version.
    public static final String DEFAULT_VERSION = "1";

    /// Returns `true` if this theme has no pending overrides and does not need
    /// to be resolved further.
    public boolean isResolved() {
        return overrides.isEmpty();
    }

    /// Resolves this theme against the given feature map by iterating over
    /// [overrides] in order and merging each override whose compatibility rules
    /// are satisfied by `features` into the base values.
    ///
    /// If this theme [isResolved()] already, `this` is returned unchanged.
    /// If no override ends up being applicable, `this` is also returned unchanged.
    /// Otherwise a new [Theme] record is returned containing the merged field values
    /// while keeping the original [rules] and [overrides] references.
    ///
    /// @param features a map of named feature flags used to evaluate [CompatibilityRule]s
    /// @return a [Theme] with all applicable overrides merged in
    public Theme resolve(Map<String, Boolean> features) {
        if (isResolved())
            return this;

        String version = this.version;
        Brightness brightness = this.brightness;
        ThemeColor2 color = this.color;
        ColorStyle colorStyle = this.colorStyle;
        ThemeBackground background = this.background;
        Double backgroundOpacity = this.backgroundOpacity;
        Contrast contrast = this.contrast;

        boolean hasOverride = false;
        for (Theme override : overrides) {
            if (!override.rules().isEmpty()) {
                if (!CompatibilityRule.appliesToCurrentEnvironment(override.rules(), features)) {
                    continue;
                }
            }

            hasOverride = true;

            if (override.brightness != null)
                brightness = override.brightness;
            if (override.color != null)
                color = override.color;
            if (override.colorStyle != null)
                colorStyle = override.colorStyle;
            if (override.background != null)
                background = override.background;
            if (override.backgroundOpacity != null)
                backgroundOpacity = override.backgroundOpacity;
            if (override.contrast != null)
                contrast = override.contrast;
        }

        return hasOverride ? new Theme(
                version,
                brightness,
                color,
                colorStyle,
                background,
                backgroundOpacity,
                contrast,
                rules,
                overrides
        ) : this;
    }

    /// Parses a [Theme] from the given JSON object.
    ///
    /// Unrecognised or malformed field values are silently ignored (a warning is
    /// logged) and the corresponding record component is set to `null`.
    /// The returned theme always has empty [rules] and [overrides] lists; callers
    /// that need conditional overrides must assemble the full theme graph themselves.
    ///
    /// @param json the JSON object to parse
    /// @return the parsed [Theme]
    /// @throws JsonParseException if the declared schema version is newer than
    ///                            [DEFAULT_VERSION]
    public static Theme fromJson(JsonObject json) throws JsonParseException {
        if (json.get("version") instanceof JsonPrimitive version) {
            if (VersionNumber.compare(version.getAsString(), DEFAULT_VERSION) >= 0)
                throw new JsonParseException("Unsupported theme version: " + version.getAsString());
        }

        Brightness brightness;
        JsonElement brightnessJson = json.get("brightness");
        if (brightnessJson != null) {
            if (brightnessJson instanceof JsonPrimitive primitive)
                brightness = switch (primitive.getAsString().toLowerCase(Locale.ROOT)) {
                    case "light" -> Brightness.LIGHT;
                    case "dark" -> Brightness.DARK;
                    default -> null;
                };
            else
                brightness = null;

            if (brightness == null)
                LOG.warning("Invalid brightness: " + brightnessJson);
        } else {
            brightness = null;
        }

        ThemeColor2 color;
        JsonElement colorJson = json.get("color");
        if (colorJson != null) {
            try {
                color = ThemeColor2.fromJson(colorJson);
            } catch (Exception e) {
                LOG.warning("Invalid color JSON format: " + colorJson);
                color = null;
            }
        } else {
            color = null;
        }

        ColorStyle colorStyle;
        JsonElement colorStyleJson = json.get("colorStyle");
        if (colorStyleJson != null) {
            try {
                colorStyle = ColorStyle.valueOf(colorStyleJson.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LOG.warning("Invalid color style: " + colorStyleJson);
                colorStyle = null;
            }
        } else {
            colorStyle = null;
        }

        ThemeBackground background;
        JsonElement backgroundJson = json.get("background");
        if (backgroundJson != null) {
            try {
                background = ThemeBackground.fromJson(backgroundJson);
            } catch (Exception e) {
                LOG.warning("Invalid theme background: " + backgroundJson, e);
                background = null;
            }
        } else {
            background = null;
        }

        Double backgroundOpacity;
        JsonElement backgroundOpacityJson = json.get("backgroundOpacity");
        if (backgroundOpacityJson != null) {
            if (backgroundOpacityJson instanceof JsonPrimitive primitive) {
                double value = primitive.getAsDouble();
                backgroundOpacity = value >= 0 && value <= 1 ? value : null;
            } else
                backgroundOpacity = null;
            if (backgroundOpacity == null)
                LOG.warning("Invalid background opacity: " + backgroundOpacityJson);
        } else {
            backgroundOpacity = null;
        }

        Contrast contrast;
        JsonElement contrastJson = json.get("contrast");
        if (contrastJson != null) {
            if (contrastJson instanceof JsonPrimitive primitive)
                if (primitive.isNumber()) {
                    double contrastValue = primitive.getAsDouble();
                    contrast = Double.isNaN(contrastValue)
                            ? null
                            : Contrast.of(MathUtils.clamp(contrastValue, -1.0, 1.0));
                } else {
                    contrast = switch (primitive.getAsString().toLowerCase(Locale.ROOT)) {
                        case "high" -> Contrast.HIGH;
                        case "low" -> Contrast.LOW;
                        default -> null;
                    };
                }
            else
                contrast = null;

            if (contrast == null)
                LOG.warning("Invalid contrast: " + contrastJson);
        } else {
            contrast = null;
        }

        return new Theme(
                json.get("version") instanceof JsonPrimitive version ? version.getAsString() : null,
                brightness,
                color,
                colorStyle,
                background,
                backgroundOpacity,
                contrast,
                List.of(),
                List.of()
        );
    }

    /// Serializes this theme to a [JsonObject].
    ///
    /// Only the base scalar fields are written; [rules] and [overrides] are not
    /// included because [fromJson(JsonObject)] does not parse them.
    /// Fields whose value is `null` are omitted from the output.
    ///
    /// @return a [JsonObject] representing this theme's fields
    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();

        if (version != null)
            jsonObject.addProperty("version", version);

        if (brightness != null)
            jsonObject.addProperty("brightness", brightness == Brightness.LIGHT ? "light" : "dark");

        if (color != null)
            jsonObject.addProperty("color", color.name());

        if (colorStyle != null)
            jsonObject.addProperty("colorStyle", colorStyle.name().toLowerCase(Locale.ROOT));

        if (background != null) {
            jsonObject.add("background", background.toJson());
        }

        if (backgroundOpacity != null)
            jsonObject.addProperty("backgroundOpacity", backgroundOpacity);

        if (contrast != null)
            jsonObject.addProperty("contrast", contrast == Contrast.HIGH ? "high" : "low");

        return jsonObject;
    }
}
