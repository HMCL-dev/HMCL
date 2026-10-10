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
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

/// Coordinates the main window's visual tree, navigation, dialogs, and native window behavior.
///
/// A decorator owns one navigation stack and one stable root node. The root manages its shadow insets and effect
/// without replacing or reparenting the main-window content. Attaching a stage also attaches the retained scene;
/// hiding or closing that stage does not discard either the scene or the decorator state.
@NotNullByDefault
public final class Decorator {
    /// The minimum width, in pixels, of the visible window content.
    private static final double MIN_CONTENT_WIDTH = 800.0;

    /// The minimum height, in pixels, including the 450-pixel background and 40-pixel title bar.
    private static final double MIN_CONTENT_HEIGHT = 450.0 + 40.0;

    /// The minimum visible overlap, in pixels, used to accept a persisted window position.
    private static final double MIN_VISIBLE_WINDOW_AREA = 20.0;

    /// The primary-screen bounds used by the persisted normalized window coordinates.
    private static final Rectangle2D PRIMARY_SCREEN_BOUNDS = Screen.getPrimary().getBounds();

    /// The space, in pixels, reserved on each side of the window content for the custom shadow.
    private static final double SHADOW_SIZE = 8.0;

    /// The width, in pixels, of the resize hit area while the custom shadow is disabled.
    private static final double RESIZE_BORDER_SIZE = 8.0;

    /// The inward resize hit area used while the custom shadow is disabled.
    private static final Insets RESIZE_INSETS = new Insets(RESIZE_BORDER_SIZE);

    /// The root padding used to reserve space for the custom window shadow.
    private static final Insets SHADOW_INSETS = new Insets(SHADOW_SIZE);

    /// The stable root exposed to the scene and responsible for shadow insets.
    private final StackPane root = new StackPane();

    /// The stable content host to which the optional window-shadow effect is applied.
    private final StackPane shadowContainer = new StackPane();

    /// The drop-shadow effect installed on [#shadowContainer] while the window shadow is enabled.
    private final DropShadow windowShadow = new DropShadow(
            BlurType.ONE_PASS_BOX,
            Color.rgb(0, 0, 0, 0.4),
            10,
            0.3,
            0.0,
            0.0);

    /// The navigation stack rendered in the main window.
    private final Navigator navigator = new Navigator();

    /// Reflective access to system decoration, or `null` on unsupported runtimes.
    private final @Nullable NativeWindowDecoration nativeDecoration = NativeWindowDecoration.create();

    /// Whether the attached stage delegates window decoration and gestures to the platform.
    private boolean nativeDecorationEnabled;

    /// Installs application-specific identity and lifecycle integration on replacement stages before attachment.
    private final Consumer<Stage> stageInitializer;

    /// Whether an appearance change has already queued a window-style check.
    private boolean windowStyleUpdateQueued;

    /// Coalesces appearance changes so stage replacement runs after theme bindings have updated.
    private final ChangeListener<Boolean> windowStyleListener = (observable, oldValue, newValue) -> {
        if (!windowStyleUpdateQueued) {
            windowStyleUpdateQueued = true;
            Platform.runLater(() -> {
                windowStyleUpdateQueued = false;
                updateWindowStyle();
            });
        }
    };

    /// The clipped main-window content hosted inside [#shadowContainer].
    private final MainWindowPane mainWindowPane;

    /// The snackbar shared by all main-window toast messages.
    private final JFXSnackbar snackbar = new JFXSnackbar();

    /// Whether the navigation bar should display its close or home action.
    private final BooleanProperty canClose = new SimpleBooleanProperty(this, "canClose");

    /// Whether the navigation close action should use the home icon.
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(this, "showCloseAsHome");

    /// The width of the visible window content, excluding native borders and custom shadow padding.
    private final DoubleProperty contentWidth = new SimpleDoubleProperty(this, "contentWidth");

    /// The height of the visible window content, excluding native borders and custom shadow padding.
    private final DoubleProperty contentHeight = new SimpleDoubleProperty(this, "contentHeight");

    /// The stage currently controlled by this decorator, or `null` while detached.
    private @Nullable Stage stage;

    /// Tracks the attached stage's content geometry, or `null` while detached.
    private @Nullable WindowBounds windowBounds;

    /// The title-bar state supplied by the current page, or `null` before navigation is initialized.
    private final ObjectProperty<DecoratorPage.@Nullable State> state = new SimpleObjectProperty<>(this, "state");

    /// Whether a drag starting at the current pointer location may move the stage.
    private boolean allowMove;

    /// Whether a stage move or resize gesture is currently active.
    private boolean dragging;

    /// The initial horizontal pointer position of the active move or resize gesture.
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

    /// Whether the next de-iconification should play the restore animation.
    private boolean playRestoreMinimizeAnimation;

    /// The active root-node window animation, or `null` when the root is stationary.
    private @Nullable Timeline windowAnimation;

    /// Handles restore animation when the current stage leaves its iconified state.
    private final ChangeListener<Boolean> iconifiedListener = (observable, oldValue, iconified) -> {
        if (playRestoreMinimizeAnimation && !iconified) {
            playRestoreMinimizeAnimation = false;
            playRestoreAnimation();
        }
    };

    /// Plays the opening animation immediately before the attached stage becomes visible.
    private final EventHandler<WindowEvent> windowShowingHandler = event -> playOpenAnimation();

    /// Updates the custom window decoration when the attached stage changes maximized or full-screen state.
    private final InvalidationListener stageDecorationListener = observable -> {
        @Nullable Stage currentStage = stage;
        if (currentStage != null) {
            updateWindowDecoration(WindowState.isMaximized(currentStage) || currentStage.isFullScreen());
        }
    };

    /// The direction used by the next title-bar transition.
    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;

    /// Creates a detached decorator and initializes its navigation stack.
    ///
    /// The decorator initially enables its custom window shadow. No scene is created until
    /// [#attachStage(Stage)] is called or [#getRoot()] is otherwise installed in a scene.
    /// Appearance changes replace the attached stage when required, preserving its retained scene and window state.
    ///
    /// @param mainPage the permanent root page of the navigation stack
    /// @param stageInitializer configures each replacement stage on the JavaFX application thread before attachment;
    ///                         it must not show the stage and is not called for stages supplied to [#attachStage(Stage)]
    public Decorator(Node mainPage, Consumer<Stage> stageInitializer) {
        this.stageInitializer = Objects.requireNonNull(stageInitializer);
        navigator.setOnNavigated(this::onNavigated);

        mainWindowPane = new MainWindowPane(this);
        shadowContainer.getChildren().setAll(mainWindowPane);
        root.setPickOnBounds(true);
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        root.getChildren().setAll(shadowContainer);
        updateWindowDecoration(false);
        root.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        root.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        root.addEventFilter(MouseEvent.MOUSE_MOVED, this::onMouseMoved);

        snackbar.registerSnackbarContainer(mainWindowPane);

        navigator.init(mainPage);

        setupInputRouting();

        // Setup authlib injector DnD
        root.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        root.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }

    /// Returns the node that must be installed as the scene root.
    ///
    /// @return the stable scene root owned by this decorator
    public Parent getRoot() {
        return root;
    }

    /// Returns the space reserved inside the scene for custom window shadows.
    ///
    /// @return the custom shadow insets, or [Insets#EMPTY] with native decoration, supported maximization, or full-screen
    public Insets getWindowInsets() {
        return root.getPadding();
    }

    /// Returns the visible window-content width property, excluding native borders and custom shadow padding.
    ///
    /// @return the current content-width property
    public ReadOnlyDoubleProperty contentWidthProperty() {
        return contentWidth;
    }

    /// Returns the visible window-content height property, excluding native borders and custom shadow padding.
    ///
    /// @return the current content-height property
    public ReadOnlyDoubleProperty contentHeightProperty() {
        return contentHeight;
    }

    /// Returns the insets used for custom resize hit testing.
    ///
    /// @return the shadow insets for a normal window, or an inward resize area while maximized or full-screen
    private Insets getResizeInsets() {
        Insets windowInsets = getWindowInsets();
        return Insets.EMPTY.equals(windowInsets) ? RESIZE_INSETS : windowInsets;
    }

    /// Updates the shadow, outer insets, and content corner shape for an edge-to-edge window state.
    ///
    /// @param edgeToEdge whether the attached stage is maximized under [WindowState]'s policy or full-screen
    private void updateWindowDecoration(boolean edgeToEdge) {
        boolean customDecoration = !nativeDecorationEnabled && !edgeToEdge;
        root.setPadding(customDecoration ? SHADOW_INSETS : Insets.EMPTY);
        shadowContainer.setEffect(customDecoration ? windowShadow : null);
        mainWindowPane.setWindowCornersRounded(customDecoration);
    }

    /// Returns the pane on which application dialogs are stacked.
    ///
    /// @return the stable main-window pane used as the dialog container
    public StackPane getDialogContainer() {
        return mainWindowPane;
    }

    /// Returns the pane that behaves as the overlay of dialogs.
    ///
    /// @return the dialog overlay pane
    public StackPane getDialogOverlayPane() {
        return mainWindowPane.getDialogOverlayPane();
    }

    /// Returns the navigation stack rendered by the main window.
    ///
    /// @return this decorator's navigator
    Navigator getNavigator() {
        return navigator;
    }

    /// Navigates to `node` using the supplied transition.
    ///
    /// @param node              the page to push
    /// @param animationProducer the transition used to reveal the page
    /// @param duration          the transition duration
    /// @param interpolator      the transition interpolator
    public void navigate(
            Node node,
            TransitionPane.AnimationProducer animationProducer,
            Duration duration,
            Interpolator interpolator) {
        navigator.navigate(node, animationProducer, duration, interpolator);
    }

    /// Returns whether the navigation stack contains a page before the current page.
    ///
    /// @return the navigator's read-only backable property
    public ReadOnlyBooleanProperty backableProperty() {
        return navigator.backableProperty();
    }

    /// Shows `node` as the current application dialog.
    ///
    /// If the window has not yet been attached to a scene, showing is deferred to the JavaFX application thread.
    ///
    /// @param node the dialog content
    public void showDialog(Node node) {
        DialogUtils.show(this, node);
    }

    /// Queues `node` to be shown after the current application dialog closes.
    ///
    /// @param node the dialog content
    public void showDialogLater(Node node) {
        DialogUtils.showLater(this, node);
    }

    /// Shows a transient message at the bottom center of the main window.
    ///
    /// @param content the message to show
    public void showToast(String content) {
        snackbar.fireEvent(new JFXSnackbar.SnackbarEvent(new JFXSnackbarLayout(content)));
    }

    /// Starts a wizard without an explicit category title.
    ///
    /// @param wizardProvider the wizard provider to start
    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    /// Starts a wizard and pushes its displayer onto the navigation stack.
    ///
    /// @param wizardProvider the wizard provider to start
    /// @param category       the category title, or `null` to use the provider's default presentation
    public void startWizard(WizardProvider wizardProvider, @Nullable String category) {
        FXUtils.checkFxUserThread();
        navigator.navigate(
                new DecoratorWizardDisplayer(wizardProvider, category),
                ContainerAnimations.FORWARD,
                Motion.SHORT4,
                Motion.EASE);
    }

    /// Marks `node` as an area from which a primary-button drag may move the stage.
    ///
    /// @param node the drag-enabled node
    public void capableDraggingWindow(Node node) {
        if (nativeDecoration != null) {
            nativeDecoration.setDraggable(node, true);
        }
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (!nativeDecorationEnabled) {
                allowMove = true;
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (!dragging) {
                allowMove = false;
            }
        });
    }

    /// Configures window dragging through an overlay while retaining ordinary mouse picking.
    ///
    /// Native decoration permits dragging only over underlying draggable header areas. Custom decoration
    /// permits dragging from the overlay itself. Interactive content must be excluded with [#forbidDraggingWindow(Node)].
    ///
    /// @param overlay the overlay above the window content and title bar
    public void capableDraggingOverlay(Node overlay) {
        capableDraggingWindow(overlay);
        if (nativeDecoration != null) {
            nativeDecoration.setDragTransparent(overlay);
        }
    }

    /// Registers `node` as the title-bar area used to restore and maximize the attached stage.
    ///
    /// @param node the title-bar node
    void registerTitleBar(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, this::onTitleBarDoubleClick);
        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onTitleBarDragged);
    }

    /// Prevents a stage-move gesture from starting while the pointer is over `node`.
    ///
    /// @param node the node that must consume drag eligibility
    public void forbidDraggingWindow(Node node) {
        if (nativeDecoration != null) {
            nativeDecoration.setDraggable(node, false);
        }
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (!nativeDecorationEnabled) {
                allowMove = false;
                event.consume();
            }
        });
    }

    /// Toggles maximization when the primary button double-clicks a movable title-bar point.
    ///
    /// @param event the title-bar click event
    private void onTitleBarDoubleClick(MouseEvent event) {
        @Nullable Stage currentStage = stage;
        if (nativeDecorationEnabled
                || currentStage == null
                || !WindowState.supportsMaximization(currentStage)
                || event.getButton() != MouseButton.PRIMARY
                || event.getClickCount() != 2) {
            return;
        }

        currentStage.setMaximized(!WindowState.isMaximized(currentStage));
        event.consume();
    }

    /// Restores a maximized stage under the pointer when its title bar is dragged.
    ///
    /// @param event the title-bar drag event
    private void onTitleBarDragged(MouseEvent event) {
        @Nullable Stage currentStage = stage;
        if (nativeDecorationEnabled || currentStage == null || dragging || !WindowState.isMaximized(currentStage)) {
            return;
        }

        dragging = true;
        mouseInitX = event.getScreenX();
        mouseInitY = event.getScreenY();
        currentStage.setMaximized(false);
        stageInitWidth = currentStage.getWidth();
        stageInitHeight = currentStage.getHeight();
        currentStage.setY(stageInitY = 0);
        currentStage.setX(stageInitX = mouseInitX - stageInitWidth / 2);
    }

    /// Returns whether `x` lies in the root's right resize inset.
    ///
    /// @param x the horizontal pointer coordinate in the root
    /// @return `true` when the coordinate is on the right edge
    private boolean isRightEdge(double x) {
        Insets insets = getResizeInsets();
        return x < root.getWidth() && x >= root.getWidth() - insets.getRight();
    }

    /// Returns whether `y` lies in the root's top resize inset.
    ///
    /// @param y the vertical pointer coordinate in the root
    /// @return `true` when the coordinate is on the top edge
    private boolean isTopEdge(double y) {
        return y >= 0 && y <= getResizeInsets().getTop();
    }

    /// Returns whether `y` lies in the root's bottom resize inset.
    ///
    /// @param y the vertical pointer coordinate in the root
    /// @return `true` when the coordinate is on the bottom edge
    private boolean isBottomEdge(double y) {
        return y < root.getHeight() && y >= root.getHeight() - getResizeInsets().getBottom();
    }

    /// Returns whether `x` lies in the root's left resize inset.
    ///
    /// @param x the horizontal pointer coordinate in the root
    /// @return `true` when the coordinate is on the left edge
    private boolean isLeftEdge(double x) {
        return x >= 0 && x <= getResizeInsets().getLeft();
    }

    /// Resizes the attached stage while enforcing its configured minimum dimensions.
    ///
    /// A negative dimension leaves that dimension unchanged.
    ///
    /// @param newWidth  the requested width, or a negative value to preserve it
    /// @param newHeight the requested height, or a negative value to preserve it
    private void resizeStage(double newWidth, double newHeight) {
        @Nullable Stage currentStage = stage;
        if (currentStage == null) {
            return;
        }

        if (newWidth < 0) {
            newWidth = currentStage.getWidth();
        }
        newWidth = Math.max(newWidth, currentStage.getMinWidth());

        if (newHeight < 0) {
            newHeight = currentStage.getHeight();
        }
        newHeight = Math.max(newHeight, currentStage.getMinHeight());

        // Width and height must be set together to avoid JDK-8344372.
        currentStage.setWidth(newWidth);
        currentStage.setHeight(newHeight);
    }

    /// Selects the resize cursor for the pointer's current root position.
    ///
    /// @param event the pointer movement event
    private void onMouseMoved(MouseEvent event) {
        @Nullable Stage currentStage = stage;
        if (nativeDecorationEnabled || currentStage == null
                || currentStage.isIconified()
                || currentStage.isFullScreen()
                || WindowState.isMaximized(currentStage)
                || !currentStage.isResizable()) {
            root.setCursor(Cursor.DEFAULT);
            return;
        }

        double x = event.getX();
        double y = event.getY();
        Insets insets = getResizeInsets();
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
        dragging = false;
        allowMove = false;
    }

    /// Moves or resizes the attached stage according to the active cursor.
    ///
    /// @param event the primary-button drag event
    private void onMouseDragged(MouseEvent event) {
        @Nullable Stage currentStage = stage;
        if (nativeDecorationEnabled || currentStage == null
                || currentStage.isIconified()
                || currentStage.isFullScreen()
                || WindowState.isMaximized(currentStage)
                || !event.isPrimaryButtonDown()
                || event.isStillSincePress()) {
            return;
        }

        if (!dragging) {
            dragging = true;
            mouseInitX = event.getScreenX();
            mouseInitY = event.getScreenY();
            stageInitX = currentStage.getX();
            stageInitY = currentStage.getY();
            stageInitWidth = currentStage.getWidth();
            stageInitHeight = currentStage.getHeight();
        }

        double dx = event.getScreenX() - mouseInitX;
        double dy = event.getScreenY() - mouseInitY;
        Cursor cursor = root.getCursor();

        if (allowMove && cursor == Cursor.DEFAULT) {
            currentStage.setX(stageInitX + dx);
            currentStage.setY(stageInitY + dy);
            event.consume();
        }

        if (!currentStage.isResizable()) {
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
            currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
            event.consume();
        } else if (cursor == Cursor.N_RESIZE) {
            resizeStage(-1, stageInitHeight - dy);
            currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
            event.consume();
        } else if (cursor == Cursor.SE_RESIZE) {
            resizeStage(stageInitWidth + dx, stageInitHeight + dy);
            event.consume();
        } else if (cursor == Cursor.SW_RESIZE) {
            resizeStage(stageInitWidth - dx, stageInitHeight + dy);
            currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
            event.consume();
        } else if (cursor == Cursor.NW_RESIZE) {
            resizeStage(stageInitWidth - dx, stageInitHeight - dy);
            currentStage.setX(stageInitX + stageInitWidth - currentStage.getWidth());
            currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
            event.consume();
        } else if (cursor == Cursor.NE_RESIZE) {
            resizeStage(stageInitWidth + dx, stageInitHeight - dy);
            currentStage.setY(stageInitY + stageInitHeight - currentStage.getHeight());
            event.consume();
        }
    }

    /// Returns the scene retained by this decorator's root.
    ///
    /// A detached decorator retains its scene after [#detachStage()] so that stylesheets and scene state survive
    /// attachment to another stage.
    ///
    /// @return the retained scene, or `null` if the root has never been installed in one
    public @Nullable Scene getScene() {
        return root.getScene();
    }

    /// Returns the persisted normal content rectangle without native borders or custom shadow padding.
    ///
    /// An off-screen persisted position is replaced with a position centered on the primary screen.
    ///
    /// @return the initial content bounds in JavaFX screen coordinates
    private Rectangle2D initialContentBounds() {
        double initialContentWidth = Math.max(MIN_CONTENT_WIDTH, SettingsManager.state().getWidth());
        double initialContentHeight = Math.max(MIN_CONTENT_HEIGHT, SettingsManager.state().getHeight());
        double initialContentX = SettingsManager.state().getX() * PRIMARY_SCREEN_BOUNDS.getWidth();
        double initialContentY = SettingsManager.state().getY() * PRIMARY_SCREEN_BOUNDS.getHeight();

        boolean visible = false;
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            if (bounds.getMinX() + MIN_VISIBLE_WINDOW_AREA <= initialContentX + initialContentWidth
                    && initialContentX <= bounds.getMaxX() - MIN_VISIBLE_WINDOW_AREA
                    && bounds.getMinY() + MIN_VISIBLE_WINDOW_AREA <= initialContentY
                    && initialContentY <= bounds.getMaxY() - MIN_VISIBLE_WINDOW_AREA) {
                visible = true;
                break;
            }
        }

        if (!visible) {
            initialContentX = (PRIMARY_SCREEN_BOUNDS.getWidth() - initialContentWidth) / 2;
            initialContentY = (PRIMARY_SCREEN_BOUNDS.getHeight() - initialContentHeight) / 2;
        }

        return new Rectangle2D(initialContentX, initialContentY, initialContentWidth, initialContentHeight);
    }

    /// Persists a normal content rectangle independently of the window's decoration style.
    ///
    /// @param bounds the content bounds in JavaFX screen coordinates
    private void saveContentBounds(Rectangle2D bounds) {
        SettingsManager.state().setX(bounds.getMinX() / PRIMARY_SCREEN_BOUNDS.getWidth());
        SettingsManager.state().setY(bounds.getMinY() / PRIMARY_SCREEN_BOUNDS.getHeight());
        SettingsManager.state().setWidth(bounds.getWidth());
        SettingsManager.state().setHeight(bounds.getHeight());
    }

    /// Returns the preferred style for the configured native-decoration policy and current theme.
    ///
    /// @return the system style when available and permitted by the policy; otherwise the custom transparent style
    private StageStyle preferredStageStyle() {
        return nativeDecoration != null && nativeDecoration.isPreferred(
                Themes.windowTransparentProperty().get(), Themes.darkModeProperty().get())
                ? nativeDecoration.style : StageStyle.TRANSPARENT;
    }

    /// Replaces the attached stage when transparency or theme brightness requires a different decoration style.
    ///
    /// Preserves the scene, navigation, dialogs, normal bounds, supported window states, and focus owner.
    /// Hidden windows remain hidden. Does nothing while detached or when the current style is still suitable.
    /// Must run on the JavaFX application thread after theme bindings have updated.
    private void updateWindowStyle() {
        FXUtils.checkFxUserThread();
        @Nullable Stage previousStage = stage;
        if (previousStage == null || previousStage.getStyle() == preferredStageStyle()) {
            return;
        }

        boolean showing = previousStage.isShowing();
        boolean maximized = WindowState.isMaximized(previousStage);
        boolean fullScreen = previousStage.isFullScreen();
        boolean iconified = previousStage.isIconified();
        @Nullable Node focusOwner = previousStage.getScene().getFocusOwner();

        Stage replacement = new Stage();
        stageInitializer.accept(replacement);
        replacement.setTitle(previousStage.getTitle());
        replacement.getIcons().setAll(previousStage.getIcons());
        replacement.setOnCloseRequest(previousStage.getOnCloseRequest());
        replacement.setResizable(previousStage.isResizable());
        replacement.setAlwaysOnTop(previousStage.isAlwaysOnTop());
        replacement.setFullScreenExitHint(previousStage.getFullScreenExitHint());
        replacement.setFullScreenExitKeyCombination(previousStage.getFullScreenExitKeyCombination());

        detachStage();
        previousStage.hide();
        attachStage(replacement);
        replacement.setMaximized(maximized && WindowState.supportsMaximization(replacement));
        replacement.setFullScreen(fullScreen);
        replacement.setIconified(iconified);
        if (showing) {
            replacement.show();
        }
        if (focusOwner != null) {
            focusOwner.requestFocus();
        }
    }

    /// Attaches the retained scene and native window behavior to `newStage`.
    ///
    /// If the root has no scene, this method reuses `newStage`'s scene and replaces its root, or creates a
    /// transparent scene when the stage has none. If the retained scene belongs to another stage, it is detached
    /// from that stage before being installed on `newStage`. A newly attached stage receives the persisted normal
    /// content bounds. Native frame offsets and minimum size are resolved when the stage is shown.
    /// The native-decoration policy selects the system or custom transparent decoration. Automatic mode uses
    /// system decoration for supported opaque windows, except for dark windows on Windows; explicit enablement
    /// bypasses the brightness exclusion. Transparent windows always use custom decoration.
    /// Any active window animation is cancelled and reset. Custom window
    /// animations run only with custom decoration. Window-style listeners remain active until [#detachStage()].
    /// On macOS, native application appearance updates are installed once per stage and retained across detachment.
    /// This method does not show the stage or invoke the replacement-stage initializer.
    ///
    /// @param newStage the stage to attach, which must be accessed on the JavaFX application thread
    /// @return the scene installed on `newStage`
    /// @throws IllegalStateException if `newStage` has already been shown with a different style, or if the
    ///                               retained root or scene cannot be transferred from its current owner
    public Scene attachStage(Stage newStage) {
        FXUtils.checkFxUserThread();
        stopWindowAnimation();

        if (stage != null && stage != newStage) {
            detachStage();
        }

        StageStyle style = preferredStageStyle();
        if (newStage.getStyle() != style) {
            newStage.initStyle(style);
        }
        nativeDecorationEnabled = nativeDecoration != null && style == nativeDecoration.style;
        if (nativeDecoration != null) {
            nativeDecoration.setContent(null);
            if (nativeDecorationEnabled) {
                nativeDecoration.configureStage(newStage);
            }
        }
        mainWindowPane.setNativeDecoration(nativeDecorationEnabled ? nativeDecoration : null);

        @Nullable Scene retainedScene = root.getScene();
        Scene scene;
        if (retainedScene == null) {
            @Nullable Scene stageScene = newStage.getScene();
            if (stageScene == null) {
                scene = new Scene(root);
            } else {
                if (root.getParent() != null) {
                    throw new IllegalStateException("Decorator root is already attached to a parent");
                }
                stageScene.setRoot(root);
                scene = stageScene;
            }
        } else {
            scene = retainedScene;
            @Nullable Window owner = scene.getWindow();
            if (owner != null && owner != newStage) {
                if (owner instanceof Stage ownerStage) {
                    ownerStage.setScene(null);
                } else {
                    throw new IllegalStateException("Decorator scene is attached to a non-stage window");
                }
            }
        }

        if (nativeDecorationEnabled) {
            scene.fillProperty().bind(Themes.colorSchemeProperty().getSurfaceContainer());
        } else {
            scene.fillProperty().unbind();
            scene.setFill(Color.TRANSPARENT);
        }

        if (newStage.getScene() != scene) {
            newStage.setScene(scene);
        }

        if (stage != newStage) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                Themes.applyNativeDarkMode(newStage);
            }
            playRestoreMinimizeAnimation = false;
            updateWindowDecoration(false);
            stage = newStage;
            newStage.addEventHandler(WindowEvent.WINDOW_SHOWING, windowShowingHandler);
            newStage.iconifiedProperty().addListener(iconifiedListener);
            newStage.maximizedProperty().addListener(stageDecorationListener);
            newStage.fullScreenProperty().addListener(stageDecorationListener);
            windowBounds = new WindowBounds(newStage, root, initialContentBounds(),
                    MIN_CONTENT_WIDTH, MIN_CONTENT_HEIGHT, this::saveContentBounds);
            contentWidth.bind(windowBounds.contentWidthProperty());
            contentHeight.bind(windowBounds.contentHeightProperty());
            updateWindowDecoration(WindowState.isMaximized(newStage) || newStage.isFullScreen());
            Themes.windowTransparentProperty().addListener(windowStyleListener);
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                Themes.darkModeProperty().addListener(windowStyleListener);
            }
        }
        root.setCursor(Cursor.DEFAULT);
        dragging = false;
        allowMove = false;
        return scene;
    }

    /// Detaches the retained scene and native window behavior from the current stage.
    ///
    /// The root, scene, navigation state, dialogs, and snackbar remain owned by this decorator and may subsequently
    /// be attached to another stage. Calling this method while detached has no effect beyond resetting root transforms.
    /// Removes window-style listeners; a queued style check does nothing unless another stage has been attached.
    /// Hiding a stage that will later be shown again does not require detachment.
    public void detachStage() {
        FXUtils.checkFxUserThread();
        stopWindowAnimation();

        if (windowBounds != null) {
            windowBounds.close();
            contentWidth.unbind();
            contentHeight.unbind();
            windowBounds = null;
        }
        if (stage != null) {
            Themes.windowTransparentProperty().removeListener(windowStyleListener);
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                Themes.darkModeProperty().removeListener(windowStyleListener);
            }
            stage.removeEventHandler(WindowEvent.WINDOW_SHOWING, windowShowingHandler);
            stage.iconifiedProperty().removeListener(iconifiedListener);
            stage.maximizedProperty().removeListener(stageDecorationListener);
            stage.fullScreenProperty().removeListener(stageDecorationListener);
            if (stage.getScene() == root.getScene()) {
                stage.setScene(null);
            }
        }
        stage = null;
        updateWindowDecoration(false);
        root.setCursor(Cursor.DEFAULT);
        dragging = false;
        allowMove = false;
    }

    /// Returns the stage currently controlled by this decorator.
    ///
    /// @return the current stage, or `null` while detached
    public @Nullable Stage getStage() {
        return stage;
    }

    /// Returns the title-bar state supplied by the current page.
    ///
    /// @return the current state, or `null` before navigation initialization
    DecoratorPage.@Nullable State getState() {
        return state.get();
    }

    /// Returns the property observed by the main window's title bar.
    ///
    /// @return the current-page state property
    ObjectProperty<DecoratorPage.@Nullable State> stateProperty() {
        return state;
    }

    /// Returns whether the navigation bar should show its close or home action.
    ///
    /// @return the close-action property
    BooleanProperty canCloseProperty() {
        return canClose;
    }

    /// Returns whether the navigation close action should use the home icon.
    ///
    /// @return the home-icon property
    BooleanProperty showCloseAsHomeProperty() {
        return showCloseAsHome;
    }

    /// Returns the direction used by the next navigation-bar transition.
    ///
    /// @return the pending navigation direction
    Navigation.NavigationDirection getNavigationDirection() {
        return navigationDirection;
    }

    /// Sets the direction used by the next navigation-bar transition.
    ///
    /// @param navigationDirection the pending navigation direction
    void setNavigationDirection(Navigation.NavigationDirection navigationDirection) {
        this.navigationDirection = navigationDirection;
    }

    /// Plays the configured root-node animation for a stage that is beginning to show.
    ///
    /// Calling this method replaces any active minimize, restore, close, or earlier opening animation. If window
    /// animations are disabled, it restores the stable root transform without starting an animation.
    private void playOpenAnimation() {
        if (nativeDecorationEnabled || !AnimationUtils.playWindowAnimation()) {
            stopWindowAnimation();
            Controllers.trimHeap();
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), 0, Motion.EASE),
                        new KeyValue(root.scaleXProperty(), 0.8, Motion.EASE),
                        new KeyValue(root.scaleYProperty(), 0.8, Motion.EASE),
                        new KeyValue(root.scaleZProperty(), 0.8, Motion.EASE)),
                new KeyFrame(Duration.millis(600),
                        new KeyValue(root.opacityProperty(), 1, Motion.EASE),
                        new KeyValue(root.scaleXProperty(), 1, Motion.EASE),
                        new KeyValue(root.scaleYProperty(), 1, Motion.EASE),
                        new KeyValue(root.scaleZProperty(), 1, Motion.EASE)));
        playWindowAnimation(timeline, () -> {
            resetRootTransform();

            // Proactively triggering GC after the window open animation typically
            // reduces physical memory usage after HMCL startup.
            Controllers.trimHeap();
        });
        // Apply the initial frame before WINDOW_SHOWING returns so the window cannot flash at full opacity.
        timeline.jumpTo(Duration.ZERO);
    }

    /// Minimizes the attached stage, using the configured window animation when supported.
    void minimizeWindow() {
        @Nullable Stage currentStage = getStage();
        if (currentStage == null) {
            return;
        }

        if (!nativeDecorationEnabled && AnimationUtils.playWindowAnimation() && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(root.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(root.translateYProperty(), 0, Motion.EASE),
                            new KeyValue(root.scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(root.scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(root.scaleZProperty(), 1, Motion.EASE)),
                    new KeyFrame(Motion.SHORT4,
                            new KeyValue(root.opacityProperty(), 0, Motion.EASE),
                            new KeyValue(root.translateYProperty(), 200, Motion.EASE),
                            new KeyValue(root.scaleXProperty(), 0.4, Motion.EASE),
                            new KeyValue(root.scaleYProperty(), 0.4, Motion.EASE),
                            new KeyValue(root.scaleZProperty(), 0.4, Motion.EASE)));
            playWindowAnimation(timeline, () -> {
                if (getStage() == currentStage) {
                    currentStage.setIconified(true);
                } else {
                    playRestoreMinimizeAnimation = false;
                    resetRootTransform();
                }
            });
            playRestoreMinimizeAnimation = true;
        } else {
            stopWindowAnimation();
            currentStage.setIconified(true);
        }
    }

    /// Closes the application, using the configured window animation when enabled.
    void closeWindow() {
        if (!nativeDecorationEnabled && AnimationUtils.playWindowAnimation()) {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(root.opacityProperty(), 1, Motion.EASE),
                            new KeyValue(root.scaleXProperty(), 1, Motion.EASE),
                            new KeyValue(root.scaleYProperty(), 1, Motion.EASE),
                            new KeyValue(root.scaleZProperty(), 0.3, Motion.EASE)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(root.opacityProperty(), 0, Motion.EASE),
                            new KeyValue(root.scaleXProperty(), 0.8, Motion.EASE),
                            new KeyValue(root.scaleYProperty(), 0.8, Motion.EASE),
                            new KeyValue(root.scaleZProperty(), 0.8, Motion.EASE)));
            playWindowAnimation(timeline, Launcher::stopApplication);
        } else {
            stopWindowAnimation();
            Launcher.stopApplication();
        }
    }

    /// Closes the current page according to its [DecoratorPage] lifecycle contract.
    void closeCurrentPage() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page && page.isPageCloseable())
            page.closePage();
        else
            navigator.clear();
    }

    /// Navigates back if the current page permits the operation.
    void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page) {
            if (page.back() && navigator.canGoBack()) {
                navigator.close();
            }
        } else if (navigator.canGoBack()) {
            navigator.close();
        }
    }

    /// Refreshes the current page when it currently permits refresh.
    void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable refreshable
                && refreshable.refreshableProperty().get()) {
            refreshable.refresh();
        }
    }

    /// Synchronizes title-bar state and layout bindings after navigation completes.
    ///
    /// @param event the completed navigation event
    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != navigator) {
            return;
        }

        Node target = event.getNode();
        canClose.set(navigator.size() > 2);
        showCloseAsHome.set(!(target instanceof DecoratorPage page) || !page.isPageCloseable());
        setNavigationDirection(event.getDirection());

        state.unbind();
        if (target instanceof DecoratorPage page) {
            state.bind(page.stateProperty());
        } else {
            state.set(new DecoratorPage.State("", null, navigator.canGoBack(), false, true));
        }

        if (target instanceof Region region && region.getParent() instanceof Region parent) {
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }

    /// Installs keyboard, full-screen, and auxiliary mouse-button routing.
    private void setupInputRouting() {
        root.addEventFilter(KeyEvent.ANY, event -> {
            if (!(event.getTarget() instanceof Node target)) {
                return;
            }

            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS
                    && event.getEventType() == KeyEvent.KEY_PRESSED
                    && event.isMetaDown()
                    && event.getCode() == KeyCode.Q) {
                return;
            }

            Node newTarget;
            @Nullable JFXDialogPane currentDialogPane =
                    (JFXDialogPane) getDialogContainer().getProperties()
                            .get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);
            if (currentDialogPane != null && currentDialogPane.peek().isPresent()) {
                newTarget = currentDialogPane.peek().orElseThrow();
            } else {
                newTarget = navigator.getCurrentPage();
            }

            for (@Nullable Node node = target; node != null; node = node.getParent()) {
                if (node == newTarget) {
                    return;
                }
            }

            event.consume();
            newTarget.fireEvent(event.copyFor(event.getSource(), newTarget));
        });

        onEscPressed(navigator, this::back);

        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            navigator.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                @Nullable Stage currentStage = getStage();
                if (currentStage != null && event.getCode() == KeyCode.F11) {
                    currentStage.setFullScreen(!currentStage.isFullScreen());
                    event.consume();
                }
            });
        }

        navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.BACK) {
                back();
                event.consume();
            }
        });
    }

    /// Restores the root node's transform after an animated minimization.
    private void playRestoreAnimation() {
        if (nativeDecorationEnabled) {
            stopWindowAnimation();
            return;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(root.opacityProperty(), 0, Motion.EASE),
                        new KeyValue(root.translateYProperty(), 200, Motion.EASE),
                        new KeyValue(root.scaleXProperty(), 0.4, Motion.EASE),
                        new KeyValue(root.scaleYProperty(), 0.4, Motion.EASE),
                        new KeyValue(root.scaleZProperty(), 0.4, Motion.EASE)),
                new KeyFrame(Motion.SHORT4,
                        new KeyValue(root.opacityProperty(), 1, Motion.EASE),
                        new KeyValue(root.translateYProperty(), 0, Motion.EASE),
                        new KeyValue(root.scaleXProperty(), 1, Motion.EASE),
                        new KeyValue(root.scaleYProperty(), 1, Motion.EASE),
                        new KeyValue(root.scaleZProperty(), 1, Motion.EASE)));
        playWindowAnimation(timeline, this::resetRootTransform);
    }

    /// Cancels the current window animation, starts `timeline`, and runs `onFinished` after normal completion.
    ///
    /// @param timeline   the animation to start
    /// @param onFinished the action to run only when this animation reaches its end
    private void playWindowAnimation(Timeline timeline, Runnable onFinished) {
        stopWindowAnimation();
        windowAnimation = timeline;
        timeline.setOnFinished(event -> {
            if (windowAnimation != timeline) {
                return;
            }
            windowAnimation = null;
            onFinished.run();
        });
        timeline.play();
    }

    /// Cancels any active window animation and restores the stable root transform.
    private void stopWindowAnimation() {
        playRestoreMinimizeAnimation = false;
        if (windowAnimation != null) {
            windowAnimation.stop();
            windowAnimation = null;
        }
        resetRootTransform();
    }

    /// Restores the root transform used while the stage is visible and stationary.
    private void resetRootTransform() {
        root.setOpacity(1);
        root.setTranslateY(0);
        root.setScaleX(1);
        root.setScaleY(1);
        root.setScaleZ(1);
    }
}
