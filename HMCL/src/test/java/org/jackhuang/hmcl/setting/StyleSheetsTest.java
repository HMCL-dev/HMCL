// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.glavo.m3fx.animation.M3MotionSettings;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies automatic window theming and live appearance changes through normal scene initialization.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
@Isolated("Temporarily supplies in-memory launcher settings")
final class StyleSheetsTest {
    /// Scenes share the launcher theme, including controls added after initialization and replacement roots.
    @Test
    void themesAllInitializedScenes() throws Exception {
        Field settingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        settingsField.setAccessible(true);
        @Nullable Object previous = settingsField.get(null);
        LauncherSettings settings = new LauncherSettings();
        settings.launcherFontFamilyProperty().set("System");
        settings.getThemeAppearanceOverrides().addAll(List.of(
                LauncherSettings.THEME_APPEARANCE_COLOR, LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE));
        settings.themeColorTypeProperty().set(ThemeColorType.CUSTOM);
        settings.themeBrightnessModeProperty().set("light");
        settingsField.set(null, settings);
        try {
            onFxThread(() -> {
                VBox mainRoot = new VBox();
                Scene main = new Scene(mainRoot);
                Scene secondary = new Scene(new VBox());
                M3Button windowButton = new M3Button("Window action");
                Decorator decorator = new Decorator(new StackPane(windowButton), stage -> { });
                Scene window = new Scene(decorator.getRoot());
                StyleSheets.init(window, decorator.getContentRoot());
                decorator.getRoot().applyCss();
                assertFalse(decorator.getRoot().getStyleClass().contains(M3ThemeManager.ROOT_STYLE_CLASS));
                assertTrue(decorator.getContentRoot().getStyleClass().contains(M3ThemeManager.ROOT_STYLE_CLASS));
                assertNull(M3ThemeManager.getTheme(window));
                assertEquals(Color.TRANSPARENT,
                        ((StackPane) decorator.getRoot()).getBackground().getFills().getFirst().getFill());
                String externalStylesheet = "data:text/css;base64," + Base64.getEncoder().encodeToString(
                        ".external-style { -fx-padding: 7px; }".getBytes(StandardCharsets.UTF_8));
                main.getStylesheets().add(externalStylesheet);
                StyleSheets.init(main);
                StyleSheets.init(secondary);
                M3Theme initialTheme = Objects.requireNonNull(M3ThemeManager.getTheme(main));
                assertEquals(M3ThemeManager.getTheme(main), M3ThemeManager.getTheme(secondary));
                assertEquals(!AnimationUtils.isAnimationEnabled(), M3MotionSettings.isGlobalReducedMotionRequested());

                M3Button button = new M3Button("Action");
                mainRoot.getChildren().add(button);
                @Unmodifiable List<String> initialStylesheets = List.copyOf(main.getStylesheets());
                StyleSheets.init(main);
                assertEquals(initialStylesheets, main.getStylesheets());

                settings.themeBrightnessModeProperty().set("dark");
                settings.customThemeColorProperty().set(ThemeColor.of(Color.PURPLE));
                FontManager.setFontFamily("Monospaced");
                mainRoot.applyCss();

                M3Theme theme = Objects.requireNonNull(M3ThemeManager.getTheme(main));
                assertEquals(Brightness.DARK, theme.brightness());
                assertSame(Themes.getColorScheme(), theme.colorScheme());
                assertSame(theme, M3ThemeManager.getTheme(secondary));
                assertSame(theme, M3ThemeManager.getTheme(decorator.getContentRoot()));
                decorator.getRoot().applyCss();
                assertEquals(button.getFont(), windowButton.getFont());
                assertEquals(button.getTextFill(), windowButton.getTextFill());
                assertEquals(Color.TRANSPARENT,
                        ((StackPane) decorator.getRoot()).getBackground().getFills().getFirst().getFill());
                assertEquals(Font.font("Monospaced").getFamily(), button.getFont().getFamily());
                assertEquals(theme.tokens().typographyTokens().labelLarge().size(), button.getFont().getSize());
                assertColorEquals(theme.colorScheme().getColor(ColorRole.ON_PRIMARY), (Color) button.getTextFill());
                assertEquals(initialStylesheets.size(), main.getStylesheets().size());
                assertEquals(initialStylesheets.indexOf(externalStylesheet), main.getStylesheets().indexOf(externalStylesheet));
                var expected = initialTheme.tokens().typographyTokens().labelLarge();
                var actual = theme.tokens().typographyTokens().labelLarge();
                assertEquals(expected.size(), actual.size());
                assertEquals(expected.weight(), actual.weight());
                assertEquals(expected.lineHeight(), actual.lineHeight());
                assertEquals(expected.tracking(), actual.tracking());

                M3Button replacementButton = new M3Button("Replacement");
                VBox replacement = new VBox(replacementButton);
                main.setRoot(replacement);
                replacement.applyCss();
                assertSame(theme, M3ThemeManager.getTheme(replacement));
                assertNull(M3ThemeManager.getTheme(mainRoot));
                assertEquals(button.getFont(), replacementButton.getFont());

                FontManager.setFontFamily("System");
                replacement.applyCss();
                assertEquals(Font.getDefault().getFamily(), replacementButton.getFont().getFamily());
            });
        } finally {
            settingsField.set(null, previous);
        }
    }

    /// Allows the byte-channel rounding used by generated M3FX CSS.
    private static void assertColorEquals(Color expected, Color actual) {
        assertEquals(expected.getRed(), actual.getRed(), 1.0 / 255);
        assertEquals(expected.getGreen(), actual.getGreen(), 1.0 / 255);
        assertEquals(expected.getBlue(), actual.getBlue(), 1.0 / 255);
    }

    /// Runs scene-graph assertions on the JavaFX thread and propagates failures.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
