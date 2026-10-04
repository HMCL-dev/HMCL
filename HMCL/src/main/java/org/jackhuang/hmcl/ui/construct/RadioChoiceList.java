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

import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A vertical single-selection list backed by radio buttons.
///
/// Each choice owns its optional editor control, such as a text field or file selector.
/// The editor is enabled only while the choice is selected.
///
/// @param <T> the selected value type
/// @author Glavo
@NotNullByDefault
public final class RadioChoiceList<T extends @UnknownNullability Object> extends VBox {
    /// The selected choice value.
    private final ObjectProperty<T> selectedValue = new SimpleObjectProperty<>(this, "selectedValue");

    /// The fallback value selected when the requested value is not present.
    private final ObjectProperty<T> fallbackValue = new SimpleObjectProperty<>(this, "fallbackValue");

    /// The currently selected choice object.
    private final ObjectProperty<@Nullable Choice<T>> selectedChoice = new SimpleObjectProperty<>(this, "selectedChoice");

    /// The shared radio-button group.
    private final ToggleGroup group = new ToggleGroup();

    /// The choices currently displayed by this list.
    private final ObservableList<Choice<T>> choices = FXCollections.observableArrayList();

    /// The reverse lookup from rendered toggles to choices.
    private final Map<Toggle, Choice<T>> choiceByToggle = new IdentityHashMap<>();

    /// Whether the current selection update should keep the list empty instead of selecting the fallback.
    private boolean clearingSelection;

    /// Creates an empty radio choice list.
    public RadioChoiceList() {
        getStyleClass().addAll("radio-choice-list", "multi-file-item");
        setSpacing(8);

        group.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            Choice<T> choice = newValue != null ? choiceByToggle.get(newValue) : null;
            selectedValue.set(choice != null ? choice.getValue() : null);
            selectedChoice.set(choice);
        });

        selectedValue.addListener((observable, oldValue, newValue) -> {
            if (!clearingSelection) {
                selectValue(newValue);
            }
        });
    }

    /// Replaces the displayed choices.
    @SafeVarargs
    public final void setChoices(Choice<T>... choices) {
        setChoices(List.of(choices));
    }

    /// Replaces the displayed choices.
    public void setChoices(Collection<? extends Choice<T>> choices) {
        this.choices.setAll(choices);
        choiceByToggle.clear();

        getChildren().setAll(this.choices.stream()
                .map(choice -> {
                    Node node = choice.createNode(group);
                    choiceByToggle.put(choice.getRadioButton(), choice);
                    return node;
                })
                .toList());

        selectValue(getSelectedValue());
    }

    /// Returns the immutable list of displayed choices.
    public @Unmodifiable List<Choice<T>> getChoices() {
        return List.copyOf(choices);
    }

    /// Clears the selected choice.
    public void clearSelection() {
        clearingSelection = true;
        try {
            Toggle selectedToggle = group.getSelectedToggle();
            if (selectedToggle != null) {
                selectedToggle.setSelected(false);
            } else {
                selectedChoice.set(null);
                selectedValue.set(null);
            }
        } finally {
            clearingSelection = false;
        }
    }

    /// Returns the selected value.
    public T getSelectedValue() {
        return selectedValue.get();
    }

    /// Returns the selected value property.
    public ObjectProperty<T> selectedValueProperty() {
        return selectedValue;
    }

    /// Sets the selected value.
    public void setSelectedValue(T selectedValue) {
        this.selectedValue.set(selectedValue);
    }

    /// Returns the fallback value.
    public T getFallbackValue() {
        return fallbackValue.get();
    }

    /// Returns the fallback value property.
    public ObjectProperty<T> fallbackValueProperty() {
        return fallbackValue;
    }

    /// Sets the fallback value.
    public void setFallbackValue(T fallbackValue) {
        this.fallbackValue.set(fallbackValue);
    }

    /// Returns the selected choice.
    public @Nullable Choice<T> getSelectedChoice() {
        return selectedChoice.get();
    }

    /// Returns the selected choice property.
    public ObjectProperty<@Nullable Choice<T>> selectedChoiceProperty() {
        return selectedChoice;
    }

    /// Selects the matching choice for the given value.
    private void selectValue(T value) {
        @Nullable Choice<T> choice = findChoice(value);
        if (choice == null) {
            choice = findChoice(getFallbackValue());
        }

        if (choice != null) {
            choice.setSelected(true);
        }
    }

    /// Finds the first choice with the given value.
    private @Nullable Choice<T> findChoice(T value) {
        for (Choice<T> choice : choices) {
            if (Objects.equals(choice.getValue(), value)) {
                return choice;
            }
        }
        return null;
    }

    /// A radio choice with an optional subtitle or tooltip.
    ///
    /// @param <T> the selected value type
    public static class Choice<T extends @UnknownNullability Object> {
        /// The title shown beside the radio button.
        protected String title;

        /// The selected value represented by this choice.
        protected final T value;

        /// The optional subtitle shown on the right side.
        protected @Nullable String subtitle;

        /// The optional tooltip installed on the radio button.
        protected @Nullable String tooltip;

        /// The radio button used by this choice.
        protected final JFXRadioButton radioButton = new JFXRadioButton();

        /// Creates a choice.
        public Choice(String title, T value) {
            this.title = title;
            this.value = value;
        }

        /// Returns the selected value represented by this choice.
        public T getValue() {
            return value;
        }

        /// Returns the title shown beside the radio button.
        public String getTitle() {
            return title;
        }

        /// Sets the title shown beside the radio button.
        public Choice<T> setTitle(String title) {
            this.title = title;
            radioButton.setText(title);
            return this;
        }

        /// Returns the optional subtitle.
        public @Nullable String getSubtitle() {
            return subtitle;
        }

        /// Sets the optional subtitle.
        public Choice<T> setSubtitle(@Nullable String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /// Sets the optional tooltip.
        public Choice<T> setTooltip(@Nullable String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /// Returns whether this choice is selected.
        public boolean isSelected() {
            return radioButton.isSelected();
        }

        /// Returns this choice's selected property.
        public BooleanProperty selectedProperty() {
            return radioButton.selectedProperty();
        }

        /// Sets whether this choice is selected.
        public void setSelected(boolean selected) {
            radioButton.setSelected(selected);
        }

        /// Returns the radio button owned by this choice.
        protected final JFXRadioButton getRadioButton() {
            return radioButton;
        }

        /// Configures the radio button for rendering in this list.
        protected final void configureRadioButton(ToggleGroup group) {
            radioButton.setText(title);
            radioButton.setToggleGroup(group);
            radioButton.setUserData(value);
            if (StringUtils.isNotBlank(tooltip)) {
                FXUtils.installFastTooltip(radioButton, tooltip);
            }
        }

        /// Creates the rendered row node.
        private Node createNode(ToggleGroup group) {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(3));
            FXUtils.setLimitHeight(pane, 30);

            configureRadioButton(group);
            BorderPane.setAlignment(radioButton, Pos.CENTER_LEFT);
            pane.setLeft(radioButton);

            @Nullable Node right = createRightNode();
            if (right != null) {
                if (shouldDisableRightNodeWhenUnselected()) {
                    right.disableProperty().bind(radioButton.selectedProperty().not());
                }
                BorderPane.setAlignment(right, Pos.CENTER_RIGHT);
                pane.setRight(right);
            } else if (StringUtils.isNotBlank(subtitle)) {
                pane.setCenter(createSubtitleLabel());
            }

            return pane;
        }

        /// Creates the optional right-side editor node.
        protected @Nullable Node createRightNode() {
            return null;
        }

        /// Returns whether the right-side node should be disabled while this choice is not selected.
        protected boolean shouldDisableRightNodeWhenUnselected() {
            return true;
        }

        /// Creates the subtitle label for choices without a right-side editor.
        private Node createSubtitleLabel() {
            var label = new javafx.scene.control.Label(subtitle);
            BorderPane.setAlignment(label, Pos.CENTER_RIGHT);
            label.setWrapText(true);
            label.getStyleClass().add("subtitle-label");
            label.setStyle("-fx-font-size: 10;");
            label.setPadding(new Insets(0, 0, 0, 15));
            return label;
        }
    }

    /// A choice with an attached text field.
    ///
    /// @param <T> the selected value type
    public static final class TextChoice<T extends @UnknownNullability Object> extends Choice<T> {
        /// The text field attached to this choice.
        private final JFXTextField textField = new JFXTextField();

        /// Creates a text choice.
        public TextChoice(String title, T value) {
            super(title, value);
        }

        /// Returns the attached text field.
        public JFXTextField getTextField() {
            return textField;
        }

        /// Returns the text field value.
        public String getText() {
            return textField.getText();
        }

        /// Returns the text field property.
        public StringProperty textProperty() {
            return textField.textProperty();
        }

        /// Sets the text field value.
        public void setText(String value) {
            textField.setText(value);
        }

        /// Binds the text field to another property.
        public TextChoice<T> bindTextBidirectional(Property<String> property) {
            FXUtils.bindString(textField, property);
            return this;
        }

        /// Sets validators on the text field.
        public TextChoice<T> setValidators(ValidatorBase... validators) {
            textField.setValidators(validators);
            return this;
        }

        /// Creates the right-side text field.
        @Override
        protected Node createRightNode() {
            if (!textField.getValidators().isEmpty()) {
                FXUtils.setValidateWhileTextChanged(textField, true);
            }
            return textField;
        }
    }

    /// A choice with an attached file selector.
    ///
    /// @param <T> the selected value type
    public static final class FileChoice<T extends @UnknownNullability Object> extends Choice<T> {
        /// The file selector attached to this choice.
        private final FileSelector selector = new FileSelector();

        /// Creates a file choice.
        public FileChoice(String title, T value) {
            super(title, value);
        }

        /// Returns the selected path.
        public String getPath() {
            return selector.getValue();
        }

        /// Returns the selected path property.
        public StringProperty pathProperty() {
            return selector.valueProperty();
        }

        /// Sets the selected path.
        public void setPath(String value) {
            selector.setValue(value);
        }

        /// Sets the file selector mode.
        public FileChoice<T> setSelectionMode(FileSelector.SelectionMode selectionMode) {
            selector.setSelectionMode(selectionMode);
            return this;
        }

        /// Binds the selected path to another property.
        public FileChoice<T> bindPathBidirectional(Property<String> property) {
            selector.valueProperty().bindBidirectional(property);
            return this;
        }

        /// Sets the chooser title.
        public FileChoice<T> setChooserTitle(String chooserTitle) {
            selector.setChooserTitle(chooserTitle);
            return this;
        }

        /// Adds an extension filter to the chooser.
        public FileChoice<T> addExtensionFilter(FileChooser.ExtensionFilter filter) {
            selector.getExtensionFilters().add(filter);
            return this;
        }

        /// Creates the right-side file selector.
        @Override
        protected Node createRightNode() {
            return selector;
        }
    }
}
