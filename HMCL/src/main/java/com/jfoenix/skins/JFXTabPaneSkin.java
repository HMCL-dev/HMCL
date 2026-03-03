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

package com.jfoenix.skins;

import com.jfoenix.controls.JFXRippler;
import com.jfoenix.controls.JFXRippler.RipplerMask;
import com.jfoenix.controls.JFXRippler.RipplerPos;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.event.MultiplePropertyChangeListenerHandler;
import com.jfoenix.svg.SVGGlyph;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.TabPaneSkin;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <h1>Material Design TabPane Skin</h1>
 *
 * @author Shadi Shaheen
 */
public class JFXTabPaneSkin extends TabPaneSkin {

    private final Color defaultColor = Color.valueOf("#00BCD4");
    private final Color ripplerColor = Color.valueOf("#FFFF8D");
    private final Color selectedTabText = Color.WHITE;
    private Color tempLabelColor = Color.WHITE;

    private HeaderContainer header;
    private ObservableList<TabContentHolder> tabContentHolders;
    private Rectangle clip;
    private Rectangle tabsClip;
    private Tab selectedTab;
    private boolean isSelectingTab = false;
    private double dragStart, offsetStart;
    private AnchorPane tabsContainer;
    private AnchorPane tabsContainerHolder;
    private static final int SPACER = 10;
    private double maxWidth = 0.0d;
    private double maxHeight = 0.0d;

    public JFXTabPaneSkin(TabPane tabPane) {
        super(tabPane);
        tabContentHolders = FXCollections.observableArrayList();
        header = new HeaderContainer();
        getChildren().add(JFXDepthManager.createMaterialNode(header, 1));

        tabsContainer = new AnchorPane();
        tabsContainerHolder = new AnchorPane();
        tabsContainerHolder.getChildren().add(tabsContainer);
        tabsClip = new Rectangle();
        tabsContainerHolder.setClip(tabsClip);
        getChildren().add(tabsContainerHolder);

        // add tabs
        for (Tab tab : getSkinnable().getTabs()) {
            addTabContentHolder(tab);
        }

        // clipping tabpane/header pane
        clip = new Rectangle(tabPane.getWidth(), tabPane.getHeight());
        getSkinnable().setClip(clip);
        if (getSkinnable().getTabs().size() == 0) {
            header.setVisible(false);
        }

        // select a tab
        selectedTab = getSkinnable().getSelectionModel().getSelectedItem();
        if (selectedTab == null && getSkinnable().getSelectionModel().getSelectedIndex() != -1) {
            getSkinnable().getSelectionModel().select(getSkinnable().getSelectionModel().getSelectedIndex());
            selectedTab = getSkinnable().getSelectionModel().getSelectedItem();
        }
        // if no selected tab, then select the first tab
        if (selectedTab == null) {
            getSkinnable().getSelectionModel().selectFirst();
        }
        selectedTab = getSkinnable().getSelectionModel().getSelectedItem();

        header.headersRegion.setOnMouseDragged(me -> {
            header.updateScrollOffset(offsetStart + (isHorizontal() ? me.getSceneX() : me.getSceneY()) - dragStart);
            me.consume();
        });
        getSkinnable().setOnMousePressed(me -> {
            dragStart = (isHorizontal() ? me.getSceneX() : me.getSceneY());
            offsetStart = header.scrollOffset;
        });

        // add listeners on tab list
        getSkinnable().getTabs().addListener((ListChangeListener<Tab>) change -> {
            List<Tab> tabsToBeRemoved = new ArrayList<>();
            List<Tab> tabsToBeAdded = new ArrayList<>();
            int insertIndex = -1;
            while (change.next()) {
                if (change.wasPermutated()) {
                    Tab selectedTab = getSkinnable().getSelectionModel().getSelectedItem();
                    List<Tab> permutatedTabs = new ArrayList<>(change.getTo() - change.getFrom());
                    getSkinnable().getSelectionModel().clearSelection();
                    for (int i = change.getFrom(); i < change.getTo(); i++) {
                        permutatedTabs.add(getSkinnable().getTabs().get(i));
                    }
                    removeTabs(permutatedTabs);
                    addTabs(permutatedTabs, change.getFrom());
                    getSkinnable().getSelectionModel().select(selectedTab);
                }
                if (change.wasRemoved()) {
                    tabsToBeRemoved.addAll(change.getRemoved());
                }
                if (change.wasAdded()) {
                    tabsToBeAdded.addAll(change.getAddedSubList());
                    insertIndex = change.getFrom();
                }
            }
            // only remove the tabs that are not in tabsToBeAdded
            tabsToBeRemoved.removeAll(tabsToBeAdded);
            removeTabs(tabsToBeRemoved);
            // add the new tabs
            if (!tabsToBeAdded.isEmpty()) {
                for (TabContentHolder tabContentHolder : tabContentHolders) {
                    TabHeaderContainer tabHeaderContainer = header.getTabHeaderContainer(tabContentHolder.tab);
                    if (!tabHeaderContainer.isClosing && tabsToBeAdded.contains(tabContentHolder.tab)) {
                        tabsToBeAdded.remove(tabContentHolder.tab);
                    }
                }
                addTabs(tabsToBeAdded, insertIndex == -1 ? tabContentHolders.size() : insertIndex);
            }
            getSkinnable().requestLayout();
        });

        registerChangeListener(tabPane.getSelectionModel().selectedItemProperty(), (e) -> handleControlPropertyChanged("SELECTED_TAB"));
        registerChangeListener(tabPane.widthProperty(), (e) -> handleControlPropertyChanged("WIDTH"));
        registerChangeListener(tabPane.heightProperty(), (e) -> handleControlPropertyChanged("HEIGHT"));

    }

    protected void handleControlPropertyChanged(String property) {
        if ("SELECTED_TAB".equals(property)) {
            isSelectingTab = true;
            selectedTab = getSkinnable().getSelectionModel().getSelectedItem();
            getSkinnable().requestLayout();
        } else if ("WIDTH".equals(property)) {
            clip.setWidth(getSkinnable().getWidth());
        } else if ("HEIGHT".equals(property)) {
            clip.setHeight(getSkinnable().getHeight());
        }
    }

    private void removeTabs(List<? extends Tab> removedTabs) {
        for (Tab tab : removedTabs) {
            TabHeaderContainer tabHeaderContainer = header.getTabHeaderContainer(tab);
            if (tabHeaderContainer != null) {
                tabHeaderContainer.isClosing = true;
                removeTab(tab);
                // if tabs list is empty hide the header container
                if (getSkinnable().getTabs().isEmpty()) {
                    header.setVisible(false);
                }
            }
        }
    }

    private void addTabs(List<? extends Tab> addedTabs, int startIndex) {
        int i = 0;
        for (Tab tab : addedTabs) {
            // show header container if we are adding the 1st tab
            if (!header.isVisible()) {
                header.setVisible(true);
            }
            header.addTab(tab, startIndex + i++, false);
            addTabContentHolder(tab);
            final TabHeaderContainer tabHeaderContainer = header.getTabHeaderContainer(tab);
            if (tabHeaderContainer != null) {
                tabHeaderContainer.setVisible(true);
                tabHeaderContainer.inner.requestLayout();
            }
        }
    }

    private void addTabContentHolder(Tab tab) {
        // create new content place holder
        TabContentHolder tabContentHolder = new TabContentHolder(tab);
        tabContentHolder.setClip(new Rectangle());
        tabContentHolders.add(tabContentHolder);
        // always add tab content holder below its header
        tabsContainer.getChildren().add(0, tabContentHolder);
    }

    private void removeTabContentHolder(Tab tab) {
        for (TabContentHolder tabContentHolder : tabContentHolders) {
            if (tabContentHolder.tab.equals(tab)) {
                tabContentHolder.removeListeners(tab);
                getChildren().remove(tabContentHolder);
                tabContentHolders.remove(tabContentHolder);
                tabsContainer.getChildren().remove(tabContentHolder);
                break;
            }
        }
    }

    private void removeTab(Tab tab) {
        final TabHeaderContainer tabHeaderContainer = header.getTabHeaderContainer(tab);
        if (tabHeaderContainer != null) {
            tabHeaderContainer.removeListeners(tab);
        }
        header.removeTab(tab);
        removeTabContentHolder(tab);
        header.requestLayout();
    }

    private boolean isHorizontal() {
        final Side tabPosition = getSkinnable().getSide();
        return Side.TOP.equals(tabPosition) || Side.BOTTOM.equals(tabPosition);
    }

    private static int getRotation(Side pos) {
        switch (pos) {
            case TOP:
                return 0;
            case BOTTOM:
                return 180;
            case LEFT:
                return -90;
            case RIGHT:
                return 90;
            default:
                return 0;
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        for (TabContentHolder tabContentHolder : tabContentHolders) {
            maxWidth = Math.max(maxWidth, snapSize(tabContentHolder.prefWidth(-1)));
        }
        final double headerContainerWidth = snapSize(header.prefWidth(-1));
        double prefWidth = Math.max(maxWidth, headerContainerWidth);
        return snapSize(prefWidth) + rightInset + leftInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        for (TabContentHolder tabContentHolder : tabContentHolders) {
            maxHeight = Math.max(maxHeight, snapSize(tabContentHolder.prefHeight(-1)));
        }
        final double headerContainerHeight = snapSize(header.prefHeight(-1));
        double prefHeight = maxHeight + snapSize(headerContainerHeight);
        return snapSize(prefHeight) + topInset + bottomInset;
    }

    @Override
    public double computeBaselineOffset(double topInset, double rightInset, double bottomInset, double leftInset) {
        return header.getBaselineOffset() + topInset;
    }

    /*
     *  keep track of indices after changing the tabs, it used to fix
     *  tabs animation after changing the tabs (remove/add)
     */
    private int diffTabsIndices = 0;

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        final double headerHeight = snapSize(header.prefHeight(-1));
        final Side side = getSkinnable().getSide();
        double tabsX = side == Side.RIGHT ? x + w - headerHeight : x;
        double tabsY = side == Side.BOTTOM ? y + h - headerHeight : y;
        final int rotation = getRotation(side);

        // update header
        switch (side) {
            case TOP:
                header.resize(w, headerHeight);
                header.relocate(tabsX, tabsY);
                break;
            case LEFT:
                header.resize(h, headerHeight);
                header.relocate(tabsX + headerHeight, h - headerHeight);
                break;
            case RIGHT:
                header.resize(h, headerHeight);
                header.relocate(tabsX, y - headerHeight);
                break;
            case BOTTOM:
                header.resize(w, headerHeight);
                header.relocate(w, tabsY - headerHeight);
                break;
        }
        header.getTransforms().setAll(new Rotate(rotation, 0, headerHeight, 1));

        // update header clip
//        header.clip.setX(0);
//        header.clip.setY(0);
//        header.clip.setWidth(isHorizontal() ? w : h);
//        header.clip.setHeight(headerHeight + 10); // 10 is the height of the shadow effect

        // position the tab content of the current selected tab
        double contentStartX = x + (side == Side.LEFT ? headerHeight : 0);
        double contentStartY = y + (side == Side.TOP ? headerHeight : 0);
        double contentWidth = w - (isHorizontal() ? 0 : headerHeight);
        double contentHeight = h - (isHorizontal() ? headerHeight : 0);

        // update tabs container
        tabsClip.setWidth(contentWidth);
        tabsClip.setHeight(contentHeight);
        tabsContainerHolder.resize(contentWidth, contentHeight);
        tabsContainerHolder.relocate(contentStartX, contentStartY);
        tabsContainer.resize(contentWidth * tabContentHolders.size(), contentHeight);

        for (int i = 0, max = tabContentHolders.size(); i < max; i++) {
            TabContentHolder tabContentHolder = tabContentHolders.get(i);
            tabContentHolder.setVisible(true);
            tabContentHolder.setTranslateX(contentWidth * i);
            if (tabContentHolder.getClip() != null) {
                ((Rectangle) tabContentHolder.getClip()).setWidth(contentWidth);
                ((Rectangle) tabContentHolder.getClip()).setHeight(contentHeight);
            }
            if (tabContentHolder.tab == selectedTab) {
                int index = getSkinnable().getTabs().indexOf(selectedTab);
                if (index != i) {
                    tabsContainer.setTranslateX(-contentWidth * i);
                    diffTabsIndices = i - index;
                } else {
                    // fix X translation after changing the tabs
                    if (diffTabsIndices != 0) {
                        tabsContainer.setTranslateX(tabsContainer.getTranslateX() + contentWidth * diffTabsIndices);
                        diffTabsIndices = 0;
                    }
                    // animate upon tab selection only otherwise just translate the selected tab
                    if (isSelectingTab) {
                        new CachedTransition(tabsContainer,
                            new Timeline(new KeyFrame(Duration.millis(1000), new KeyValue(tabsContainer.translateXProperty(), -contentWidth * index, Interpolator.EASE_BOTH)))) {{
                                    setCycleDuration(Duration.seconds(0.320));
                                    setDelay(Duration.seconds(0));
                                }
                        }.play();
                    } else {
                        tabsContainer.setTranslateX(-contentWidth * index);
                    }
                }
            }
            tabContentHolder.resize(contentWidth, contentHeight);
//            tabContentHolder.relocate(contentStartX, contentStartY);
        }
    }

    /**************************************************************************
     *                                                                          *
     * HeaderContainer: tabs headers container                                    *
     *                                                                          *
     **************************************************************************/
    protected class HeaderContainer extends StackPane {

        private Rectangle clip;
        private StackPane headersRegion;
        private StackPane headerBackground;

        private HeaderControl rightControlButton;
        private HeaderControl leftControlButton;
        private StackPane selectedTabLine;
        private boolean initialized = false;
        private boolean measureClosingTabs = false;
        private double scrollOffset, selectedTabLineOffset;

        private final Scale scale;
        private final Rotate rotate;
        private int direction;
        private Timeline timeline;
        private final double translateScaleFactor = 1.3;

        public HeaderContainer() {
            // keep track of the current side
            getSkinnable().sideProperty().addListener(observable -> updateDirection());
            updateDirection();

            getStyleClass().setAll("tab-header-area");
            setManaged(false);
            clip = new Rectangle();
            headersRegion = new StackPane() {
                @Override
                protected double computePrefWidth(double height) {
                    double width = 0.0F;
                    for (Node child : getChildren()) {
                        if (child instanceof TabHeaderContainer && child.isVisible() && (measureClosingTabs || !((TabHeaderContainer) child).isClosing)) {
                            width += child.prefWidth(height);
                        }
                    }
                    return snapSize(width) + snappedLeftInset() + snappedRightInset();
                }

                @Override
                protected double computePrefHeight(double width) {
                    double height = 0.0F;
                    for (Node child : getChildren()) {
                        if (child instanceof TabHeaderContainer) {
                            height = Math.max(height, child.prefHeight(width));
                        }
                    }
                    return snapSize(height) + snappedTopInset() + snappedBottomInset();
                }

                @Override
                protected void layoutChildren() {
                    if (isTabsFitHeaderWidth()) {
                        updateScrollOffset(0.0);
                    } else {
                        if (!removedTabsHeaders.isEmpty()) {
                            double offset = 0;
                            double w = header.getWidth() - snapSize(rightControlButton.prefWidth(-1)) - snapSize(leftControlButton.prefWidth(-1)) - snappedLeftInset() - SPACER;
                            Iterator<Node> itr = getChildren().iterator();
                            while (itr.hasNext()) {
                                Node temp = itr.next();
                                if (temp instanceof TabHeaderContainer) {
                                    TabHeaderContainer tabHeaderContainer = (TabHeaderContainer) temp;
                                    double containerPrefWidth = snapSize(tabHeaderContainer.prefWidth(-1));
                                    // if tab has been removed
                                    if (removedTabsHeaders.contains(tabHeaderContainer)) {
                                        if (offset < w) {
                                            isSelectingTab = true;
                                        }
                                        itr.remove();
                                        removedTabsHeaders.remove(tabHeaderContainer);
                                        if (removedTabsHeaders.isEmpty()) {
                                            break;
                                        }
                                    }
                                    offset += containerPrefWidth;
                                }
                            }
                        }
                    }

                    if (isSelectingTab) {
                        // make sure the selected tab is visible
                        animateSelectionLine();
                        isSelectingTab = false;
                    } else {
                        // validate scroll offset
                        updateScrollOffset(scrollOffset);
                    }

                    final double tabBackgroundHeight = snapSize(prefHeight(-1));
                    final Side side = getSkinnable().getSide();
                    double tabStartX = (side == Side.LEFT || side == Side.BOTTOM) ? snapSize(getWidth()) - scrollOffset : scrollOffset;
                    updateHeaderContainerClip();
                    for (Node node : getChildren()) {
                        if (node instanceof TabHeaderContainer) {
                            TabHeaderContainer tabHeaderContainer = (TabHeaderContainer) node;
                            double tabHeaderPrefWidth = snapSize(tabHeaderContainer.prefWidth(-1));
                            double tabHeaderPrefHeight = snapSize(tabHeaderContainer.prefHeight(-1));
                            tabHeaderContainer.resize(tabHeaderPrefWidth, tabHeaderPrefHeight);

                            double tabStartY = side == Side.BOTTOM ? 0 : tabBackgroundHeight - tabHeaderPrefHeight - snappedBottomInset();
                            if (side == Side.LEFT || side == Side.BOTTOM) {
                                // build from the right
                                tabStartX -= tabHeaderPrefWidth;
                                tabHeaderContainer.relocate(tabStartX, tabStartY);
                            } else {
                                // build from the left
                                tabHeaderContainer.relocate(tabStartX, tabStartY);
                                tabStartX += tabHeaderPrefWidth;
                            }
                        }
                    }
                    selectedTabLine.resizeRelocate((side == Side.LEFT || side == Side.BOTTOM) ? snapSize(headersRegion.getWidth()) : 0, tabBackgroundHeight - selectedTabLine.prefHeight(-1), snapSize(selectedTabLine.prefWidth(-1)), snapSize(selectedTabLine.prefHeight(-1)));
                }
            };

            headersRegion.getStyleClass().setAll("headers-region");
            headersRegion.setCache(true);
            headersRegion.setClip(clip);

            headerBackground = new StackPane();
            headerBackground.setBackground(new Background(new BackgroundFill(defaultColor, CornerRadii.EMPTY, Insets.EMPTY)));
            headerBackground.getStyleClass().setAll("tab-header-background");
            selectedTabLine = new StackPane();
            scale = new Scale(1, 1, 0, 0);
            rotate = new Rotate(0, 0, 1);
            rotate.pivotYProperty().bind(selectedTabLine.heightProperty().divide(2));

            selectedTabLine.getTransforms().addAll(scale, rotate);
            selectedTabLine.setCache(true);
            selectedTabLine.getStyleClass().add("tab-selected-line");
            selectedTabLine.setPrefHeight(2);
            selectedTabLine.setPrefWidth(1);
            selectedTabLine.setBackground(new Background(new BackgroundFill(ripplerColor, CornerRadii.EMPTY, Insets.EMPTY)));
            headersRegion.getChildren().add(selectedTabLine);

            rightControlButton = new HeaderControl(ArrowPosition.RIGHT);
            leftControlButton = new HeaderControl(ArrowPosition.LEFT);
            rightControlButton.setVisible(false);
            leftControlButton.setVisible(false);
            rightControlButton.inner.prefHeightProperty().bind(headersRegion.heightProperty());
            leftControlButton.inner.prefHeightProperty().bind(headersRegion.heightProperty());

            getChildren().addAll(headerBackground, headersRegion, leftControlButton, rightControlButton);

            int i = 0;
            for (Tab tab : getSkinnable().getTabs()) {
                addTab(tab, i++, true);
            }

            // support for mouse scroll of header area
            addEventHandler(ScrollEvent.SCROLL, (ScrollEvent e) -> updateScrollOffset(scrollOffset + e.getDeltaY() * (isHorizontal() ? -1 : 1)));
        }

        private void updateDirection() {
            final Side side = getSkinnable().getSide();
            direction = (side == Side.BOTTOM || side == Side.LEFT) ? -1 : 1;
        }

        private void updateHeaderContainerClip() {
            final double clipOffset = getClipOffset();
            final Side side = getSkinnable().getSide();
            double controlPrefWidth = 2 * snapSize(rightControlButton.prefWidth(-1));
            // Add the spacer if the control buttons are shown
//            controlPrefWidth = controlPrefWidth > 0 ? controlPrefWidth + SPACER : controlPrefWidth;

            measureClosingTabs = true;
            final double headersPrefWidth = snapSize(headersRegion.prefWidth(-1));
            final double headersPrefHeight = snapSize(headersRegion.prefHeight(-1));
            measureClosingTabs = false;

            final double maxWidth = snapSize(getWidth()) - controlPrefWidth - clipOffset;
            final double clipWidth = headersPrefWidth < maxWidth ? headersPrefWidth : maxWidth;
            final double clipHeight = headersPrefHeight;

            clip.setX((side == Side.LEFT || side == Side.BOTTOM) && headersPrefWidth >= maxWidth ? headersPrefWidth - maxWidth : 0);
            clip.setY(0);
            clip.setWidth(clipWidth);
            clip.setHeight(clipHeight);
        }

        private double getClipOffset() {
            return isHorizontal() ? snappedLeftInset() : snappedRightInset();
        }

        private void addTab(Tab tab, int addToIndex, boolean visible) {
            TabHeaderContainer tabHeaderContainer = new TabHeaderContainer(tab);
            tabHeaderContainer.setVisible(visible);
            headersRegion.getChildren().add(addToIndex, tabHeaderContainer);
        }

        private List<TabHeaderContainer> removedTabsHeaders = new ArrayList<>();

        private void removeTab(Tab tab) {
            TabHeaderContainer tabHeaderContainer = getTabHeaderContainer(tab);
            if (tabHeaderContainer != null) {
                if (isTabsFitHeaderWidth()) {
                    headersRegion.getChildren().remove(tabHeaderContainer);
                } else {
                    // we need to keep track of the removed tab headers
                    // to compute scroll offset of the header
                    removedTabsHeaders.add(tabHeaderContainer);
                    tabHeaderContainer.removeListeners(tab);
                }
            }
        }

        private TabHeaderContainer getTabHeaderContainer(Tab tab) {
            for (Node child : headersRegion.getChildren()) {
                if (child instanceof TabHeaderContainer) {
                    if (((TabHeaderContainer) child).tab.equals(tab)) {
                        return (TabHeaderContainer) child;
                    }
                }
            }
            return null;
        }

        private boolean isTabsFitHeaderWidth() {
            double headerPrefWidth = snapSize(headersRegion.prefWidth(-1));
            double rightControlWidth = 2 * snapSize(rightControlButton.prefWidth(-1));
            double visibleWidth = headerPrefWidth + rightControlWidth + snappedLeftInset() + SPACER;
            return visibleWidth < getWidth();
        }

        private void runTimeline(double newTransX, double newWidth) {
            newWidth = snapSizeX(newWidth);
            newTransX = snapPositionX(newTransX);

            double tempScaleX = 0;
            double tempWidth = 0;
            final double lineWidth = snapSizeX(selectedTabLine.prefWidth(-1));

            if (isAnimating()) {
                timeline.stop();
                tempScaleX = scale.getX();
                if (rotate.getAngle() != 0) {
                    rotate.setAngle(0);
                    tempWidth = snapSizeX(tempScaleX * lineWidth);
                    selectedTabLine.setTranslateX(snapPositionX(selectedTabLine.getTranslateX() - tempWidth));
                }
            }

            final double oldScaleX = scale.getX();
            final double oldWidth = snapSizeX(lineWidth * oldScaleX);
            final double oldTransX = snapPositionX(selectedTabLine.getTranslateX());

            final double newScaleX = newWidth / lineWidth;

            selectedTabLineOffset = newTransX;
            newTransX = snapPositionX(newTransX + offsetStart * direction);

            final double transDiff = newTransX - oldTransX;

            double midScaleX = tempScaleX != 0 ? tempScaleX : snapSizeX(((Math.abs(transDiff) / translateScaleFactor + oldWidth)) / lineWidth);

            if (transDiff < 0) {
                selectedTabLine.setTranslateX(snapPositionX(selectedTabLine.getTranslateX() + oldWidth));
                newTransX = snapPositionX(newTransX + newWidth);
                rotate.setAngle(180);
            }

            timeline = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(selectedTabLine.translateXProperty(), selectedTabLine.getTranslateX(), Interpolator.EASE_BOTH)), new KeyFrame(Duration.seconds(0.12), new KeyValue(scale.xProperty(), midScaleX, Interpolator.EASE_BOTH)), new KeyFrame(Duration.seconds(0.24), new KeyValue(scale.xProperty(), newScaleX, Interpolator.EASE_BOTH), new KeyValue(selectedTabLine.translateXProperty(), newTransX, Interpolator.EASE_BOTH)));

            timeline.setOnFinished(finish -> {
                if (rotate.getAngle() != 0) {
                    rotate.setAngle(0);
                    double finalX = snapPositionX(selectedTabLine.getTranslateX() - (lineWidth * newScaleX));
                    selectedTabLine.setTranslateX(finalX);
                }
            });
            timeline.play();
        }

        private boolean isAnimating() {
            return timeline != null && timeline.getStatus() == Animation.Status.RUNNING;
        }

        public void updateScrollOffset(double newOffset) {
            double tabPaneWidth = snapSize(isHorizontal() ? getSkinnable().getWidth() : getSkinnable().getHeight());
            double controlTabWidth = 2 * snapSize(rightControlButton.getWidth());
            double visibleWidth = tabPaneWidth - controlTabWidth - snappedLeftInset() - SPACER;

            // compute all tabs headers width
            double offset = 0.0;
            for (Node node : headersRegion.getChildren()) {
                if (node instanceof TabHeaderContainer) {
                    double tabHeaderPrefWidth = snapSize(node.prefWidth(-1));
                    offset += tabHeaderPrefWidth;
                }
            }

            double actualOffset = newOffset;
            if ((visibleWidth - newOffset) > offset && newOffset < 0) {
                actualOffset = visibleWidth - offset;
            } else if (newOffset > 0) {
                actualOffset = 0;
            }

            if (actualOffset != scrollOffset) {
                scrollOffset = actualOffset;
                headersRegion.requestLayout();
                if (!isAnimating()) {
                    selectedTabLine.setTranslateX(selectedTabLineOffset + scrollOffset * direction);
                }
            }
        }

        @Override
        protected double computePrefWidth(double height) {
            final double padding = isHorizontal() ? 2 * snappedLeftInset() + snappedRightInset() : 2 * snappedTopInset() + snappedBottomInset();
            return snapSize(headersRegion.prefWidth(height)) + 2 * rightControlButton.prefWidth(height) + padding + SPACER;
        }

        @Override
        protected double computePrefHeight(double width) {
            final double padding = isHorizontal() ? snappedTopInset() + snappedBottomInset() : snappedLeftInset() + snappedRightInset();
            return snapSize(headersRegion.prefHeight(-1)) + padding;
        }

        @Override
        public double getBaselineOffset() {
            return getSkinnable().getSide() == Side.TOP ? headersRegion.getBaselineOffset() + snappedTopInset() : 0;
        }

        @Override
        protected void layoutChildren() {
            final double leftInset = snappedLeftInset();
            final double rightInset = snappedRightInset();
            final double topInset = snappedTopInset();
            final double bottomInset = snappedBottomInset();
            final double padding = isHorizontal() ? leftInset + rightInset : topInset + bottomInset;
            final double w = snapSize(getWidth()) - padding;
            final double h = snapSize(getHeight()) - padding;
            final double tabBackgroundHeight = snapSize(prefHeight(-1));
            final double headersPrefWidth = snapSize(headersRegion.prefWidth(-1));
            final double headersPrefHeight = snapSize(headersRegion.prefHeight(-1));

            rightControlButton.showTabsMenu(!isTabsFitHeaderWidth());
            leftControlButton.showTabsMenu(!isTabsFitHeaderWidth());

            updateHeaderContainerClip();
            headersRegion.requestLayout();

            // layout left/right controls buttons
            final double btnWidth = snapSize(rightControlButton.prefWidth(-1));
            final double btnHeight = rightControlButton.prefHeight(btnWidth);
            rightControlButton.resize(btnWidth, btnHeight);
            leftControlButton.resize(btnWidth, btnHeight);

            // layout tabs
            headersRegion.resize(headersPrefWidth, headersPrefHeight);
            headerBackground.resize(snapSize(getWidth()), snapSize(getHeight()));

            final Side side = getSkinnable().getSide();
            double startX = 0;
            double startY = 0;
            double controlStartX = 0;
            double controlStartY = 0;
            switch (side) {
                case TOP:
                    startX = leftInset;
                    startY = tabBackgroundHeight - headersPrefHeight - bottomInset;
                    controlStartX = w - btnWidth + leftInset;
                    controlStartY = snapSize(getHeight()) - btnHeight - bottomInset;
                    break;
                case BOTTOM:
                    startX = snapSize(getWidth()) - headersPrefWidth - leftInset;
                    startY = tabBackgroundHeight - headersPrefHeight - topInset;
                    controlStartX = rightInset;
                    controlStartY = snapSize(getHeight()) - btnHeight - topInset;
                    break;
                case LEFT:
                    startX = snapSize(getWidth()) - headersPrefWidth - topInset;
                    startY = tabBackgroundHeight - headersPrefHeight - rightInset;
                    controlStartX = leftInset;
                    controlStartY = snapSize(getHeight()) - btnHeight - rightInset;
                    break;
                case RIGHT:
                    startX = topInset;
                    startY = tabBackgroundHeight - headersPrefHeight - leftInset;
                    controlStartX = w - btnWidth + topInset;
                    controlStartY = snapSize(getHeight()) - btnHeight - leftInset;
                    break;
            }

            if (headerBackground.isVisible()) {
                positionInArea(headerBackground, 0, 0, snapSize(getWidth()), snapSize(getHeight()), 0, HPos.CENTER, VPos.CENTER);
            }

            positionInArea(headersRegion, startX + btnWidth * ((side == Side.LEFT || side == Side.BOTTOM) ? -1 : 1), startY, w, h, 0, HPos.LEFT, VPos.CENTER);

            positionInArea(rightControlButton, controlStartX, controlStartY, btnWidth, btnHeight, 0, HPos.CENTER, VPos.CENTER);

            positionInArea(leftControlButton, (side == Side.LEFT || side == Side.BOTTOM) ? w - btnWidth : 0, controlStartY, btnWidth, btnHeight, 0, HPos.CENTER, VPos.CENTER);

            rightControlButton.setRotate((side == Side.LEFT || side == Side.BOTTOM) ? 180.0F : 0.0F);
            leftControlButton.setRotate((side == Side.LEFT || side == Side.BOTTOM) ? 180.0F : 0.0F);

            if (!initialized) {
                animateSelectionLine();
                initialized = true;
            }
        }

        private void animateSelectionLine() {
            double offset = 0.0;
            double selectedTabOffset = 0.0;
            double selectedTabWidth = 0.0;
            final Side side = getSkinnable().getSide();
            for (Node node : headersRegion.getChildren()) {
                if (node instanceof TabHeaderContainer) {
                    TabHeaderContainer tabHeader = (TabHeaderContainer) node;
                    double tabHeaderPrefWidth = snapSize(tabHeader.prefWidth(-1));
                    if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                        selectedTabOffset = (side == Side.LEFT || side == Side.BOTTOM) ? -offset - tabHeaderPrefWidth : offset;
                        selectedTabWidth = tabHeaderPrefWidth;
                        break;
                    }
                    offset += tabHeaderPrefWidth;
                }
            }
            // animate the tab selection
            runTimeline(selectedTabOffset, selectedTabWidth);
        }
    }

    /**************************************************************************
     *                                                                          *
     * TabHeaderContainer: each tab Container                                  *
     *                                                                          *
     **************************************************************************/

    protected class TabHeaderContainer extends StackPane {

        private Tab tab = null;
        private Label tabText;
        private Tooltip oldTooltip;
        private Tooltip tooltip;
        private BorderPane inner;
        private JFXRippler rippler;
        private boolean systemChange = false;
        private boolean isClosing = false;

        private final MultiplePropertyChangeListenerHandler listener = new MultiplePropertyChangeListenerHandler(param -> {
            handlePropertyChanged(param);
            return null;
        });

        private final ListChangeListener<String> styleClassListener = (Change<? extends String> change) -> getStyleClass().setAll(tab.getStyleClass());

        private final WeakListChangeListener<String> weakStyleClassListener = new WeakListChangeListener<>(styleClassListener);

        public TabHeaderContainer(final Tab tab) {
            this.tab = tab;

            getStyleClass().setAll(tab.getStyleClass());
            setId(tab.getId());
            setStyle(tab.getStyle());

            tabText = new Label(tab.getText(), tab.getGraphic());
            tabText.setFont(Font.font("", FontWeight.BOLD, 16));
            tabText.setPadding(new Insets(5, 10, 5, 10));
            tabText.getStyleClass().setAll("tab-label");

            inner = new BorderPane();
            inner.setCenter(tabText);
            inner.getStyleClass().add("tab-container");
            inner.setRotate(getSkinnable().getSide().equals(Side.BOTTOM) ? 180.0F : 0.0F);

            rippler = new JFXRippler(inner, RipplerPos.FRONT);
            rippler.setRipplerFill(ripplerColor);
            getChildren().addAll(rippler);

            tooltip = tab.getTooltip();
            if (tooltip != null) {
                Tooltip.install(this, tooltip);
                oldTooltip = tooltip;
            }

            if (tab.isSelected()) {
                tabText.setTextFill(selectedTabText);
            } else {
                tabText.setTextFill(tempLabelColor.deriveColor(0, 0, 0.9, 1));
            }


            tabText.textFillProperty().addListener((o, oldVal, newVal) -> {
                if (!systemChange) {
                    tempLabelColor = (Color) newVal;
                }
            });

            tab.selectedProperty().addListener((o, oldVal, newVal) -> {
                systemChange = true;
                if (newVal) {
                    tabText.setTextFill(tempLabelColor);
                } else {
                    tabText.setTextFill(tempLabelColor.deriveColor(0, 0, 0.9, 1));
                }
                systemChange = false;
            });


            listener.registerChangeListener(tab.selectedProperty(), "SELECTED");
            listener.registerChangeListener(tab.textProperty(), "TEXT");
            listener.registerChangeListener(tab.graphicProperty(), "GRAPHIC");
            listener.registerChangeListener(tab.tooltipProperty(), "TOOLTIP");
            listener.registerChangeListener(tab.disableProperty(), "DISABLE");
            listener.registerChangeListener(tab.styleProperty(), "STYLE");
            listener.registerChangeListener(getSkinnable().tabMinWidthProperty(), "TAB_MIN_WIDTH");
            listener.registerChangeListener(getSkinnable().tabMaxWidthProperty(), "TAB_MAX_WIDTH");
            listener.registerChangeListener(getSkinnable().tabMinHeightProperty(), "TAB_MIN_HEIGHT");
            listener.registerChangeListener(getSkinnable().tabMaxHeightProperty(), "TAB_MAX_HEIGHT");
            listener.registerChangeListener(getSkinnable().sideProperty(), "SIDE");
            tab.getStyleClass().addListener(weakStyleClassListener);

            getProperties().put(Tab.class, tab);

            setOnMouseClicked((event) -> {
                if (tab.isDisable() || !event.isStillSincePress()) {
                    return;
                }
                if (event.getButton() == MouseButton.PRIMARY) {
                    setOpacity(1);
                    TabPane tabPane = tab.getTabPane();
                    if (tabPane != null) {
                        tabPane.getSelectionModel().select(tab);
                    }
                }
            });

            addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                ContextMenu contextMenu = tab.getContextMenu();
                if (contextMenu != null) {
                    contextMenu.show(tabText, event.getScreenX(), event.getScreenY());
                    event.consume();
                }
            });

            // initialize pseudo-class state
            pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, tab.isSelected());
            pseudoClassStateChanged(DISABLED_PSEUDOCLASS_STATE, tab.isDisable());
            final Side side = getSkinnable().getSide();
            pseudoClassStateChanged(TOP_PSEUDOCLASS_STATE, (side == Side.TOP));
            pseudoClassStateChanged(RIGHT_PSEUDOCLASS_STATE, (side == Side.RIGHT));
            pseudoClassStateChanged(BOTTOM_PSEUDOCLASS_STATE, (side == Side.BOTTOM));
            pseudoClassStateChanged(LEFT_PSEUDOCLASS_STATE, (side == Side.LEFT));
        }

        private void handlePropertyChanged(final String p) {
            if ("SELECTED".equals(p)) {
                pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, tab.isSelected());
                inner.requestLayout();
                requestLayout();
            } else if ("TEXT".equals(p)) {
                tabText.setText(tab.getText());
            } else if ("GRAPHIC".equals(p)) {
                tabText.setGraphic(tab.getGraphic());
            } else if ("TOOLTIP".equals(p)) {
                // install new Tooltip / uninstall the old one
                if (oldTooltip != null) {
                    Tooltip.uninstall(this, oldTooltip);
                }
                tooltip = tab.getTooltip();
                if (tooltip != null) {
                    Tooltip.install(this, tooltip);
                    oldTooltip = tooltip;
                }
            } else if ("DISABLE".equals(p)) {
                pseudoClassStateChanged(DISABLED_PSEUDOCLASS_STATE, tab.isDisable());
                inner.requestLayout();
                requestLayout();
            } else if ("STYLE".equals(p)) {
                setStyle(tab.getStyle());
            } else if ("TAB_MIN_WIDTH".equals(p)) {
                requestLayout();
                getSkinnable().requestLayout();
            } else if ("TAB_MAX_WIDTH".equals(p)) {
                requestLayout();
                getSkinnable().requestLayout();
            } else if ("TAB_MIN_HEIGHT".equals(p)) {
                requestLayout();
                getSkinnable().requestLayout();
            } else if ("TAB_MAX_HEIGHT".equals(p)) {
                requestLayout();
                getSkinnable().requestLayout();
            } else if ("SIDE".equals(p)) {
                final Side side = getSkinnable().getSide();
                pseudoClassStateChanged(TOP_PSEUDOCLASS_STATE, (side == Side.TOP));
                pseudoClassStateChanged(RIGHT_PSEUDOCLASS_STATE, (side == Side.RIGHT));
                pseudoClassStateChanged(BOTTOM_PSEUDOCLASS_STATE, (side == Side.BOTTOM));
                pseudoClassStateChanged(LEFT_PSEUDOCLASS_STATE, (side == Side.LEFT));
                inner.setRotate(side == Side.BOTTOM ? 180.0F : 0.0F);
            }
        }

        private void removeListeners(Tab tab) {
            listener.dispose();
            inner.getChildren().clear();
            getChildren().clear();
        }

        @Override
        protected double computePrefWidth(double height) {
            double minWidth = snapSize(getSkinnable().getTabMinWidth());
            double maxWidth = snapSize(getSkinnable().getTabMaxWidth());
            double paddingRight = snappedRightInset();
            double paddingLeft = snappedLeftInset();
            double tmpPrefWidth = snapSize(tabText.prefWidth(-1));

            if (tmpPrefWidth > maxWidth) {
                tmpPrefWidth = maxWidth;
            } else if (tmpPrefWidth < minWidth) {
                tmpPrefWidth = minWidth;
            }
            tmpPrefWidth += paddingRight + paddingLeft;
            return tmpPrefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            double minHeight = snapSize(getSkinnable().getTabMinHeight());
            double maxHeight = snapSize(getSkinnable().getTabMaxHeight());
            double paddingTop = snappedTopInset();
            double paddingBottom = snappedBottomInset();
            double tmpPrefHeight = snapSize(tabText.prefHeight(width));

            if (tmpPrefHeight > maxHeight) {
                tmpPrefHeight = maxHeight;
            } else if (tmpPrefHeight < minHeight) {
                tmpPrefHeight = minHeight;
            }
            tmpPrefHeight += paddingTop + paddingBottom;
            return tmpPrefHeight;
        }

        @Override
        protected void layoutChildren() {
            double w = snapSize(getWidth()) - snappedRightInset() - snappedLeftInset();
            rippler.resize(w, snapSize(getHeight()) - snappedTopInset() - snappedBottomInset());
            rippler.relocate(snappedLeftInset(), snappedTopInset());
        }

        @Override
        protected void setWidth(double value) {
            super.setWidth(value);
        }

        @Override
        protected void setHeight(double value) {
            super.setHeight(value);
        }
    }

    private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass DISABLED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("disabled");
    private static final PseudoClass TOP_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("top");
    private static final PseudoClass BOTTOM_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("bottom");
    private static final PseudoClass LEFT_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("right");

    /**************************************************************************
     *                                                                          *
     * TabContentHolder: each tab content container                              *
     *                                                                          *
     **************************************************************************/
    protected static class TabContentHolder extends StackPane {
        private Tab tab;
        private InvalidationListener tabContentListener = valueModel -> updateContent();
        private InvalidationListener tabSelectedListener = valueModel -> setVisible(tab.isSelected());
        private WeakInvalidationListener weakTabContentListener = new WeakInvalidationListener(tabContentListener);
        private WeakInvalidationListener weakTabSelectedListener = new WeakInvalidationListener(tabSelectedListener);

        public TabContentHolder(Tab tab) {
            this.tab = tab;
            getStyleClass().setAll("tab-content-area");
            setManaged(false);
            updateContent();
            setVisible(tab.isSelected());
            tab.selectedProperty().addListener(weakTabSelectedListener);
            tab.contentProperty().addListener(weakTabContentListener);
        }

        private void updateContent() {
            Node newContent = tab.getContent();
            if (newContent == null) {
                getChildren().clear();
            } else {
                getChildren().setAll(newContent);
            }
        }

        private void removeListeners(Tab tab) {
            tab.selectedProperty().removeListener(weakTabSelectedListener);
            tab.contentProperty().removeListener(weakTabContentListener);
        }
    }

    private enum ArrowPosition {
        RIGHT, LEFT
    }

    /**************************************************************************
     *                                                                          *
     * HeaderControl: left/right controls to interact with HeaderContainer*
     *                                                                          *
     **************************************************************************/
    protected class HeaderControl extends StackPane {
        private StackPane inner;
        private boolean showControlButtons, isLeftArrow;
        private Timeline arrowAnimation;
        private SVGGlyph arrowButton;
        private SVGGlyph leftChevron = new SVGGlyph(0, "CHEVRON_LEFT", "M 742,-37 90,614 Q 53,651 53,704.5 53,758 90,795 l 652,651 q 37,37 90.5,37 53.5,0 90.5,-37 l 75,-75 q 37,-37 37,-90.5 0,-53.5 -37,-90.5 L 512,704 998,219 q 37,-38 37,-91 0,-53 -37,-90 L 923,-37 Q 886,-74 832.5,-74 779,-74 742,-37 z", Color.WHITE);
        private SVGGlyph rightChevron = new SVGGlyph(0, "CHEVRON_RIGHT", "m 1099,704 q 0,-52 -37,-91 L 410,-38 q -37,-37 -90,-37 -53,0 -90,37 l -76,75 q -37,39 -37,91 0,53 37,90 l 486,486 -486,485 q -37,39 -37,91 0,53 37,90 l 76,75 q 36,38 90,38 54,0 90,-38 l 652,-651 q 37,-37 37,-90 z", Color.WHITE);

        public HeaderControl(ArrowPosition pos) {
            getStyleClass().setAll("control-buttons-tab");
            isLeftArrow = pos == ArrowPosition.LEFT;
            arrowButton = isLeftArrow ? leftChevron : rightChevron;
            arrowButton.setStyle("-fx-min-width:0.8em;-fx-max-width:0.8em;-fx-min-height:1.3em;-fx-max-height:1.3em;");
            arrowButton.getStyleClass().setAll("tab-down-button");
            arrowButton.setVisible(isControlButtonShown());
            arrowButton.setFill(selectedTabText);

            DoubleProperty offsetProperty = new SimpleDoubleProperty(0);
            offsetProperty.addListener((o, oldVal, newVal) -> header.updateScrollOffset(newVal.doubleValue()));

            StackPane container = new StackPane(arrowButton);
            container.getStyleClass().add("container");
            container.setPadding(new Insets(7));
            container.setCursor(Cursor.HAND);

            container.setOnMousePressed(press -> {
                offsetProperty.set(header.scrollOffset);
                double offset = isLeftArrow ? header.scrollOffset + header.headersRegion.getWidth() : header.scrollOffset - header.headersRegion.getWidth();
                arrowAnimation = new Timeline(new KeyFrame(Duration.seconds(1), new KeyValue(offsetProperty, offset, Interpolator.LINEAR)));
                arrowAnimation.play();
            });
            container.setOnMouseReleased(release -> arrowAnimation.stop());
            JFXRippler arrowRippler = new JFXRippler(container, RipplerMask.CIRCLE, RipplerPos.BACK);
            arrowRippler.ripplerFillProperty().bind(arrowButton.fillProperty());
            StackPane.setMargin(arrowButton, new Insets(0, 0, 0, isLeftArrow ? -4 : 4));

            inner = new StackPane() {
                @Override
                protected double computePrefWidth(double height) {
                    double preferWidth = 0.0d;
                    double maxArrowWidth = !isControlButtonShown() ? 0 : snapSize(arrowRippler.prefWidth(getHeight()));
                    preferWidth += isControlButtonShown() ? maxArrowWidth : 0;
                    preferWidth += (preferWidth > 0) ? snappedLeftInset() + snappedRightInset() : 0;
                    return preferWidth;
                }

                @Override
                protected double computePrefHeight(double width) {
                    double prefHeight = 0.0d;
                    prefHeight = isControlButtonShown() ? Math.max(prefHeight, snapSize(arrowRippler.prefHeight(width))) : 0;
                    prefHeight += prefHeight > 0 ? snappedTopInset() + snappedBottomInset() : 0;
                    return prefHeight;
                }

                @Override
                protected void layoutChildren() {
                    if (isControlButtonShown()) {
                        double x = 0;
                        double y = snappedTopInset();
                        double width = snapSize(getWidth()) - x + snappedLeftInset();
                        double height = snapSize(getHeight()) - y + snappedBottomInset();
                        positionArrow(arrowRippler, x, y, width, height);
                    }
                }

                private void positionArrow(JFXRippler rippler, double x, double y, double width, double height) {
                    rippler.resize(width, height);
                    positionInArea(rippler, x, y, width, height, 0, HPos.CENTER, VPos.CENTER);
                }
            };

            arrowRippler.setPadding(new Insets(0, 5, 0, 5));
            inner.getChildren().add(arrowRippler);
            StackPane.setMargin(arrowRippler, new Insets(0, 4, 0, 4));
            getChildren().add(inner);

            showControlButtons = false;
            if (isControlButtonShown()) {
                showControlButtons = true;
                requestLayout();
            }
        }

        private boolean showTabsHeaderControls = false;

        private void showTabsMenu(boolean value) {
            final boolean wasTabsMenuShowing = isControlButtonShown();
            this.showTabsHeaderControls = value;
            if (showTabsHeaderControls && !wasTabsMenuShowing) {
                arrowButton.setVisible(true);
                showControlButtons = true;
                inner.requestLayout();
                header.requestLayout();
            } else if (!showTabsHeaderControls && wasTabsMenuShowing) {
                // hide control button
                if (isControlButtonShown()) {
                    showControlButtons = true;
                } else {
                    setVisible(false);
                }
                requestLayout();
            }
        }

        private boolean isControlButtonShown() {
            return showTabsHeaderControls;
        }

        @Override
        protected double computePrefWidth(double height) {
            double prefWidth = snapSize(inner.prefWidth(height));
            if (prefWidth > 0) {
                prefWidth += snappedLeftInset() + snappedRightInset();
            }
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return Math.max(getSkinnable().getTabMinHeight(), snapSize(inner.prefHeight(width))) + snappedTopInset() + snappedBottomInset();
        }

        @Override
        protected void layoutChildren() {
            double x = snappedLeftInset();
            double y = snappedTopInset();
            double width = snapSize(getWidth()) - x + snappedRightInset();
            double height = snapSize(getHeight()) - y + snappedBottomInset();
            if (showControlButtons) {
                setVisible(true);
                showControlButtons = false;
            }
            inner.resize(width, height);
            positionInArea(inner, x, y, width, height, 0, HPos.CENTER, VPos.BOTTOM);
        }
    }

}

