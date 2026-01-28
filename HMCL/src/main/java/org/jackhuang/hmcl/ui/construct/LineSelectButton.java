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
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
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
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

    private JFXPopup popup;

    public LineSelectButton() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        root.setMouseTransparent(true);

        HBox right = new HBox();
        root.setRight(right);
        {
            right.setAlignment(Pos.CENTER_RIGHT);

            Label valueLabel = new Label();
            valueLabel.getStyleClass().add("subtitle");

            valueLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> {
                        T value = getValue();
                        if (value == null)
                            return "";

                        Function<T, String> converter = getConverter();
                        return converter != null ? converter.apply(value) : Objects.toString(value, "");
                    },
                    converterProperty(), valueProperty()));

            Node arrowIcon = SVG.UNFOLD_MORE.createIcon(24);
            HBox.setMargin(arrowIcon, new Insets(0, 8, 0, 8));
            arrowIcon.opacityProperty().bind(Bindings.when(disabledProperty())
                    .then(0.4)
                    .otherwise(1.0));

            right.getChildren().setAll(valueLabel, arrowIcon);
        }

        container.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (popup == null) {
                    PopupMenu popupMenu = new PopupMenu();
                    this.popup = new JFXPopup(popupMenu);

                    Bindings.bindContent(popupMenu.getContent(), MappedObservableList.create(itemsProperty(), item -> {
                        VBox vbox = new VBox();

                        var itemTitleLabel = new Label();
                        itemTitleLabel.getStyleClass().add("title");
                        itemTitleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
                            if (item == null)
                                return "";

                            Function<T, String> converter = getConverter();
                            return converter != null ? converter.apply(item) : Objects.toString(item, "");
                        }, converterProperty()));

                        var itemSubtitleLabel = new Label();
                        itemSubtitleLabel.getStyleClass().add("subtitle");
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
                            popup.hide();
                        });

                        FXUtils.onChangeAndOperate(valueProperty(),
                                value -> wrapper.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, Objects.equals(value, item)));

                        return ripplerContainer;
                    }));

                    popup.showingProperty().addListener((observable, oldValue, newValue) ->
                            container.getRippler().setRipplerDisabled(newValue));
                }

                if (popup.isShowing()) {
                    popup.hide();
                } else {
                    JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
                    popup.show(this, vPosition, JFXPopup.PopupHPosition.RIGHT,
                            0,
                            vPosition == JFXPopup.PopupVPosition.TOP ? this.getHeight() : -this.getHeight());
                }

                event.consume();
            } else if (event.getButton() == MouseButton.SECONDARY) {
                if (popup != null)
                    popup.hide();

                event.consume();
            }
        });
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

    private final ObjectProperty<Function<T, String>> descriptionConverter = new SimpleObjectProperty<>(this, "labelConverter");

    public ObjectProperty<Function<T, String>> descriptionConverterProperty() {
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

}
