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
import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.AnimationProducer;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.wizard.Refreshable;

public abstract class DecoratorTransitionPage extends Control implements DecoratorPage {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(""));
    private final DoubleProperty leftPaneWidth = new SimpleDoubleProperty();
    private final BooleanProperty titleBarTransparent = new SimpleBooleanProperty(false);
    private final BooleanProperty backable = new SimpleBooleanProperty(false);
    private final BooleanProperty refreshable = new SimpleBooleanProperty(false);
    private Node currentPage;
    protected final TransitionPane transitionPane = new TransitionPane();

    protected void navigate(Node page, AnimationProducer animation) {
        transitionPane.setContent(currentPage = page, animation);
    }

    protected void onNavigating(Node from) {
        if (from instanceof DecoratorPage)
            ((DecoratorPage) from).back();
    }

    protected void onNavigated(Node to) {
        if (to instanceof Refreshable) {
            refreshableProperty().bind(((Refreshable) to).refreshableProperty());
        } else {
            refreshableProperty().unbind();
            refreshableProperty().set(false);
        }

        if (to instanceof DecoratorPage) {
            state.bind(Bindings.createObjectBinding(() -> {
                State state = ((DecoratorPage) to).stateProperty().get();
                return new State(state.getTitle(), state.getTitleNode(), backable.get(), state.isRefreshable(), true, titleBarTransparent.get(), leftPaneWidth.get());
            }, ((DecoratorPage) to).stateProperty()));
        } else {
            state.unbind();
            state.set(new State("", null, backable.get(), false, true, titleBarTransparent.get(), leftPaneWidth.get()));
        }

        if (to instanceof Region) {
            Region region = (Region) to;
            // Let root pane fix window size.
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }

    @Override
    protected abstract Skin<?> createDefaultSkin();

    protected Node getCurrentPage() {
        return currentPage;
    }

    public boolean isBackable() {
        return backable.get();
    }

    public BooleanProperty backableProperty() {
        return backable;
    }

    public void setBackable(boolean backable) {
        this.backable.set(backable);
    }

    public boolean isRefreshable() {
        return refreshable.get();
    }

    @Override
    public BooleanProperty refreshableProperty() {
        return refreshable;
    }

    public void setRefreshable(boolean refreshable) {
        this.refreshable.set(refreshable);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public double getLeftPaneWidth() {
        return leftPaneWidth.get();
    }

    public DoubleProperty leftPaneWidthProperty() {
        return leftPaneWidth;
    }

    public void setLeftPaneWidth(double leftPaneWidth) {
        this.leftPaneWidth.set(leftPaneWidth);
    }

    public boolean isTitleBarTransparent() {
        return titleBarTransparent.get();
    }

    public BooleanProperty titleBarTransparentProperty() {
        return titleBarTransparent;
    }

    public void setTitleBarTransparent(boolean titleBarTransparent) {
        this.titleBarTransparent.set(titleBarTransparent);
    }
}
