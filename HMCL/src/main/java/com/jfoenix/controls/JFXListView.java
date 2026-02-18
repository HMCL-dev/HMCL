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

    private IntegerProperty depth;

    public IntegerProperty depthProperty() {
        if (depth == null) {
            depth = new SimpleIntegerProperty(this, "depth", 0);
        }
        return depth;
    }

    public int getDepth() {
        return depth != null ? depth.get() : 0;
    }

    public void setDepth(int depth) {
        depthProperty().set(depth);
    }

    private DoubleProperty currentVerticalGap;

    DoubleProperty currentVerticalGapProperty() {
        if (currentVerticalGap == null) {
            currentVerticalGap = new SimpleDoubleProperty(this, "currentVerticalGap");
        }
        return currentVerticalGap;
    }

    private void updateVerticalGap() {
        if (isExpanded()) {
            currentVerticalGapProperty().set(verticalGap.get());
        } else {
            currentVerticalGapProperty().set(0);
        }
    }

    /*
     * this only works if the items were labels / strings
     */
    private BooleanProperty showTooltip;

    public final BooleanProperty showTooltipProperty() {
        if (showTooltip == null) {
            showTooltip = new SimpleBooleanProperty(this, "showTooltip", false);
        }
        return this.showTooltip;
    }

    public final boolean isShowTooltip() {
        return showTooltip != null && showTooltip.get();
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

    private StyleableDoubleProperty verticalGap;

    public StyleableDoubleProperty verticalGapProperty() {
        if (this.verticalGap == null) {
            this.verticalGap = new StyleableDoubleProperty(0.0) {
                @Override
                public Object getBean() {
                    return JFXListView.this;
                }

                @Override
                public String getName() {
                    return "verticalGap";
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.VERTICAL_GAP;
                }

                @Override
                protected void invalidated() {
                    updateVerticalGap();
                }
            };
        }
        return this.verticalGap;
    }

    public Double getVerticalGap() {
        return verticalGap == null ? 0.0 : verticalGap.get();
    }

    public void setVerticalGap(Double gap) {
        verticalGapProperty().set(gap);
    }

    private StyleableBooleanProperty expanded;

    public StyleableBooleanProperty expandedProperty() {
        if (expanded == null) {
            expanded = new StyleableBooleanProperty(false) {
                @Override
                public Object getBean() {
                    return JFXListView.this;
                }

                @Override
                public String getName() {
                    return "expanded";
                }

                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.EXPANDED;
                }

                @Override
                protected void invalidated() {
                    updateVerticalGap();
                }
            };
        }

        return this.expanded;
    }

    public Boolean isExpanded() {
        return expanded != null && expanded.get();
    }

    public void setExpanded(Boolean expanded) {
        expandedProperty().set(expanded);
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
                new CssMetaData<>("-jfx-expanded",
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
            CHILD_STYLEABLES = List.copyOf(styleables);
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
