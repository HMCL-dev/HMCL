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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Renders the clipped window content and implements custom title-bar, move, and resize behavior.
///
/// The owning decorator's stable root receives native resize events. Its shadow padding supplies the resize area
/// while the shadow is enabled; otherwise an inward edge region supplies the same behavior.
@NotNullByDefault
final class MainWindowPane extends StackPane {
    /// The diameter, in pixels, of the rounded window corners.
    private static final double ARC = 8.0;

    /// The decorator whose state and actions are represented by this pane.
    private final Decorator decorator;

    /// The frame containing the title bar and current navigation page.
    private final BorderPane frame;

    /// The pane on which modal dialogs are stacked above [#frame].
    private final StackPane dialogContainer;

    /// The title bar containing page navigation state.
    private final BorderPane titleBar;

    /// The transition container used when the title-bar state changes.
    private final TransitionPane navBarPane;

    /// Retains listener delegates that are registered through weak listener wrappers.
    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// Handles primary-button double clicks on draggable title-bar content.
    private final EventHandler<MouseEvent> onTitleBarDoubleClick = event -> {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            return;
        }

        @Nullable Stage stage = getCurrentStage();
        if (stage != null
                && event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2) {
            stage.setMaximized(!stage.isMaximized());
            event.consume();
        }
    };

    /// Ends the current move or resize gesture.
    private final EventHandler<MouseEvent> onMouseReleased = this::onMouseReleased;

    /// Applies stage movement or resizing while the primary button is dragged.
    private final EventHandler<MouseEvent> onMouseDragged = this::onMouseDragged;

    /// Updates the resize cursor according to the pointer position.
    private final EventHandler<MouseEvent> onMouseMoved = this::onMouseMoved;

    /// The stable pane that receives native move and resize event filters.
    private final StackPane windowEventRoot;

    /// The stage whose native-state properties currently have listeners installed.
    private @Nullable Stage observedStage;

    /// Enables or disables custom resizing as the attached stage changes native state.
    private final InvalidationListener onWindowsStatusChange = observable -> {
        @Nullable Stage stage = observedStage;
        if (stage == null) {
            setWindowEventFiltersEnabled(false);
            return;
        }

        setWindowEventFiltersEnabled(
                !stage.isIconified() && !stage.isFullScreen() && !stage.isMaximized());
    };

    /// Whether the move and resize event filters are currently installed on the active event root.
    private boolean windowEventFiltersInstalled;

    /// The initial pointer position of the active move or resize gesture.
    private double mouseInitX;

    /// The initial vertical pointer position of the active move or resize gesture.
    private double mouseInitY;

    /// The initial horizontal stage position of the active move or resize gesture.
    private double stageInitX;

    /// The initial vertical stage position of the active move or resize gesture.
    private double stageInitY;

    /// The initial stage width of the active move or resize gesture.
    private double stageInitWidth;

    /// The initial stage height of the active move or resize gesture.
    private double stageInitHeight;

    /// Creates and wires the main-window content for `decorator`.
    ///
    /// @param decorator       the owning window decorator
    /// @param windowEventRoot the stable root that receives move and resize events
    MainWindowPane(Decorator decorator, StackPane windowEventRoot) {
        this.decorator = decorator;
        this.windowEventRoot = windowEventRoot;

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

        Region backgroundNode = createBackgroundNode();

        frame = new BorderPane();
        frame.getStyleClass().add("jfx-decorator");

        StackPane center = new StackPane();
        center.getStyleClass().add("jfx-decorator-content-container");
        FXUtils.setOverflowHidden(center);
        center.getChildren().setAll(decorator.getNavigator());
        frame.setCenter(center);

        HBox rightButtonsContainer = createWindowButtons();
        Rectangle rightButtonsPlaceholder = new Rectangle();
        rightButtonsPlaceholder.widthProperty().bind(rightButtonsContainer.widthProperty());

        titleBar = new BorderPane();
        titleBar.setPickOnBounds(false);
        titleBar.getStyleClass().add("jfx-tool-bar");
        titleBar.setRight(rightButtonsPlaceholder);

        navBarPane = new TransitionPane();
        navBarPane.setId("decoratorTitleTransitionPane");
        titleBar.setCenter(navBarPane);
        frame.setTop(titleBar);

        updateTitleBarBackground();
        InvalidationListener titleBarBackgroundListener = observable -> updateTitleBarBackground();
        Themes.colorSchemeProperty().addListener(holder.weak(titleBarBackgroundListener));
        Themes.titleBarTransparentProperty().addListener(holder.weak(titleBarBackgroundListener));

        InvalidationListener navBarListener = observable -> updateNavBar();
        decorator.stateProperty().addListener(holder.weak(navBarListener));
        updateNavBar();

        decorator.capableDraggingWindow(titleBar);

        dialogContainer = new StackPane(frame);

        StackPane windowControlsLayer = new StackPane(rightButtonsContainer);
        windowControlsLayer.setPickOnBounds(false);
        StackPane.setAlignment(rightButtonsContainer, Pos.TOP_RIGHT);

        getChildren().setAll(backgroundNode, dialogContainer, windowControlsLayer);
    }

    /// Returns the pane used as the JFoenix dialog host.
    ///
    /// @return the stable dialog container
    StackPane getDialogContainer() {
        return dialogContainer;
    }

    /// Creates the launcher-background layer and keeps it synchronized with the active theme.
    ///
    /// @return the mouse-transparent background node
    private Region createBackgroundNode() {
        Region backgroundNode = new Region();
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
        return backgroundNode;
    }

    /// Creates the help, minimize, and application-close buttons.
    ///
    /// @return a size-limited top-right button row
    private HBox createWindowButtons() {
        HBox buttons = new HBox();
        buttons.setAlignment(Pos.TOP_RIGHT);
        buttons.setMaxSize(Region.USE_PREF_SIZE, 40);

        JFXButton helpButton = new JFXButton();
        helpButton.setFocusTraversable(false);
        helpButton.setGraphic(SVG.HELP.createIcon(Themes.titleFillProperty()));
        helpButton.getStyleClass().add("jfx-decorator-button");
        helpButton.setOnAction(event -> FXUtils.openLink(Metadata.CONTACT_URL));
        decorator.forbidDraggingWindow(helpButton);

        JFXButton minimizeButton = new JFXButton();
        minimizeButton.setFocusTraversable(false);
        minimizeButton.setGraphic(SVG.MINIMIZE_CENTER.createIcon(Themes.titleFillProperty()));
        minimizeButton.getStyleClass().add("jfx-decorator-button");
        minimizeButton.setOnAction(event -> decorator.minimizeWindow());
        decorator.forbidDraggingWindow(minimizeButton);

        JFXButton closeButton = new JFXButton();
        closeButton.setFocusTraversable(false);
        closeButton.setGraphic(SVG.CLOSE.createIcon(Themes.titleFillProperty()));
        closeButton.getStyleClass().add("jfx-decorator-button");
        closeButton.setOnAction(event -> decorator.closeWindow());
        decorator.forbidDraggingWindow(closeButton);

        buttons.getChildren().setAll(helpButton, minimizeButton, closeButton);
        return buttons;
    }

    /// Recomputes the title-bar background for the current theme and transparency preference.
    private void updateTitleBarBackground() {
        @Nullable ColorScheme colorScheme = Themes.getColorScheme();
        if (colorScheme == null) {
            titleBar.setBackground(null);
        } else if (Themes.titleBarTransparentProperty().get()) {
            Color surface = colorScheme.getSurface();
            titleBar.setBackground(new Background(new BackgroundFill(
                    new Color(surface.getRed(), surface.getGreen(), surface.getBlue(), 0.5),
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
        } else {
            titleBar.setBackground(new Background(new BackgroundFill(
                    colorScheme.getPrimaryContainer(),
                    CornerRadii.EMPTY,
                    Insets.EMPTY)));
        }
    }

    /// Rebuilds the navigation bar from the current page state.
    private void updateNavBar() {
        DecoratorPage.@Nullable State state = decorator.getState();
        if (state == null) {
            navBarPane.getChildren().clear();
            return;
        }

        Node node = createNavBar(
                state.leftPaneWidth(),
                state.backable(),
                decorator.canCloseProperty().get(),
                decorator.showCloseAsHomeProperty().get(),
                state.refreshable(),
                state.title(),
                state.titleNode());

        if (state.animate()) {
            TransitionPane.AnimationProducer animation = switch (decorator.getNavigationDirection()) {
                case NEXT -> NavBarAnimations.NEXT;
                case PREVIOUS -> NavBarAnimations.PREVIOUS;
                default -> ContainerAnimations.FADE;
            };
            decorator.setNavigationDirection(Navigation.NavigationDirection.START);
            navBarPane.setContent(node, animation, Motion.SHORT4);
        } else {
            navBarPane.getChildren().setAll(node);
        }
    }

    /// Creates the page-specific navigation controls and title.
    ///
    /// @param leftPaneWidth  the width reserved for the page's left pane
    /// @param canBack       whether to show the back action
    /// @param canClose      whether to show the close or home action
    /// @param showHome      whether the close action should use the home icon
    /// @param canRefresh    whether to show the refresh action
    /// @param title         the text title, or `null`
    /// @param titleNode     the custom title node, or `null`
    /// @return a newly constructed navigation bar
    private Node createNavBar(
            double leftPaneWidth,
            boolean canBack,
            boolean canClose,
            boolean showHome,
            boolean canRefresh,
            @Nullable String title,
            @Nullable Node titleNode) {
        BorderPane navBar = new BorderPane();
        navBar.getStyleClass().add("navigation-bar");

        HBox navLeft = new HBox();
        navLeft.setAlignment(Pos.CENTER_LEFT);
        navLeft.setPadding(new Insets(0, 5, 0, 5));

        if (canBack) {
            JFXButton backButton = new JFXButton();
            backButton.setFocusTraversable(false);
            backButton.setGraphic(SVG.ARROW_BACK.createIcon(Themes.titleFillProperty()));
            backButton.getStyleClass().add("jfx-decorator-button");
            backButton.setOnAction(event -> decorator.back());
            decorator.forbidDraggingWindow(backButton);
            navLeft.getChildren().add(backButton);
        }

        if (canClose) {
            JFXButton closeButton = new JFXButton();
            closeButton.setFocusTraversable(false);
            closeButton.setGraphic((showHome ? SVG.HOME : SVG.CLOSE).createIcon(Themes.titleFillProperty()));
            closeButton.getStyleClass().add("jfx-decorator-button");
            closeButton.setOnAction(event -> decorator.closeCurrentPage());
            decorator.forbidDraggingWindow(closeButton);
            navLeft.getChildren().add(closeButton);
        }

        if (canBack || canClose) {
            navBar.setLeft(navLeft);
        }

        BorderPane center = new BorderPane();
        if (title != null) {
            Label titleLabel = new Label(title);
            titleLabel.textFillProperty().bind(Themes.titleFillProperty());
            titleLabel.getStyleClass().add("jfx-decorator-title");
            if (titleNode == null) {
                titleLabel.maxWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> getWidth() - 150 - navLeft.getWidth(),
                        widthProperty(),
                        navLeft.widthProperty()));
            } else {
                titleLabel.prefWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> leftPaneWidth - 8 - navLeft.getWidth(),
                        navLeft.widthProperty()));
            }
            center.setLeft(titleLabel);
            BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        }

        if (titleNode != null) {
            center.setCenter(titleNode);
            BorderPane.setAlignment(titleNode, Pos.CENTER_LEFT);
            BorderPane.setMargin(titleNode, new Insets(0, 0, 0, 8));
        }

        center.setOnMouseClicked(onTitleBarDoubleClick);
        center.setOnMouseDragged(this::onTitleBarDragged);
        navBar.setCenter(center);

        if (canRefresh) {
            HBox navRight = new HBox();
            navRight.setAlignment(Pos.CENTER_RIGHT);

            JFXButton refreshButton = new JFXButton();
            refreshButton.setFocusTraversable(false);
            refreshButton.setGraphic(SVG.REFRESH.createIcon(Themes.titleFillProperty()));
            refreshButton.getStyleClass().add("jfx-decorator-button");
            refreshButton.setOnAction(event -> decorator.refresh());
            decorator.forbidDraggingWindow(refreshButton);

            navRight.getChildren().setAll(refreshButton);
            navBar.setRight(navRight);
        }

        return navBar;
    }

    /// Restores a maximized stage under the pointer when its title bar is dragged.
    ///
    /// @param event the title-bar drag event
    private void onTitleBarDragged(MouseEvent event) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null || decorator.isDragging() || !stage.isMaximized()) {
            return;
        }

        decorator.setDragging(true);
        mouseInitX = event.getScreenX();
        mouseInitY = event.getScreenY();
        stage.setMaximized(false);
        stageInitWidth = stage.getWidth();
        stageInitHeight = stage.getHeight();
        stage.setY(stageInitY = 0);
        stage.setX(stageInitX = mouseInitX - stageInitWidth / 2);
    }

    /// Returns the stage currently attached to the owning decorator.
    ///
    /// @return the current stage, or `null` while detached
    private @Nullable Stage getCurrentStage() {
        return decorator.getStage();
    }

    /// Moves native-state listeners and resize filters to `newStage`.
    ///
    /// @param newStage the newly attached stage, or `null`
    void updateStage(@Nullable Stage newStage) {
        if (observedStage != null && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            observedStage.iconifiedProperty().removeListener(onWindowsStatusChange);
            observedStage.maximizedProperty().removeListener(onWindowsStatusChange);
            observedStage.fullScreenProperty().removeListener(onWindowsStatusChange);
        }

        observedStage = newStage;
        setWindowEventFiltersEnabled(false);

        if (newStage == null) {
            return;
        }

        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            setWindowEventFiltersEnabled(true);
        } else {
            newStage.iconifiedProperty().addListener(onWindowsStatusChange);
            newStage.maximizedProperty().addListener(onWindowsStatusChange);
            newStage.fullScreenProperty().addListener(onWindowsStatusChange);
            onWindowsStatusChange.invalidated(null);
        }
    }

    /// Cancels the active move or resize gesture and restores the root cursor.
    void cancelWindowGesture() {
        windowEventRoot.setCursor(Cursor.DEFAULT);
        decorator.setDragging(false);
        decorator.setAllowMove(false);
    }

    /// Installs or removes the custom move and resize filters on the active event root.
    ///
    /// @param enabled whether the filters should be installed
    private void setWindowEventFiltersEnabled(boolean enabled) {
        if (enabled == windowEventFiltersInstalled) {
            return;
        }

        if (enabled) {
            installWindowEventFilters(windowEventRoot);
        } else {
            removeWindowEventFilters(windowEventRoot);
            cancelWindowGesture();
        }
        windowEventFiltersInstalled = enabled;
    }

    /// Installs the custom move and resize filters on `root`.
    ///
    /// @param root the event root that must receive the filters
    private void installWindowEventFilters(StackPane root) {
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
        root.addEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
    }

    /// Removes the custom move and resize filters from `root`.
    ///
    /// @param root the event root from which the filters must be removed
    private void removeWindowEventFilters(StackPane root) {
        root.removeEventFilter(MouseEvent.MOUSE_RELEASED, onMouseReleased);
        root.removeEventFilter(MouseEvent.MOUSE_DRAGGED, onMouseDragged);
        root.removeEventFilter(MouseEvent.MOUSE_MOVED, onMouseMoved);
    }

    /// Returns whether `x` lies in the event root's right resize inset.
    ///
    /// @param x the horizontal pointer coordinate in the event root
    /// @return `true` when the coordinate is on the right edge
    private boolean isRightEdge(double x) {
        Insets insets = decorator.getResizeInsets();
        return x < windowEventRoot.getWidth()
                && x >= windowEventRoot.getWidth() - insets.getRight();
    }

    /// Returns whether `y` lies in the event root's top resize inset.
    ///
    /// @param y the vertical pointer coordinate in the event root
    /// @return `true` when the coordinate is on the top edge
    private boolean isTopEdge(double y) {
        return y >= 0 && y <= decorator.getResizeInsets().getTop();
    }

    /// Returns whether `y` lies in the event root's bottom resize inset.
    ///
    /// @param y the vertical pointer coordinate in the event root
    /// @return `true` when the coordinate is on the bottom edge
    private boolean isBottomEdge(double y) {
        return y < windowEventRoot.getHeight()
                && y >= windowEventRoot.getHeight() - decorator.getResizeInsets().getBottom();
    }

    /// Returns whether `x` lies in the event root's left resize inset.
    ///
    /// @param x the horizontal pointer coordinate in the event root
    /// @return `true` when the coordinate is on the left edge
    private boolean isLeftEdge(double x) {
        return x >= 0 && x <= decorator.getResizeInsets().getLeft();
    }

    /// Resizes the current stage while enforcing its configured minimum dimensions.
    ///
    /// A negative dimension leaves that dimension unchanged.
    ///
    /// @param newWidth  the requested width, or a negative value to preserve it
    /// @param newHeight the requested height, or a negative value to preserve it
    private void resizeStage(double newWidth, double newHeight) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null) {
            return;
        }

        if (newWidth < 0) {
            newWidth = stage.getWidth();
        }
        newWidth = Math.max(newWidth, stage.getMinWidth());
        if (titleBar.getMinWidth() >= 0) {
            newWidth = Math.max(newWidth, titleBar.getMinWidth());
        }

        if (newHeight < 0) {
            newHeight = stage.getHeight();
        }
        newHeight = Math.max(newHeight, stage.getMinHeight());
        if (titleBar.getMinHeight() >= 0) {
            newHeight = Math.max(newHeight, titleBar.getMinHeight());
        }

        // Width and height must be set together to avoid JDK-8344372.
        stage.setWidth(newWidth);
        stage.setHeight(newHeight);
    }

    /// Selects the resize cursor for the pointer's current event-root position.
    ///
    /// @param event the pointer movement event
    private void onMouseMoved(MouseEvent event) {
        @Nullable Stage stage = getCurrentStage();
        StackPane root = windowEventRoot;
        if (stage == null || stage.isFullScreen() || !stage.isResizable()) {
            root.setCursor(Cursor.DEFAULT);
            return;
        }

        double x = event.getX();
        double y = event.getY();
        Insets insets = decorator.getResizeInsets();
        double diagonalSize = Math.max(
                Math.max(insets.getLeft(), insets.getRight()),
                Math.max(insets.getTop(), insets.getBottom())) + 10;

        if (isRightEdge(x)) {
            if (y < diagonalSize) {
                root.setCursor(Cursor.NE_RESIZE);
            } else if (y > root.getHeight() - diagonalSize) {
                root.setCursor(Cursor.SE_RESIZE);
            } else {
                root.setCursor(Cursor.E_RESIZE);
            }
        } else if (isLeftEdge(x)) {
            if (y < diagonalSize) {
                root.setCursor(Cursor.NW_RESIZE);
            } else if (y > root.getHeight() - diagonalSize) {
                root.setCursor(Cursor.SW_RESIZE);
            } else {
                root.setCursor(Cursor.W_RESIZE);
            }
        } else if (isTopEdge(y)) {
            if (x < diagonalSize) {
                root.setCursor(Cursor.NW_RESIZE);
            } else if (x > root.getWidth() - diagonalSize) {
                root.setCursor(Cursor.NE_RESIZE);
            } else {
                root.setCursor(Cursor.N_RESIZE);
            }
        } else if (isBottomEdge(y)) {
            if (x < diagonalSize) {
                root.setCursor(Cursor.SW_RESIZE);
            } else if (x > root.getWidth() - diagonalSize) {
                root.setCursor(Cursor.SE_RESIZE);
            } else {
                root.setCursor(Cursor.S_RESIZE);
            }
        } else {
            root.setCursor(Cursor.DEFAULT);
        }
    }

    /// Ends the current move or resize gesture.
    ///
    /// @param event the release event
    private void onMouseReleased(MouseEvent event) {
        decorator.setDragging(false);
        decorator.setAllowMove(false);
    }

    /// Moves or resizes the attached stage according to the active cursor.
    ///
    /// @param event the primary-button drag event
    private void onMouseDragged(MouseEvent event) {
        @Nullable Stage stage = getCurrentStage();
        if (stage == null) {
            return;
        }

        if (!decorator.isDragging()) {
            decorator.setDragging(true);
            mouseInitX = event.getScreenX();
            mouseInitY = event.getScreenY();
            stageInitX = stage.getX();
            stageInitY = stage.getY();
            stageInitWidth = stage.getWidth();
            stageInitHeight = stage.getHeight();
        }

        if (stage.isFullScreen() || !event.isPrimaryButtonDown() || event.isStillSincePress()) {
            return;
        }

        double dx = event.getScreenX() - mouseInitX;
        double dy = event.getScreenY() - mouseInitY;
        Cursor cursor = windowEventRoot.getCursor();

        if (decorator.isAllowMove() && cursor == Cursor.DEFAULT) {
            stage.setX(stageInitX + dx);
            stage.setY(stageInitY + dy);
            event.consume();
        }

        if (!stage.isResizable()) {
            return;
        }

        if (cursor == Cursor.E_RESIZE) {
            resizeStage(stageInitWidth + dx, -1);
            event.consume();
        } else if (cursor == Cursor.S_RESIZE) {
            resizeStage(-1, stageInitHeight + dy);
            event.consume();
        } else if (cursor == Cursor.W_RESIZE) {
            resizeStage(stageInitWidth - dx, -1);
            stage.setX(stageInitX + stageInitWidth - stage.getWidth());
            event.consume();
        } else if (cursor == Cursor.N_RESIZE) {
            resizeStage(-1, stageInitHeight - dy);
            stage.setY(stageInitY + stageInitHeight - stage.getHeight());
            event.consume();
        } else if (cursor == Cursor.SE_RESIZE) {
            resizeStage(stageInitWidth + dx, stageInitHeight + dy);
            event.consume();
        } else if (cursor == Cursor.SW_RESIZE) {
            resizeStage(stageInitWidth - dx, stageInitHeight + dy);
            stage.setX(stageInitX + stageInitWidth - stage.getWidth());
            event.consume();
        } else if (cursor == Cursor.NW_RESIZE) {
            resizeStage(stageInitWidth - dx, stageInitHeight - dy);
            stage.setX(stageInitX + stageInitWidth - stage.getWidth());
            stage.setY(stageInitY + stageInitHeight - stage.getHeight());
            event.consume();
        } else if (cursor == Cursor.NE_RESIZE) {
            resizeStage(stageInitWidth + dx, stageInitHeight - dy);
            stage.setY(stageInitY + stageInitHeight - stage.getHeight());
            event.consume();
        }
    }

    /// Produces directional transitions for page-title changes.
    private enum NavBarAnimations implements TransitionPane.AnimationProducer {
        /// Moves the next title in from the right.
        NEXT {
            /// {@inheritDoc}
            @Override
            public void init(TransitionPane container, Node previousNode, Node nextNode) {
                super.init(container, previousNode, nextNode);
                nextNode.setTranslateX(container.getWidth());
            }

            /// {@inheritDoc}
            @Override
            public Timeline animate(
                    Pane container,
                    Node previousNode,
                    Node nextNode,
                    Duration duration,
                    Interpolator interpolator) {
                return createTimeline(previousNode, nextNode, 50, -50, duration, interpolator);
            }

            /// {@inheritDoc}
            @Override
            public TransitionPane.AnimationProducer opposite() {
                return PREVIOUS;
            }
        },

        /// Moves the previous title in from the left.
        PREVIOUS {
            /// {@inheritDoc}
            @Override
            public void init(TransitionPane container, Node previousNode, Node nextNode) {
                super.init(container, previousNode, nextNode);
                nextNode.setTranslateX(-container.getWidth());
            }

            /// {@inheritDoc}
            @Override
            public Timeline animate(
                    Pane container,
                    Node previousNode,
                    Node nextNode,
                    Duration duration,
                    Interpolator interpolator) {
                return createTimeline(previousNode, nextNode, -50, 50, duration, interpolator);
            }

            /// {@inheritDoc}
            @Override
            public TransitionPane.AnimationProducer opposite() {
                return NEXT;
            }
        };

        /// Creates a title transition with the supplied horizontal offsets.
        ///
        /// @param previousNode  the title being removed
        /// @param nextNode      the title being shown
        /// @param nextOffset    the initial offset of the next title
        /// @param previousOffset the final offset of the previous title
        /// @param duration      the transition duration
        /// @param interpolator  the transition interpolator
        /// @return the unstarted transition timeline
        private static Timeline createTimeline(
                Node previousNode,
                Node nextNode,
                double nextOffset,
                double previousOffset,
                Duration duration,
                Interpolator interpolator) {
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(nextNode.translateXProperty(), nextOffset, interpolator),
                            new KeyValue(previousNode.translateXProperty(), 0, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 0, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 1, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.translateXProperty(), previousOffset, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator),
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator)));
        }
    }
}
