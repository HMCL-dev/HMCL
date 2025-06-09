/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXSnackbar;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newBuiltinImage;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.io.FileUtils.getExtension;

public class DecoratorController {
    private static final String PROPERTY_DIALOG_CLOSE_HANDLER = DecoratorController.class.getName() + ".dialog.closeListener";

    private final Decorator decorator;
    private final Navigator navigator;

    private JFXDialog dialog;
    private JFXDialogPane dialogPane;

    public DecoratorController(Stage stage, Node mainPage) {
        decorator = new Decorator(stage);
        decorator.setOnCloseButtonAction(() -> {
            if (AnimationUtils.playWindowAnimation()) {
                Timeline timeline = new Timeline(
                        new KeyFrame(Duration.millis(0),
                                new KeyValue(decorator.opacityProperty(), 1, FXUtils.EASE),
                                new KeyValue(decorator.scaleXProperty(), 1, FXUtils.EASE),
                                new KeyValue(decorator.scaleYProperty(), 1, FXUtils.EASE),
                                new KeyValue(decorator.scaleZProperty(), 0.3, FXUtils.EASE)
                        ),
                        new KeyFrame(Duration.millis(200),
                                new KeyValue(decorator.opacityProperty(), 0, FXUtils.EASE),
                                new KeyValue(decorator.scaleXProperty(), 0.8, FXUtils.EASE),
                                new KeyValue(decorator.scaleYProperty(), 0.8, FXUtils.EASE),
                                new KeyValue(decorator.scaleZProperty(), 0.8, FXUtils.EASE)
                        )
                );
                timeline.setOnFinished(event -> Launcher.stopApplication());
                timeline.play();
            } else {
                Launcher.stopApplication();
            }
        });
        decorator.titleTransparentProperty().bind(config().titleTransparentProperty());

        navigator = new Navigator();
        navigator.setOnNavigated(this::onNavigated);
        navigator.init(mainPage);

        decorator.getContent().setAll(navigator);
        decorator.onCloseNavButtonActionProperty().set(e -> close());
        decorator.onBackNavButtonActionProperty().set(e -> back());
        decorator.onRefreshNavButtonActionProperty().set(e -> refresh());

        setupAuthlibInjectorDnD();

        // Setup background
        decorator.setContentBackground(getBackground());
        changeBackgroundListener = o -> {
            final int currentCount = ++this.changeBackgroundCount;
            CompletableFuture.supplyAsync(this::getBackground, Schedulers.io())
                    .thenAcceptAsync(background -> {
                        if (this.changeBackgroundCount == currentCount)
                            decorator.setContentBackground(background);
                    }, Schedulers.javafx());
        };
        WeakInvalidationListener weakListener = new WeakInvalidationListener(changeBackgroundListener);
        config().backgroundImageTypeProperty().addListener(weakListener);
        config().backgroundImageProperty().addListener(weakListener);
        config().backgroundImageUrlProperty().addListener(weakListener);

        // pass key events to current dialog / current page
        decorator.addEventFilter(KeyEvent.ANY, e -> {
            if (!(e.getTarget() instanceof Node)) {
                return; // event source can't be determined
            }

            Node newTarget;
            if (dialogPane != null && dialogPane.peek().isPresent()) {
                newTarget = dialogPane.peek().get(); // current dialog
            } else {
                newTarget = navigator.getCurrentPage(); // current page
            }

            boolean needsRedirect = true;
            Node t = (Node) e.getTarget();
            while (t != null) {
                if (t == newTarget) {
                    // current event target is in newTarget
                    needsRedirect = false;
                    break;
                }
                t = t.getParent();
            }
            if (!needsRedirect) {
                return;
            }

            e.consume();
            newTarget.fireEvent(e.copyFor(e.getSource(), newTarget));
        });

        // press ESC to go back
        onEscPressed(navigator, this::back);

        try {
            // For JavaFX 12+
            MouseButton button = MouseButton.valueOf("BACK");
            navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.getButton() == button) {
                    back();
                    e.consume();
                }
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    public Decorator getDecorator() {
        return decorator;
    }

    // ==== Background ====

    //FXThread
    private int changeBackgroundCount = 0;

    @SuppressWarnings("FieldCanBeLocal") // Strong reference
    private final InvalidationListener changeBackgroundListener;

    private Background getBackground() {
        EnumBackgroundImage imageType = config().getBackgroundImageType();

        Image image = null;
        switch (imageType) {
            case CUSTOM:
                String backgroundImage = config().getBackgroundImage();
                if (backgroundImage != null)
                    try {
                        image = tryLoadImage(Paths.get(backgroundImage));
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                break;
            case NETWORK:
                String backgroundImageUrl = config().getBackgroundImageUrl();
                if (backgroundImageUrl != null) {
                    try {
                        image = FXUtils.loadImage(new URL(backgroundImageUrl));
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case CLASSIC:
                image = newBuiltinImage("/assets/img/background-classic.jpg");
                break;
            case TRANSLUCENT:
                return new Background(new BackgroundFill(new Color(1, 1, 1, 0.5), CornerRadii.EMPTY, Insets.EMPTY));
        }
        if (image == null) {
            image = loadDefaultBackgroundImage();
        }
        return new Background(new BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, new BackgroundSize(800, 480, false, false, true, true)));
    }

    /**
     * Load background image from bg/, background.png, background.jpg, background.gif
     */
    private Image loadDefaultBackgroundImage() {
        Image image = randomImageIn(Metadata.HMCL_CURRENT_DIRECTORY.resolve("background"));
        if (image != null)
            return image;

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            image = tryLoadImage(Metadata.HMCL_CURRENT_DIRECTORY.resolve("background." + extension));
            if (image != null)
                return image;
        }

        image = randomImageIn(Metadata.CURRENT_DIRECTORY.resolve("bg"));
        if (image != null)
            return image;

        for (String extension : FXUtils.IMAGE_EXTENSIONS) {
            image = tryLoadImage(Metadata.CURRENT_DIRECTORY.resolve("background." + extension));
            if (image != null)
                return image;
        }

        return newBuiltinImage("/assets/img/background.jpg");
    }

    private @Nullable Image randomImageIn(Path imageDir) {
        if (!Files.isDirectory(imageDir)) {
            return null;
        }

        List<Path> candidates;
        try (Stream<Path> stream = Files.list(imageDir)) {
            candidates = stream
                    .filter(it -> FXUtils.IMAGE_EXTENSIONS.contains(getExtension(it).toLowerCase(Locale.ROOT)))
                    .filter(Files::isReadable)
                    .collect(toList());
        } catch (IOException e) {
            LOG.warning("Failed to list files in ./bg", e);
            return null;
        }

        Random rnd = new Random();
        while (!candidates.isEmpty()) {
            int selected = rnd.nextInt(candidates.size());
            Image loaded = tryLoadImage(candidates.get(selected));
            if (loaded != null)
                return loaded;
            else
                candidates.remove(selected);
        }
        return null;
    }

    private @Nullable Image tryLoadImage(Path path) {
        if (!Files.isReadable(path))
            return null;

        try {
            return FXUtils.loadImage(path);
        } catch (Exception e) {
            LOG.warning("Couldn't load background image", e);
            return null;
        }
    }

    // ==== Navigation ====

    private static final DecoratorAnimationProducer animation = new DecoratorAnimationProducer();

    public void navigate(Node node) {
        navigator.navigate(node, animation);
    }

    private void close() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            // FIXME: Get WorldPage working first, and revisit this later
            page.closePage();
            if (page.isPageCloseable()) {
                return;
            }
        }
        navigator.clear();
    }

    private void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

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

    private void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable) {
            Refreshable refreshable = (Refreshable) navigator.getCurrentPage();

            if (refreshable.refreshableProperty().get())
                refreshable.refresh();
        }
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node to = event.getNode();

        if (to instanceof Refreshable) {
            decorator.canRefreshProperty().bind(((Refreshable) to).refreshableProperty());
        } else {
            decorator.canRefreshProperty().unbind();
            decorator.canRefreshProperty().set(false);
        }

        decorator.canCloseProperty().set(navigator.size() > 2);

        if (to instanceof DecoratorPage) {
            decorator.showCloseAsHomeProperty().set(!((DecoratorPage) to).isPageCloseable());
        } else {
            decorator.showCloseAsHomeProperty().set(true);
        }

        decorator.setNavigationDirection(event.getDirection());

        // state property should be updated at last.
        if (to instanceof DecoratorPage) {
            decorator.stateProperty().bind(((DecoratorPage) to).stateProperty());
        } else {
            decorator.stateProperty().unbind();
            decorator.stateProperty().set(new DecoratorPage.State("", null, navigator.canGoBack(), false, true));
        }

        if (to instanceof Region) {
            Region region = (Region) to;
            // Let root pane fix window size.
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }

    // ==== Dialog ====

    public void showDialog(Node node) {
        FXUtils.checkFxUserThread();

        if (dialog == null) {
            if (decorator.getDrawerWrapper() == null) {
                // Sometimes showDialog will be invoked before decorator was initialized.
                // Keep trying again.
                Platform.runLater(() -> showDialog(node));
                return;
            }
            dialog = new JFXDialog();
            dialogPane = new JFXDialogPane();

            dialog.setContent(dialogPane);
            decorator.capableDraggingWindow(dialog);
            decorator.forbidDraggingWindow(dialogPane);
            dialog.setDialogContainer(decorator.getDrawerWrapper());
            dialog.setOverlayClose(false);
            dialog.show();

            navigator.setDisable(true);
        }
        dialogPane.push(node);

        EventHandler<DialogCloseEvent> handler = event -> closeDialog(node);
        node.getProperties().put(PROPERTY_DIALOG_CLOSE_HANDLER, handler);
        node.addEventHandler(DialogCloseEvent.CLOSE, handler);

        if (dialog.isVisible()) {
            dialog.requestFocus();
            if (node instanceof DialogAware)
                ((DialogAware) node).onDialogShown();
        } else {
            dialog.visibleProperty().addListener(new ChangeListener<Boolean>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        dialog.requestFocus();
                        if (node instanceof DialogAware)
                            ((DialogAware) node).onDialogShown();
                        observable.removeListener(this);
                    }
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void closeDialog(Node node) {
        FXUtils.checkFxUserThread();

        Optional.ofNullable(node.getProperties().get(PROPERTY_DIALOG_CLOSE_HANDLER))
                .ifPresent(handler -> node.removeEventHandler(DialogCloseEvent.CLOSE, (EventHandler<DialogCloseEvent>) handler));

        if (dialog != null) {
            JFXDialogPane pane = dialogPane;

            if (pane.size() == 1 && pane.peek().orElse(null) == node) {
                dialog.setOnDialogClosed(e -> pane.pop(node));
                dialog.close();
                dialog = null;
                dialogPane = null;

                navigator.setDisable(false);
            } else {
                pane.pop(node);
            }

            if (node instanceof DialogAware) {
                ((DialogAware) node).onDialogClosed();
            }
        }
    }

    // ==== Toast ====

    public void showToast(String content) {
        decorator.getSnackbar().fireEvent(new JFXSnackbar.SnackbarEvent(content, null, 2000L, false, null));
    }

    // ==== Wizard ====

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        FXUtils.checkFxUserThread();

        navigator.navigate(new DecoratorWizardDisplayer(wizardProvider, category), ContainerAnimations.FADE);
    }

    // ==== Authlib Injector DnD ====

    private void setupAuthlibInjectorDnD() {
        decorator.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }
}
