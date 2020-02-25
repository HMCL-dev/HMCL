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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.ui.animation.AnimationProducer;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.wizard.Refreshable;

public abstract class DecoratorTransitionPage extends Control implements DecoratorPage, Refreshable {
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty refreshable = new SimpleBooleanProperty(false);
    private final BooleanProperty backable = new SimpleBooleanProperty();
    private Node currentPage;
    protected final TransitionPane transitionPane = new TransitionPane();

    protected void navigate(Node page, AnimationProducer animation) {
        transitionPane.setContent(currentPage = page, animation);
        refreshable.setValue(page instanceof Refreshable);
    }

    @Override
    protected abstract Skin<?> createDefaultSkin();

    protected Node getCurrentPage() {
        return currentPage;
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public boolean isRefreshable() {
        return refreshable.get();
    }

    @Override
    public void refresh() {
        // empty implementation for default
    }

    @Override
    public BooleanProperty refreshableProperty() {
        return refreshable;
    }

    public void setRefreshable(boolean refreshable) {
        this.refreshable.set(refreshable);
    }

    public boolean isBackable() {
        return backable.get();
    }

    @Override
    public BooleanProperty backableProperty() {
        return backable;
    }

    public void setBackable(boolean backable) {
        this.backable.set(backable);
    }
}
