/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDrawer;
import com.jfoenix.controls.JFXHamburger;
import com.jfoenix.svg.SVGGlyph;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.animation.AnimationProducer;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogWizardDisplayer;
import org.jackhuang.hmcl.ui.wizard.*;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Decorator extends StackPane implements TaskExecutorDialogWizardDisplayer {
    private static final SVGGlyph minus = Lang.apply(new SVGGlyph(0, "MINUS", "M804.571 420.571v109.714q0 22.857-16 38.857t-38.857 16h-694.857q-22.857 0-38.857-16t-16-38.857v-109.714q0-22.857 16-38.857t38.857-16h694.857q22.857 0 38.857 16t16 38.857z", Color.WHITE),
            glyph -> { glyph.setSize(12, 2); glyph.setTranslateY(4); });
    private static final SVGGlyph resizeMax = Lang.apply(new SVGGlyph(0, "RESIZE_MAX", "M726 810v-596h-428v596h428zM726 44q34 0 59 25t25 59v768q0 34-25 60t-59 26h-428q-34 0-59-26t-25-60v-768q0-34 25-60t59-26z", Color.WHITE),
            glyph -> { glyph.setPrefSize(12, 12); glyph.setSize(12, 12); });
    private static final SVGGlyph resizeMin = Lang.apply(new SVGGlyph(0, "RESIZE_MIN", "M80.842 943.158v-377.264h565.894v377.264h-565.894zM0 404.21v619.79h727.578v-619.79h-727.578zM377.264 161.684h565.894v377.264h-134.736v80.842h215.578v-619.79h-727.578v323.37h80.842v-161.686z", Color.WHITE),
            glyph -> { glyph.setPrefSize(12, 12); glyph.setSize(12, 12); });
    private static final SVGGlyph close = Lang.apply(new SVGGlyph(0, "CLOSE", "M810 274l-238 238 238 238-60 60-238-238-238 238-60-60 238-238-238-238 60-60 238 238 238-238z", Color.WHITE),
            glyph -> { glyph.setPrefSize(12, 12); glyph.setSize(12, 12); });

    private final ObjectProperty<Runnable> onCloseButtonAction = new SimpleObjectProperty<>(Main::stopApplication);
    private final BooleanProperty customMaximize = new SimpleBooleanProperty(false);

    private final Stage primaryStage;
    private final Node mainPage;
    private final boolean max, min;
    private final WizardController wizardController = new WizardController(this);
    private final Queue<Object> cancelQueue = new ConcurrentLinkedQueue<>();

    private double xOffset, yOffset, newX, newY, initX, initY;
    private boolean allowMove, isDragging, dialogShown, maximized;
    private BoundingBox originalBox, maximizedBox;
    private final TransitionHandler animationHandler;

    @FXML
    private StackPane contentPlaceHolder;
    @FXML
    private StackPane drawerWrapper;
    @FXML
    private BorderPane titleContainer;
    @FXML
    private BorderPane leftRootPane;
    @FXML
    private HBox buttonsContainer;
    @FXML
    private JFXButton backNavButton;
    @FXML
    private JFXButton refreshNavButton;
    @FXML
    private JFXButton closeNavButton;
    @FXML
    private JFXButton refreshMenuButton;
    @FXML
    private Label titleLabel;
    @FXML
    private Label lblTitle;
    @FXML
    private AdvancedListBox leftPane;
    @FXML
    private JFXDrawer drawer;
    @FXML
    private StackPane titleBurgerContainer;
    @FXML
    private JFXHamburger titleBurger;
    @FXML
    private JFXDialog dialog;
    @FXML
    private JFXButton btnMin;
    @FXML
    private JFXButton btnMax;
    @FXML
    private JFXButton btnClose;
    @FXML
    private HBox updatePane;
    @FXML
    private HBox navLeft;

    public Decorator(Stage primaryStage, Node mainPage, String title) {
        this(primaryStage, mainPage, title, true, true);
    }

    public Decorator(Stage primaryStage, Node mainPage, String title, boolean max, boolean min) {
        this.primaryStage = primaryStage;
        this.mainPage = mainPage;
        this.max = max;
        this.min = min;

        FXUtils.loadFXML(this, "/assets/fxml/decorator.fxml");

        updatePane.setCursor(Cursor.HAND);
        updatePane.setOnMouseClicked(event -> Main.UPDATE_CHECKER.checkOutdate());

        primaryStage.initStyle(StageStyle.UNDECORATED);
        btnClose.setGraphic(close);
        btnMin.setGraphic(minus);
        btnMax.setGraphic(resizeMax);

        close.fillProperty().bind(Theme.foregroundFillBinding());
        minus.fillProperty().bind(Theme.foregroundFillBinding());
        resizeMax.fillProperty().bind(Theme.foregroundFillBinding());
        resizeMin.fillProperty().bind(Theme.foregroundFillBinding());

        refreshNavButton.setGraphic(SVG.refresh(Theme.foregroundFillBinding(), 15, 15));
        closeNavButton.setGraphic(SVG.close(Theme.foregroundFillBinding(), 15, 15));
        backNavButton.setGraphic(SVG.back(Theme.foregroundFillBinding(), 15, 15));

        lblTitle.setText(title);

        buttonsContainer.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        titleContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2)
                btnMax.fire();
        });

        dialog.setDialogContainer(drawerWrapper);
        dialog.setOnDialogClosed(e -> dialogShown = false);
        dialog.setOnDialogOpened(e -> dialogShown = true);

        if (!min) buttonsContainer.getChildren().remove(btnMin);
        if (!max) buttonsContainer.getChildren().remove(btnMax);

        //JFXDepthManager.setDepth(titleContainer, 1);
        titleContainer.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> allowMove = true);
        titleContainer.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!isDragging) allowMove = false;
        });
        Rectangle rectangle = new Rectangle(0, 0, 0, 0);
        rectangle.widthProperty().bind(titleContainer.widthProperty());
        rectangle.heightProperty().bind(Bindings.createDoubleBinding(() -> titleContainer.getHeight() + 100, titleContainer.heightProperty()));
        titleContainer.setClip(rectangle);

        animationHandler = new TransitionHandler(contentPlaceHolder);

        loadBackground();
    }

    private void loadBackground() {
        Image background;

        if (Settings.INSTANCE.getBackgroundImageType() == EnumBackgroundImage.DEFAULT)
            background = searchBackgroundImage(new Image("/assets/img/background.jpg"), "");
        else
            background = searchBackgroundImage(new Image("/assets/img/background.jpg"), Settings.INSTANCE.getBackgroundImage());

        drawerWrapper.setBackground(new Background(new BackgroundImage(background, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, new BackgroundSize(800, 480, false, false, true, true))));
    }

    private static Image searchBackgroundImage(Image def, String customPath) {
        Random random = new Random();
        boolean loaded = false;
        Image background = def;

        // custom path
        if (StringUtils.isNotBlank(customPath)) {
            try {
                background = new Image("file:" + customPath);
                loaded = true;
            } catch (IllegalArgumentException ignore) {
            }
        }

        // images in ./bg
        if (!loaded) {
            File backgroundImageFile = new File("bg");
            if (backgroundImageFile.isDirectory()) {
                File[] backgroundPath = backgroundImageFile.listFiles(file -> StringUtils.containsOne(FileUtils.getExtension(file), "png", "jpg"));
                if (backgroundPath != null && backgroundPath.length > 0) {
                    int index = random.nextInt(backgroundPath.length);
                    try {
                        background = new Image("file:" + backgroundPath[index].getAbsolutePath());
                        loaded = true;
                    } catch (IllegalArgumentException ignore) {
                    }
                }
            }
        }

        // background.png
        if (!loaded) {
            File backgroundImageFile = new File("background.png");
            if (backgroundImageFile.exists()) {
                try {
                    background = new Image("file:" + backgroundImageFile.getAbsolutePath());
                    loaded = true;
                } catch (IllegalArgumentException ignore) {
                }
            }
        }

        // background.jpg
        if (!loaded) {
            File backgroundImageFile = new File("background.jpg");
            if (backgroundImageFile.exists()) {
                try {
                    background = new Image("file:" + backgroundImageFile.getAbsolutePath());
                    loaded = true;
                } catch (IllegalArgumentException ignore) {
                }
            }
        }

        return background;
    }

    @FXML
    private void onMouseMoved(MouseEvent mouseEvent) {
        if (!primaryStage.isMaximized() && !primaryStage.isFullScreen() && !maximized) {
            if (!primaryStage.isResizable())
                updateInitMouseValues(mouseEvent);
            else {
                double x = mouseEvent.getX(), y = mouseEvent.getY();
                Bounds boundsInParent = getBoundsInParent();
                if (getBorder() != null && getBorder().getStrokes().size() > 0) {
                    double borderWidth = this.contentPlaceHolder.snappedLeftInset();
                    if (this.isRightEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            setCursor(Cursor.NE_RESIZE);
                        } else if (y > this.getHeight() - borderWidth) {
                            setCursor(Cursor.SE_RESIZE);
                        } else {
                            setCursor(Cursor.E_RESIZE);
                        }
                    } else if (this.isLeftEdge(x, y, boundsInParent)) {
                        if (y < borderWidth) {
                            setCursor(Cursor.NW_RESIZE);
                        } else if (y > this.getHeight() - borderWidth) {
                            setCursor(Cursor.SW_RESIZE);
                        } else {
                            setCursor(Cursor.W_RESIZE);
                        }
                    } else if (this.isTopEdge(x, y, boundsInParent)) {
                        setCursor(Cursor.N_RESIZE);
                    } else if (this.isBottomEdge(x, y, boundsInParent)) {
                        setCursor(Cursor.S_RESIZE);
                    } else {
                        setCursor(Cursor.DEFAULT);
                    }

                    this.updateInitMouseValues(mouseEvent);
                }
            }
        } else {
            setCursor(Cursor.DEFAULT);
        }
    }

    @FXML
    private void onMouseReleased() {
        isDragging = false;
    }

    @FXML
    private void onMouseDragged(MouseEvent mouseEvent) {
        this.isDragging = true;
        if (mouseEvent.isPrimaryButtonDown() && (this.xOffset != -1.0 || this.yOffset != -1.0)) {
            if (!this.primaryStage.isFullScreen() && !mouseEvent.isStillSincePress() && !this.primaryStage.isMaximized() && !this.maximized) {
                this.newX = mouseEvent.getScreenX();
                this.newY = mouseEvent.getScreenY();
                double deltaX = this.newX - this.initX;
                double deltaY = this.newY - this.initY;
                Cursor cursor = this.getCursor();
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

    @FXML
    private void onMin() {
        primaryStage.setIconified(true);
    }

    @FXML
    private void onMax() {
        if (!max) return;
        if (!this.isCustomMaximize()) {
            this.primaryStage.setMaximized(!this.primaryStage.isMaximized());
            this.maximized = this.primaryStage.isMaximized();
            if (this.primaryStage.isMaximized()) {
                this.btnMax.setGraphic(resizeMin);
                this.btnMax.setTooltip(new Tooltip("Restore Down"));
            } else {
                this.btnMax.setGraphic(resizeMax);
                this.btnMax.setTooltip(new Tooltip("Maximize"));
            }
        } else {
            if (!this.maximized) {
                this.originalBox = new BoundingBox(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight());
                Screen screen = Screen.getScreensForRectangle(primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight()).get(0);
                Rectangle2D bounds = screen.getVisualBounds();
                this.maximizedBox = new BoundingBox(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
                primaryStage.setX(this.maximizedBox.getMinX());
                primaryStage.setY(this.maximizedBox.getMinY());
                primaryStage.setWidth(this.maximizedBox.getWidth());
                primaryStage.setHeight(this.maximizedBox.getHeight());
                this.btnMax.setGraphic(resizeMin);
                this.btnMax.setTooltip(new Tooltip("Restore Down"));
            } else {
                primaryStage.setX(this.originalBox.getMinX());
                primaryStage.setY(this.originalBox.getMinY());
                primaryStage.setWidth(this.originalBox.getWidth());
                primaryStage.setHeight(this.originalBox.getHeight());
                this.originalBox = null;
                this.btnMax.setGraphic(resizeMax);
                this.btnMax.setTooltip(new Tooltip("Maximize"));
            }

            this.maximized = !this.maximized;
        }
    }

    @FXML
    private void onClose() {
        onCloseButtonAction.get().run();
    }

    private void updateInitMouseValues(MouseEvent mouseEvent) {
        initX = mouseEvent.getScreenX();
        initY = mouseEvent.getScreenY();
        xOffset = mouseEvent.getSceneX();
        yOffset = mouseEvent.getSceneY();
    }

    private boolean isRightEdge(double x, double y, Bounds boundsInParent) {
        return x < getWidth() && x > getWidth() - contentPlaceHolder.snappedLeftInset();
    }

    private boolean isTopEdge(double x, double y, Bounds boundsInParent) {
        return y >= 0 && y < contentPlaceHolder.snappedLeftInset();
    }

    private boolean isBottomEdge(double x, double y, Bounds boundsInParent) {
        return y < getHeight() && y > getHeight() - contentPlaceHolder.snappedLeftInset();
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

    public void setMaximized(boolean maximized) {
        if (this.maximized != maximized) {
            Platform.runLater(btnMax::fire);
        }
    }

    public void showUpdate() {
        updatePane.setVisible(true);
    }

    private void showCloseNavButton() {
        navLeft.getChildren().add(closeNavButton);
    }

    private void hideCloseNavButton() {
        navLeft.getChildren().remove(closeNavButton);
    }

    private void setContent(Node content, AnimationProducer animation) {
        isWizardPageNow = false;
        animationHandler.setContent(content, animation);

        if (content instanceof Region) {
            ((Region) content).setMinSize(0, 0);
            FXUtils.setOverflowHidden((Region) content, true);
        }

        refreshNavButton.setVisible(content instanceof Refreshable);
        backNavButton.setVisible(content != mainPage);

        String prefix = category == null ? "" : category + " - ";

        titleLabel.textProperty().unbind();

        if (content instanceof WizardPage)
            titleLabel.setText(prefix + ((WizardPage) content).getTitle());

        if (content instanceof DecoratorPage)
            titleLabel.textProperty().bind(((DecoratorPage) content).titleProperty());
    }

    private String category;
    private Node nowPage;
    private boolean isWizardPageNow;

    public Node getNowPage() {
        return nowPage;
    }

    public void showPage(Node content) {
        contentPlaceHolder.getStyleClass().removeAll("gray-background", "white-background");
        if (content != null)
            contentPlaceHolder.getStyleClass().add("gray-background");

        Node c = content == null ? mainPage : content;
        onEnd();
        if (nowPage instanceof DecoratorPage)
            ((DecoratorPage) nowPage).onClose();
        nowPage = content;

        setContent(c, ContainerAnimations.FADE.getAnimationProducer());

        if (c instanceof Region) {
            // Let root pane fix window size.
            StackPane parent = (StackPane) c.getParent();
            ((Region) c).prefWidthProperty().bind(parent.widthProperty());
            ((Region) c).prefHeightProperty().bind(parent.heightProperty());
        }
    }

    public JFXDialog showDialog(Region content) {
        dialog.setContent(content);
        if (!dialogShown)
            dialog.show();
        return dialog;
    }

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        this.category = category;
        wizardController.setProvider(wizardProvider);
        wizardController.onStart();
    }

    @Override
    public void onStart() {
        backNavButton.setVisible(true);
        backNavButton.setDisable(false);
        showCloseNavButton();
        refreshNavButton.setVisible(false);
    }

    @Override
    public void onEnd() {
        backNavButton.setVisible(false);
        hideCloseNavButton();
        refreshNavButton.setVisible(false);
    }

    @Override
    public void navigateTo(Node page, Navigation.NavigationDirection nav) {
        contentPlaceHolder.getStyleClass().removeAll("gray-background", "white-background");
        contentPlaceHolder.getStyleClass().add("white-background");
        setContent(page, nav.getAnimation().getAnimationProducer());
        isWizardPageNow = true;
    }

    @FXML
    private void onRefresh() {
        ((Refreshable) contentPlaceHolder.getChildren().get(0)).refresh();
    }

    @FXML
    private void onCloseNav() {
        wizardController.onCancel();
        showPage(null);
    }

    @FXML
    private void onBack() {
        if (isWizardPageNow && wizardController.canPrev())
            wizardController.onPrev(true);
        else
            onCloseNav();
    }

    @Override
    public Queue<Object> getCancelQueue() {
        return cancelQueue;
    }

    public Runnable getOnCloseButtonAction() {
        return onCloseButtonAction.get();
    }

    public ObjectProperty<Runnable> onCloseButtonActionProperty() {
        return onCloseButtonAction;
    }

    public void setOnCloseButtonAction(Runnable onCloseButtonAction) {
        this.onCloseButtonAction.set(onCloseButtonAction);
    }

    public boolean isCustomMaximize() {
        return customMaximize.get();
    }

    public BooleanProperty customMaximizeProperty() {
        return customMaximize;
    }

    public void setCustomMaximize(boolean customMaximize) {
        this.customMaximize.set(customMaximize);
    }

    @Override
    public WizardController getWizardController() {
        return wizardController;
    }

    public JFXDialog getDialog() {
        return dialog;
    }

    public AdvancedListBox getLeftPane() {
        return leftPane;
    }
}
