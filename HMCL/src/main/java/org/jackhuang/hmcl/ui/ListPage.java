/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

public abstract class ListPage<T extends Node> extends Control {
    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", false);
    private final BooleanProperty refreshable = new SimpleBooleanProperty(this, "refreshable", false);

    public abstract void add();

    public void refresh() {
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ListPageSkin(this);
    }

    public ObservableList<T> getItems() {
        return items.get();
    }

    public void setItems(ObservableList<T> items) {
        this.items.set(items);
    }

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public boolean isRefreshable() {
        return refreshable.get();
    }

    public BooleanProperty refreshableProperty() {
        return refreshable;
    }

    public void setRefreshable(boolean refreshable) {
        this.refreshable.set(refreshable);
    }
}
