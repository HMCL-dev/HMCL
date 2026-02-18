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

import com.jfoenix.skins.JFXListViewSkin;
import javafx.beans.property.*;
import javafx.css.*;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseEvent;

import java.util.*;

/// Material design implementation of List View
///
/// @author Shadi Shaheen
/// @version 1.0
/// @since 2016-03-09
public class JFXListView<T> extends ListView<T> {

    /**
     * {@inheritDoc}
     */
    public JFXListView() {
        this.setCellFactory(listView -> new JFXListCell<>());
        initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXListViewSkin<>(this);
    }

    private final ObjectProperty<Integer> depthProperty = new SimpleObjectProperty<>(0);

    public ObjectProperty<Integer> depthProperty() {
        return depthProperty;
    }

    public int getDepth() {
        return depthProperty.get();
    }

    public void setDepth(int depth) {
        depthProperty.set(depth);
    }

    private final ReadOnlyDoubleWrapper currentVerticalGapProperty = new ReadOnlyDoubleWrapper();

    ReadOnlyDoubleProperty currentVerticalGapProperty() {
        return currentVerticalGapProperty.getReadOnlyProperty();
    }

    private void expand() {
        currentVerticalGapProperty.set(verticalGap.get());
    }

    private void collapse() {
        currentVerticalGapProperty.set(0);
    }

    /*
     * this only works if the items were labels / strings
     */
    private final BooleanProperty showTooltip = new SimpleBooleanProperty(false);

    public final BooleanProperty showTooltipProperty() {
        return this.showTooltip;
    }

    public final boolean isShowTooltip() {
        return this.showTooltipProperty().get();
    }

    public final void setShowTooltip(final boolean showTooltip) {
        this.showTooltipProperty().set(showTooltip);
    }

    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    /// Initialize the style class to 'jfx-list-view'.
    ///
    /// This is the selector class from which CSS can be used to style
    /// this control.
    private static final String DEFAULT_STYLE_CLASS = "jfx-list-view";

    private void initialize() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        expanded.addListener((o, oldVal, newVal) -> {
            if (newVal) {
                expand();
            } else {
                collapse();
            }
        });

        verticalGap.addListener((o, oldVal, newVal) -> {
            if (isExpanded()) {
                expand();
            } else {
                collapse();
            }
        });
    }


    // allow single selection across the list and all sublits
    private boolean allowClear = true;

    private void clearSelection(JFXListView<?> selectedList) {
        if (allowClear) {
            allowClear = false;
            if (this != selectedList) {
                this.getSelectionModel().clearSelection();
            }
            allowClear = true;
        }
    }

    /**
     * propagate mouse events to the parent node ( e.g. to allow dragging while clicking on the list)
     */
    public void propagateMouseEventsToParent() {
        this.addEventHandler(MouseEvent.ANY, e -> {
            e.consume();
            this.getParent().fireEvent(e);
        });
    }

    private final StyleableDoubleProperty verticalGap = new SimpleStyleableDoubleProperty(StyleableProperties.VERTICAL_GAP,
            JFXListView.this,
            "verticalGap",
            0.0);

    public Double getVerticalGap() {
        return verticalGap == null ? 0 : verticalGap.get();
    }

    public StyleableDoubleProperty verticalGapProperty() {
        return this.verticalGap;
    }

    public void setVerticalGap(Double gap) {
        this.verticalGap.set(gap);
    }

    private final StyleableBooleanProperty expanded = new SimpleStyleableBooleanProperty(StyleableProperties.EXPANDED,
            JFXListView.this,
            "expanded",
            false);

    public Boolean isExpanded() {
        return expanded != null && expanded.get();
    }

    public StyleableBooleanProperty expandedProperty() {
        return this.expanded;
    }

    public void setExpanded(Boolean expanded) {
        this.expanded.set(expanded);
    }

    private static class StyleableProperties {
        private static final CssMetaData<JFXListView<?>, Number> VERTICAL_GAP =
                new CssMetaData<>("-jfx-vertical-gap",
                        SizeConverter.getInstance(), 0) {
                    @Override
                    public boolean isSettable(JFXListView<?> control) {
                        return control.verticalGap == null || !control.verticalGap.isBound();
                    }

                    @Override
                    public StyleableDoubleProperty getStyleableProperty(JFXListView<?> control) {
                        return control.verticalGapProperty();
                    }
                };
        private static final CssMetaData<JFXListView<?>, Boolean> EXPANDED =
                new CssMetaData<JFXListView<?>, Boolean>("-jfx-expanded",
                        BooleanConverter.getInstance(), false) {
                    @Override
                    public boolean isSettable(JFXListView<?> control) {
                        // it's only settable if the List is not shown yet
                        return control.getHeight() == 0 && (control.expanded == null || !control.expanded.isBound());
                    }

                    @Override
                    public StyleableBooleanProperty getStyleableProperty(JFXListView<?> control) {
                        return control.expandedProperty();
                    }
                };
        private static final List<CssMetaData<? extends Styleable, ?>> CHILD_STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ListView.getClassCssMetaData());
            Collections.addAll(styleables, VERTICAL_GAP, EXPANDED);
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
