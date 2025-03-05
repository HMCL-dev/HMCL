/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.controls;

import com.jfoenix.assets.JFoenixResources;
import com.jfoenix.controls.base.IFXLabelFloatControl;
import com.jfoenix.converters.base.NodeConverter;
import com.jfoenix.skins.JFXComboBoxListViewSkin;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JFXComboBox is the material design implementation of a combobox.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class JFXComboBox<T> extends ComboBox<T> implements IFXLabelFloatControl {

    /**
     * {@inheritDoc}
     */
    public JFXComboBox() {
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    public JFXComboBox(ObservableList<T> items) {
        super(items);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        this.setCellFactory(listView -> new JFXListCell<T>() {
            @Override
            public void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                updateDisplayText(this, item, empty);
            }
        });

        // had to refactor the code out of the skin class to allow
        // customization of the button cell
        this.setButtonCell(new ListCell<T>() {
            {
                // fixed clearing the combo box value is causing
                // java prompt text to be shown because the button cell is not updated
                JFXComboBox.this.valueProperty().addListener(observable -> {
                    if (JFXComboBox.this.getValue() == null) {
                        updateItem(null, true);
                    }
                });
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                updateDisplayText(this, item, empty);
                this.setVisible(item != null || !empty);
            }

        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return JFoenixResources.load("css/controls/jfx-combo-box.css").toExternalForm();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXComboBoxListViewSkin<>(this);
    }

    /**
     * Initialize the style class to 'jfx-combo-box'.
     * <p>
     * This is the selector class from which CSS can be used to style
     * this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "jfx-combo-box";

    /***************************************************************************
     *                                                                         *
     * Node Converter Property                                                 *
     *                                                                         *
     **************************************************************************/
    /**
     * Converts the user-typed input (when the ComboBox is
     * {@link #editableProperty() editable}) to an object of type T, such that
     * the input may be retrieved via the  {@link #valueProperty() value} property.
     */
    public ObjectProperty<NodeConverter<T>> nodeConverterProperty() {
        return nodeConverter;
    }

    private final ObjectProperty<NodeConverter<T>> nodeConverter = new SimpleObjectProperty<>(this, "nodeConverter",
        JFXComboBox.defaultNodeConverter());

    public final void setNodeConverter(NodeConverter<T> value) {
        nodeConverterProperty().set(value);
    }

    public final NodeConverter<T> getNodeConverter() {
        return nodeConverterProperty().get();
    }

    private static <T> NodeConverter<T> defaultNodeConverter() {
        return new NodeConverter<T>() {
            @Override
            public Node toNode(T object) {
                if (object == null) {
                    return null;
                }
                StackPane selectedValueContainer = new StackPane();
                selectedValueContainer.getStyleClass().add("combo-box-selected-value-container");
                selectedValueContainer.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
                Label selectedValueLabel = object instanceof Label ? new Label(((Label) object).getText()) : new Label(
                    object.toString());
                selectedValueLabel.setTextFill(Color.BLACK);
                selectedValueContainer.getChildren().add(selectedValueLabel);
                StackPane.setAlignment(selectedValueLabel, Pos.CENTER_LEFT);
                StackPane.setMargin(selectedValueLabel, new Insets(0, 0, 0, 5));
                return selectedValueContainer;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T fromNode(Node node) {
                return (T) node;
            }

            @Override
            public String toString(T object) {
                if (object == null) {
                    return null;
                }
                if (object instanceof Label) {
                    return ((Label) object).getText();
                }
                return object.toString();
            }
        };
    }

    private boolean updateDisplayText(ListCell<T> cell, T item, boolean empty) {
        if (empty) {
            // create empty cell
            if (cell == null) {
                return true;
            }
            cell.setGraphic(null);
            cell.setText(null);
            return true;
        } else if (item instanceof Node) {
            Node currentNode = cell.getGraphic();
            Node newNode = (Node) item;
            //  create a node from the selected node of the listview
            //  using JFXComboBox {@link #nodeConverterProperty() NodeConverter})
            NodeConverter<T> nc = this.getNodeConverter();
            Node node = nc == null ? null : nc.toNode(item);
            if (currentNode == null || !currentNode.equals(newNode)) {
                cell.setText(null);
                cell.setGraphic(node == null ? newNode : node);
            }
            return node == null;
        } else {
            // run item through StringConverter if it isn't null
            StringConverter<T> c = this.getConverter();
            String s = item == null ? this.getPromptText() : (c == null ? item.toString() : c.toString(item));
            cell.setText(s);
            cell.setGraphic(null);
            return s == null || s.isEmpty();
        }
    }

    /***************************************************************************
     *                                                                         *
     * Properties                                                              *
     *                                                                         *
     **************************************************************************/

    /**
     * wrapper for validation properties / methods
     */
    private final ValidationControl validationControl = new ValidationControl(this);

    @Override
    public ValidatorBase getActiveValidator() {
        return validationControl.getActiveValidator();
    }

    @Override
    public ReadOnlyObjectProperty<ValidatorBase> activeValidatorProperty() {
        return validationControl.activeValidatorProperty();
    }

    @Override
    public ObservableList<ValidatorBase> getValidators() {
        return validationControl.getValidators();
    }

    @Override
    public void setValidators(ValidatorBase... validators) {
        validationControl.setValidators(validators);
    }

    @Override
    public boolean validate() {
        return validationControl.validate();
    }

    @Override
    public void resetValidation() {
        validationControl.resetValidation();
    }

    /***************************************************************************
     *                                                                         *
     * styleable Properties                                                    *
     *                                                                         *
     **************************************************************************/

    /**
     * set true to show a float the prompt text when focusing the field
     */
    private final StyleableBooleanProperty labelFloat = new SimpleStyleableBooleanProperty(StyleableProperties.LABEL_FLOAT,
        JFXComboBox.this,
        "lableFloat",
        false);

    public final StyleableBooleanProperty labelFloatProperty() {
        return this.labelFloat;
    }

    public final boolean isLabelFloat() {
        return this.labelFloatProperty().get();
    }

    public final void setLabelFloat(final boolean labelFloat) {
        this.labelFloatProperty().set(labelFloat);
    }

    /**
     * default color used when the field is unfocused
     */
    private final StyleableObjectProperty<Paint> unFocusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.UNFOCUS_COLOR,
        JFXComboBox.this,
        "unFocusColor",
        Color.rgb(77,
            77,
            77));

    public Paint getUnFocusColor() {
        return unFocusColor == null ? Color.rgb(77, 77, 77) : unFocusColor.get();
    }

    public StyleableObjectProperty<Paint> unFocusColorProperty() {
        return this.unFocusColor;
    }

    public void setUnFocusColor(Paint color) {
        this.unFocusColor.set(color);
    }

    /**
     * default color used when the field is focused
     */
    private final StyleableObjectProperty<Paint> focusColor = new SimpleStyleableObjectProperty<>(StyleableProperties.FOCUS_COLOR,
        JFXComboBox.this,
        "focusColor",
        Color.valueOf("#4059A9"));

    public Paint getFocusColor() {
        return focusColor == null ? Color.valueOf("#4059A9") : focusColor.get();
    }

    public StyleableObjectProperty<Paint> focusColorProperty() {
        return this.focusColor;
    }

    public void setFocusColor(Paint color) {
        this.focusColor.set(color);
    }


    /**
     * disable animation on validation
     */
    private final StyleableBooleanProperty disableAnimation = new SimpleStyleableBooleanProperty(StyleableProperties.DISABLE_ANIMATION,
        JFXComboBox.this,
        "disableAnimation",
        false);

    @Override
    public final StyleableBooleanProperty disableAnimationProperty() {
        return this.disableAnimation;
    }

    @Override
    public final Boolean isDisableAnimation() {
        return disableAnimation != null && this.disableAnimationProperty().get();
    }

    @Override
    public final void setDisableAnimation(final Boolean disabled) {
        this.disableAnimationProperty().set(disabled);
    }


    private static class StyleableProperties {
        private static final CssMetaData<JFXComboBox<?>, Paint> UNFOCUS_COLOR = new CssMetaData<JFXComboBox<?>, Paint>(
            "-jfx-unfocus-color",
            StyleConverter.getPaintConverter(),
            Color.valueOf("#A6A6A6")) {
            @Override
            public boolean isSettable(JFXComboBox<?> control) {
                return !control.unFocusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXComboBox<?> control) {
                return control.unFocusColorProperty();
            }
        };
        private static final CssMetaData<JFXComboBox<?>, Paint> FOCUS_COLOR = new CssMetaData<JFXComboBox<?>, Paint>(
            "-jfx-focus-color",
            StyleConverter.getPaintConverter(),
            Color.valueOf("#3f51b5")) {
            @Override
            public boolean isSettable(JFXComboBox<?> control) {
                return !control.focusColor.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(JFXComboBox<?> control) {
                return control.focusColorProperty();
            }
        };
        private static final CssMetaData<JFXComboBox<?>, Boolean> LABEL_FLOAT = new CssMetaData<JFXComboBox<?>, Boolean>(
            "-jfx-label-float", StyleConverter.getBooleanConverter(), false) {
            @Override
            public boolean isSettable(JFXComboBox<?> control) {
                return !control.labelFloat.isBound();
            }

            @Override
            public StyleableBooleanProperty getStyleableProperty(JFXComboBox<?> control) {
                return control.labelFloatProperty();
            }
        };

        private static final CssMetaData<JFXComboBox<?>, Boolean> DISABLE_ANIMATION =
            new CssMetaData<JFXComboBox<?>, Boolean>("-jfx-disable-animation",
                StyleConverter.getBooleanConverter(), false) {
                @Override
                public boolean isSettable(JFXComboBox<?> control) {
                    return !control.disableAnimation.isBound();
                }

                @Override
                public StyleableBooleanProperty getStyleableProperty(JFXComboBox control) {
                    return control.disableAnimationProperty();
                }
            };

        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(ComboBox.getClassCssMetaData());
            Collections.addAll(styleables, UNFOCUS_COLOR, FOCUS_COLOR, LABEL_FLOAT, DISABLE_ANIMATION);
            CHILD_STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.CHILD_STYLEABLES;
    }
}
