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

import com.jfoenix.controls.JFXSnackbar;
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newBuiltinImage;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.io.FileUtils.getExtension;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DecoratorController {
    private final Decorator decorator;
    private final Navigator navigator;

    public DecoratorController(Stage stage, Node mainPage) {
        decorator = new Decorator(stage);
        decorator.setOnCloseButtonAction(() -> {
            if (AnimationUtils.playWindowAnimation()) {
                Timeline timeline = new Timeline(
                        new KeyFrame(Duration.millis(0),
                                new KeyValue(decorator.opacityProperty(), 1, Motion.EASE),
                                new KeyValue(decorator.scaleXProperty(), 1, Motion.EASE),
                                new KeyValue(decorator.scaleYProperty(), 1, Motion.EASE),
                                new KeyValue(decorator.scaleZProperty(), 0.3, Motion.EASE)
                        ),
                        new KeyFrame(Duration.millis(200),
                                new KeyValue(decorator.opacityProperty(), 0, Motion.EASE),
                                new KeyValue(decorator.scaleXProperty(), 0.8, Motion.EASE),
                                new KeyValue(decorator.scaleYProperty(), 0.8, Motion.EASE),
                                new KeyValue(decorator.scaleZProperty(), 0.8, Motion.EASE)
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
        changeBackgroundListener = o -> updateBackground();
        WeakInvalidationListener weakListener = new WeakInvalidationListener(changeBackgroundListener);
        config().backgroundImageTypeProperty().addListener(weakListener);
        config().backgroundImageProperty().addListener(weakListener);
        config().backgroundImageUrlProperty().addListener(weakListener);
        config().backgroundPaintProperty().addListener(weakListener);
        config().backgroundImageOpacityProperty().addListener(weakListener);

        // pass key events to current dialog / current page
        decorator.addEventFilter(KeyEvent.ANY, e -> {
            if (!(e.getTarget() instanceof Node t)) {
                return;
            }

            Node newTarget;

            JFXDialogPane currentDialogPane = null;
            if (decorator.getDrawerWrapper() != null) {
                currentDialogPane = (JFXDialogPane) decorator.getDrawerWrapper().getProperties().get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);
            }

            if (currentDialogPane != null && currentDialogPane.peek().isPresent()) {
                newTarget = currentDialogPane.peek().get();
            } else {
                newTarget = navigator.getCurrentPage();
            }
            boolean needsRedirect = true;

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

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            // press F11 to toggle full screen
            navigator.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.F11) {
                    stage.setFullScreen(!stage.isFullScreen());
                    e.consume();
                }
            });
        }

        navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.BACK) {
                back();
                e.consume();
            }
        });
    }

    public Decorator getDecorator() {
        return decorator;
    }

    // ==== Background ====

    //FXThread
    private int changeBackgroundCount = 0;

    @SuppressWarnings("FieldCanBeLocal") // Strong reference
    private final InvalidationListener changeBackgroundListener;

    private void updateBackground() {
        final int currentCount = ++this.changeBackgroundCount;
        Task.supplyAsync(Schedulers.io(), this::getBackground)
                .setName("Update background")
                .whenComplete(Schedulers.javafx(), (background, exception) -> {
                    if (exception == null) {
                        if (this.changeBackgroundCount == currentCount)
                            decorator.setContentBackground(background);
                    } else {
                        LOG.warning("Failed to update background", exception);
                    }
                }).start();
    }

    private Background getBackground() {
        EnumBackgroundImage imageType = config().getBackgroundImageType();
        if (imageType == null)
            imageType = EnumBackgroundImage.DEFAULT;

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
                        image = FXUtils.loadImage(backgroundImageUrl);
                    } catch (Exception e) {
                        LOG.warning("Couldn't load background image", e);
                    }
                }
                break;
            case CLASSIC:
                image = newBuiltinImage("/assets/img/background-classic.jpg");
                break;
            case TRANSLUCENT: // Deprecated
                return new Background(new BackgroundFill(new Color(1, 1, 1, 0.5), CornerRadii.EMPTY, Insets.EMPTY));
            case PAINT:
                Paint paint = config().getBackgroundPaint();
                double opacity = Lang.clamp(0, config().getBackgroundImageOpacity(), 100) / 100.;
                if (paint instanceof Color || paint == null) {
                    Color color = (Color) paint;
                    if (color == null)
                        color = Color.WHITE; // Default to white if no color is set
                    if (opacity < 1.)
                        color = new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
                    return new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY));
                } else {
                    // TODO: Support opacity for non-color paints
                    return new Background(new BackgroundFill(paint, CornerRadii.EMPTY, Insets.EMPTY));
                }
        }
        if (image == null) {
            image = loadDefaultBackgroundImage();
        }
        return createBackgroundWithOpacity(image, config().getBackgroundImageOpacity());
    }

    private Background createBackgroundWithOpacity(Image image, int opacity) {
        if (opacity <= 0) {
            return new Background(new BackgroundFill(new Color(1, 1, 1, 0), CornerRadii.EMPTY, Insets.EMPTY));
        } else if (opacity >= 100 || image.getPixelReader() == null) {
            return new Background(new BackgroundImage(
                    image,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    new BackgroundSize(800, 480, false, false, true, true)
            ));
        } else {
            WritableImage tempImage = new WritableImage((int) image.getWidth(), (int) image.getHeight());
            PixelReader pixelReader = image.getPixelReader();
            PixelWriter pixelWriter = tempImage.getPixelWriter();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    Color color = pixelReader.getColor(x, y);
                    Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * opacity / 100);
                    pixelWriter.setColor(x, y, newColor);
                }
            }

            return new Background(new BackgroundImage(
                    tempImage,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.DEFAULT,
                    new BackgroundSize(800, 480, false, false, true, true)
            ));
        }
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

    public void navigate(Node node, AnimationProducer animationProducer, Duration duration, Interpolator interpolator) {
        navigator.navigate(node, animationProducer, duration, interpolator);
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
        DialogUtils.show(decorator, node);
    }

    private void closeDialog(Node node) {
        DialogUtils.close(node);
    }

    // ==== Toast ====

    public void showToast(String content) {
        decorator.getSnackbar().fireEvent(new JFXSnackbar.SnackbarEvent(new JFXSnackbarLayout(content)));
    }

    // ==== Wizard ====

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        FXUtils.checkFxUserThread();

        navigator.navigate(new DecoratorWizardDisplayer(wizardProvider, category),
                ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    // ==== Authlib Injector DnD ====

    private void setupAuthlibInjectorDnD() {
        decorator.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
    }
}
