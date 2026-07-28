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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
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

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

/// Coordinates the main window's visual tree, navigation, dialogs, and native window behavior.
///
/// A decorator owns one navigation stack and one root node. Its stage may be replaced or detached through
/// [#setStage(Stage)], but its root node and navigation stack remain valid for the decorator's lifetime.
@NotNullByDefault
public final class Decorator {
    /// The space, in pixels, reserved on each side of the window content for the custom shadow.
    static final double SHADOW_SIZE = 8.0;

    /// The scene root that reserves space for and renders the custom window shadow.
    private final ShadowWrapper root = new ShadowWrapper();

    /// The navigation stack rendered in the main window.
    private final Navigator navigator = new Navigator();

    /// The clipped main-window content hosted inside [#root].
    private final MainWindowPane mainWindowPane;

    /// The snackbar shared by all main-window toast messages.
    private final JFXSnackbar snackbar = new JFXSnackbar();

    /// Whether the navigation bar should display its close or home action.
    private final BooleanProperty canClose = new SimpleBooleanProperty(this, "canClose");

    /// Whether the navigation close action should use the home icon.
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(this, "showCloseAsHome");

    /// The stage currently controlled by this decorator, or `null` while detached.
    private final ObjectProperty<@Nullable Stage> stage = new SimpleObjectProperty<>(this, "stage");

    /// The title-bar state supplied by the current page, or `null` before navigation is initialized.
    private final ObjectProperty<DecoratorPage.@Nullable State> state = new SimpleObjectProperty<>(this, "state");

    /// Whether a drag starting at the current pointer location may move the stage.
    private final ReadOnlyBooleanWrapper allowMove = new ReadOnlyBooleanWrapper(this, "allowMove");

    /// Whether a stage move or resize gesture is currently active.
    private final ReadOnlyBooleanWrapper dragging = new ReadOnlyBooleanWrapper(this, "dragging");

    /// Whether the next de-iconification should play the restore animation.
    private boolean playRestoreMinimizeAnimation;

    /// Handles restore animation when the current stage leaves its iconified state.
    private final ChangeListener<Boolean> iconifiedListener = (observable, oldValue, iconified) -> {
        if (playRestoreMinimizeAnimation && !iconified) {
            playRestoreMinimizeAnimation = false;
            playRestoreAnimation();
        }
    };

    /// Reattaches stage-specific listeners when [#stage] changes.
    private final ChangeListener<@Nullable Stage> stageListener =
            (observable, oldStage, newStage) -> onStageChanged(oldStage, newStage);

    /// The direction used by the next title-bar transition.
    private Navigation.NavigationDirection navigationDirection = Navigation.NavigationDirection.START;

    /// Creates a decorator attached to `primaryStage` and initializes its navigation stack with `mainPage`.
    ///
    /// @param primaryStage the stage controlled by the decorator
    /// @param mainPage     the permanent root page of the navigation stack
    public Decorator(Stage primaryStage, Node mainPage) {
        stage.addListener(stageListener);
        setStage(primaryStage);

        navigator.setOnNavigated(this::onNavigated);

        mainWindowPane = new MainWindowPane(this);
        root.setContent(mainWindowPane);
        snackbar.registerSnackbarContainer(mainWindowPane);

        navigator.init(mainPage);

        setupInputRouting();
        setupAuthlibInjectorDnD();
    }

    /// Returns the node that must be installed as the scene root.
    ///
    /// @return the stable scene root owned by this decorator
    public Parent getRoot() {
        return root;
    }

    /// Returns the concrete root used by package-local window layout code.
    ///
    /// @return the custom-shadow wrapper
    ShadowWrapper getWindowRoot() {
        return root;
    }

    /// Returns the pane on which application dialogs are stacked.
    ///
    /// @return the stable dialog container inside the main window
    public StackPane getDialogContainer() {
        return mainWindowPane.getDialogContainer();
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
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> allowMove.set(true));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            if (!isDragging()) {
                allowMove.set(false);
            }
        });
    }

    /// Prevents a stage-move gesture from starting while the pointer is over `node`.
    ///
    /// @param node the node that must consume drag eligibility
    public void forbidDraggingWindow(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            allowMove.set(false);
            event.consume();
        });
    }

    /// Returns the stage currently controlled by this decorator.
    ///
    /// @return the current stage, or `null` while detached
    @Nullable Stage getStage() {
        return stage.get();
    }

    /// Attaches this decorator to `stage`, detaching listeners from any previous stage.
    ///
    /// Passing `null` disables native window operations until another stage is attached.
    ///
    /// @param stage the new stage, or `null` to detach
    void setStage(@Nullable Stage stage) {
        this.stage.set(stage);
    }

    /// Returns the mutable stage property used by window components.
    ///
    /// @return the current-stage property
    ObjectProperty<@Nullable Stage> stageProperty() {
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

    /// Returns whether a pointer drag may move the stage.
    ///
    /// @return `true` when stage movement is permitted
    boolean isAllowMove() {
        return allowMove.get();
    }

    /// Updates whether a pointer drag may move the stage.
    ///
    /// @param allowMove whether stage movement is permitted
    void setAllowMove(boolean allowMove) {
        this.allowMove.set(allowMove);
    }

    /// Returns the read-only stage-movement eligibility property.
    ///
    /// @return the movement eligibility property
    ReadOnlyBooleanProperty allowMoveProperty() {
        return allowMove.getReadOnlyProperty();
    }

    /// Returns whether a stage move or resize gesture is active.
    ///
    /// @return `true` while a gesture is active
    boolean isDragging() {
        return dragging.get();
    }

    /// Updates whether a stage move or resize gesture is active.
    ///
    /// @param dragging whether a gesture is active
    void setDragging(boolean dragging) {
        this.dragging.set(dragging);
    }

    /// Returns the read-only active-gesture property.
    ///
    /// @return the active-gesture property
    ReadOnlyBooleanProperty draggingProperty() {
        return dragging.getReadOnlyProperty();
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

    /// Minimizes the attached stage, using the configured window animation when supported.
    void minimizeWindow() {
        @Nullable Stage currentStage = getStage();
        if (currentStage == null) {
            return;
        }

        if (AnimationUtils.playWindowAnimation() && OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            playRestoreMinimizeAnimation = true;
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
            timeline.setOnFinished(event -> currentStage.setIconified(true));
            timeline.play();
        } else {
            currentStage.setIconified(true);
        }
    }

    /// Closes the application, using the configured window animation when enabled.
    void closeWindow() {
        if (AnimationUtils.playWindowAnimation()) {
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
            timeline.setOnFinished(event -> Launcher.stopApplication());
            timeline.play();
        } else {
            Launcher.stopApplication();
        }
    }

    /// Closes the current page according to its [DecoratorPage] lifecycle contract.
    void closeCurrentPage() {
        if (navigator.getCurrentPage() instanceof DecoratorPage page && page.isPageCloseable()) {
            page.closePage();
            return;
        }
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

    /// Installs drag-and-drop handling for authlib-injector server URLs.
    private void setupAuthlibInjectorDnD() {
        root.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        root.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }

    /// Moves the restore listener from `oldStage` to `newStage`.
    ///
    /// @param oldStage the previously attached stage, or `null`
    /// @param newStage the newly attached stage, or `null`
    private void onStageChanged(@Nullable Stage oldStage, @Nullable Stage newStage) {
        if (oldStage != null) {
            oldStage.iconifiedProperty().removeListener(iconifiedListener);
        }
        if (newStage != null) {
            newStage.iconifiedProperty().addListener(iconifiedListener);
        } else {
            playRestoreMinimizeAnimation = false;
        }
    }

    /// Restores the root node's transform after an animated minimization.
    private void playRestoreAnimation() {
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
        timeline.play();
    }
}
