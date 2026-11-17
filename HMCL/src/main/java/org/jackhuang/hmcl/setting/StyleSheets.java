// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import org.glavo.m3fx.animation.M3MotionSettings;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.m3fx.tokens.M3TextStyle;
import org.glavo.m3fx.tokens.M3TokenSet;
import org.glavo.m3fx.tokens.M3TypographyTokens;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.theme.ResolvedTheme;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/// Maintains launcher styles and the shared M3FX theme for initialized scenes.
public final class StyleSheets {
    /// The launcher font stylesheet slot.
    private static final int FONT_STYLE_SHEET_INDEX = 0;
    /// The launcher color stylesheet slot.
    private static final int THEME_STYLE_SHEET_INDEX = 1;
    /// The launcher brightness stylesheet slot.
    private static final int BRIGHTNESS_SHEET_INDEX = 2;

    /// Live scenes and optional subtree theme roots, without retaining closed windows.
    private static final Map<Scene, @Nullable WeakReference<Parent>> scenes = new WeakHashMap<>();

    /// Current launcher stylesheet URLs, replaced when appearance settings change.
    private static final String[] stylesheets;

    /// The shared theme used by existing and newly initialized scenes.
    private static M3Theme m3Theme;

    static {
        stylesheets = new String[]{
                getFontStyleSheet(),
                getThemeStyleSheet(),
                getBrightnessStyleSheet(),
                "/assets/css/root.css"
        };
        m3Theme = createM3Theme();
        M3MotionSettings.setGlobalReducedMotionRequested(!AnimationUtils.isAnimationEnabled());

        FontManager.fontProperty().addListener(o -> {
            updateStyleSheet(FONT_STYLE_SHEET_INDEX, getFontStyleSheet());
            updateM3Theme();
        });
        Themes.colorSchemeProperty().addListener(o -> {
            updateStyleSheet(THEME_STYLE_SHEET_INDEX, getThemeStyleSheet());
            updateStyleSheet(BRIGHTNESS_SHEET_INDEX, getBrightnessStyleSheet());
            updateM3Theme();
        });
    }

    private static String toStyleSheetUri(String styleSheet) {
        return "data:text/css;charset=UTF-8;base64," + Base64.getEncoder().encodeToString(styleSheet.getBytes(StandardCharsets.UTF_8));
    }

    private static String getFontStyleSheet() {
        final String defaultCss = "/assets/css/font.css";
        final FontManager.FontReference font = FontManager.getFont();

        if (font == null || "System".equals(font.family()))
            return defaultCss;

        String fontFamily = font.family();
        String style = font.style();
        String weight = null;
        String posture = null;

        if (style != null) {
            style = style.toLowerCase(Locale.ROOT);

            if (style.contains("thin"))
                weight = "100";
            else if (style.contains("extralight") || style.contains("extra light") || style.contains("ultralight") | style.contains("ultra light"))
                weight = "200";
            else if (style.contains("medium"))
                weight = "500";
            else if (style.contains("semibold") || style.contains("semi bold") || style.contains("demibold") || style.contains("demi bold"))
                weight = "600";
            else if (style.contains("extrabold") || style.contains("extra bold") || style.contains("ultrabold") || style.contains("ultra bold"))
                weight = "800";
            else if (style.contains("black") || style.contains("heavy"))
                weight = "900";
            else if (style.contains("light"))
                weight = "lighter";
            else if (style.contains("bold"))
                weight = "bold";

            posture = style.contains("italic") || style.contains("oblique") ? "italic" : null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(".root {");
        builder.append("-fx-font-family:\"").append(fontFamily).append("\";");

        if (weight != null)
            builder.append("-fx-font-weight:").append(weight).append(";");

        if (posture != null)
            builder.append("-fx-font-style:").append(posture).append(";");

        builder.append('}');

        return toStyleSheetUri(builder.toString());
    }

    private static String getBrightnessStyleSheet() {
        return Themes.getColorScheme().getBrightness() == Brightness.LIGHT
                ? "/assets/css/brightness-light.css"
                : "/assets/css/brightness-dark.css";
    }

    private static void addColor(StringBuilder builder, String name, Color color) {
        builder.append("  ").append(name)
                .append(": ").append(ThemeColor.getColorDisplayName(color)).append(";\n");
    }

    private static void addColor(StringBuilder builder, String name, Color color, double opacity) {
        builder.append("  ").append(name)
                .append(": ").append(ThemeColor.getColorDisplayNameWithOpacity(color, opacity)).append(";\n");
    }

    private static void addColor(StringBuilder builder, ColorScheme scheme, ColorRole role, double opacity) {
        builder.append("  ").append(role.getVariableName()).append("-transparent-%02d".formatted((int) (100 * opacity)))
                .append(": ").append(ThemeColor.getColorDisplayNameWithOpacity(scheme.getColor(role), opacity))
                .append(";\n");
    }

    private static String getThemeStyleSheet() {
        final String blueCss = "/assets/css/blue.css";

        if (ResolvedTheme.DEFAULT.equals(Themes.getTheme()))
            return blueCss;

        ColorScheme scheme = Themes.getColorScheme();

        StringBuilder builder = new StringBuilder();
        builder.append("* {\n");
        for (ColorRole colorRole : ColorRole.ALL) {
            addColor(builder, colorRole.getVariableName(), scheme.getColor(colorRole));
        }

        addColor(builder, "-monet-primary-seed", scheme.getPrimaryColorSeed());

        addColor(builder, scheme, ColorRole.PRIMARY, 0.5);
        addColor(builder, scheme, ColorRole.PRIMARY, 0.8);
        addColor(builder, scheme, ColorRole.SECONDARY_CONTAINER, 0.5);
        addColor(builder, scheme, ColorRole.SURFACE, 0.5);
        addColor(builder, scheme, ColorRole.SURFACE, 0.8);
        addColor(builder, scheme, ColorRole.ON_SURFACE_VARIANT, 0.38);
        addColor(builder, scheme, ColorRole.SURFACE_CONTAINER_LOW, 0.8);
        addColor(builder, scheme, ColorRole.SECONDARY_CONTAINER, 0.8);
        addColor(builder, scheme, ColorRole.INVERSE_SURFACE, 0.8);

        builder.append("}\n");
        return toStyleSheetUri(builder.toString());
    }

    /// Installs live launcher and M3FX themes for the entire scene, preserving externally added stylesheets.
    public static void init(Scene scene) {
        init(scene, null);
    }

    /// Initializes launcher styles, restricting M3FX to `themeRoot` when non-null.
    /// The first initialization fixes the theme scope for this scene.
    public static void init(Scene scene, @Nullable Parent themeRoot) {
        if (!scenes.containsKey(scene)) {
            scenes.put(scene, themeRoot == null ? null : new WeakReference<>(themeRoot));
            scene.getStylesheets().addAll(stylesheets);
            applyM3Theme(scene, scenes.get(scene));
        }
    }

    /// Applies the shared theme to a scene or its retained content subtree.
    private static void applyM3Theme(Scene scene, @Nullable WeakReference<Parent> rootReference) {
        if (rootReference == null) {
            M3ThemeManager.install(scene, m3Theme);
        } else {
            @Nullable Parent root = rootReference.get();
            if (root != null)
                M3ThemeManager.install(root, m3Theme);
        }
    }

    /// Replaces one launcher stylesheet without disturbing entries added by M3FX or application code.
    private static void updateStyleSheet(int slot, String stylesheet) {
        String previous = stylesheets[slot];
        if (previous.equals(stylesheet))
            return;
        stylesheets[slot] = stylesheet;
        for (Scene scene : scenes.keySet()) {
            // M3FX can insert stylesheets before ours, so scene indices are not stable.
            int index = scene.getStylesheets().indexOf(previous);
            if (index >= 0)
                scene.getStylesheets().set(index, stylesheet);
        }
    }

    /// Applies the current resolved colors and font family to every initialized scene.
    private static void updateM3Theme() {
        m3Theme = createM3Theme();
        scenes.forEach(StyleSheets::applyM3Theme);
    }

    /// Reuses launcher colors and font family while retaining Material typography metrics.
    private static M3Theme createM3Theme() {
        M3Theme base = M3Theme.fromColorScheme(Themes.getColorScheme());
        FontManager.@Nullable FontReference font = FontManager.getFont();
        if (font == null || "System".equals(font.family()))
            return base;

        String family = font.family();
        M3TypographyTokens typography = base.tokens().typographyTokens();
        M3TypographyTokens adapted = M3TypographyTokens.builder(typography)
                .displayLarge(withFamily(typography.displayLarge(), family))
                .displayMedium(withFamily(typography.displayMedium(), family))
                .displaySmall(withFamily(typography.displaySmall(), family))
                .headlineLarge(withFamily(typography.headlineLarge(), family))
                .headlineMedium(withFamily(typography.headlineMedium(), family))
                .headlineSmall(withFamily(typography.headlineSmall(), family))
                .titleLarge(withFamily(typography.titleLarge(), family))
                .titleMedium(withFamily(typography.titleMedium(), family))
                .titleSmall(withFamily(typography.titleSmall(), family))
                .labelLarge(withFamily(typography.labelLarge(), family))
                .labelMedium(withFamily(typography.labelMedium(), family))
                .labelSmall(withFamily(typography.labelSmall(), family))
                .bodyLarge(withFamily(typography.bodyLarge(), family))
                .bodyMedium(withFamily(typography.bodyMedium(), family))
                .bodySmall(withFamily(typography.bodySmall(), family))
                .build();
        return M3Theme.fromTokenSet(M3TokenSet.builder(base.tokens()).typographyTokens(adapted).build());
    }

    /// Changes a text role's family without altering its size, weight, line height, or tracking.
    private static M3TextStyle withFamily(M3TextStyle style, String family) {
        return M3TextStyle.of(family, style.size(), style.lineHeight(), style.weight(), style.tracking());
    }

    private StyleSheets() {
    }
}
