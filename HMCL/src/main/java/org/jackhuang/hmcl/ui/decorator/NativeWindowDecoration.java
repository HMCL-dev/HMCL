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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Accesses the JavaFX 27 extended-window APIs without linking them on older runtimes.
@NotNullByDefault
final class NativeWindowDecoration {
    /// Marks nodes whose title-content descendants are already observed for native hit testing.
    private static final Object DRAG_CONFIGURED = new Object();

    /// Whether the user explicitly enabled native decoration regardless of automatic platform or brightness exclusions.
    private final boolean forced;

    /// The platform-supported extended stage style.
    final StageStyle style;

    /// The header bar that supplies native dragging around the custom title content.
    final Region headerBar;

    /// Assigns the header bar's center content.
    private final MethodHandle setCenter;

    /// Assigns a node's native header hit-test behavior.
    private final MethodHandle setDragType;

    /// Reads explicit native drag exclusions on title nodes.
    private final MethodHandle getDragType;

    /// Sets the system button height to zero to hide the platform-provided buttons.
    private final MethodHandle setSystemButtonHeight;

    /// Marks only the specified node as draggable, preserving child control interaction.
    private final Object draggable;

    /// Excludes a node and its descendants from native dragging.
    private final Object notDraggable;

    /// Ignores an overlay and its descendants when testing underlying native header areas.
    private final Object transparentSubtree;

    /// Resolves all required public APIs and creates a detached header bar.
    ///
    /// @param forced whether to bypass the automatic theme brightness exclusion
    /// @throws Throwable if a required API cannot be resolved or invoked
    private NativeWindowDecoration(boolean forced) throws Throwable {
        this.forced = forced;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        style = (StageStyle) lookup.findStaticGetter(StageStyle.class, "EXTENDED", StageStyle.class).invokeExact();
        Class<?> headerClass = Class.forName("javafx.scene.layout.HeaderBar");
        Class<?> dragClass = Class.forName("javafx.scene.layout.HeaderDragType");
        // Adapt runtime-only types once so cached handles can be invoked with exact, statically known signatures.
        setCenter = lookup.findVirtual(headerClass, "setCenter", MethodType.methodType(void.class, Node.class))
                .asType(MethodType.methodType(void.class, Region.class, Node.class));
        setDragType = lookup.findStatic(headerClass, "setDragType", MethodType.methodType(void.class, Node.class, dragClass))
                .asType(MethodType.methodType(void.class, Node.class, Object.class));
        getDragType = lookup.findStatic(headerClass, "getDragType", MethodType.methodType(dragClass, Node.class))
                .asType(MethodType.methodType(Object.class, Node.class));
        setSystemButtonHeight = lookup.findStatic(headerClass, "setSystemButtonHeight",
                MethodType.methodType(void.class, Stage.class, double.class));
        draggable = Objects.requireNonNull(lookup.findStaticGetter(dragClass, "DRAGGABLE", dragClass).invoke());
        notDraggable = Objects.requireNonNull(lookup.findStaticGetter(dragClass, "NONE", dragClass).invoke());
        transparentSubtree = Objects.requireNonNull(lookup.findStaticGetter(dragClass, "TRANSPARENT_SUBTREE", dragClass).invoke());
        headerBar = (Region) lookup.findConstructor(headerClass, MethodType.methodType(void.class)).invoke();
    }

    /// Creates native decoration support according to the configured preference and runtime capabilities.
    ///
    /// The `hmcl.nativeDecoration` system property takes precedence over `HMCL_NATIVE_DECORATION`.
    /// Values are case-insensitive and trimmed: `false` disables native decoration, `true` enables it on
    /// any supported platform, and `auto` enables it only on macOS or Windows 11 and later. Missing or
    /// unrecognized values use `auto`. All modes require JavaFX 27 or later and extended-window support.
    /// The preference is read when this method is called and retained by the returned instance.
    ///
    /// @return the resolved support, or `null` when disabled by policy or unavailable on the runtime
    static @Nullable NativeWindowDecoration create() {
        @Nullable String preference = System.getProperty("hmcl.nativeDecoration", System.getenv("HMCL_NATIVE_DECORATION"));
        if (preference != null) {
            preference = preference.trim();
        }
        if ("false".equalsIgnoreCase(preference)) {
            return null;
        }
        boolean forced = "true".equalsIgnoreCase(preference);
        if (!forced && preference != null && !"auto".equalsIgnoreCase(preference)) {
            LOG.warning("Invalid native decoration preference: " + preference + "; using auto");
        }
        if (!forced && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS
                && !OperatingSystem.SYSTEM_VERSION.isAtLeast(OSVersion.WINDOWS_11)) {
            return null;
        }
        try {
            int version = Integer.parseInt(System.getProperty("javafx.version", "0").split("[.\\-+]")[0]);
            if (version < 27 || !Platform.isSupported(ConditionalFeature.valueOf("EXTENDED_WINDOW"))) {
                return null;
            }
            return new NativeWindowDecoration(forced);
        } catch (Throwable e) {
            LOG.warning("Native window decoration is unavailable; using custom decoration", e);
            return null;
        }
    }

    /// Returns whether to use native decoration with the current transparency and theme brightness.
    ///
    /// Transparent windows always use custom decoration. Automatic mode also excludes dark windows on Windows;
    /// explicitly enabling native decoration bypasses only that brightness exclusion.
    ///
    /// @param transparent whether the theme requests window transparency
    /// @param dark whether the current theme is dark
    /// @return whether this instance permits native decoration for the supplied theme settings
    boolean isPreferred(boolean transparent, boolean dark) {
        return !transparent && (forced || !(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && dark));
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
            setCenter.invokeExact(headerBar, content);
        } catch (Throwable e) {
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
            setDragType.invokeExact(node, enabled ? draggable : notDraggable);
        } catch (Throwable e) {
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
            setDragType.invokeExact(overlay, transparentSubtree);
        } catch (Throwable e) {
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
            if ((Object) getDragType.invokeExact(node) == notDraggable) {
                return;
            }
        } catch (Throwable e) {
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
            setSystemButtonHeight.invokeExact(stage, 0.0);
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot hide native window buttons", e);
        }
    }
}
