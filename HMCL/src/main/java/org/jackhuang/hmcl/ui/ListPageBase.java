// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import org.jackhuang.hmcl.ui.animation.TransitionPane;

import static org.jackhuang.hmcl.ui.construct.SpinnerPane.FAILED_ACTION;

public class ListPageBase<T> extends Control implements TransitionPane.Cacheable {
    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", false);
    private final StringProperty failedReason = new SimpleStringProperty(this, "failed");

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

    public String getFailedReason() {
        return failedReason.get();
    }

    public StringProperty failedReasonProperty() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason.set(failedReason);
    }

    public final ObjectProperty<EventHandler<Event>> onFailedActionProperty() {
        return onFailedAction;
    }

    public final void setOnFailedAction(EventHandler<Event> value) {
        onFailedActionProperty().set(value);
    }

    public final EventHandler<Event> getOnFailedAction() {
        return onFailedActionProperty().get();
    }

    private final ObjectProperty<EventHandler<Event>> onFailedAction = new SimpleObjectProperty<>(this, "onFailedAction") {
        @Override
        protected void invalidated() {
            setEventHandler(FAILED_ACTION, get());
        }
    };
}
