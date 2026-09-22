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
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.util.platform.OSVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Accesses the JavaFX 27 extended-window APIs without linking them on older runtimes.
@NotNullByDefault
final class NativeWindowDecoration {
    /// Marks nodes whose title-content descendants are already observed for native hit testing.
    private static final Object DRAG_CONFIGURED = new Object();

    /// The platform-supported extended stage style.
    final StageStyle style;

    /// The header bar that supplies native dragging around the custom title content.
    final Region headerBar;

    /// Assigns the header bar's center content.
    private final Method setCenter;

    /// Assigns a node's native header hit-test behavior.
    private final Method setDragType;

    /// Reads explicit native drag exclusions on title nodes.
    private final Method getDragType;

    /// Sets the system button height to zero to hide the platform-provided buttons.
    private final Method setSystemButtonHeight;

    /// Marks only the specified node as draggable, preserving child control interaction.
    private final Object draggable;

    /// Excludes a node and its descendants from native dragging.
    private final Object notDraggable;

    /// Ignores an overlay and its descendants when testing underlying native header areas.
    private final Object transparentSubtree;

    /// Resolves all required public APIs and creates a detached header bar.
    ///
    /// @throws ReflectiveOperationException if the runtime does not expose a required API
    private NativeWindowDecoration() throws ReflectiveOperationException {
        style = (StageStyle) Objects.requireNonNull(StageStyle.class.getField("EXTENDED").get(null));
        Class<?> headerClass = Class.forName("javafx.scene.layout.HeaderBar");
        Class<?> dragClass = Class.forName("javafx.scene.layout.HeaderDragType");
        setCenter = headerClass.getMethod("setCenter", Node.class);
        setDragType = headerClass.getMethod("setDragType", Node.class, dragClass);
        getDragType = headerClass.getMethod("getDragType", Node.class);
        setSystemButtonHeight = headerClass.getMethod("setSystemButtonHeight", Stage.class, double.class);
        draggable = Objects.requireNonNull(dragClass.getField("DRAGGABLE").get(null));
        notDraggable = Objects.requireNonNull(dragClass.getField("NONE").get(null));
        transparentSubtree = Objects.requireNonNull(dragClass.getField("TRANSPARENT_SUBTREE").get(null));
        headerBar = (Region) headerClass.getConstructor().newInstance();
    }

    /// Creates native decoration support on macOS or Windows 11 and later when JavaFX 27 or later supports it.
    ///
    /// @return the resolved support, or `null` on other platforms or unsupported runtimes
    static @Nullable NativeWindowDecoration create() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS
                && !OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_11)) {
            return null;
        }
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
    /// Configures native dragging on non-interactive descendants and observes later child additions.
    /// Controls other than labels and nodes explicitly excluded from dragging retain ordinary input handling.
    ///
    /// @param content the title-bar content, or `null` to detach it
    void setContent(@Nullable Node content) {
        try {
            if (content != null) {
                configureTitleDragging(content);
            }
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

    /// Lets native header hit testing pass through an overlay and its descendants.
    ///
    /// Ordinary mouse picking is unchanged. This does not create draggable areas outside the underlying
    /// header. Descendants excluded with [#setDraggable(Node, boolean)] block native dragging instead.
    ///
    /// @param overlay the overlay above the header
    void setDragTransparent(Node overlay) {
        try {
            setDragType.invoke(null, overlay, transparentSubtree);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot configure native header overlay", e);
        }
    }

    /// Configures native hit testing for title content, including subsequently added descendants.
    ///
    /// Containers and label graphics may move the window. Other controls and nodes explicitly excluded with
    /// [#setDraggable(Node, boolean)] retain ordinary input handling, including all of their descendants.
    ///
    /// @param node the title content to configure
    private void configureTitleDragging(Node node) {
        if (node.getProperties().putIfAbsent(DRAG_CONFIGURED, Boolean.TRUE) != null) {
            return;
        }
        try {
            if (getDragType.invoke(null, node) == notDraggable) {
                return;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read native header dragging", e);
        }
        if (node instanceof Control && !(node instanceof Label)) {
            setDraggable(node, false);
            return;
        }

        // Native hit testing starts at the deepest picked node; marking only its parent is insufficient.
        setDraggable(node, true);
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) change -> {
                while (change.next()) {
                    for (Node child : change.getAddedSubList()) {
                        configureTitleDragging(child);
                    }
                }
            });
            for (Node child : parent.getChildrenUnmodifiable()) {
                configureTitleDragging(child);
            }
        }
    }

    /// Hides platform-provided window buttons.
    ///
    /// @param stage the extended stage to configure
    void configureStage(Stage stage) {
        try {
            setSystemButtonHeight.invoke(null, stage, 0.0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot hide native window buttons", e);
        }
    }
}
