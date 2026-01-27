/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Objects;
import java.util.function.Function;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;

/// @author Glavo
public final class ChooseButton<T> extends StackPane {

    public ChooseButton() {
        getProperties().put("ComponentList.noPadding", true);

        var pane = new HBox();
        pane.setPadding(new Insets(8, 8, 8, 16));
        RipplerContainer container = new RipplerContainer(pane);
        getChildren().setAll(container);

        VBox left = new VBox();
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMouseTransparent(true);
        Label titleLabel = new Label();
        titleLabel.textProperty().bind(title);
        Label subtitleLabel = new Label();
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMouseTransparent(true);
        subtitleLabel.getStyleClass().add("subtitle");
        subtitleLabel.textProperty().bind(subtitle);
        left.setAlignment(Pos.CENTER_LEFT);

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("subtitle");
        valueLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            T value = getValue();
            Function<T, String> converter = getConverter();
            return converter != null ? converter.apply(value) : Objects.toString(value, "");
        }, converterProperty(), valueProperty()));
        StackPane valuePane = new StackPane(valueLabel);
        valuePane.setAlignment(Pos.CENTER);

        Node arrowIcon = SVG.UNFOLD_MORE.createIcon(24);
        arrowIcon.setMouseTransparent(true);

        StackPane arrowPane = new StackPane(arrowIcon);
        HBox.setMargin(arrowPane, new Insets(0, 8, 0, 8));
        arrowPane.setAlignment(Pos.CENTER);

        pane.getChildren().setAll(left, valuePane, arrowPane);

        FXUtils.onClicked(container, () -> {
            PopupMenu popupMenu = new PopupMenu();
            Bindings.bindContent(popupMenu.getContent(), MappedObservableList.create(itemsProperty(), item -> {
                Label itemLabel = new Label();
                Function<T, String> converter = getConverter();
                itemLabel.setText(converter != null ? converter.apply(item) : Objects.toString(item, ""));
                itemLabel.getStyleClass().add("menu-item-label");
                return PopupMenu.wrapPopupMenuItem(itemLabel);
            }));

            JFXPopup popup = new JFXPopup(popupMenu);
            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, JFXPopup.PopupHPosition.RIGHT,
                    0,
                    vPosition == JFXPopup.PopupVPosition.TOP ? this.getHeight() : -this.getHeight());
        });

        FXUtils.onChangeAndOperate(subtitleProperty(), subtitle -> {
            if (StringUtils.isNotBlank(subtitle)) {
                left.getChildren().setAll(titleLabel, subtitleLabel);
            } else {
                left.getChildren().setAll(titleLabel);
            }
        });
    }

    private final StringProperty title = new SimpleStringProperty(this, "title");

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
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

    public void setConverter(Function<T, String> value) {
        converterProperty().set(value);
    }

    public Function<T, String> getConverter() {
        return converterProperty().get();
    }

    private final ListProperty<T> items = new SimpleListProperty<>(this, "items");

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public void setItems(ObservableList<T> value) {
        itemsProperty().set(value);
    }

    public ObservableList<T> getItems() {
        return items.get();
    }

}
