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

import com.jfoenix.controls.JFXButton;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.glavo.monetfx.ColorRole;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

public final class MainWindowPane extends StackPane {
    private static final double ARC = 8.0;

    private final Decorator2 decorator;
    private final BorderPane frame;

    private final BorderPane titleBar;

    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    private final EventHandler<MouseEvent> onTitleBarDoubleClick = event -> {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
            return;

        @Nullable Stage stage = getCurrentStage();
        if (stage == null)
            return;

        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            stage.setMaximized(!stage.isMaximized());
            event.consume();
        }
    };

    private final EventHandler<MouseEvent> onMouseReleased = this::onMouseReleased;
    private final EventHandler<MouseEvent> onMouseDragged = this::onMouseDragged;
    private final EventHandler<MouseEvent> onMouseMoved = this::onMouseMoved;

    private final InvalidationListener onWindowsStatusChange = o -> {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
            return;
        @Nullable Stage stage = getCurrentStage();
        if (stage == null)
            return;

        if (stage.isIconified() || stage.isFullScreen() || stage.isMaximized()) {
            removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
        } else {
            addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
            addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
            addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
        }
    };

    private final WeakInvalidationListener weakOnWindowsStatusChange = new WeakInvalidationListener(onWindowsStatusChange);

    private double mouseInitX, mouseInitY, stageInitX, stageInitY, stageInitWidth, stageInitHeight;

    public MainWindowPane(Decorator2 decorator) {
        this.decorator = decorator;

        // Set the clip to a rounded rectangle to achieve rounded corners
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(ARC);
        clip.setArcHeight(ARC);
        setClip(clip);

        backgroundProperty().bind(Bindings.createObjectBinding(
                () -> Themes.windowTransparentProperty().get()
                        ? null
                        : new Background(new BackgroundFill(
                        Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER),
                        CornerRadii.EMPTY,
                        Insets.EMPTY)),
                Themes.windowTransparentProperty(),
                Themes.colorSchemeProperty()));

        // Background Node
        var backgroundNode = new Region();
        backgroundNode.setMouseTransparent(true);
        StackPane.setAlignment(backgroundNode, Pos.BOTTOM_CENTER);
        holder.onWeakChangeAndOperate(Themes.backgroundProperty(), background -> {
            double opacity = background.opacity();
            if (opacity >= 0.001) {
                backgroundNode.setBackground(background.background());
                backgroundNode.setOpacity(opacity);
            } else {
                backgroundNode.setBackground(null);
                backgroundNode.setOpacity(0);
            }
        });

        // Window frame, responsible for managing the title bar and window content
        this.frame = new BorderPane();
        frame.getStyleClass().addAll("jfx-decorator");
        getChildren().add(frame);

        // Center

        // center node with an animation layer at bottom, a container layer at middle and a "welcome" layer at top.
        var center = new StackPane();
        FXUtils.setOverflowHidden(center);
        center.getStyleClass().add("jfx-decorator-content-container");
        center.getChildren().setAll(decorator.getNavigator());

        // Top
        titleBar = new BorderPane();
        {
            titleBar.setPickOnBounds(false);

            WeakInvalidationListener updateBackground = holder.weak((InvalidationListener) observable -> {
                @Nullable ColorScheme colorScheme = Themes.getColorScheme();
                //noinspection ConstantValue
                if (colorScheme == null) {
                    titleBar.setBackground(null);
                    return;
                }

                if (Themes.titleBarTransparentProperty().get()) {
                    Color surface = colorScheme.getSurface();
                    titleBar.setBackground(new Background(new BackgroundFill((
                            new Color(surface.getRed(), surface.getGreen(), surface.getBlue(), 0.5)
                    ), null, null)));
                } else {
                    titleBar.setBackground(new Background(new BackgroundFill(colorScheme.getPrimaryContainer(), null, null)));
                }
            });
            Themes.colorSchemeProperty().addListener(updateBackground);
            Themes.titleBarTransparentProperty().addListener(updateBackground);
            updateBackground.invalidated(null);

            // Window control buttons in the top-right corner
            HBox rightButtonsContainer = new HBox();
            rightButtonsContainer.setAlignment(Pos.TOP_RIGHT);
            rightButtonsContainer.setMaxHeight(40);
            {
                var btnHelp = new JFXButton();
                btnHelp.setFocusTraversable(false);
                btnHelp.setGraphic(SVG.HELP.createIcon(Themes.titleFillProperty()));
                btnHelp.getStyleClass().add("jfx-decorator-button");
                btnHelp.setOnAction(e -> FXUtils.openLink(Metadata.CONTACT_URL));

                var btnMin = new JFXButton();
                btnMin.setFocusTraversable(false);
                btnMin.setGraphic(SVG.MINIMIZE_CENTER.createIcon(Themes.titleFillProperty()));
                btnMin.getStyleClass().add("jfx-decorator-button");
                btnMin.setOnAction(e -> decorator.minimize());

                var btnClose = new JFXButton();
                btnClose.setFocusTraversable(false);
                btnClose.setGraphic(SVG.CLOSE.createIcon(Themes.titleFillProperty()));
                btnClose.getStyleClass().add("jfx-decorator-button");
                btnClose.setOnAction(e -> decorator.close());

                rightButtonsContainer.getChildren().setAll(btnHelp, btnMin, btnClose);
            }
            titleBar.setRight(rightButtonsContainer);

            var navBarPane = new TransitionPane();
            FXUtils.onChangeAndOperate(decorator.stateProperty(), s -> {
                if (s == null) return;
                Node node = createNavBar(s.leftPaneWidth(), s.backable(), decorator.canCloseProperty().get(), decorator.showCloseAsHomeProperty().get(), s.refreshable(), s.title(), s.titleNode());
                if (s.animate()) {
                    TransitionPane.AnimationProducer animation = switch (decorator.getNavigationDirection()) {
                        case NEXT -> DecoratorSkin.NavBarAnimations.NEXT;
                        case PREVIOUS -> DecoratorSkin.NavBarAnimations.PREVIOUS;
                        default -> ContainerAnimations.FADE;
                    };
                    decorator.setNavigationDirection(Navigation.NavigationDirection.START);
                    navBarPane.setContent(node, animation, Motion.SHORT4);
                } else {
                    navBarPane.getChildren().setAll(node);
                }
            });
            titleBar.setCenter(navBarPane);
        }
        frame.setTop(titleBar);
    }

    private @Nullable Stage getCurrentStage() {
        return decorator.getStage();
    }

    private void onStageChange(@Nullable Stage oldStage, @Nullable Stage newStage) {
        if (oldStage != null) {
            oldStage.iconifiedProperty().removeListener(weakOnWindowsStatusChange);
            oldStage.maximizedProperty().removeListener(weakOnWindowsStatusChange);
            oldStage.fullScreenProperty().removeListener(weakOnWindowsStatusChange);
        }
        if (newStage != null) {
            newStage.iconifiedProperty().addListener(weakOnWindowsStatusChange);
            newStage.maximizedProperty().addListener(weakOnWindowsStatusChange);
            newStage.fullScreenProperty().addListener(weakOnWindowsStatusChange);
        }
    }

    private Node createNavBar(
            double leftPaneWidth,
            boolean canBack, boolean canClose,
            boolean showCloseAsHome,
            boolean canRefresh,
            String title, Node titleNode) {
        BorderPane navBar = new BorderPane();
        navBar.getStyleClass().add("navigation-bar");

        {
            HBox navLeft = new HBox();
            navLeft.setAlignment(Pos.CENTER_LEFT);
            navLeft.setPadding(new Insets(0, 5, 0, 5));

            if (canBack) {
                JFXButton backNavButton = new JFXButton();
                decorator.forbidDraggingWindow(backNavButton);
                backNavButton.setFocusTraversable(false);
                backNavButton.setGraphic(SVG.ARROW_BACK.createIcon(Themes.titleFillProperty()));
                backNavButton.getStyleClass().add("jfx-decorator-button");
                backNavButton.setOnAction(event -> decorator.back());
                backNavButton.visibleProperty().set(canBack);

                navLeft.getChildren().add(backNavButton);
            }

            if (canClose) {
                JFXButton closeNavButton = new JFXButton();
                decorator.forbidDraggingWindow(closeNavButton);
                closeNavButton.setFocusTraversable(false);
                closeNavButton.setGraphic(SVG.CLOSE.createIcon(Themes.titleFillProperty()));
                closeNavButton.getStyleClass().add("jfx-decorator-button");
                closeNavButton.setOnAction(event -> decorator.close());
                if (showCloseAsHome)
                    closeNavButton.setGraphic(SVG.HOME.createIcon(Themes.titleFillProperty()));
                else
                    closeNavButton.setGraphic(SVG.CLOSE.createIcon(Themes.titleFillProperty()));

                navLeft.getChildren().add(closeNavButton);
            }

            if (canBack || canClose) {
                navBar.setLeft(navLeft);
            }

            BorderPane center = new BorderPane();
            if (title != null) {
                Label titleLabel = new Label();
                titleLabel.textFillProperty().bind(Themes.titleFillProperty());
                BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
                titleLabel.getStyleClass().add("jfx-decorator-title");
                titleLabel.setText(title);
                center.setLeft(titleLabel);
                BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
            }
            if (titleNode != null) {
                center.setCenter(titleNode);
                BorderPane.setAlignment(titleNode, Pos.CENTER_LEFT);
                BorderPane.setMargin(titleNode, new Insets(0, 0, 0, 8));
            }
            if (onTitleBarDoubleClick != null)
                center.setOnMouseClicked(onTitleBarDoubleClick);
            center.setOnMouseDragged(mouseEvent -> {
                @Nullable Stage stage = getCurrentStage();
                if (stage == null)
                    return;

                if (!decorator.isDragging() && stage.isMaximized()) {
                    decorator.setDragging(true);
                    mouseInitX = mouseEvent.getScreenX();
                    mouseInitY = mouseEvent.getScreenY();
                    stage.setMaximized(false);
                    stageInitWidth = stage.getWidth();
                    stageInitHeight = stage.getHeight();
                    stage.setY(stageInitY = 0);
                    stage.setX(stageInitX = mouseInitX - stageInitWidth / 2);
                }
            });
            navBar.setCenter(center);

            if (canRefresh) {
                HBox navRight = new HBox();
                navRight.setAlignment(Pos.CENTER_RIGHT);
                JFXButton refreshNavButton = new JFXButton();
                refreshNavButton.setGraphic(SVG.REFRESH.createIcon(Themes.titleFillProperty()));
                refreshNavButton.getStyleClass().add("jfx-decorator-button");
                refreshNavButton.setOnAction(event -> decorator.refresh());
                decorator.forbidDraggingWindow(refreshNavButton);

                navRight.getChildren().setAll(refreshNavButton);
                navBar.setRight(navRight);
            }
        }
        return navBar;
    }

    private boolean isRightEdge(double x, double y, Bounds boundsInParent) {
        return x < getWidth() && x >= getWidth() - snappedLeftInset();
    }

    private boolean isTopEdge(double x, double y, Bounds boundsInParent) {
        return y >= 0 && y <= snappedTopInset();
    }

    private boolean isBottomEdge(double x, double y, Bounds boundsInParent) {
        return y < getHeight() && y >= getHeight() - snappedLeftInset();
    }

    private boolean isLeftEdge(double x, double y, Bounds boundsInParent) {
        return x >= 0 && x <= snappedLeftInset();
    }

    private void resizeStage(double newWidth, double newHeight) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null)
            return;

        if (newWidth < 0)
            newWidth = stage.getWidth();
        if (newWidth < stage.getMinWidth())
            newWidth = stage.getMinWidth();
        if (newWidth < titleBar.getMinWidth())
            newWidth = titleBar.getMinWidth();

        if (newHeight < 0)
            newHeight = stage.getHeight();
        if (newHeight < stage.getMinHeight())
            newHeight = stage.getMinHeight();
        if (newHeight < titleBar.getMinHeight())
            newHeight = titleBar.getMinHeight();

        // Width and height must be set simultaneously to avoid JDK-8344372 (https://github.com/openjdk/jfx/pull/1654)
        stage.setWidth(newWidth);
        stage.setHeight(newHeight);
    }

    private void onMouseMoved(MouseEvent mouseEvent) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null)
            return;

        if (!stage.isFullScreen() && stage.isResizable()) {
            double x = mouseEvent.getX(), y = mouseEvent.getY();
            Bounds boundsInParent = getBoundsInParent();
            double diagonalSize = snappedLeftInset() + 10;
            if (isRightEdge(x, y, boundsInParent)) {
                if (y < diagonalSize) {
                    setCursor(Cursor.NE_RESIZE);
                } else if (y > getHeight() - diagonalSize) {
                    setCursor(Cursor.SE_RESIZE);
                } else {
                    setCursor(Cursor.E_RESIZE);
                }
            } else if (isLeftEdge(x, y, boundsInParent)) {
                if (y < diagonalSize) {
                    setCursor(Cursor.NW_RESIZE);
                } else if (y > getHeight() - diagonalSize) {
                    setCursor(Cursor.SW_RESIZE);
                } else {
                    setCursor(Cursor.W_RESIZE);
                }
            } else if (isTopEdge(x, y, boundsInParent)) {
                if (x < diagonalSize) {
                    setCursor(Cursor.NW_RESIZE);
                } else if (x > getWidth() - diagonalSize) {
                    setCursor(Cursor.NE_RESIZE);
                } else {
                    setCursor(Cursor.N_RESIZE);
                }
            } else if (isBottomEdge(x, y, boundsInParent)) {
                if (x < diagonalSize) {
                    setCursor(Cursor.SW_RESIZE);
                } else if (x > getWidth() - diagonalSize) {
                    setCursor(Cursor.SE_RESIZE);
                } else {
                    setCursor(Cursor.S_RESIZE);
                }
            } else {
                setCursor(Cursor.DEFAULT);
            }
        } else {
            setCursor(Cursor.DEFAULT);
        }
    }

    private void onMouseReleased(MouseEvent mouseEvent) {
        decorator.setDragging(false);
    }

    private void onMouseDragged(MouseEvent mouseEvent) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null)
            return;

        if (!decorator.isDragging()) {
            decorator.setDragging(true);
            mouseInitX = mouseEvent.getScreenX();
            mouseInitY = mouseEvent.getScreenY();
            stageInitX = stage.getX();
            stageInitY = stage.getY();
            stageInitWidth = stage.getWidth();
            stageInitHeight = stage.getHeight();
        }

        if (stage.isFullScreen() || !mouseEvent.isPrimaryButtonDown() || mouseEvent.isStillSincePress())
            return;

        double dx = mouseEvent.getScreenX() - mouseInitX;
        double dy = mouseEvent.getScreenY() - mouseInitY;

        Cursor cursor = getCursor();
        if (decorator.isAllowMove()) {
            if (cursor == Cursor.DEFAULT) {
                stage.setX(stageInitX + dx);
                stage.setY(stageInitY + dy);
                mouseEvent.consume();
            }
        }

        if (stage.isResizable()) {
            if (cursor == Cursor.E_RESIZE) {
                resizeStage(stageInitWidth + dx, -1);
                mouseEvent.consume();

            } else if (cursor == Cursor.S_RESIZE) {
                resizeStage(-1, stageInitHeight + dy);
                mouseEvent.consume();

            } else if (cursor == Cursor.W_RESIZE) {
                resizeStage(stageInitWidth - dx, -1);
                stage.setX(stageInitX + stageInitWidth - stage.getWidth());
                mouseEvent.consume();

            } else if (cursor == Cursor.N_RESIZE) {
                resizeStage(-1, stageInitHeight - dy);
                stage.setY(stageInitY + stageInitHeight - stage.getHeight());
                mouseEvent.consume();

            } else if (cursor == Cursor.SE_RESIZE) {
                resizeStage(stageInitWidth + dx, stageInitHeight + dy);
                mouseEvent.consume();

            } else if (cursor == Cursor.SW_RESIZE) {
                resizeStage(stageInitWidth - dx, stageInitHeight + dy);
                stage.setX(stageInitX + stageInitWidth - stage.getWidth());
                mouseEvent.consume();

            } else if (cursor == Cursor.NW_RESIZE) {
                resizeStage(stageInitWidth - dx, stageInitHeight - dy);
                stage.setX(stageInitX + stageInitWidth - stage.getWidth());
                stage.setY(stageInitY + stageInitHeight - stage.getHeight());
                mouseEvent.consume();

            } else if (cursor == Cursor.NE_RESIZE) {
                resizeStage(stageInitWidth + dx, stageInitHeight - dy);
                stage.setY(stageInitY + stageInitHeight - stage.getHeight());
                mouseEvent.consume();
            }
        }
    }
}
