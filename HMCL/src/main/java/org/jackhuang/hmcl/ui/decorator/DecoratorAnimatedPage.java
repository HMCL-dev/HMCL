// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Lays out a fixed-width sidebar and a growing content area as independent animation targets.
@NotNullByDefault
public class DecoratorAnimatedPage extends BorderPane {

    /// The fixed-width animation target containing navigation and optional bottom actions.
    private final BorderPane left = new BorderPane();
    /// The scrollable navigation list owned by this page.
    private final AdvancedListBox sidebar = new AdvancedListBox();
    /// The stable animation target containing the replaceable page content.
    private final StackPane center = new StackPane();

    /// Creates both animation targets without requiring CSS or skin initialization.
    public DecoratorAnimatedPage() {
        getStyleClass().add("gray-background");
        FXUtils.setLimitWidth(left, 200);
        sidebar.setMinSize(0, 0);
        left.setCenter(sidebar);
        setLeft(left);
        setCenter(center);
    }

    /// Returns the navigation list; its entries share the space above the bottom content.
    public AdvancedListBox getSidebar() {
        return sidebar;
    }

    /// Returns the fixed bottom content, or null when none is installed.
    public @Nullable Node getSidebarBottom() {
        return left.getBottom();
    }

    /// Replaces the bottom content, reserving its preferred height and a 12-pixel bottom gap.
    /// Passing null removes it without replacing the sidebar animation target.
    public void setSidebarBottom(@Nullable Node node) {
        if (node != null) {
            BorderPane.setMargin(node, new Insets(0, 0, 12, 0));
        }
        left.setBottom(node);
    }

    /// Returns the page content, or null when no content is installed.
    public @Nullable Node getContent() {
        return center.getChildren().isEmpty() ? null : center.getChildren().getFirst();
    }

    /// Replaces the page content; null clears it while preserving the animation target.
    public void setContent(@Nullable Node node) {
        if (node == null) {
            center.getChildren().clear();
        } else {
            center.getChildren().setAll(node);
        }
    }
}
