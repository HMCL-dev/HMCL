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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.StackContainerPane;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Stream;

import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.io.FileUtils.getExtension;

public class DecoratorController {
    private static final String PROPERTY_DIALOG_CLOSE_HANDLER = DecoratorController.class.getName() + ".dialog.closeListener";

    private final Decorator decorator;
    private final Navigator navigator;

    private JFXDialog dialog;
    private StackContainerPane dialogPane;

    public DecoratorController(Stage stage, Node mainPage) {
        decorator = new Decorator(stage);
        decorator.setOnCloseButtonAction(Launcher::stopApplication);

        navigator = new Navigator();
        navigator.setOnNavigated(this::onNavigated);
        navigator.init(mainPage);

        decorator.getContent().setAll(navigator);
        decorator.onCloseNavButtonActionProperty().set(e -> close());
        decorator.onBackNavButtonActionProperty().set(e -> back());
        decorator.onRefreshNavButtonActionProperty().set(e -> refresh());

        setupBackground();

        setupAuthlibInjectorDnD();
    }

    public Decorator getDecorator() {
        return decorator;
    }

    // ==== Background ====

    private void setupBackground() {
        decorator.contentBackgroundProperty().bind(
                Bindings.createObjectBinding(
                        () -> {
                            Image image = null;
                            if (config().getBackgroundImageType() == EnumBackgroundImage.CUSTOM && config().getBackgroundImage() != null) {
                                image = tryLoadImage(Paths.get(config().getBackgroundImage()))
                                        .orElse(null);
                            }
                            if (image == null) {
                                image = loadDefaultBackgroundImage();
                            }
                            return new Background(new BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, new BackgroundSize(800, 480, false, false, true, true)));
                        },
                        config().backgroundImageTypeProperty(),
                        config().backgroundImageProperty()));
    }

    private Image defaultBackground = newImage("/assets/img/background.jpg");

    /**
     * Load background image from bg/, background.png, background.jpg
     */
    private Image loadDefaultBackgroundImage() {
        Optional<Image> image = randomImageIn(Paths.get("bg"));
        if (!image.isPresent()) {
            image = tryLoadImage(Paths.get("background.png"));
        }
        if (!image.isPresent()) {
            image = tryLoadImage(Paths.get("background.jpg"));
        }
        return image.orElse(defaultBackground);
    }

    private Optional<Image> randomImageIn(Path imageDir) {
        if (!Files.isDirectory(imageDir)) {
            return Optional.empty();
        }

        List<Path> candidates;
        try (Stream<Path> stream = Files.list(imageDir)) {
            candidates = stream
                .filter(Files::isReadable)
                    .filter(it -> {
                        String ext = getExtension(it).toLowerCase();
                        return ext.equals("png") || ext.equals("jpg");
                    })
                    .collect(toList());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list files in ./bg", e);
            return Optional.empty();
        }

        Random rnd = new Random();
        while (candidates.size() > 0) {
            int selected = rnd.nextInt(candidates.size());
            Optional<Image> loaded = tryLoadImage(candidates.get(selected));
            if (loaded.isPresent()) {
                return loaded;
            } else {
                candidates.remove(selected);
            }
        }
        return Optional.empty();
    }

    private Optional<Image> tryLoadImage(Path path) {
        if (!Files.isReadable(path))
            return Optional.empty();

        Image img;
        try {
            img = new Image(path.toAbsolutePath().toUri().toString());
        } catch (IllegalArgumentException e) {
            LOG.log(WARNING, "Couldn't load background image", e);
            return Optional.empty();
        }

        if (img.getException() != null) {
            LOG.log(WARNING, "Couldn't load background image", img.getException());
            return Optional.empty();
        }

        return Optional.of(img);
    }

    // ==== Navigation ====

    public Navigator getNavigator() {
        return navigator;
    }

    private void close() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.isPageCloseable()) {
                page.closePage();
                return;
            }
        }
        navigator.clear();
    }

    private void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.back())
                navigator.close();
        } else {
            navigator.close();
        }
    }

    private void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable) {
            Refreshable refreshable = (Refreshable) navigator.getCurrentPage();

            if (refreshable.refreshableProperty().get())
                refreshable.refresh();
        }
    }

    private void onNavigating(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node from = event.getNode();

        if (from instanceof DecoratorPage)
            ((DecoratorPage) from).back();
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
            dialogPane = new StackContainerPane();

            dialog.setContent(dialogPane);
            decorator.capableDraggingWindow(dialog);
            decorator.forbidDraggingWindow(dialogPane);
            dialog.setDialogContainer(decorator.getDrawerWrapper());
            dialog.setOverlayClose(false);
            dialog.show();
        }
        dialogPane.push(node);

        EventHandler<DialogCloseEvent> handler = event -> closeDialog(node);
        node.getProperties().put(PROPERTY_DIALOG_CLOSE_HANDLER, handler);
        node.addEventHandler(DialogCloseEvent.CLOSE, handler);

        if (node instanceof DialogAware) {
            DialogAware dialogAware = (DialogAware) node;
            if (dialog.isVisible()) {
                dialogAware.onDialogShown();
            } else {
                dialog.visibleProperty().addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                        if (newValue) {
                            dialogAware.onDialogShown();
                            observable.removeListener(this);
                        }
                    }
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void closeDialog(Node node) {
        FXUtils.checkFxUserThread();

        Optional.ofNullable(node.getProperties().get(PROPERTY_DIALOG_CLOSE_HANDLER))
                .ifPresent(handler -> node.removeEventHandler(DialogCloseEvent.CLOSE, (EventHandler<DialogCloseEvent>) handler));

        if (dialog != null) {
            dialogPane.pop(node);

            if (dialogPane.getChildren().isEmpty()) {
                dialog.close();
                dialog = null;
                dialogPane = null;
            }
        }
    }

    // ==== Wizard ====

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        FXUtils.checkFxUserThread();

        getNavigator().navigate(new DecoratorWizardDisplayer(wizardProvider, category), ContainerAnimations.FADE.getAnimationProducer());
    }

    // ==== Authlib Injector DnD ====

    private void setupAuthlibInjectorDnD() {
        decorator.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }
}
