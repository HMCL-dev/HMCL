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
import org.jetbrains.annotations.NotNullByDefault;

/// Holds at most one hidden page in the window's scene for normal CSS and layout processing.
/// This pane does not participate in its parent's size calculations or receive input.
@NotNullByDefault
final class PagePreloadPane extends StackPane {
    /// Supplies the size captured when a page is prepared.
    private final Region viewport;

    /// Identifies the latest preparation, including reuse of the same page.
    private long preparationId;

    /// Creates a hidden layout root that samples the navigation viewport on each preparation.
    ///
    /// @param viewport the region supplying the page's available width and height
    PagePreloadPane(Region viewport) {
        this.viewport = viewport;
        setVisible(false);
        setManaged(false);
        setMouseTransparent(true);
    }

    /// Replaces the candidate with an unowned page, using a snapshot of the viewport size.
    /// CSS and layout are deferred to scene pulses. After layout, a queued task
    /// detaches the page if it is still this preparation's candidate.
    ///
    /// Requires the JavaFX Application Thread and, for preparation to proceed,
    /// a scene that processes pulses. The caller must retain the page for reuse.
    ///
    /// @param page the page to prepare
    /// @return the supplied page
    /// @throws IllegalArgumentException if the page already belongs to a parent or scene
    <N extends Node> N prepare(N page) {
        if (page.getParent() != null || page.getScene() != null) {
            throw new IllegalArgumentException("Page already belongs to a parent or scene");
        }
        preparationId++;
        resize(viewport.getWidth(), viewport.getHeight());
        getChildren().setAll(page);
        return page;
    }

    /// Lays out the candidate and defers its removal until descendant layout has finished.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (getScene() != null && !getChildren().isEmpty()) {
            Node page = getChildren().get(0);
            long requestId = preparationId;
            Platform.runLater(() -> {
                if (requestId == preparationId && page.getParent() == this) {
                    getChildren().remove(page);
                }
            });
        }
    }
}
