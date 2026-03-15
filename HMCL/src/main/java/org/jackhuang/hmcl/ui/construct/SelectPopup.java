/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXPopup;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public class SelectPopup<T> extends ScrollPane {

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");
    private final ObjectProperty<Function<T, String>> converter = new SimpleObjectProperty<>(this, "converter");
    private final ObjectProperty<Function<T, String>> descriptionConverter = new SimpleObjectProperty<>(this, "descriptionConverter");
    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());

    private Runnable onSelectedAction;
    private JFXPopup popup;

    public SelectPopup() {
        this.getStyleClass().add("select-popup");
        this.setMaxHeight(365);
        this.setFitToWidth(true);
        this.setHbarPolicy(ScrollBarPolicy.NEVER);
        this.setVbarPolicy(ScrollBarPolicy.NEVER);

        VBox contentBox = new VBox();
        this.setContent(contentBox);

        Bindings.bindContent(contentBox.getChildren(), MappedObservableList.create(itemsProperty(), item -> {
            VBox vbox = new VBox();

            var itemTitleLabel = new Label();
            itemTitleLabel.getStyleClass().add("title-label");
            itemTitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                if (item == null) return "";

                Function<T, String> converter = getConverter();
                return converter != null ? converter.apply(item) : Objects.toString(item, "");
            }, converterProperty()));

            var itemSubtitleLabel = new Label();
            itemSubtitleLabel.getStyleClass().add("subtitle-label");
            itemSubtitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                Function<T, String> descriptionConverter = getDescriptionConverter();
                return descriptionConverter != null ? descriptionConverter.apply(item) : "";
            }, descriptionConverterProperty()));

            FXUtils.onChangeAndOperate(itemSubtitleLabel.textProperty(), text -> {
                if (text == null || text.isEmpty()) {
                    vbox.getChildren().setAll(itemTitleLabel);
                } else {
                    vbox.getChildren().setAll(itemTitleLabel, itemSubtitleLabel);
                }
            });

            var wrapper = new StackPane(vbox);
            wrapper.setAlignment(Pos.CENTER_LEFT);
            wrapper.getStyleClass().add("menu-container");
            wrapper.setMouseTransparent(true);
            RipplerContainer ripplerContainer = new RipplerContainer(wrapper);

            FXUtils.onClicked(ripplerContainer, () -> {
                setValue(item);
                if (onSelectedAction != null) {
                    onSelectedAction.run();
                }
                hide();
            });

            FXUtils.onChangeAndOperate(valueProperty(), value -> wrapper.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, Objects.equals(value, item)));

            return ripplerContainer;
        }));
    }

    private void ensurePopup() {
        if (popup == null) {
            popup = new JFXPopup(this);
        }
    }

    public void showRelativeTo(Region owner) {
        ensurePopup();
        JFXPopup.PopupVPosition vPosition = FXUtils.determineOptimalPopupPosition(owner, popup);
        popup.show(owner, vPosition, JFXPopup.PopupHPosition.RIGHT, 0, vPosition == JFXPopup.PopupVPosition.TOP ? owner.getHeight() : -owner.getHeight(), true);
    }

    public void show(Node owner, JFXPopup.PopupVPosition vAlign, JFXPopup.PopupHPosition hAlign, double initOffsetX, double initOffsetY) {
        ensurePopup();
        popup.show(owner, vAlign, hAlign, initOffsetX, initOffsetY);
    }

    public void hide() {
        if (popup != null && popup.isShowing()) {
            popup.hide();
        }
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    public ReadOnlyBooleanProperty showingProperty() {
        ensurePopup();
        return popup.showingProperty();
    }

    public void setOnSelectedAction(Runnable action) {
        this.onSelectedAction = action;
    }

    public ObjectProperty<T> valueProperty() {
        return value;
    }

    public T getValue() {
        return value.get();
    }

    public void setValue(T value) {
        this.value.set(value);
    }

    public ObjectProperty<Function<T, String>> converterProperty() {
        return converter;
    }

    public Function<T, String> getConverter() {
        return converter.get();
    }

    public void setConverter(Function<T, String> value) {
        this.converter.set(value);
    }

    public ObjectProperty<Function<T, String>> descriptionConverterProperty() {
        return descriptionConverter;
    }

    public Function<T, String> getDescriptionConverter() {
        return descriptionConverter.get();
    }

    public void setDescriptionConverter(Function<T, String> value) {
        this.descriptionConverter.set(value);
    }

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public ObservableList<T> getItems() {
        return items.get();
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
}
