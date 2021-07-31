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

import com.jfoenix.controls.JFXRippler;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

public class TabHeader extends Control implements TabControl {

    public TabHeader(Tab... tabs) {
        getStyleClass().setAll("tab-header");
        if (tabs != null) {
            getTabs().addAll(tabs);
        }
    }

    private ObservableList<Tab> tabs = FXCollections.observableArrayList();

    @Override
    public ObservableList<Tab> getTabs() {
        return tabs;
    }

    private final ObjectProperty<SingleSelectionModel<Tab>> selectionModel = new SimpleObjectProperty<>(this, "selectionModel", new TabControlSelectionModel(this));

    public SingleSelectionModel<Tab> getSelectionModel() {
        return selectionModel.get();
    }

    public ObjectProperty<SingleSelectionModel<Tab>> selectionModelProperty() {
        return selectionModel;
    }

    public void setSelectionModel(SingleSelectionModel<Tab> selectionModel) {
        this.selectionModel.set(selectionModel);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new TabHeaderSkin(this);
    }

    public static class TabHeaderSkin extends SkinBase<TabHeader> {

        private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");

        private final Color ripplerColor = Color.valueOf("#FFFF8D");

        private final HeaderContainer header;
        private boolean isSelectingTab = false;
        private Tab selectedTab;

        protected TabHeaderSkin(TabHeader control) {
            super(control);

            header = new HeaderContainer();
            getChildren().setAll(header);

            FXUtils.onChangeAndOperate(control.getSelectionModel().selectedItemProperty(), item -> {
                isSelectingTab = true;
                selectedTab = item;
                Platform.runLater(() -> {
                    header.setNeedsLayout2(true);
                    header.layout();
                });
            });

            this.selectedTab = control.getSelectionModel().getSelectedItem();
            if (this.selectedTab == null && control.getSelectionModel().getSelectedIndex() != -1) {
                control.getSelectionModel().select(control.getSelectionModel().getSelectedIndex());
                this.selectedTab = control.getSelectionModel().getSelectedItem();
            }

            if (this.selectedTab == null) {
                control.getSelectionModel().selectFirst();
            }

            this.selectedTab = control.getSelectionModel().getSelectedItem();
        }

        protected class HeaderContainer extends StackPane {
            private Timeline timeline;
            private StackPane selectedTabLine;
            private StackPane headersRegion;
            private Scale scale = new Scale(1, 1, 0, 0);
            private Rotate rotate = new Rotate(0, 0, 1);
            private double selectedTabLineOffset;
            private ObservableList<Node> binding;

            public HeaderContainer() {
                getStyleClass().add("tab-header-area");
                setPickOnBounds(false);

                headersRegion = new StackPane() {
                    @Override
                    protected double computePrefWidth(double height) {
                        double width = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            width += child.prefWidth(height);
                        }
                        return snapSize(width) + snappedLeftInset() + snappedRightInset();
                    }

                    @Override
                    protected double computePrefHeight(double width) {
                        double height = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            height = Math.max(height, child.prefHeight(width));
                        }
                        return snapSize(height) + snappedTopInset() + snappedBottomInset();
                    }

                    @Override
                    protected void layoutChildren() {
                        if (isSelectingTab) {
                            animateSelectionLine();
                            isSelectingTab = false;
                        }

                        double headerHeight = snapSize(prefHeight(-1));
                        double tabStartX = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(tabStartX, headerHeight - h - snappedBottomInset());
                            tabStartX += w;
                        }

                        selectedTabLine.resizeRelocate(0,
                                headerHeight - selectedTabLine.prefHeight(-1),
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                };

                selectedTabLine = new StackPane();
                selectedTabLine.setManaged(false);
                selectedTabLine.getTransforms().addAll(scale, rotate);
                selectedTabLine.setCache(true);
                selectedTabLine.getStyleClass().addAll("tab-selected-line");
                selectedTabLine.setPrefHeight(2);
                selectedTabLine.setPrefWidth(1);
                selectedTabLine.setBackground(new Background(new BackgroundFill(ripplerColor, CornerRadii.EMPTY, Insets.EMPTY)));
                getChildren().setAll(headersRegion, selectedTabLine);
                headersRegion.setPickOnBounds(false);
                headersRegion.prefHeightProperty().bind(heightProperty());
                prefWidthProperty().bind(headersRegion.widthProperty());

                Bindings.bindContent(headersRegion.getChildren(), binding = MappedObservableList.create(getSkinnable().getTabs(), tab -> {
                    TabHeaderContainer container = new TabHeaderContainer(tab);
                    container.setVisible(true);
                    return container;
                }));
            }

            public void setNeedsLayout2(boolean value) {
                setNeedsLayout(value);
            }

            private void runTimeline(double newTransX, double newWidth) {
                double tempScaleX = 0.0D;
                double tempWidth = 0.0D;
                double lineWidth = this.selectedTabLine.prefWidth(-1.0D);
                if (this.isAnimating()) {
                    this.timeline.stop();
                    tempScaleX = this.scale.getX();
                    if (this.rotate.getAngle() != 0.0D) {
                        this.rotate.setAngle(0.0D);
                        tempWidth = tempScaleX * lineWidth;
                        this.selectedTabLine.setTranslateX(this.selectedTabLine.getTranslateX() - tempWidth);
                    }
                }

                double oldScaleX = this.scale.getX();
                double oldWidth = lineWidth * oldScaleX;
                double oldTransX = this.selectedTabLine.getTranslateX();
                double newScaleX = newWidth * oldScaleX / oldWidth;
                this.selectedTabLineOffset = newTransX;
                // newTransX += offsetStart * (double)this.direction;
                double transDiff = newTransX - oldTransX;
                if (transDiff < 0.0D) {
                    this.selectedTabLine.setTranslateX(this.selectedTabLine.getTranslateX() + oldWidth);
                    newTransX += newWidth;
                    this.rotate.setAngle(180.0D);
                }

                this.timeline = new Timeline(
                        new KeyFrame(
                                Duration.ZERO,
                                new KeyValue(this.selectedTabLine.translateXProperty(), this.selectedTabLine.getTranslateX(), Interpolator.EASE_BOTH)
                        ),
                        new KeyFrame(
                                Duration.seconds(0.24D),
                                new KeyValue(this.scale.xProperty(), newScaleX, Interpolator.EASE_BOTH),
                                new KeyValue(this.selectedTabLine.translateXProperty(), newTransX, Interpolator.EASE_BOTH)
                        )
                );
                this.timeline.setOnFinished((finish) -> {
                    if (this.rotate.getAngle() != 0.0D) {
                        this.rotate.setAngle(0.0D);
                        this.selectedTabLine.setTranslateX(this.selectedTabLine.getTranslateX() - newWidth);
                    }

                });
                this.timeline.play();
            }

            private boolean isAnimating() {
                return this.timeline != null && this.timeline.getStatus() == Animation.Status.RUNNING;
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();

                if (isSelectingTab) {
                    animateSelectionLine();
                    isSelectingTab = false;
                }
            }

            private void animateSelectionLine() {
                double offset = 0.0D;
                double selectedTabOffset = 0.0D;
                double selectedTabWidth = 0.0D;
                Side side = Side.TOP;

                for (Node node : headersRegion.getChildren()) {
                    if (node instanceof TabHeaderContainer) {
                        TabHeaderContainer tabHeader = (TabHeaderContainer)node;
                        double tabHeaderPrefWidth = this.snapSize(tabHeader.prefWidth(-1.0D));
                        if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                            selectedTabOffset = side != Side.LEFT && side != Side.BOTTOM ? offset : -offset - tabHeaderPrefWidth;
                            selectedTabWidth = tabHeaderPrefWidth;
                            break;
                        }

                        offset += tabHeaderPrefWidth;
                    }
                }

                this.runTimeline(selectedTabOffset, selectedTabWidth);
            }
        }

        protected class TabHeaderContainer extends StackPane {

            private final Tab tab;
            private final Label tabText;
            private final BorderPane inner;
            private final JFXRippler rippler;

            public TabHeaderContainer(Tab tab) {
                this.tab = tab;

                tabText = new Label();
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                inner = new BorderPane();
                inner.setCenter(tabText);
                inner.getStyleClass().add("tab-container");
                rippler = new JFXRippler(inner, JFXRippler.RipplerPos.FRONT);
                rippler.setRipplerFill(ripplerColor);
                getChildren().setAll(rippler);

                FXUtils.onChangeAndOperate(tab.selectedProperty(), selected -> inner.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, selected));

                this.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        this.setOpacity(1);
                        getSkinnable().getSelectionModel().select(tab);
                    }
                });
            }
        }
    }
}
