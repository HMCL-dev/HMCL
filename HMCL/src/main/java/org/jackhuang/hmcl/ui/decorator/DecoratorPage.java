/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

    class State {
        private final String title;
        private final Node titleNode;
        private final boolean backable;
        private final boolean refreshable;
        private final boolean animate;
        private final boolean titleBarTransparent;
        private final double leftPaneWidth;

        public State(String title, Node titleNode, boolean backable, boolean refreshable, boolean animate) {
            this(title, titleNode, backable, refreshable, animate, false, 0);
        }

        public State(String title, Node titleNode, boolean backable, boolean refreshable, boolean animate, boolean titleBarTransparent, double leftPaneWidth) {
            this.title = title;
            this.titleNode = titleNode;
            this.backable = backable;
            this.refreshable = refreshable;
            this.animate = animate;
            this.titleBarTransparent = titleBarTransparent;
            this.leftPaneWidth = leftPaneWidth;
        }

        public static State fromTitle(String title) {
            return new State(title, null, true, false, true);
        }

        public static State fromTitleNode(Node titleNode) {
            return new State(null, titleNode, true, false, true);
        }

        public String getTitle() {
            return title;
        }

        public Node getTitleNode() {
            return titleNode;
        }

        public boolean isBackable() {
            return backable;
        }

        public boolean isRefreshable() {
            return refreshable;
        }

        public boolean isAnimate() {
            return animate;
        }

        public boolean isTitleBarTransparent() {
            return titleBarTransparent;
        }

        public double getLeftPaneWidth() {
            return leftPaneWidth;
        }
    }
}
