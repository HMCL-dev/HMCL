/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Refreshable;

public abstract class DecoratorNavigatorPage extends DecoratorTransitionPage {
    protected final Navigator navigator = new Navigator();

    {
        this.navigator.setOnNavigating(this::onNavigating);
        this.navigator.setOnNavigated(this::onNavigated);
    }

    @Override
    protected void navigate(Node page, AnimationProducer animationProducer) {
        navigator.navigate(page, animationProducer);
    }

    @Override
    public boolean back() {
        if (navigator.canGoBack()) {
            navigator.close();
            return false;
        } else {
            return true;
        }
    }

    private void onNavigating(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node from = event.getNode();

        if (from instanceof DecoratorPage)
            ((DecoratorPage) from).back();
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node to = event.getNode();

        if (to instanceof Refreshable) {
            refreshableProperty().bind(((Refreshable) to).refreshableProperty());
        } else {
            refreshableProperty().unbind();
            refreshableProperty().set(false);
        }

        if (to instanceof DecoratorPage) {
            state.bind(Bindings.createObjectBinding(() -> {
                State state = ((DecoratorPage) to).stateProperty().get();
                return new State(state.getTitle(), state.getTitleNode(), navigator.canGoBack(), state.isRefreshable(), true);
            }, ((DecoratorPage) to).stateProperty()));
        } else {
            state.unbind();
            state.set(new State("", null, navigator.canGoBack(), false, true));
        }

        if (to instanceof Region) {
            Region region = (Region) to;
            // Let root pane fix window size.
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }
}
