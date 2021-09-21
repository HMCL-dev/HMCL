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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.beans.value.WritableValue;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ResourceNotFoundError;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.ExtendedProperties;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class FXUtils {
    private FXUtils() {
    }

    public static void runInFX(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    public static void checkFxUserThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Not on FX application thread; currentThread = "
                    + Thread.currentThread().getName());
        }
    }

    public static InvalidationListener onInvalidating(Runnable action) {
        return arg -> action.run();
    }

    public static <T> void onChange(ObservableValue<T> value, Consumer<T> consumer) {
        value.addListener((a, b, c) -> consumer.accept(c));
    }

    public static <T> WeakChangeListener<T> onWeakChange(ObservableValue<T> value, Consumer<T> consumer) {
        WeakChangeListener<T> listener = new WeakChangeListener<>((a, b, c) -> consumer.accept(c));
        value.addListener(listener);
        return listener;
    }

    public static <T> void onChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        consumer.accept(value.getValue());
        onChange(value, consumer);
    }

    public static <T> WeakChangeListener<T> onWeakChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        consumer.accept(value.getValue());
        return onWeakChange(value, consumer);
    }

    public static InvalidationListener observeWeak(Runnable runnable, Observable... observables) {
        InvalidationListener originalListener = observable -> runnable.run();
        WeakInvalidationListener listener = new WeakInvalidationListener(originalListener);
        for (Observable observable : observables) {
            observable.addListener(listener);
        }
        runnable.run();
        return originalListener;
    }

    public static void runLaterIf(BooleanSupplier condition, Runnable runnable) {
        if (condition.getAsBoolean()) Platform.runLater(() -> runLaterIf(condition, runnable));
        else runnable.run();
    }

    public static void limitSize(ImageView imageView, double maxWidth, double maxHeight) {
        imageView.setPreserveRatio(true);
        onChangeAndOperate(imageView.imageProperty(), image -> {
            if (image != null && (image.getWidth() > maxWidth || image.getHeight() > maxHeight)) {
                imageView.setFitHeight(maxHeight);
                imageView.setFitWidth(maxWidth);
            } else {
                imageView.setFitHeight(-1);
                imageView.setFitWidth(-1);
            }
        });
    }

    private static class ListenerPair<T> {
        private final ObservableValue<T> value;
        private final ChangeListener<? super T> listener;

        ListenerPair(ObservableValue<T> value, ChangeListener<? super T> listener) {
            this.value = value;
            this.listener = listener;
        }

        void bind() {
            value.addListener(listener);
        }

        void unbind() {
            value.removeListener(listener);
        }
    }

    public static <T> void addListener(Node node, String key, ObservableValue<T> value, Consumer<? super T> callback) {
        ListenerPair<T> pair = new ListenerPair<>(value, (a, b, newValue) -> callback.accept(newValue));
        node.getProperties().put(key, pair);
        pair.bind();
    }

    public static void removeListener(Node node, String key) {
        tryCast(node.getProperties().get(key), ListenerPair.class)
                .ifPresent(info -> {
                    info.unbind();
                    node.getProperties().remove(key);
                });
    }

    public static <K, T> void setupCellValueFactory(JFXTreeTableColumn<K, T> column, Function<K, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> {
            if (column.validateValue(param))
                return mapper.apply(param.getValue().getValue());
            else
                return column.getComputedValue(param);
        });
    }

    public static Node wrapMargin(Node node, Insets insets) {
        StackPane.setMargin(node, insets);
        return new StackPane(node);
    }

    public static void setValidateWhileTextChanged(Node field, boolean validate) {
        if (field instanceof JFXTextField) {
            if (validate) {
                addListener(field, "FXUtils.validation", ((JFXTextField) field).textProperty(), o -> ((JFXTextField) field).validate());
            } else {
                removeListener(field, "FXUtils.validation");
            }
            ((JFXTextField) field).validate();
        } else if (field instanceof JFXPasswordField) {
            if (validate) {
                addListener(field, "FXUtils.validation", ((JFXPasswordField) field).textProperty(), o -> ((JFXPasswordField) field).validate());
            } else {
                removeListener(field, "FXUtils.validation");
            }
            ((JFXPasswordField) field).validate();
        } else
            throw new IllegalArgumentException("Only JFXTextField and JFXPasswordField allowed");
    }

    public static boolean getValidateWhileTextChanged(Node field) {
        return field.getProperties().containsKey("FXUtils.validation");
    }

    public static Rectangle setOverflowHidden(Region region) {
        Rectangle rectangle = new Rectangle();
        rectangle.widthProperty().bind(region.widthProperty());
        rectangle.heightProperty().bind(region.heightProperty());
        region.setClip(rectangle);
        return rectangle;
    }

    public static Rectangle setOverflowHidden(Region region, double arc) {
        Rectangle rectangle = setOverflowHidden(region);
        rectangle.setArcWidth(arc);
        rectangle.setArcHeight(arc);
        return rectangle;
    }

    public static void setLimitWidth(Region region, double width) {
        region.setMaxWidth(width);
        region.setMinWidth(width);
        region.setPrefWidth(width);
    }

    public static double getLimitWidth(Region region) {
        return region.getMaxWidth();
    }

    public static void setLimitHeight(Region region, double height) {
        region.setMaxHeight(height);
        region.setMinHeight(height);
        region.setPrefHeight(height);
    }

    public static double getLimitHeight(Region region) {
        return region.getMaxHeight();
    }

    public static Node limitingSize(Node node, double width, double height) {
        StackPane pane = new StackPane(node);
        pane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(pane, width);
        FXUtils.setLimitHeight(pane, height);
        return pane;
    }

    public static void smoothScrolling(ScrollPane scrollPane) {
        JFXScrollPane.smoothScrolling(scrollPane);
    }

    public static void loadFXML(Node node, String absolutePath) {
        FXMLLoader loader = new FXMLLoader(node.getClass().getResource(absolutePath), I18n.getResourceBundle());
        loader.setRoot(node);
        loader.setController(node);
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void installFastTooltip(Node node, Tooltip tooltip) {
        installTooltip(node, 50, 5000, 0, tooltip);
    }

    public static void installFastTooltip(Node node, String tooltip) {
        installFastTooltip(node, new Tooltip(tooltip));
    }

    public static void installSlowTooltip(Node node, Tooltip tooltip) {
        installTooltip(node, 500, 5000, 0, tooltip);
    }

    public static void installSlowTooltip(Node node, String tooltip) {
        installSlowTooltip(node, new Tooltip(tooltip));
    }

    public static void installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
        runInFX(() -> {
            try {
                // Java 8
                Class<?> behaviorClass = Class.forName("javafx.scene.control.Tooltip$TooltipBehavior");
                Constructor<?> behaviorConstructor = behaviorClass.getDeclaredConstructor(Duration.class, Duration.class, Duration.class, boolean.class);
                behaviorConstructor.setAccessible(true);
                Object behavior = behaviorConstructor.newInstance(new Duration(openDelay), new Duration(visibleDelay), new Duration(closeDelay), false);
                Method installMethod = behaviorClass.getDeclaredMethod("install", Node.class, Tooltip.class);
                installMethod.setAccessible(true);
                installMethod.invoke(behavior, node, tooltip);
            } catch (ReflectiveOperationException e) {
                try {
                    // Java 9
                    Tooltip.class.getMethod("setShowDelay", Duration.class).invoke(tooltip, new Duration(openDelay));
                    Tooltip.class.getMethod("setShowDuration", Duration.class).invoke(tooltip, new Duration(visibleDelay));
                    Tooltip.class.getMethod("setHideDelay", Duration.class).invoke(tooltip, new Duration(closeDelay));
                } catch (ReflectiveOperationException e2) {
                    e.addSuppressed(e2);
                    Logging.LOG.log(Level.SEVERE, "Cannot install tooltip", e);
                }
                Tooltip.install(node, tooltip);
            }
        });
    }

    public static void playAnimation(Node node, String animationKey, Timeline timeline) {
        animationKey = "FXUTILS.ANIMATION." + animationKey;
        Object oldTimeline = node.getProperties().get(animationKey);
        if (oldTimeline instanceof Timeline) ((Timeline) oldTimeline).stop();
        if (timeline != null) timeline.play();
        node.getProperties().put(animationKey, timeline);
    }

    public static <T> Animation playAnimation(Node node, String animationKey, Duration duration, WritableValue<T> property, T from, T to, Interpolator interpolator) {
        if (from == null) from = property.getValue();
        if (duration == null || Objects.equals(duration, Duration.ZERO) || Objects.equals(from, to)) {
            playAnimation(node, animationKey, null);
            property.setValue(to);
            return null;
        } else {
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(property, from, interpolator)),
                    new KeyFrame(duration, new KeyValue(property, to, interpolator))
            );
            playAnimation(node, animationKey, timeline);
            return timeline;
        }
    }

    public static void openFolder(File file) {
        if (!FileUtils.makeDirectory(file)) {
            Logging.LOG.log(Level.SEVERE, "Unable to make directory " + file);
            return;
        }

        String path = file.getAbsolutePath();

        switch (OperatingSystem.CURRENT_OS) {
            case OSX:
                try {
                    Runtime.getRuntime().exec(new String[]{"/usr/bin/open", path});
                } catch (IOException e) {
                    Logging.LOG.log(Level.SEVERE, "Unable to open " + path + " by executing /usr/bin/open", e);
                }
                break;
            default:
                thread(() -> {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        try {
                            java.awt.Desktop.getDesktop().open(file);
                        } catch (Throwable e) {
                            Logging.LOG.log(Level.SEVERE, "Unable to open " + path + " by java.awt.Desktop.getDesktop()::open", e);
                        }
                    }
                });
        }
    }

    public static void showFileInExplorer(Path file) {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                try {
                    Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", file.toAbsolutePath().toString()});
                } catch (IOException e) {
                    Logging.LOG.log(Level.SEVERE, "Unable to open " + file + " by executing explorer /select", e);
                }
                break;
            default:
                // Currently unsupported.
                break;
        }
    }

    private static final String[] linuxBrowsers = {
            "xdg-open",
            "google-chrome",
            "firefox",
            "microsoft-edge",
            "opera",
            "konqueror",
            "mozilla"
    };

    /**
     * Open URL in browser
     *
     * @param link null is allowed but will be ignored
     */
    public static void openLink(String link) {
        if (link == null)
            return;

        if (java.awt.Desktop.isDesktopSupported()) {
            thread(() -> {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                    for (String browser : linuxBrowsers) {
                        try (final InputStream is = Runtime.getRuntime().exec(new String[]{"which", browser}).getInputStream()) {
                            if (is.read() != -1) {
                                Runtime.getRuntime().exec(new String[]{browser, link});
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                        Logging.LOG.log(Level.WARNING, "No known browser found");
                    }
                }
                try {
                    java.awt.Desktop.getDesktop().browse(new URI(link));
                } catch (Throwable e) {
                    if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                        try {
                            Runtime.getRuntime().exec(new String[]{"/usr/bin/open", link});
                        } catch (IOException ex) {
                            Logging.LOG.log(Level.WARNING, "Unable to open link: " + link, ex);
                        }
                    Logging.LOG.log(Level.WARNING, "Failed to open link: " + link, e);
                }
            });

        }
    }

    public static void showWebDialog(String title, String content) {
        showWebDialog(title, content, 800, 480);
    }

    public static void showWebDialog(String title, String content, int width, int height) {
        try {
            WebStage stage = new WebStage(width, height);
            stage.getWebView().getEngine().loadContent(content);
            stage.setTitle(title);
            stage.showAndWait();
        } catch (NoClassDefFoundError | UnsatisfiedLinkError e) {
            LOG.log(Level.WARNING, "WebView is missing or initialization failed, use JEditorPane replaced", e);

            SwingUtilities.invokeLater(() -> {
                final JFrame frame = new JFrame(title);
                frame.setSize(width, height);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setLocationByPlatform(true);
                //noinspection ConstantConditions
                frame.setIconImage(new ImageIcon(FXUtils.class.getResource("/assets/img/icon.png")).getImage());
                frame.setLayout(new BorderLayout());

                final JProgressBar progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                frame.add(progressBar, BorderLayout.PAGE_START);

                Schedulers.defaultScheduler().execute(() -> {
                    final JEditorPane pane = new JEditorPane("text/html", content);
                    pane.setEditable(false);
                    pane.addHyperlinkListener(event -> {
                        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            openLink(event.getURL().toExternalForm());
                        }
                    });
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setVisible(false);
                        frame.add(new JScrollPane(pane), BorderLayout.CENTER);
                    });
                });

                frame.setVisible(true);
                frame.toFront();
            });
        }
    }

    public static void bindInt(JFXTextField textField, Property<Number> property) {
        textField.textProperty().bindBidirectional(property, SafeStringConverter.fromInteger());
    }

    public static void unbindInt(JFXTextField textField, Property<Number> property) {
        textField.textProperty().unbindBidirectional(property);
    }

    public static void bindString(JFXTextField textField, Property<String> property) {
        textField.textProperty().bindBidirectional(property);
    }

    public static void unbindString(JFXTextField textField, Property<String> property) {
        textField.textProperty().unbindBidirectional(property);
    }

    public static void bindBoolean(JFXToggleButton toggleButton, Property<Boolean> property) {
        toggleButton.selectedProperty().bindBidirectional(property);
    }

    public static void unbindBoolean(JFXToggleButton toggleButton, Property<Boolean> property) {
        toggleButton.selectedProperty().unbindBidirectional(property);
    }

    public static void bindBoolean(JFXCheckBox checkBox, Property<Boolean> property) {
        checkBox.selectedProperty().bindBidirectional(property);
    }

    public static void unbindBoolean(JFXCheckBox checkBox, Property<Boolean> property) {
        checkBox.selectedProperty().unbindBidirectional(property);
    }

    /**
     * Bind combo box selection with given enum property bidirectionally.
     * You should <b>only and always</b> use {@code bindEnum} as well as {@code unbindEnum} at the same time.
     *
     * @param comboBox the combo box being bound with {@code property}.
     * @param property the property being bound with {@code combo box}.
     * @see #unbindEnum(JFXComboBox)
     * @deprecated Use {@link ExtendedProperties#selectedItemPropertyFor(ComboBox)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static void bindEnum(JFXComboBox<?> comboBox, Property<? extends Enum<?>> property) {
        unbindEnum(comboBox);
        @SuppressWarnings("rawtypes")
        ChangeListener<Number> listener = (a, b, newValue) ->
                ((Property) property).setValue(property.getValue().getClass().getEnumConstants()[newValue.intValue()]);
        comboBox.getSelectionModel().select(property.getValue().ordinal());
        comboBox.getProperties().put("FXUtils.bindEnum.listener", listener);
        comboBox.getSelectionModel().selectedIndexProperty().addListener(listener);
    }

    /**
     * Unbind combo box selection with given enum property bidirectionally.
     * You should <b>only and always</b> use {@code bindEnum} as well as {@code unbindEnum} at the same time.
     *
     * @param comboBox the combo box being bound with the property which can be inferred by {@code bindEnum}.
     * @see #bindEnum(JFXComboBox, Property)
     * @deprecated Use {@link ExtendedProperties#selectedItemPropertyFor(ComboBox)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static void unbindEnum(JFXComboBox<?> comboBox) {
        @SuppressWarnings("rawtypes")
        ChangeListener listener = tryCast(comboBox.getProperties().get("FXUtils.bindEnum.listener"), ChangeListener.class).orElse(null);
        if (listener == null) return;
        comboBox.getSelectionModel().selectedIndexProperty().removeListener(listener);
    }

    /**
     * Suppress IllegalArgumentException since the url is supposed to be correct definitely.
     *
     * @param url the url of image. The image resource should be a file within the jar.
     * @return the image resource within the jar.
     * @see org.jackhuang.hmcl.util.CrashReporter
     * @see ResourceNotFoundError
     */
    public static Image newImage(String url) {
        try {
            return new Image(url);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundError("Cannot access image: " + url, e);
        }
    }

    public static void applyDragListener(Node node, FileFilter filter, Consumer<List<File>> callback) {
        applyDragListener(node, filter, callback, null);
    }

    public static void applyDragListener(Node node, FileFilter filter, Consumer<List<File>> callback, Runnable dragDropped) {
        node.setOnDragOver(event -> {
            if (event.getGestureSource() != node && event.getDragboard().hasFiles()) {
                if (event.getDragboard().getFiles().stream().anyMatch(filter::accept))
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                List<File> acceptFiles = files.stream().filter(filter::accept).collect(Collectors.toList());
                if (!acceptFiles.isEmpty()) {
                    callback.accept(acceptFiles);
                    event.setDropCompleted(true);
                }
            }
            if (dragDropped != null)
                dragDropped.run();
            event.consume();
        });
    }

    public static <T> StringConverter<T> stringConverter(Function<T, String> func) {
        return new StringConverter<T>() {

            @Override
            public String toString(T object) {
                return object == null ? "" : func.apply(object);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <T> Callback<ListView<T>, ListCell<T>> jfxListCellFactory(Function<T, Node> graphicBuilder) {
        return view -> new JFXListCell<T>() {
            @Override
            public void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setGraphic(graphicBuilder.apply(item));
                }
            }
        };
    }

    public static ColumnConstraints getColumnFillingWidth() {
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setFillWidth(true);
        return constraint;
    }

    public static ColumnConstraints getColumnHgrowing() {
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setFillWidth(true);
        constraint.setHgrow(Priority.ALWAYS);
        return constraint;
    }

    public static final Interpolator SINE = new Interpolator() {
        @Override
        protected double curve(double t) {
            return Math.sin(t * Math.PI / 2);
        }

        @Override
        public String toString() {
            return "Interpolator.SINE";
        }
    };

    public static Runnable withJFXPopupClosing(Runnable runnable, JFXPopup popup) {
        return () -> {
            runnable.run();
            popup.hide();
        };
    }

    public static void onEscPressed(Node node, Runnable action) {
        node.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                action.run();
                e.consume();
            }
        });
    }

    // Based on https://stackoverflow.com/a/57552025
    // Fix #874: Use it instead of SwingFXUtils.toFXImage
    public static WritableImage toFXImage(BufferedImage image) {
        WritableImage wr = new WritableImage(image.getWidth(), image.getHeight());
        PixelWriter pw = wr.getPixelWriter();

        final int iw = image.getWidth();
        final int ih = image.getHeight();
        for (int x = 0; x < iw; x++) {
            for (int y = 0; y < ih; y++) {
                pw.setArgb(x, y, image.getRGB(x, y));
            }
        }
        return wr;
    }

    public static void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        Controllers.showToast(i18n("message.copied"));
    }
}
