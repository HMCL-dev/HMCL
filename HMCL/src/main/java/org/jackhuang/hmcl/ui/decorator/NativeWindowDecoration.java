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
package org.jackhuang.hmcl.ui.decorator;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Accesses the JavaFX 27 extended-window APIs without linking them on older runtimes.
@NotNullByDefault
final class NativeWindowDecoration {
    /// The platform-supported extended stage style.
    final StageStyle style;

    /// The header bar that reserves space for the system window buttons.
    final Region headerBar;

    /// Assigns the header bar's center content.
    private final Method setCenter;

    /// Assigns a node's native header hit-test behavior.
    private final Method setDragType;

    /// Returns the stage's system-button color-scheme property.
    private final Method systemColorSchemeProperty;

    /// Marks only the specified node as draggable, preserving child control interaction.
    private final Object draggable;

    /// Excludes a node and its descendants from native dragging.
    private final Object notDraggable;

    /// The light system-button color scheme.
    private final Object light;

    /// The dark system-button color scheme.
    private final Object dark;

    /// Resolves all required public APIs and creates a detached header bar.
    ///
    /// @throws ReflectiveOperationException if the runtime does not expose a required API
    private NativeWindowDecoration() throws ReflectiveOperationException {
        style = (StageStyle) Objects.requireNonNull(StageStyle.class.getField("EXTENDED").get(null));
        Class<?> headerClass = Class.forName("javafx.scene.layout.HeaderBar");
        Class<?> dragClass = Class.forName("javafx.scene.layout.HeaderDragType");
        Class<?> colorClass = Class.forName("javafx.application.ColorScheme");
        setCenter = headerClass.getMethod("setCenter", Node.class);
        setDragType = headerClass.getMethod("setDragType", Node.class, dragClass);
        systemColorSchemeProperty = headerClass.getMethod("systemColorSchemeProperty", Stage.class);
        draggable = Objects.requireNonNull(dragClass.getField("DRAGGABLE").get(null));
        notDraggable = Objects.requireNonNull(dragClass.getField("NONE").get(null));
        light = Objects.requireNonNull(colorClass.getField("LIGHT").get(null));
        dark = Objects.requireNonNull(colorClass.getField("DARK").get(null));
        headerBar = (Region) headerClass.getConstructor().newInstance();
    }

    /// Creates native decoration support on JavaFX 27 or later when supported by the platform.
    ///
    /// @return the resolved support, or `null` on older or unsupported runtimes
    static @Nullable NativeWindowDecoration create() {
        try {
            int version = Integer.parseInt(System.getProperty("javafx.version", "0").split("[.\\-+]")[0]);
            if (version < 27 || !Platform.isSupported(ConditionalFeature.valueOf("EXTENDED_WINDOW"))) {
                return null;
            }
            return new NativeWindowDecoration();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warning("Native window decoration is unavailable; using custom decoration", e);
            return null;
        }
    }

    /// Sets or removes the header content; the caller must first detach it from any other parent.
    ///
    /// @param content the title-bar content, or `null` to detach it
    void setContent(@Nullable Node content) {
        try {
            setCenter.invoke(headerBar, content);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot update native header content", e);
        }
    }

    /// Marks a node as draggable or explicitly non-draggable during native header hit testing.
    ///
    /// Draggability does not extend to descendants, so interactive title content retains its behavior.
    ///
    /// @param node the node to configure
    /// @param enabled whether the node itself may move the window
    void setDraggable(Node node, boolean enabled) {
        try {
            setDragType.invoke(null, node, enabled ? draggable : notDraggable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot configure native header dragging", e);
        }
    }

    /// Keeps the platform window buttons synchronized with the launcher color scheme.
    ///
    /// @param stage the extended stage to configure
    @SuppressWarnings("unchecked")
    void configureStage(Stage stage) {
        try {
            ObjectProperty<Object> colorScheme = (ObjectProperty<Object>)
                    Objects.requireNonNull(systemColorSchemeProperty.invoke(null, stage));
            colorScheme.bind(Bindings.createObjectBinding(
                    () -> Themes.darkModeProperty().get() ? dark : light, Themes.darkModeProperty()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot configure native window buttons", e);
        }
    }
}
