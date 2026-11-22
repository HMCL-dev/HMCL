// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.decorator;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Refreshable;

public interface DecoratorPage extends Refreshable {
    ReadOnlyObjectProperty<State> stateProperty();

    default boolean isPageCloseable() {
        return false;
    }

    default boolean back() {
        return true;
    }

    @Override
    default void refresh() {
    }

    default void closePage() {
    }

    default void onDecoratorPageNavigating(Navigator.NavigationEvent event) {
        ((Node) this).getStyleClass().add("content-background");
    }

    record State(String title, Node titleNode, boolean backable, boolean refreshable, boolean animate, double leftPaneWidth) {
        public State(String title, Node titleNode, boolean backable, boolean refreshable, boolean animate) {
            this(title, titleNode, backable, refreshable, animate, 0);
        }

        public static State fromTitle(String title) {
            return new State(title, null, true, false, true);
        }

        public static State fromTitle(String title, double leftPaneWidth) {
            return new State(title, null, true, false, true, leftPaneWidth);
        }

        public static State fromTitleNode(Node titleNode) {
            return new State(null, titleNode, true, false, true);
        }
    }
}
