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

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class TabHeader extends Control implements TabControl, PageAware {

    private final TransitionPane contentPane;

    public TabHeader(Tab<?>... tabs) {
        this(null, tabs);
    }

    public TabHeader(@Nullable TransitionPane contentPane, Tab<?>... tabs) {
        this.contentPane = contentPane;

        getStyleClass().setAll("tab-header");
        if (tabs != null) {
            getTabs().addAll(tabs);
        }
    }

    private final ObservableList<Tab<?>> tabs = FXCollections.observableArrayList();

    @Override
    public ObservableList<Tab<?>> getTabs() {
        return tabs;
    }

    private final SingleSelectionModel<Tab<?>> selectionModel = new TabControlSelectionModel(this);

    public SingleSelectionModel<Tab<?>> getSelectionModel() {
        return selectionModel;
    }

    public void select(Tab<?> tab) {
        select(tab, true);
    }

    public void select(Tab<?> tab, boolean playAnimation) {
        Tab<?> oldTab = getSelectionModel().getSelectedItem();
        if (oldTab != null) {
            if (oldTab.getNode() instanceof PageAware pageAware) {
                pageAware.onPageHidden();
            }
        }

        tab.initializeIfNeeded();
        if (tab.getNode() instanceof PageAware pageAware) {
            pageAware.onPageShown();
        }

        getSelectionModel().select(tab);

        if (contentPane != null) {
            if (playAnimation && contentPane.getCurrentNode() != null) {
                contentPane.setContent(tab.getNode(),
                        ContainerAnimations.SLIDE_UP_FADE_IN,
                        Motion.MEDIUM4,
                        Motion.EASE_IN_OUT_CUBIC_EMPHASIZED
                );
            } else {
                contentPane.setContent(tab.getNode(), ContainerAnimations.NONE);
            }
        }
    }

    @Override
    public void onPageShown() {
        Tab<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null) {
            if (tab.getNode() instanceof PageAware) {
                ((PageAware) tab.getNode()).onPageShown();
            }
        }
    }

    @Override
    public void onPageHidden() {
        Tab<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null) {
            if (tab.getNode() instanceof PageAware) {
                ((PageAware) tab.getNode()).onPageHidden();
            }
        }
    }

    private final ObjectProperty<Side> side = new SimpleObjectProperty<>(Side.TOP);

    /**
     * The position of the tabs.
     */
    public ObjectProperty<Side> sideProperty() {
        return side;
    }

    /**
     * The position to place the tabs.
     */
    public Side getSide() {
        return side.get();
    }

    /**
     * The position the place the tabs in this TabHeader.
     */
    public void setSide(Side side) {
        this.side.set(side);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new TabHeaderSkin(this);
    }

    public static class TabHeaderSkin extends SkinBase<TabHeader> {

        private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");

        private final HeaderContainer header;
        private boolean isSelectingTab = false;
        private Tab<?> selectedTab;

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

        protected final class HeaderContainer extends StackPane {
            private Timeline timeline;
            private final StackPane selectedTabLine;
            private final HeadersRegion headersRegion;
            private final Scale scale = new Scale(1, 1, 0, 0);
            private final Rotate rotate = new Rotate(0, 0, 1);
            @SuppressWarnings({"FieldCanBeLocal", "unused"})
            private final ObservableList<Node> binding;

            public HeaderContainer() {
                getStyleClass().add("tab-header-area");
                setPickOnBounds(false);

                headersRegion = new HeadersRegion();
                headersRegion.sideProperty().bind(getSkinnable().sideProperty());

                selectedTabLine = new StackPane();
                selectedTabLine.setManaged(false);
                selectedTabLine.getTransforms().addAll(scale, rotate);
                selectedTabLine.setCache(true);
                selectedTabLine.getStyleClass().addAll("tab-selected-line");
                selectedTabLine.setPrefHeight(2);
                selectedTabLine.setPrefWidth(2);
                getChildren().setAll(headersRegion, selectedTabLine);
                headersRegion.setPickOnBounds(false);
                headersRegion.prefHeightProperty().bind(heightProperty());
                rotate.pivotXProperty().bind(Bindings.createDoubleBinding(() -> getSkinnable().getSide().isHorizontal() ? 0.0 : 1, getSkinnable().sideProperty()));
                rotate.pivotYProperty().bind(Bindings.createDoubleBinding(() -> getSkinnable().getSide().isHorizontal() ? 1.0 : 0, getSkinnable().sideProperty()));

                Bindings.bindContent(headersRegion.getChildren(), binding = MappedObservableList.create(getSkinnable().getTabs(), tab -> {
                    TabHeaderContainer container = new TabHeaderContainer(tab);
                    container.setVisible(true);
                    return container;
                }));
            }

            public void setNeedsLayout2(boolean value) {
                setNeedsLayout(value);
            }

            private boolean isAnimating() {
                return this.timeline != null && this.timeline.getStatus() == Animation.Status.RUNNING;
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();

                if (isSelectingTab) {
                    headersRegion.animateSelectionLine();
                    isSelectingTab = false;
                }
            }

            private final class HeadersRegion extends StackPane {
                private SideAction action;
                private final ObjectProperty<Side> side = new SimpleObjectProperty<Side>() {
                    @Override
                    protected void invalidated() {
                        super.invalidated();

                        action = switch (get()) {
                            case TOP -> new Top();
                            case BOTTOM -> new Bottom();
                            case LEFT -> new Left();
                            case RIGHT -> new Right();
                        };
                    }
                };

                public Side getSide() {
                    return side.get();
                }

                public ObjectProperty<Side> sideProperty() {
                    return side;
                }

                public void setSide(Side side) {
                    this.side.set(side);
                }

                @Override
                protected double computePrefWidth(double height) {
                    return action.computePrefWidth(height);
                }

                @Override
                protected double computePrefHeight(double width) {
                    return action.computePrefHeight(width);
                }

                @Override
                protected void layoutChildren() {
                    action.layoutChildren();
                }

                private void animateSelectionLine() {
                    action.animateSelectionLine();
                }

                private abstract class SideAction {
                    abstract double computePrefWidth(double height);

                    abstract double computePrefHeight(double width);

                    void layoutChildren() {
                        if (isSelectingTab) {
                            animateSelectionLine();
                            isSelectingTab = false;
                        }
                    }

                    abstract void animateSelectionLine();
                }

                private abstract class Horizontal extends SideAction {
                    @Override
                    public double computePrefWidth(double height) {
                        double width = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            width += child.prefWidth(height);
                        }
                        return snapSize(width) + snappedLeftInset() + snappedRightInset();
                    }

                    @Override
                    public double computePrefHeight(double width) {
                        double height = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            height = Math.max(height, child.prefHeight(width));
                        }
                        return snapSize(height) + snappedTopInset() + snappedBottomInset();
                    }

                    private void runTimeline(double newTransX, double newWidth) {
                        double lineWidth = selectedTabLine.prefWidth(-1.0D);
                        if (isAnimating()) {
                            timeline.stop();
                            double tempScaleX = scale.getX();
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                double tempWidth = tempScaleX * lineWidth;
                                selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() - tempWidth);
                            }
                        }

                        double oldScaleX = scale.getX();
                        double oldWidth = lineWidth * oldScaleX;
                        double oldTransX = selectedTabLine.getTranslateX();
                        double newScaleX = newWidth * oldScaleX / oldWidth;
                        // newTransX += offsetStart * (double)this.direction;
                        double transDiff = newTransX - oldTransX;
                        if (transDiff < 0.0D) {
                            selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() + oldWidth);
                            newTransX += newWidth;
                            rotate.setAngle(180.0D);
                        }

                        timeline = new Timeline(
                                new KeyFrame(
                                        Duration.ZERO,
                                        new KeyValue(selectedTabLine.translateXProperty(), selectedTabLine.getTranslateX(), Interpolator.EASE_BOTH)
                                ),
                                new KeyFrame(
                                        Duration.seconds(0.24D),
                                        new KeyValue(scale.xProperty(), newScaleX, Interpolator.EASE_BOTH),
                                        new KeyValue(selectedTabLine.translateXProperty(), newTransX, Interpolator.EASE_BOTH)
                                )
                        );
                        timeline.setOnFinished((finish) -> {
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() - newWidth);
                            }

                        });
                        timeline.play();
                    }

                    @Override
                    public void animateSelectionLine() {
                        double offset = 0.0D;
                        double selectedTabOffset = 0.0D;
                        double selectedTabWidth = 0.0D;

                        for (Node node : headersRegion.getChildren()) {
                            if (node instanceof TabHeaderContainer) {
                                TabHeaderContainer tabHeader = (TabHeaderContainer) node;
                                double tabHeaderPrefWidth = snapSize(tabHeader.prefWidth(-1.0D));
                                if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                                    selectedTabOffset = offset;
                                    selectedTabWidth = tabHeaderPrefWidth;
                                    break;
                                }

                                offset += tabHeaderPrefWidth;
                            }
                        }

                        this.runTimeline(selectedTabOffset, selectedTabWidth);
                    }
                }

                private final class Top extends Horizontal {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

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
                }

                private final class Bottom extends Horizontal {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerHeight = snapSize(prefHeight(-1));
                        double tabStartX = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(tabStartX, snappedTopInset());
                            tabStartX += w;
                        }

                        selectedTabLine.resizeRelocate(0, 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

                private abstract class Vertical extends SideAction {
                    @Override
                    public double computePrefWidth(double height) {
                        double width = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            width = Math.max(width, child.prefWidth(height));
                        }
                        return snapSize(width) + snappedLeftInset() + snappedRightInset();
                    }

                    @Override
                    public double computePrefHeight(double width) {
                        double height = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            height += child.prefHeight(width);
                        }
                        return snapSize(height) + snappedTopInset() + snappedBottomInset();
                    }

                    private void runTimeline(double newTransY, double newHeight) {
                        double lineHeight = selectedTabLine.prefHeight(-1.0D);
                        if (isAnimating()) {
                            timeline.stop();
                            double tempScaleY = scale.getY();
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                double tempHeight = tempScaleY * lineHeight;
                                selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() - tempHeight);
                            }
                        }

                        double oldScaleY = scale.getY();
                        double oldHeight = lineHeight * oldScaleY;
                        double oldTransY = selectedTabLine.getTranslateY();
                        double newScaleY = newHeight * oldScaleY / oldHeight;
                        // newTransY += offsetStart * (double)this.direction;
                        double transDiff = newTransY - oldTransY;
                        if (transDiff < 0.0D) {
                            selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() + oldHeight);
                            newTransY += newHeight;
                            rotate.setAngle(180.0D);
                        }

                        timeline = new Timeline(
                                new KeyFrame(
                                        Duration.ZERO,
                                        new KeyValue(selectedTabLine.translateYProperty(), selectedTabLine.getTranslateY(), Interpolator.EASE_BOTH)
                                ),
                                new KeyFrame(
                                        Duration.seconds(1.24D),
                                        new KeyValue(scale.yProperty(), newScaleY, Interpolator.EASE_BOTH),
                                        new KeyValue(selectedTabLine.translateYProperty(), newTransY, Interpolator.EASE_BOTH)
                                )
                        );
                        timeline.setOnFinished((finish) -> {
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() - newHeight);
                            }

                        });
                        timeline.play();
                    }

                    @Override
                    public void animateSelectionLine() {
                        double offset = 0.0D;
                        double selectedTabOffset = 0.0D;
                        double selectedTabHeight = 0.0D;

                        for (Node node : headersRegion.getChildren()) {
                            if (node instanceof TabHeaderContainer) {
                                TabHeaderContainer tabHeader = (TabHeaderContainer) node;
                                double tabHeaderPrefHeight = snapSize(tabHeader.prefHeight(-1.0D));
                                if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                                    selectedTabOffset = offset;
                                    selectedTabHeight = tabHeaderPrefHeight;
                                    break;
                                }

                                offset += tabHeaderPrefHeight;
                            }
                        }

                        this.runTimeline(selectedTabOffset, selectedTabHeight);
                    }
                }

                private final class Left extends Vertical {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerWidth = snapSize(prefWidth(-1));
                        double tabStartY = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(headerWidth - w - snappedRightInset(), tabStartY);
                            tabStartY += h;
                        }

                        selectedTabLine.resizeRelocate(headerWidth - selectedTabLine.prefWidth(-1), 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

                private final class Right extends Vertical {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerWidth = snapSize(prefWidth(-1));
                        double tabStartY = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(snappedLeftInset(), tabStartY);
                            tabStartY += h;
                        }

                        selectedTabLine.resizeRelocate(0, 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

            }
        }

        protected class TabHeaderContainer extends StackPane {

            private final Tab<?> tab;
            private final Label tabText;
            private final BorderPane inner;

            public TabHeaderContainer(Tab<?> tab) {
                this.tab = tab;

                tabText = new Label();
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                inner = new BorderPane();
                inner.setCenter(tabText);
                inner.getStyleClass().add("tab-container");
                inner.setMouseTransparent(true);
                RipplerContainer rippler = new RipplerContainer(inner);
                getChildren().setAll(rippler);

                FXUtils.onChangeAndOperate(tab.selectedProperty(), selected -> inner.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, selected));

                FXUtils.onClicked(this, () -> {
                    this.setOpacity(1);
                    getSkinnable().getSelectionModel().select(tab);
                });
            }
        }
    }

}
