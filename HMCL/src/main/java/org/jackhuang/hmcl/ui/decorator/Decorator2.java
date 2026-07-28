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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXSnackbar;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public final class Decorator2 {
    static final double SHADOW_SIZE = 8.0;

    private final ShadowWrapper shadowWrapper = new ShadowWrapper();
    private final MainWindowPane mainWindowPane;

    private final JFXSnackbar snackbar = new JFXSnackbar();
    private final Navigator navigator;

    private final BooleanProperty canRefresh = new SimpleBooleanProperty(false);
    private final BooleanProperty canBack = new SimpleBooleanProperty(false);
    private final BooleanProperty canClose = new SimpleBooleanProperty(false);
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(false);

    public Decorator2(Stage primaryStage) {
        this.stage.set(primaryStage);
        this.navigator = new Navigator();
        this.mainWindowPane = new MainWindowPane(this);
        snackbar.registerSnackbarContainer(mainWindowPane);
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

    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;

    // TODO: Dirty implementation.
    public Navigation.NavigationDirection getNavigationDirection() {
        return navigationDirection;
    }

    public void setNavigationDirection(Navigation.NavigationDirection navigationDirection) {
        this.navigationDirection = navigationDirection;
    }

    // ---

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

    private boolean playRestoreMinimizeAnimation;

    void minimize() {
        @Nullable Stage stage = getStage();
        if (stage == null)
            return;

        if (AnimationUtils.playWindowAnimation() && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            playRestoreMinimizeAnimation = true;
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(mainWindowPane.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(mainWindowPane.translateYProperty(), 0, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleZProperty(), 1, Motion.EASE)
                    ),
                    new KeyFrame(Motion.SHORT4,
                            new KeyValue(mainWindowPane.opacityProperty(), 0, Motion.EASE),
                            new KeyValue(mainWindowPane.translateYProperty(), 200, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleXProperty(), 0.4, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleYProperty(), 0.4, Motion.EASE),
                            new KeyValue(mainWindowPane.scaleZProperty(), 0.4, Motion.EASE)
                    )
            );
            timeline.setOnFinished(event -> stage.setIconified(true));
            timeline.play();
        } else {
            stage.setIconified(true);
        }
    }

    void close() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page) {
            if (page.isPageCloseable()) {
                page.closePage();
                return;
            }
        }
        navigator.clear();
    }

    void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page) {
            if (page.back()) {
                if (navigator.canGoBack()) {
                    navigator.close();
                }
            }
        } else {
            if (navigator.canGoBack()) {
                navigator.close();
            }
        }
    }

    void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable refreshable) {
            if (refreshable.refreshableProperty().get())
                refreshable.refresh();
        }
    }

    // ---


    public Navigator getNavigator() {
        return navigator;
    }

    private final ObjectProperty<@Nullable Stage> stage = new SimpleObjectProperty<>(this, "stage");

    public @Nullable Stage getStage() {
        return stage.get();
    }

    public void setStage(@Nullable Stage stage) {
        this.stage.set(stage);
    }

    public ObjectProperty<@Nullable Stage> stageProperty() {
        return stage;
    }

    private final ObjectProperty<DecoratorPage.@Nullable State> state = new SimpleObjectProperty<>(this, "state");

    public DecoratorPage.@Nullable State getState() {
        return state.get();
    }

    public void setState(DecoratorPage.@Nullable State state) {
        this.state.set(state);
    }

    public ObjectProperty<DecoratorPage.@Nullable State> stateProperty() {
        return state;
    }

    // --

    private final BooleanProperty allowMove = new ReadOnlyBooleanWrapper();

    public boolean isAllowMove() {
        return allowMove.get();
    }

    public void setAllowMove(boolean allowMove) {
        this.allowMove.set(allowMove);
    }

    public BooleanProperty allowMoveProperty() {
        return allowMove;
    }

    private final BooleanProperty dragging = new ReadOnlyBooleanWrapper();

    public boolean isDragging() {
        return dragging.get();
    }

    public void setDragging(boolean dragging) {
        this.dragging.set(dragging);
    }

    public BooleanProperty draggingProperty() {
        return dragging;
    }
}
