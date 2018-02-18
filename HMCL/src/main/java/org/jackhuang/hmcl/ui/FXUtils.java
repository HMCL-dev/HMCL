/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.*;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.ReflectionHelper.call;
import static org.jackhuang.hmcl.util.ReflectionHelper.construct;

public final class FXUtils {
    private FXUtils() {
    }

    public static <T> void onChange(ObservableValue<T> value, Consumer<T> consumer) {
        value.addListener((a, b, c) -> consumer.accept(c));
    }

    public static <T> void onWeakChange(ObservableValue<T> value, Consumer<T> consumer) {
        value.addListener(new WeakChangeListener<>((a, b, c) -> consumer.accept(c)));
    }

    public static <T> void onChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        onChange(value, consumer);
        consumer.accept(value.getValue());
    }

    public static <T> void onWeakChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        onWeakChange(value, consumer);
        consumer.accept(value.getValue());
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
        ObservableValue<T> value;
        ChangeListener<? super T> listener;

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
        Lang.cast(node.getProperties().get(key), ListenerPair.class)
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

    public static void smoothScrolling(ScrollPane scrollPane) {
        JFXScrollPane.smoothScrolling(scrollPane);
    }

    public static void loadFXML(Node node, String absolutePath) {
        FXMLLoader loader = new FXMLLoader(node.getClass().getResource(absolutePath), Main.RESOURCE_BUNDLE);
        loader.setRoot(node);
        loader.setController(node);
        Lang.invoke(() -> loader.load());
    }

    public static void resetChildren(JFXMasonryPane pane, List<Node> children) {
        // Fixes mis-repositioning.
        ReflectionHelper.setFieldContent(JFXMasonryPane.class, pane, "oldBoxes", null);
        pane.getChildren().setAll(children);
    }

    public static void installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
        try {
            // Java 8
            call(construct(Class.forName("javafx.scene.control.Tooltip$TooltipBehavior"), new Duration(openDelay), new Duration(visibleDelay), new Duration(closeDelay), false),
                    "install", node, tooltip);
        } catch (Throwable e) {
            try {
                // Java 9
                call(tooltip, "setShowDelay", new Duration(openDelay));
                call(tooltip, "setShowDuration", new Duration(visibleDelay));
                call(tooltip, "setHideDelay", new Duration(closeDelay));
            } catch (Throwable e2) {
                e.addSuppressed(e2);
                Logging.LOG.log(Level.SEVERE, "Cannot install tooltip by reflection", e);
            }
            Tooltip.install(node, tooltip);
        }
    }

    public static boolean alert(Alert.AlertType type, String title, String contentText) {
        return alert(type, title, contentText, null);
    }

    public static boolean alert(Alert.AlertType type, String title, String contentText, String headerText) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static Optional<String> inputDialog(String title, String contentText) {
        return inputDialog(title, contentText, null);
    }

    public static Optional<String> inputDialog(String title, String contentText, String headerText) {
        return inputDialog(title, contentText, headerText, "");
    }

    public static Optional<String> inputDialog(String title, String contentText, String headerText, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(headerText);
        dialog.setContentText(contentText);
        return dialog.showAndWait();
    }

    public static void openFolder(File file) {
        file.mkdirs();
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
                try {
                    java.awt.Desktop.getDesktop().open(file);
                } catch (Throwable e) {
                    Logging.LOG.log(Level.SEVERE, "Unable to open " + path + " by java.awt.Desktop.getDesktop()::open", e);
                }
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
        try {
            java.awt.Desktop.getDesktop().browse(new URI(link));
        } catch (Throwable e) {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
                try {
                    Runtime.getRuntime().exec(new String[] { "/usr/bin/open", link });
                } catch (IOException ex) {
                    Logging.LOG.log(Level.WARNING, "Unable to open link: " + link, ex);
                }
            Logging.LOG.log(Level.WARNING, "Failed to open link: " + link, e);
        }
    }

    public static void bindInt(JFXTextField textField, Property<Number> property) {
        textField.textProperty().bindBidirectional(property, SafeIntStringConverter.INSTANCE);
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

    public static void bindEnum(JFXComboBox<?> comboBox, Property<? extends Enum> property) {
        unbindEnum(comboBox);
        ChangeListener<Number> listener = (a, b, newValue) -> {
            ((Property) property).setValue(property.getValue().getClass().getEnumConstants()[newValue.intValue()]);
        };
        comboBox.getSelectionModel().select(property.getValue().ordinal());
        comboBox.getProperties().put("listener", listener);
        comboBox.getSelectionModel().selectedIndexProperty().addListener(listener);
    }

    public static void unbindEnum(JFXComboBox<?> comboBox) {
        ChangeListener listener = Lang.get(comboBox.getProperties(), "listener", ChangeListener.class).orElse(null);
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

    public static final Image DEFAULT_ICON = new Image("/assets/img/icon.png");

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
}
