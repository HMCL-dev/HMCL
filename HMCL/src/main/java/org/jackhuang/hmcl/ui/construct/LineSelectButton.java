// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.*;
import org.glavo.m3fx.controls.M3MenuItem;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/// @author Glavo
public class LineSelectButton<T extends @UnknownNullability Object> extends LineButton {

    private static final String DEFAULT_STYLE_CLASS = "line-select-button";

    /// The lazily populated selection menu.
    private @Nullable PopupMenu popup;

    public LineSelectButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        InvalidationListener updateTrailingText = observable -> {
            Function<T, String> converter = getConverter();
            T value = getValue();
            setTrailingText(converter != null ? converter.apply(value) : Objects.toString(value, ""));
        };
        converterProperty().addListener(updateTrailingText);
        valueProperty().addListener(updateTrailingText);

        setTrailingIcon(SVG.UNFOLD_MORE);

        ripplerContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (popup != null)
                    popup.hide();
                event.consume();
            }
        });
    }

    @Override
    public void fire() {
        super.fire();
        if (popup == null) {
            var menu = new PopupMenu();
            popup = menu;
            menu.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyEvent);
            ripplerContainer.addEventFilter(ScrollEvent.ANY, ignored -> menu.hide());
            var rows = MappedObservableList.create(itemsProperty(), item -> {
                var row = new M3MenuItem();
                row.headlineTextProperty().bind(Bindings.createStringBinding(() -> {
                    var converter = getConverter();
                    return converter != null ? converter.apply(item) : Objects.toString(item, "");
                }, converterProperty()));
                row.supportingTextProperty().bind(Bindings.createStringBinding(() -> {
                    var converter = getDescriptionConverter();
                    return converter != null ? converter.apply(item) : "";
                }, descriptionConverterProperty()));
                row.setUserData(item);
                row.setSelected(Objects.equals(getValue(), item));
                row.setOnAction(event -> setValue(item));
                return row;
            });
            Bindings.bindContent(menu.getItems(), rows);
            valueProperty().subscribe(value -> rows.forEach(row -> row.setSelected(Objects.equals(value, row.getUserData()))));

            popup.showingProperty().addListener((observable, oldValue, newValue) ->
                    ripplerContainer.setPressFeedbackEnabled(!newValue));
        }

        if (popup.isShowing()) {
            popup.hide();
        } else {
            popup.showBelow(this, this);
        }
    }

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    public ObjectProperty<T> valueProperty() {
        return value;
    }

    public T getValue() {
        return valueProperty().get();
    }

    public void setValue(T value) {
        valueProperty().set(value);
    }

    private final ObjectProperty<Function<T, String>> converter = new SimpleObjectProperty<>(this, "converter");

    public ObjectProperty<Function<T, String>> converterProperty() {
        return converter;
    }

    public Function<T, String> getConverter() {
        return converterProperty().get();
    }

    public void setConverter(Function<T, String> value) { // TODO: rename
        converterProperty().set(value);
    }

    public void setNullSafeConverter(Function<@NotNull T, String> value) {
        converterProperty().set(it -> it != null ? value.apply(it) : "");
    }

    private ObjectProperty<Function<T, String>> descriptionConverter;

    public ObjectProperty<Function<T, String>> descriptionConverterProperty() {
        if (descriptionConverter == null)
            descriptionConverter = new SimpleObjectProperty<>(this, "descriptionConverter");
        return descriptionConverter;
    }

    public Function<T, String> getDescriptionConverter() {
        return descriptionConverterProperty().get();
    }

    public void setDescriptionConverter(Function<T, String> value) {
        descriptionConverterProperty().set(value);
    }

    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.emptyObservableList());

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public void setItems(ObservableList<T> value) {
        itemsProperty().set(value);
    }

    public void setItems(Collection<T> value) {
        if (value instanceof ObservableList<T> observableList) {
            this.setItems(observableList);
        } else {
            this.setItems(FXCollections.observableArrayList(value));
        }
    }

    @SafeVarargs
    public final void setItems(T... values) {
        this.setItems(FXCollections.observableArrayList(values));
    }

    public ObservableList<T> getItems() {
        return items.get();
    }

    private void handleKeyEvent(KeyEvent event) {
        ObservableList<T> list = getItems();
        if (list == null || list.isEmpty()) return;
        int index = list.indexOf(getValue());

        var code = event.getCode();

        if (code == KeyCode.UP) {
            if (index > 0) {
                setValue(list.get(index - 1));
            } else {
                setValue(list.get(list.size() - 1));
            }
            event.consume();
        } else if (code == KeyCode.DOWN) {
            if (index < list.size() - 1) {
                setValue(list.get(index + 1));
            } else {
                setValue(list.get(0));
            }
            event.consume();
        } else if (code == KeyCode.ENTER || code == KeyCode.ESCAPE) {
            if (popup != null && popup.isShowing()) {
                popup.hide();
                event.consume();
            }
        }
    }
}
