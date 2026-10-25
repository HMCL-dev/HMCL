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

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Displays navigation content and prepares one hidden page in the same scene.
@NotNullByDefault
final class NavigationContentPane extends StackPane {
    /// The visible navigation viewport supplying preparation dimensions.
    private final Region navigator;

    /// The temporary preparation container, or `null` while no candidate is pending.
    private @Nullable PreloadPane preloadPane;

    /// Creates a clipped content area containing the navigator.
    ///
    /// @param navigator the navigation viewport to display
    NavigationContentPane(Region navigator) {
        this.navigator = navigator;
        getStyleClass().add("jfx-decorator-content-container");
        FXUtils.setOverflowHidden(this);
        getChildren().setAll(navigator);
    }

    /// Replaces the candidate with an unowned page, using a snapshot of the navigator size.
    /// Each request uses a temporary hidden container. CSS and layout are deferred
    /// to scene pulses; after layout, a queued task removes and empties the container.
    ///
    /// Requires the JavaFX Application Thread and, for preparation to proceed,
    /// a scene that processes pulses. The caller must retain the page for reuse.
    ///
    /// @param page the page to prepare
    /// @return the supplied page
    /// @throws IllegalArgumentException if the page already belongs to a parent or scene
    <N extends Node> N preparePage(N page) {
        if (page.getParent() != null || page.getScene() != null) {
            throw new IllegalArgumentException("Page already belongs to a parent or scene");
        }
        if (preloadPane != null) {
            preloadPane.dispose();
        }
        preloadPane = new PreloadPane(page);
        getChildren().add(0, preloadPane);
        return page;
    }

    /// Holds the candidate without affecting the content area's size or input handling.
    @NotNullByDefault
    private final class PreloadPane extends StackPane {
        /// Creates an invisible layout root for the page at the navigator's current size.
        ///
        /// @param page the unowned candidate
        private PreloadPane(Node page) {
            setVisible(false);
            setManaged(false);
            setMouseTransparent(true);
            resize(navigator.getWidth(), navigator.getHeight());
            getChildren().setAll(page);
        }

        /// Lays out the candidate and defers removal until descendant layout has finished.
        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (getScene() != null) {
                Platform.runLater(this::dispose);
            }
        }

        /// Removes this container and releases its child and owner-held reference; repeated calls have no effect.
        private void dispose() {
            NavigationContentPane.this.getChildren().remove(this);
            getChildren().clear();
            if (preloadPane == this) {
                preloadPane = null;
            }
        }
    }
}
