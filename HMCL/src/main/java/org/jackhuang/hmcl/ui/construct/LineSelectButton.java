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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;

/// @author Glavo
public final class LineSelectButton<T> extends LineButtonBase {

    private static final String DEFAULT_STYLE_CLASS = "line-select-button";

    public LineSelectButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin<>(this);
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

    public void setConverter(Function<T, String> value) {
        converterProperty().set(value);
    }

    private final ListProperty<T> items = new SimpleListProperty<>(this, "items");

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

    private static final class Skin<T> extends LineButtonBaseSkin {
        private final LineSelectButton<T> control;

        Skin(LineSelectButton<T> control) {
            super(control);

            this.control = control;

            root.setMouseTransparent(true);

            HBox right = new HBox();
            root.setRight(right);
            {
                Label valueLabel = new Label();
                valueLabel.getStyleClass().add("subtitle");

                valueLabel.textProperty().bind(Bindings.createStringBinding(
                        () -> toDisplayString(control.getValue()),
                        control.converterProperty(), control.valueProperty()));
                StackPane valuePane = new StackPane(valueLabel);
                valuePane.setAlignment(Pos.CENTER);

                Node arrowIcon = SVG.UNFOLD_MORE.createIcon(24);

                StackPane arrowPane = new StackPane(arrowIcon);
                arrowPane.opacityProperty().bind(Bindings.when(control.disabledProperty())
                        .then(0.4)
                        .otherwise(1.0));
                HBox.setMargin(arrowPane, new Insets(0, 8, 0, 8));
                arrowPane.setAlignment(Pos.CENTER);

                right.getChildren().setAll(valuePane, arrowPane);
            }

            FXUtils.onClicked(container, () -> {
                PopupMenu popupMenu = new PopupMenu();
                JFXPopup popup = new JFXPopup(popupMenu);

                Bindings.bindContent(popupMenu.getContent(), MappedObservableList.create(control.itemsProperty(), item -> {
                    Label itemLabel = new Label();
                    itemLabel.textProperty().bind(Bindings.createStringBinding(() -> toDisplayString(item), control.converterProperty()));

                    itemLabel.textFillProperty().bind(Bindings.createObjectBinding(() ->
                                    Objects.equals(control.getValue(), item)
                                            ? Themes.getColorScheme().getPrimary()
                                            : Themes.getColorScheme().getOnSurface(),
                            control.valueProperty(), Themes.colorSchemeProperty()));

                    var wrapper = new StackPane(itemLabel);
                    wrapper.setAlignment(Pos.CENTER_LEFT);
                    wrapper.getStyleClass().add("menu-container");
                    wrapper.setMouseTransparent(true);
                    RipplerContainer ripplerContainer = new RipplerContainer(wrapper);
                    FXUtils.onClicked(ripplerContainer, () -> {
                        control.setValue(item);
                        popup.hide();
                    });
                    return ripplerContainer;
                }));

                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(control, popup);
                popup.show(control, vPosition, JFXPopup.PopupHPosition.RIGHT,
                        0,
                        vPosition == JFXPopup.PopupVPosition.TOP ? control.getHeight() : -control.getHeight());
            });
        }

        private String toDisplayString(T value) {
            if (value == null)
                return "";

            Function<T, String> converter = control.getConverter();
            return converter != null ? converter.apply(value) : Objects.toString(value, "");
        }
    }

}
