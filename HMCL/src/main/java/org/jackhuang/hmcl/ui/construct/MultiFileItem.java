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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultiFileItem<T> extends VBox {
    private final ObjectProperty<T> selectedData = new SimpleObjectProperty<>(this, "selectedData");
    private final ObjectProperty<T> fallbackData = new SimpleObjectProperty<>(this, "fallbackData");

    private final ToggleGroup group = new ToggleGroup();

    private Consumer<Toggle> toggleSelectedListener;

    @SuppressWarnings("unchecked")
    public MultiFileItem() {
        setPadding(new Insets(0, 0, 10, 0));
        setSpacing(8);

        group.selectedToggleProperty().addListener((a, b, newValue) -> {
            if (toggleSelectedListener != null)
                toggleSelectedListener.accept(newValue);

            selectedData.set((T) newValue.getUserData());
        });
        selectedData.addListener((a, b, newValue) -> {
            Optional<Toggle> selecting = group.getToggles().stream()
                    .filter(it -> it.getUserData() == newValue)
                    .findFirst();
            if (!selecting.isPresent()) {
                selecting = group.getToggles().stream()
                        .filter(it -> it.getUserData() == getFallbackData())
                        .findFirst();
            }

            selecting.ifPresent(toggle -> toggle.setSelected(true));
        });
    }

    public void loadChildren(Collection<Option<T>> options) {
        getChildren().setAll(options.stream()
                .map(option -> option.createItem(group))
                .collect(Collectors.toList()));
    }

    public ToggleGroup getGroup() {
        return group;
    }

    public void setToggleSelectedListener(Consumer<Toggle> consumer) {
        toggleSelectedListener = consumer;
    }

    public T getSelectedData() {
        return selectedData.get();
    }

    public ObjectProperty<T> selectedDataProperty() {
        return selectedData;
    }

    public void setSelectedData(T selectedData) {
        this.selectedData.set(selectedData);
    }

    public T getFallbackData() {
        return fallbackData.get();
    }

    public ObjectProperty<T> fallbackDataProperty() {
        return fallbackData;
    }

    public void setFallbackData(T fallbackData) {
        this.fallbackData.set(fallbackData);
    }

    public static class Option<T> {
        protected final String title;
        protected String subtitle;
        protected final T data;

        public Option(String title, T data) {
            this.title = title;
            this.data = data;
        }

        public T getData() {
            return data;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public Option<T> setSubtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        protected Node createItem(ToggleGroup group) {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(3));
            FXUtils.setLimitHeight(pane, 30);

            JFXRadioButton left = new JFXRadioButton(title);
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            left.setToggleGroup(group);
            left.setUserData(data);
            pane.setLeft(left);

            if (StringUtils.isNotBlank(subtitle)) {
                Optional<String> shortSubtitle = StringUtils.truncate(subtitle);
                Label right;
                if (shortSubtitle.isPresent()) {
                    right = new Label(shortSubtitle.get());
                    right.setTooltip(new Tooltip(subtitle));
                } else {
                    right = new Label(subtitle);
                }
                BorderPane.setAlignment(right, Pos.CENTER_RIGHT);
                right.setWrapText(true);
                right.getStyleClass().add("subtitle-label");
                right.setStyle("-fx-font-size: 10;");
                pane.setRight(right);
            }

            return pane;
        }
    }

    public static class StringOption<T> extends Option<T> {
        private StringProperty value = new SimpleStringProperty();
        private ValidatorBase[] validators;

        public StringOption(String title, T data) {
            super(title, data);
        }

        public String getValue() {
            return value.get();
        }

        public StringProperty valueProperty() {
            return value;
        }

        public void setValue(String value) {
            this.value.set(value);
        }

        public StringOption<T> bindBidirectional(Property<String> property) {
            this.value.bindBidirectional(property);
            return this;
        }

        public StringOption<T> setValidators(ValidatorBase... validators) {
            this.validators = validators;
            return this;
        }

        @Override
        protected Node createItem(ToggleGroup group) {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(3));
            FXUtils.setLimitHeight(pane, 30);

            JFXRadioButton left = new JFXRadioButton(title);
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            left.setToggleGroup(group);
            left.setUserData(data);
            pane.setLeft(left);

            JFXTextField customField = new JFXTextField();
            BorderPane.setAlignment(customField, Pos.CENTER_RIGHT);
            customField.textProperty().bindBidirectional(valueProperty());
            customField.disableProperty().bind(left.selectedProperty().not());
            if (validators != null) {
                customField.setValidators(validators);
            }
            pane.setRight(customField);

            return pane;
        }
    }

    public static class FileOption<T> extends Option<T> {
        private FileSelector selector = new FileSelector();

        public FileOption(String title, T data) {
            super(title, data);
        }

        public String getValue() {
            return selector.getValue();
        }

        public StringProperty valueProperty() {
            return selector.valueProperty();
        }

        public void setValue(String value) {
            selector.setValue(value);
        }

        public FileOption<T> setDirectory(boolean directory) {
            selector.setDirectory(directory);
            return this;
        }

        public FileOption<T> bindBidirectional(Property<String> property) {
            selector.valueProperty().bindBidirectional(property);
            return this;
        }

        public FileOption<T> setChooserTitle(String chooserTitle) {
            selector.setChooserTitle(chooserTitle);
            return this;
        }

        public ObservableList<FileChooser.ExtensionFilter> getExtensionFilters() {
            return selector.getExtensionFilters();
        }

        @Override
        protected Node createItem(ToggleGroup group) {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(3));
            FXUtils.setLimitHeight(pane, 30);

            JFXRadioButton left = new JFXRadioButton(title);
            BorderPane.setAlignment(left, Pos.CENTER_LEFT);
            left.setToggleGroup(group);
            left.setUserData(data);
            pane.setLeft(left);

            selector.disableProperty().bind(left.selectedProperty().not());
            BorderPane.setAlignment(selector, Pos.CENTER_RIGHT);
            pane.setRight(selector);
            return pane;
        }
    }
}
