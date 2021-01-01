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

import javafx.scene.Node;
import org.jackhuang.hmcl.ui.animation.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.Navigator;

public abstract class DecoratorNavigatorPage extends DecoratorTransitionPage {
    protected final Navigator navigator = new Navigator();

    {
        this.navigator.setOnNavigating(this::onNavigating);
        this.navigator.setOnNavigated(this::onNavigated);
        backableProperty().bind(navigator.backableProperty());
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
        onNavigating(event.getNode());
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        onNavigated(event.getNode());
    }
}
