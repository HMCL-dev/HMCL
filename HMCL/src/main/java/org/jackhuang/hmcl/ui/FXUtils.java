/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.ExtendedProperties;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Lang.tryCast;

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

    public static <T> void onWeakChange(ObservableValue<T> value, Consumer<T> consumer) {
        value.addListener(new WeakChangeListener<>((a, b, c) -> consumer.accept(c)));
    }

    public static <T> void onChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        consumer.accept(value.getValue());
        onChange(value, consumer);
    }

    public static <T> void onWeakChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        consumer.accept(value.getValue());
        onWeakChange(value, consumer);
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

    public static void setOverflowHidden(Region region, boolean hidden) {
        if (hidden) {
            Rectangle rectangle = new Rectangle();
            rectangle.widthProperty().bind(region.widthProperty());
            rectangle.heightProperty().bind(region.heightProperty());
            region.setClip(rectangle);
        } else {
            region.setClip(null);
        }
    }

    public static boolean getOverflowHidden(Region region) {
        return region.getClip() != null;
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

    /**
     * Open URL by java.awt.Desktop
     *
     * @param link null is allowed but will be ignored
     */
    public static void openLink(String link) {
        if (link == null)
            return;
        thread(() -> {
            if (java.awt.Desktop.isDesktopSupported()) {
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
            }
        });
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
     * @param comboBox the combo box being bound with {@code property}.
     * @param property the property being bound with {@code combo box}.
     * @see #unbindEnum(JFXComboBox)
     * @deprecated Use {@link ExtendedProperties#selectedItemPropertyFor(ComboBox)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static void bindEnum(JFXComboBox<?> comboBox, Property<? extends Enum> property) {
        unbindEnum(comboBox);
        ChangeListener<Number> listener = (a, b, newValue) ->
                ((Property) property).setValue(property.getValue().getClass().getEnumConstants()[newValue.intValue()]);
        comboBox.getSelectionModel().select(property.getValue().ordinal());
        comboBox.getProperties().put("FXUtils.bindEnum.listener", listener);
        comboBox.getSelectionModel().selectedIndexProperty().addListener(listener);
    }

    /**
     * Unbind combo box selection with given enum property bidirectionally.
     * You should <b>only and always</b> use {@code bindEnum} as well as {@code unbindEnum} at the same time.
     * @param comboBox the combo box being bound with the property which can be inferred by {@code bindEnum}.
     * @see #bindEnum(JFXComboBox, Property)
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static void unbindEnum(JFXComboBox<?> comboBox) {
        ChangeListener listener = tryCast(comboBox.getProperties().get("FXUtils.bindEnum.listener"), ChangeListener.class).orElse(null);
        if (listener == null) return;
        comboBox.getSelectionModel().selectedIndexProperty().removeListener(listener);
    }

    public static void smoothScrolling(ListView<?> listView) {
        listView.skinProperty().addListener(o -> {
            ScrollBar bar = (ScrollBar) listView.lookup(".scroll-bar");
            Node virtualFlow = listView.lookup(".virtual-flow");
            double[] frictions = new double[]{0.99, 0.1, 0.05, 0.04, 0.03, 0.02, 0.01, 0.04, 0.01, 0.008, 0.008, 0.008, 0.008, 0.0006, 0.0005, 0.00003, 0.00001};
            double[] pushes = new double[]{1};
            double[] derivatives = new double[frictions.length];

            Timeline timeline = new Timeline();
            bar.addEventHandler(MouseEvent.DRAG_DETECTED, e -> timeline.stop());

            EventHandler<ScrollEvent> scrollEventHandler = event -> {
                if (event.getEventType() == ScrollEvent.SCROLL) {
                    int direction = event.getDeltaY() > 0 ? -1 : 1;
                    for (int i = 0; i < pushes.length; ++i)
                        derivatives[i] += direction * pushes[i];
                    if (timeline.getStatus() == Animation.Status.STOPPED)
                        timeline.play();
                    event.consume();
                }
            };

            bar.addEventHandler(ScrollEvent.ANY, scrollEventHandler);
            virtualFlow.setOnScroll(scrollEventHandler);

            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(3), event -> {
                for (int i = 0; i < derivatives.length; ++i)
                    derivatives[i] *= frictions[i];
                for (int i = 1; i < derivatives.length; ++i)
                    derivatives[i] += derivatives[i - 1];
                double dy = derivatives[derivatives.length - 1];
                double height = listView.getLayoutBounds().getHeight();
                bar.setValue(Math.min(Math.max(bar.getValue() + dy / height, 0), 1));
                if (Math.abs(dy) < 0.001)
                    timeline.stop();
                listView.requestLayout();
            }));
            timeline.setCycleCount(Animation.INDEFINITE);
        });
    }

    public static <T> T runInUIThread(Supplier<T> supplier) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            return supplier.get();
        } else {
            CountDownLatch doneLatch = new CountDownLatch(1);
            AtomicReference<T> reference = new AtomicReference<>();
            Platform.runLater(() -> {
                try {
                    reference.set(supplier.get());
                } finally {
                    doneLatch.countDown();
                }

            });

            try {
                doneLatch.await();
            } catch (InterruptedException var3) {
                Thread.currentThread().interrupt();
            }

            return reference.get();
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
}
