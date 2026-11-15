// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.scene.shape.SVGPath;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorScheme;
import org.glavo.monetfx.ColorSpecVersion;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies that the title-bar task button uses the same foreground color as the other title-bar controls.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class MainWindowPaneTitleBarStyleTest {

    /// The style class marking a transparent title bar.
    private static final String TRANSPARENT_TITLE_BAR_STYLE_CLASS = "transparent-title-bar";

    /// Verifies that the transparent title-bar style class follows the transparency preference.
    @Test
    void appliesTransparentTitleBarStyleClass() throws Exception {
        onFxThread(() -> {
            BorderPane titleBar = new BorderPane();
            titleBar.getStyleClass().add("jfx-tool-bar");

            MainWindowPane.updateTitleBarTransparencyStyleClass(titleBar, true);
            assertTrue(titleBar.getStyleClass().contains(TRANSPARENT_TITLE_BAR_STYLE_CLASS));

            MainWindowPane.updateTitleBarTransparencyStyleClass(titleBar, false);
            assertFalse(titleBar.getStyleClass().contains(TRANSPARENT_TITLE_BAR_STYLE_CLASS));
        });
    }

    /// Verifies that the loading spinner of the task button follows the transparent title-bar foreground color.
    @Test
    void spinnerUsesOnSurfaceWhenTitleBarIsTransparent() throws Exception {
        ColorScheme scheme = darkGreenScheme();
        onFxThread(() -> {
            TitleBarFixture fixture = createTitleBarFixture(scheme, true);
            assertEquals(scheme.getOnSurface(), spinnerStroke(fixture));
        });
    }

    /// Verifies that the loading spinner of the task button keeps the primary container foreground color.
    @Test
    void spinnerUsesOnPrimaryContainerWhenTitleBarIsOpaque() throws Exception {
        ColorScheme scheme = darkGreenScheme();
        onFxThread(() -> {
            TitleBarFixture fixture = createTitleBarFixture(scheme, false);
            assertEquals(scheme.getOnPrimaryContainer(), spinnerStroke(fixture));
        });
    }

    /// Verifies that the bound fill of the title-bar icons is not overridden by the stylesheet.
    @Test
    void iconFillIsNotOverriddenByStylesheet() throws Exception {
        ColorScheme scheme = darkGreenScheme();
        onFxThread(() -> {
            TitleBarFixture fixture = createTitleBarFixture(scheme, true);
            SVGPath icon = fixture.icon();
            assertNotNull(icon);
            assertEquals(scheme.getOnSurface(), icon.getFill());
        });
    }

    /// Returns the dark color scheme generated from the green preset color.
    private static ColorScheme darkGreenScheme() {
        return ColorScheme.newBuilder()
                .setPrimaryColorSeed(Color.web("#43A047"))
                .setColorStyle(ColorStyle.FIDELITY)
                .setBrightness(Brightness.DARK)
                .setSpecVersion(ColorSpecVersion.SPEC_2025)
                .setContrast(Contrast.DEFAULT)
                .build();
    }

    /// Builds a title bar holding the task button and the help button.
    ///
    /// @param scheme      the color scheme providing the looked-up colors
    /// @param transparent whether the title bar is marked as transparent
    /// @return the created fixture
    private static TitleBarFixture createTitleBarFixture(ColorScheme scheme, boolean transparent) {
        JFXSpinner spinner = new JFXSpinner();
        spinner.setRadius(9);
        spinner.getStyleClass().add("small-spinner");

        JFXButton taskButton = new JFXButton();
        taskButton.getStyleClass().add("jfx-decorator-button");
        taskButton.setGraphic(spinner);

        SVGPath icon = SVG.LIST_ALT.createIcon(Bindings.createObjectBinding(() -> scheme.getOnSurface()));

        JFXButton helpButton = new JFXButton();
        helpButton.getStyleClass().add("jfx-decorator-button");
        helpButton.setGraphic(icon);

        BorderPane titleBar = new BorderPane(new HBox(taskButton, helpButton));
        titleBar.getStyleClass().add("jfx-tool-bar");
        if (transparent)
            MainWindowPane.updateTitleBarTransparencyStyleClass(titleBar, true);

        StackPane root = new StackPane(titleBar);
        Scene scene = new Scene(root, 400, 200);
        scene.getStylesheets().addAll(
                themeStyleSheet(scheme),
                Objects.requireNonNull(MainWindowPaneTitleBarStyleTest.class.getResource("/assets/css/root.css")).toExternalForm());
        root.applyCss();
        root.layout();

        return new TitleBarFixture(spinner, icon);
    }

    /// Returns the stroke color of the loading spinner.
    private static Color spinnerStroke(TitleBarFixture fixture) {
        @Nullable Shape arc = (Shape) fixture.spinner().lookup(".arc");
        assertNotNull(arc);
        @Nullable Object stroke = arc.getStroke();
        assertTrue(stroke instanceof Color);
        return (Color) stroke;
    }

    /// Builds the stylesheet providing the looked-up colors of the given color scheme.
    private static String themeStyleSheet(ColorScheme scheme) {
        StringBuilder builder = new StringBuilder("* {\n");
        for (ColorRole role : ColorRole.ALL) {
            builder.append("  ").append(role.getVariableName())
                    .append(": ").append(ThemeColor.getColorDisplayName(scheme.getColor(role))).append(";\n");
        }
        builder.append("}\n");
        return "data:text/css;charset=UTF-8;base64,"
                + Base64.getEncoder().encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    /// Holds the nodes of a created title bar.
    ///
    /// @param spinner the loading spinner of the task button
    /// @param icon    the icon of the help button
    private record TitleBarFixture(JFXSpinner spinner, SVGPath icon) {
    }

    /// Runs assertions on the application thread and propagates failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        callOnFxThread(() -> {
            action.run();
            return null;
        });
    }

    /// Returns a value from the application thread without sharing mutable test holders.
    private static <T extends @Nullable Object> T callOnFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
