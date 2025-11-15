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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXSnackbar;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

public class Decorator extends Control {
    private final ListProperty<Node> drawer = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Node> content = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Node> container = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ObjectProperty<Background> contentBackground = new SimpleObjectProperty<>();
    private final ObjectProperty<DecoratorPage.State> state = new SimpleObjectProperty<>();
    private final StringProperty drawerTitle = new SimpleStringProperty();
    private final ObjectProperty<Runnable> onCloseButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onCloseNavButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onBackNavButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onRefreshNavButtonAction = new SimpleObjectProperty<>();
    private final BooleanProperty canRefresh = new SimpleBooleanProperty(false);
    private final BooleanProperty canBack = new SimpleBooleanProperty(false);
    private final BooleanProperty canClose = new SimpleBooleanProperty(false);
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(false);
    private final BooleanProperty titleTransparent = new SimpleBooleanProperty(false);
    private final Stage primaryStage;
    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;
    private StackPane drawerWrapper;
    private final JFXSnackbar snackbar = new JFXSnackbar();

    private final ReadOnlyBooleanWrapper allowMove = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper dragging = new ReadOnlyBooleanWrapper();

    private boolean playRestoreMinimizeAnimation = false;

    public Decorator(Stage primaryStage) {
        this.primaryStage = primaryStage;

        setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));

        primaryStage.initStyle(StageStyle.UNDECORATED);

        if (AnimationUtils.playWindowAnimation()) {
            FXUtils.onChange(primaryStage.iconifiedProperty(), iconified -> {
                if (playRestoreMinimizeAnimation && !iconified) {
                    playRestoreMinimizeAnimation = false;
                    Timeline timeline = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(this.opacityProperty(), 0, Motion.EASE),
                                    new KeyValue(this.translateYProperty(), 200, Motion.EASE),
                                    new KeyValue(this.scaleXProperty(), 0.4, Motion.EASE),
                                    new KeyValue(this.scaleYProperty(), 0.4, Motion.EASE),
                                    new KeyValue(this.scaleZProperty(), 0.4, Motion.EASE)
                            ),
                            new KeyFrame(Motion.SHORT4,
                                    new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                                    new KeyValue(this.translateYProperty(), 0, Motion.EASE),
                                    new KeyValue(this.scaleXProperty(), 1, Motion.EASE),
                                    new KeyValue(this.scaleYProperty(), 1, Motion.EASE),
                                    new KeyValue(this.scaleZProperty(), 1, Motion.EASE)
                            )
                    );
                    timeline.play();
                }
            });
        }

    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public StackPane getDrawerWrapper() {
        return drawerWrapper;
    }

    public void setDrawerWrapper(StackPane drawerWrapper) {
        this.drawerWrapper = drawerWrapper;
    }

    public ObservableList<Node> getDrawer() {
        return drawer.get();
    }

    public ListProperty<Node> drawerProperty() {
        return drawer;
    }

    public void setDrawer(ObservableList<Node> drawer) {
        this.drawer.set(drawer);
    }

    public ObservableList<Node> getContent() {
        return content.get();
    }

    public ListProperty<Node> contentProperty() {
        return content;
    }

    public void setContent(ObservableList<Node> content) {
        this.content.set(content);
    }

    public DecoratorPage.State getState() {
        return state.get();
    }

    public ObjectProperty<DecoratorPage.State> stateProperty() {
        return state;
    }

    public void setState(DecoratorPage.State state) {
        this.state.set(state);
    }

    public String getDrawerTitle() {
        return drawerTitle.get();
    }

    public StringProperty drawerTitleProperty() {
        return drawerTitle;
    }

    public void setDrawerTitle(String drawerTitle) {
        this.drawerTitle.set(drawerTitle);
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

    public ObservableList<Node> getContainer() {
        return container.get();
    }

    public ListProperty<Node> containerProperty() {
        return container;
    }

    public void setContainer(ObservableList<Node> container) {
        this.container.set(container);
    }

    public Background getContentBackground() {
        return contentBackground.get();
    }

    public ObjectProperty<Background> contentBackgroundProperty() {
        return contentBackground;
    }

    public void setContentBackground(Background contentBackground) {
        this.contentBackground.set(contentBackground);
    }

    public BooleanProperty canRefreshProperty() {
        return canRefresh;
    }

    public BooleanProperty canBackProperty() {
        return canBack;
    }

    public BooleanProperty canCloseProperty() {
        return canClose;
    }

    public BooleanProperty showCloseAsHomeProperty() {
        return showCloseAsHome;
    }

    public boolean isAllowMove() {
        return allowMove.get();
    }

    public ReadOnlyBooleanProperty allowMoveProperty() {
        return allowMove.getReadOnlyProperty();
    }

    void setAllowMove(boolean allowMove) {
        this.allowMove.set(allowMove);
    }

    public boolean isDragging() {
        return dragging.get();
    }

    public ReadOnlyBooleanProperty draggingProperty() {
        return dragging.getReadOnlyProperty();
    }

    void setDragging(boolean dragging) {
        this.dragging.set(dragging);
    }

    public boolean isTitleTransparent() {
        return titleTransparent.get();
    }

    public BooleanProperty titleTransparentProperty() {
        return titleTransparent;
    }

    public void setTitleTransparent(boolean titleTransparent) {
        this.titleTransparent.set(titleTransparent);
    }

    public ObjectProperty<EventHandler<ActionEvent>> onBackNavButtonActionProperty() {
        return onBackNavButtonAction;
    }

    public ObjectProperty<EventHandler<ActionEvent>> onCloseNavButtonActionProperty() {
        return onCloseNavButtonAction;
    }

    public ObjectProperty<EventHandler<ActionEvent>> onRefreshNavButtonActionProperty() {
        return onRefreshNavButtonAction;
    }

    public JFXSnackbar getSnackbar() {
        return snackbar;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DecoratorSkin(this);
    }

    public void minimize() {
        if (AnimationUtils.playWindowAnimation() && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            playRestoreMinimizeAnimation = true;
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(this.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(this.translateYProperty(), 0, Motion.EASE),
                            new KeyValue(this.scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(this.scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(this.scaleZProperty(), 1, Motion.EASE)
                    ),
                    new KeyFrame(Motion.SHORT4,
                            new KeyValue(this.opacityProperty(), 0, Motion.EASE),
                            new KeyValue(this.translateYProperty(), 200, Motion.EASE),
                            new KeyValue(this.scaleXProperty(), 0.4, Motion.EASE),
                            new KeyValue(this.scaleYProperty(), 0.4, Motion.EASE),
                            new KeyValue(this.scaleZProperty(), 0.4, Motion.EASE)
                    )
            );
            timeline.setOnFinished(event -> primaryStage.setIconified(true));
            timeline.play();
        } else {
            primaryStage.setIconified(true);
        }
    }

    public void close() {
        onCloseButtonAction.get().run();
    }

    public void capableDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> allowMove.set(true));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (!isDragging()) allowMove.set(false);
        });
    }

    public void forbidDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            allowMove.set(false);
            e.consume();
        });
    }

    // TODO: Dirty implementation.
    public Navigation.NavigationDirection getNavigationDirection() {
        return navigationDirection;
    }

    public void setNavigationDirection(Navigation.NavigationDirection navigationDirection) {
        this.navigationDirection = navigationDirection;
    }
}
