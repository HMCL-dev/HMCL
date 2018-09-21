/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.svg.SVGGlyph;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.Lang;

public class DecoratorSkin extends SkinBase<DecoratorControl> {
    private static final SVGGlyph minus = Lang.apply(new SVGGlyph(0, "MINUS", "M804.571 420.571v109.714q0 22.857-16 38.857t-38.857 16h-694.857q-22.857 0-38.857-16t-16-38.857v-109.714q0-22.857 16-38.857t38.857-16h694.857q22.857 0 38.857 16t16 38.857z", Color.WHITE),
        glyph -> { glyph.setSize(12, 2); glyph.setTranslateY(4); });

    private final BorderPane titleContainer;
    private final StackPane contentPlaceHolder;
    private final JFXButton refreshNavButton;
    private final JFXButton closeNavButton;
    private final HBox navLeft;
    private final Stage primaryStage;

    private double xOffset, yOffset, newX, newY, initX, initY;
    private boolean allowMove, isDragging;
    private BoundingBox originalBox, maximizedBox;

    /**
     * Constructor for all SkinBase instances.
     *
     * @param control The control for which this Skin should attach to.
     */
    public DecoratorSkin(DecoratorControl control) {
        super(control);

        primaryStage = control.getPrimaryStage();

        minus.fillProperty().bind(Theme.foregroundFillBinding());

        DecoratorControl skinnable = getSkinnable();

        BorderPane root = new BorderPane();
        root.getStyleClass().setAll("jfx-decorator", "resize-border");
        root.setMaxHeight(519);
        root.setMaxWidth(800);

        StackPane drawerWrapper = new StackPane();
        skinnable.setDrawerWrapper(drawerWrapper);
        drawerWrapper.getStyleClass().setAll("jfx-decorator-drawer");
        drawerWrapper.backgroundProperty().bind(skinnable.backgroundProperty());
        FXUtils.setOverflowHidden(drawerWrapper, true);
        {
            BorderPane drawer = new BorderPane();

            {
                BorderPane leftRootPane = new BorderPane();
                FXUtils.setLimitWidth(leftRootPane, 200);
                leftRootPane.getStyleClass().setAll("jfx-decorator-content-container");

                StackPane drawerContainer = new StackPane();
                drawerContainer.getStyleClass().setAll("gray-background");
                Bindings.bindContent(drawerContainer.getChildren(), skinnable.drawerProperty());
                leftRootPane.setCenter(drawerContainer);

                Rectangle separator = new Rectangle();
                separator.heightProperty().bind(drawer.heightProperty());
                separator.setWidth(1);
                separator.setFill(Color.GRAY);

                leftRootPane.setRight(separator);

                drawer.setLeft(leftRootPane);
            }

            {
                contentPlaceHolder = new StackPane();
                contentPlaceHolder.getStyleClass().setAll("jfx-decorator-content-container");
                contentPlaceHolder.backgroundProperty().bind(skinnable.contentBackgroundProperty());
                FXUtils.setOverflowHidden(contentPlaceHolder, true);
                Bindings.bindContent(contentPlaceHolder.getChildren(), skinnable.contentProperty());

                drawer.setCenter(contentPlaceHolder);
            }

            drawerWrapper.getChildren().add(drawer);
        }

        {
            StackPane container = new StackPane();
            Bindings.bindContent(container.getChildren(), skinnable.containerProperty());
            ListChangeListener<Node> listener = new ListChangeListener<Node>() {
                @Override
                public void onChanged(Change<? extends Node> c) {
                    if (skinnable.getContainer().isEmpty()) {
                        container.setMouseTransparent(true);
                        container.setVisible(false);
                    } else {
                        container.setMouseTransparent(false);
                        container.setVisible(true);
                    }
                }
            };
            skinnable.containerProperty().addListener(listener);
            listener.onChanged(null);

            drawerWrapper.getChildren().add(container);
        }

        root.setCenter(drawerWrapper);

        titleContainer = new BorderPane();
        titleContainer.setOnMouseReleased(this::onMouseReleased);
        titleContainer.setOnMouseDragged(this::onMouseDragged);
        titleContainer.setOnMouseMoved(this::onMouseMoved);
        titleContainer.setPickOnBounds(false);
        titleContainer.setMinHeight(40);
        titleContainer.getStyleClass().setAll("jfx-tool-bar");
        titleContainer.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> allowMove = true);
        titleContainer.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!isDragging) allowMove = false;
        });

        Rectangle rectangle = new Rectangle(0, 0, 0, 0);
        rectangle.widthProperty().bind(titleContainer.widthProperty());
        rectangle.heightProperty().bind(Bindings.createDoubleBinding(() -> titleContainer.getHeight() + 100, titleContainer.heightProperty()));
        titleContainer.setClip(rectangle);
        {
            BorderPane titleWrapper = new BorderPane();
            FXUtils.setLimitWidth(titleWrapper, 200);
            {
                Label lblTitle = new Label();
                BorderPane.setMargin(lblTitle, new Insets(0, 0, 0, 3));
                lblTitle.setStyle("-fx-background-color: transparent; -fx-text-fill: -fx-base-text-fill; -fx-font-size: 15px;");
                lblTitle.setMouseTransparent(true);
                lblTitle.textProperty().bind(skinnable.titleProperty());
                BorderPane.setAlignment(lblTitle, Pos.CENTER);
                titleWrapper.setCenter(lblTitle);

                Rectangle separator = new Rectangle();
                separator.heightProperty().bind(titleWrapper.heightProperty());
                separator.setWidth(1);
                separator.setFill(Color.GRAY);
                titleWrapper.setRight(separator);
            }
            titleContainer.setLeft(titleWrapper);

            BorderPane navBar = new BorderPane();
            {
                navLeft = new HBox();
                navLeft.setAlignment(Pos.CENTER_LEFT);
                navLeft.setPadding(new Insets(0, 5, 0, 5));
                {
                    JFXButton backNavButton = new JFXButton();
                    backNavButton.setGraphic(SVG.back(Theme.foregroundFillBinding(), -1, -1));
                    backNavButton.getStyleClass().setAll("jfx-decorator-button");
                    backNavButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                    backNavButton.onActionProperty().bind(skinnable.onBackNavButtonActionProperty());
                    backNavButton.visibleProperty().bind(skinnable.canBackProperty());

                    closeNavButton = new JFXButton();
                    closeNavButton.setGraphic(SVG.close(Theme.foregroundFillBinding(), -1, -1));
                    closeNavButton.getStyleClass().setAll("jfx-decorator-button");
                    closeNavButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                    closeNavButton.onActionProperty().bind(skinnable.onCloseNavButtonActionProperty());

                    navLeft.getChildren().setAll(backNavButton);

                    skinnable.canCloseProperty().addListener((a, b, newValue) -> {
                        if (newValue) navLeft.getChildren().setAll(backNavButton, closeNavButton);
                        else navLeft.getChildren().setAll(backNavButton);
                    });

                    skinnable.showCloseAsHomeProperty().addListener((a, b, newValue) -> {
                        if (newValue)
                            closeNavButton.setGraphic(SVG.home(Theme.foregroundFillBinding(), -1, -1));
                        else
                            closeNavButton.setGraphic(SVG.close(Theme.foregroundFillBinding(), -1, -1));
                    });
                }
                navBar.setLeft(navLeft);

                VBox navCenter = new VBox();
                navCenter.setAlignment(Pos.CENTER_LEFT);
                Label titleLabel = new Label();
                titleLabel.getStyleClass().setAll("jfx-decorator-title");
                titleLabel.textProperty().bind(skinnable.drawerTitleProperty());
                navCenter.getChildren().setAll(titleLabel);
                navBar.setCenter(navCenter);

                HBox navRight = new HBox();
                navRight.setAlignment(Pos.CENTER_RIGHT);
                refreshNavButton = new JFXButton();
                refreshNavButton.setGraphic(SVG.refresh(Theme.foregroundFillBinding(), -1, -1));
                refreshNavButton.getStyleClass().setAll("jfx-decorator-button");
                refreshNavButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                refreshNavButton.onActionProperty().bind(skinnable.onRefreshNavButtonActionProperty());
                refreshNavButton.visibleProperty().bind(skinnable.canRefreshProperty());
                navRight.getChildren().setAll(refreshNavButton);
                navBar.setRight(navRight);
            }
            titleContainer.setCenter(navBar);

            HBox buttonsContainer = new HBox();
            buttonsContainer.setStyle("-fx-background-color: transparent;");
            buttonsContainer.setAlignment(Pos.CENTER_RIGHT);
            buttonsContainer.setPadding(new Insets(4));
            {
                Rectangle separator = new Rectangle();
                separator.visibleProperty().bind(refreshNavButton.visibleProperty());
                separator.heightProperty().bind(navBar.heightProperty());
                separator.setFill(Color.GRAY);

                JFXButton btnMin = new JFXButton();
                StackPane pane = new StackPane(minus);
                pane.setAlignment(Pos.CENTER);
                btnMin.setGraphic(pane);
                btnMin.getStyleClass().setAll("jfx-decorator-button");
                btnMin.setOnAction(e -> skinnable.minimize());

                JFXButton btnClose = new JFXButton();
                btnClose.setGraphic(SVG.close(Theme.foregroundFillBinding(), -1, -1));
                btnClose.getStyleClass().setAll("jfx-decorator-button");
                btnClose.setOnAction(e -> skinnable.close());

                buttonsContainer.getChildren().setAll(separator, btnMin, btnClose);
            }
            titleContainer.setRight(buttonsContainer);
        }
        root.setTop(titleContainer);

        getChildren().setAll(root);

        getSkinnable().closeNavButtonVisibleProperty().addListener((a, b, newValue) -> {
            if (newValue) navLeft.getChildren().add(closeNavButton);
            else navLeft.getChildren().remove(closeNavButton);
        });
    }

    private void updateInitMouseValues(MouseEvent mouseEvent) {
        initX = mouseEvent.getScreenX();
        initY = mouseEvent.getScreenY();
        xOffset = mouseEvent.getSceneX();
        yOffset = mouseEvent.getSceneY();
    }

    private boolean isRightEdge(double x, double y, Bounds boundsInParent) {
        return x < getSkinnable().getWidth() && x > getSkinnable().getWidth() - contentPlaceHolder.snappedLeftInset();
    }

    private boolean isTopEdge(double x, double y, Bounds boundsInParent) {
        return y >= 0 && y < contentPlaceHolder.snappedLeftInset();
    }

    private boolean isBottomEdge(double x, double y, Bounds boundsInParent) {
        return y < getSkinnable().getHeight() && y > getSkinnable().getHeight() - contentPlaceHolder.snappedLeftInset();
    }

    private boolean isLeftEdge(double x, double y, Bounds boundsInParent) {
        return x >= 0 && x < contentPlaceHolder.snappedLeftInset();
    }

    private boolean setStageWidth(double width) {
        if (width >= primaryStage.getMinWidth() && width >= titleContainer.getMinWidth()) {
            primaryStage.setWidth(width);
            initX = newX;
            return true;
        } else {
            if (width >= primaryStage.getMinWidth() && width <= titleContainer.getMinWidth())
                primaryStage.setWidth(titleContainer.getMinWidth());

            return false;
        }
    }

    private boolean setStageHeight(double height) {
        if (height >= primaryStage.getMinHeight() && height >= titleContainer.getHeight()) {
            primaryStage.setHeight(height);
            initY = newY;
            return true;
        } else {
            if (height >= primaryStage.getMinHeight() && height <= titleContainer.getHeight())
                primaryStage.setHeight(titleContainer.getHeight());

            return false;
        }
    }

    // ====

    protected void onMouseMoved(MouseEvent mouseEvent) {
        if (!primaryStage.isFullScreen()) {
            if (!primaryStage.isResizable())
                updateInitMouseValues(mouseEvent);
            else {
                double x = mouseEvent.getX(), y = mouseEvent.getY();
                Bounds boundsInParent = getSkinnable().getBoundsInParent();
                if (getSkinnable().getBorder() != null && getSkinnable().getBorder().getStrokes().size() > 0) {
                    double borderWidth = contentPlaceHolder.snappedLeftInset();
                    if (this.isRightEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            getSkinnable().setCursor(Cursor.NE_RESIZE);
                        } else if (y > getSkinnable().getHeight() - borderWidth) {
                            getSkinnable().setCursor(Cursor.SE_RESIZE);
                        } else {
                            getSkinnable().setCursor(Cursor.E_RESIZE);
                        }
                    } else if (this.isLeftEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            getSkinnable().setCursor(Cursor.NW_RESIZE);
                        } else if (y > getSkinnable().getHeight() - borderWidth) {
                            getSkinnable().setCursor(Cursor.SW_RESIZE);
                        } else {
                            getSkinnable().setCursor(Cursor.W_RESIZE);
                        }
                    } else if (this.isTopEdge(x, y, boundsInParent)) {
                        getSkinnable().setCursor(Cursor.N_RESIZE);
                    } else if (this.isBottomEdge(x, y, boundsInParent)) {
                        getSkinnable().setCursor(Cursor.S_RESIZE);
                    } else {
                        getSkinnable().setCursor(Cursor.DEFAULT);
                    }

                    this.updateInitMouseValues(mouseEvent);
                }
            }
        } else {
            getSkinnable().setCursor(Cursor.DEFAULT);
        }
    }

    protected void onMouseReleased(MouseEvent mouseEvent) {
        isDragging = false;
    }

    protected void onMouseDragged(MouseEvent mouseEvent) {
        this.isDragging = true;
        if (mouseEvent.isPrimaryButtonDown() && (this.xOffset != -1.0 || this.yOffset != -1.0)) {
            if (!this.primaryStage.isFullScreen() && !mouseEvent.isStillSincePress()) {
                this.newX = mouseEvent.getScreenX();
                this.newY = mouseEvent.getScreenY();
                double deltaX = this.newX - this.initX;
                double deltaY = this.newY - this.initY;
                Cursor cursor = getSkinnable().getCursor();
                if (Cursor.E_RESIZE == cursor) {
                    this.setStageWidth(this.primaryStage.getWidth() + deltaX);
                    mouseEvent.consume();
                } else if (Cursor.NE_RESIZE == cursor) {
                    if (this.setStageHeight(this.primaryStage.getHeight() - deltaY)) {
                        this.primaryStage.setY(this.primaryStage.getY() + deltaY);
                    }

                    this.setStageWidth(this.primaryStage.getWidth() + deltaX);
                    mouseEvent.consume();
                } else if (Cursor.SE_RESIZE == cursor) {
                    this.setStageWidth(this.primaryStage.getWidth() + deltaX);
                    this.setStageHeight(this.primaryStage.getHeight() + deltaY);
                    mouseEvent.consume();
                } else if (Cursor.S_RESIZE == cursor) {
                    this.setStageHeight(this.primaryStage.getHeight() + deltaY);
                    mouseEvent.consume();
                } else if (Cursor.W_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.getWidth() - deltaX)) {
                        this.primaryStage.setX(this.primaryStage.getX() + deltaX);
                    }

                    mouseEvent.consume();
                } else if (Cursor.SW_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.getWidth() - deltaX)) {
                        this.primaryStage.setX(this.primaryStage.getX() + deltaX);
                    }

                    this.setStageHeight(this.primaryStage.getHeight() + deltaY);
                    mouseEvent.consume();
                } else if (Cursor.NW_RESIZE == cursor) {
                    if (this.setStageWidth(this.primaryStage.getWidth() - deltaX)) {
                        this.primaryStage.setX(this.primaryStage.getX() + deltaX);
                    }

                    if (this.setStageHeight(this.primaryStage.getHeight() - deltaY)) {
                        this.primaryStage.setY(this.primaryStage.getY() + deltaY);
                    }

                    mouseEvent.consume();
                } else if (Cursor.N_RESIZE == cursor) {
                    if (this.setStageHeight(this.primaryStage.getHeight() - deltaY)) {
                        this.primaryStage.setY(this.primaryStage.getY() + deltaY);
                    }

                    mouseEvent.consume();
                } else if (this.allowMove) {
                    this.primaryStage.setX(mouseEvent.getScreenX() - this.xOffset);
                    this.primaryStage.setY(mouseEvent.getScreenY() - this.yOffset);
                    mouseEvent.consume();
                }
            }
        }
    }
}
