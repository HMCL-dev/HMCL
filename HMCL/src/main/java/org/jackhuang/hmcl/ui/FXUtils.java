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
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.WeakListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MenuSeparator;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.image.ImageLoader;
import org.jackhuang.hmcl.ui.image.ImageUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ResourceNotFoundError;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ExtendedProperties;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class FXUtils {
    private FXUtils() {
    }

    public static final int JAVAFX_MAJOR_VERSION;

    public static final String GRAPHICS_PIPELINE;
    public static final boolean GPU_ACCELERATION_ENABLED;

    static {
        String pipelineName = "";

        try {
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            if (pipeline != null) {
                pipelineName = pipeline.getClass().getName();
            }
        } catch (Throwable e) {
            LOG.warning("Failed to get prism pipeline", e);
        }

        GRAPHICS_PIPELINE = pipelineName;
        GPU_ACCELERATION_ENABLED = !pipelineName.endsWith(".SWPipeline");
    }

    /// @see Platform.Preferences
    public static final @Nullable ObservableMap<String, Object> PREFERENCES;
    public static final @Nullable ObservableBooleanValue DARK_MODE;
    public static final @Nullable Boolean REDUCED_MOTION;
    public static final @Nullable ReadOnlyObjectProperty<Color> ACCENT_COLOR;

    public static final @Nullable MethodHandle TEXT_TRUNCATED_PROPERTY;
    public static final @Nullable MethodHandle FOCUS_VISIBLE_PROPERTY;

    static {
        String jfxVersion = System.getProperty("javafx.version");
        int majorVersion = -1;
        if (jfxVersion != null) {
            Matcher matcher = Pattern.compile("^(?<version>[0-9]+)").matcher(jfxVersion);
            if (matcher.find()) {
                majorVersion = Lang.parseInt(matcher.group(), -1);
            }
        }
        JAVAFX_MAJOR_VERSION = majorVersion;

        ObservableMap<String, Object> preferences = null;
        ObservableBooleanValue darkMode = null;
        ReadOnlyObjectProperty<Color> accentColorProperty = null;
        Boolean reducedMotion = null;
        if (JAVAFX_MAJOR_VERSION >= 22) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> preferencesClass = Class.forName("javafx.application.Platform$Preferences");
                @SuppressWarnings("unchecked")
                var preferences0 = (ObservableMap<String, Object>) lookup.findStatic(Platform.class, "getPreferences", MethodType.methodType(preferencesClass))
                        .invoke();
                preferences = preferences0;

                @SuppressWarnings("unchecked")
                var colorSchemeProperty = (ReadOnlyObjectProperty<? extends Enum<?>>)
                        lookup.findVirtual(preferencesClass, "colorSchemeProperty", MethodType.methodType(ReadOnlyObjectProperty.class))
                                .invoke(preferences);

                darkMode = Bindings.createBooleanBinding(() ->
                        "DARK".equals(colorSchemeProperty.get().name()), colorSchemeProperty);

                @SuppressWarnings("unchecked")
                var accentColorProperty0 = (ReadOnlyObjectProperty<Color>)
                        lookup.findVirtual(preferencesClass, "accentColorProperty", MethodType.methodType(ReadOnlyObjectProperty.class))
                                .invoke(preferences);
                accentColorProperty = accentColorProperty0;

                if (JAVAFX_MAJOR_VERSION >= 24) {
                    reducedMotion = (boolean)
                            lookup.findVirtual(preferencesClass, "isReducedMotion", MethodType.methodType(boolean.class))
                                    .invoke(preferences);
                }
            } catch (Throwable e) {
                LOG.warning("Failed to get preferences", e);
            }
        }
        PREFERENCES = preferences;
        DARK_MODE = darkMode;
        REDUCED_MOTION = reducedMotion;
        ACCENT_COLOR = accentColorProperty;

        MethodHandle textTruncatedProperty = null;
        if (JAVAFX_MAJOR_VERSION >= 23) {
            try {
                textTruncatedProperty = MethodHandles.publicLookup().findVirtual(
                        Labeled.class,
                        "textTruncatedProperty",
                        MethodType.methodType(ReadOnlyBooleanProperty.class)
                );
            } catch (Throwable e) {
                LOG.warning("Failed to lookup textTruncatedProperty", e);
            }
        }
        TEXT_TRUNCATED_PROPERTY = textTruncatedProperty;

        MethodHandle focusVisibleProperty = null;
        if (JAVAFX_MAJOR_VERSION >= 19) {
            try {
                focusVisibleProperty = MethodHandles.publicLookup().findVirtual(
                        Node.class,
                        "focusVisibleProperty",
                        MethodType.methodType(ReadOnlyBooleanProperty.class)
                );
            } catch (Throwable e) {
                LOG.warning("Failed to lookup focusVisibleProperty", e);
            }
        }
        FOCUS_VISIBLE_PROPERTY = focusVisibleProperty;
    }

    public static final String DEFAULT_MONOSPACE_FONT = OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "Consolas" : "Monospace";

    public static final List<String> IMAGE_EXTENSIONS = Lang.immutableListOf(
            "png", "jpg", "jpeg", "bmp", "gif", "webp", "apng"
    );

    private static final Map<String, Image> builtinImageCache = new ConcurrentHashMap<>();

    public static void shutdown() {
        builtinImageCache.clear();
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

    public static <T> ChangeListener<T> onWeakChange(ObservableValue<T> value, Consumer<T> consumer) {
        ChangeListener<T> listener = (a, b, c) -> consumer.accept(c);
        value.addListener(new WeakChangeListener<>(listener));
        return listener;
    }

    public static <T> void onChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
        consumer.accept(value.getValue());
        onChange(value, consumer);
    }

    public static <T> ChangeListener<T> onWeakChangeAndOperate(ObservableValue<T> value, Consumer<T> consumer) {
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

    @SuppressWarnings("unchecked")
    public static <T extends Event> void ignoreEvent(Node node, EventType<T> type, Predicate<? super T> filter) {
        EventDispatcher oldDispatcher = node.getEventDispatcher();
        node.setEventDispatcher((event, tail) -> {
            EventType<?> t = event.getEventType();
            while (t != null && t != type)
                t = t.getSuperType();
            if (t == type && filter.test((T) event)) {
                return tail.dispatchEvent(event);
            } else {
                return oldDispatcher.dispatchEvent(event, tail);
            }
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

    public static void limitCellWidth(ListView<?> listView, ListCell<?> cell) {
        ReadOnlyDoubleProperty widthProperty;

        if (listView.lookup(".clipped-container") instanceof Region clippedContainer) {
            widthProperty = clippedContainer.widthProperty();
        } else {
            widthProperty = listView.widthProperty();
        }

        cell.maxWidthProperty().bind(widthProperty);
        cell.prefWidthProperty().bind(widthProperty);
        cell.minWidthProperty().bind(widthProperty);
    }

    public static void smoothScrolling(ScrollPane scrollPane) {
        if (AnimationUtils.isAnimationEnabled())
            ScrollUtils.addSmoothScrolling(scrollPane);
    }

    public static void smoothScrolling(VirtualFlow<?> virtualFlow) {
        if (AnimationUtils.isAnimationEnabled())
            ScrollUtils.addSmoothScrolling(virtualFlow);
    }

    /// If the current environment is JavaFX 23 or higher, this method returns [Labeled#textTruncatedProperty()];
    /// Otherwise, it returns `null`.
    public static @Nullable ReadOnlyBooleanProperty textTruncatedProperty(Labeled labeled) {
        if (TEXT_TRUNCATED_PROPERTY != null) {
            try {
                return (ReadOnlyBooleanProperty) TEXT_TRUNCATED_PROPERTY.invokeExact(labeled);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    public static @Nullable ReadOnlyBooleanProperty focusVisibleProperty(Node node) {
        if (FOCUS_VISIBLE_PROPERTY != null) {
            try {
                return (ReadOnlyBooleanProperty) FOCUS_VISIBLE_PROPERTY.invokeExact(node);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    private static final Duration TOOLTIP_FAST_SHOW_DELAY = Duration.millis(50);
    private static final Duration TOOLTIP_SLOW_SHOW_DELAY = Duration.millis(500);
    private static final Duration TOOLTIP_SHOW_DURATION = Duration.millis(5000);

    public static void installTooltip(Node node, Duration showDelay, Duration showDuration, Duration hideDelay, Tooltip tooltip) {
        tooltip.setShowDelay(showDelay);
        tooltip.setShowDuration(showDuration);
        tooltip.setHideDelay(hideDelay);
        Tooltip.install(node, tooltip);
    }

    public static void installFastTooltip(Node node, Tooltip tooltip) {
        runInFX(() -> installTooltip(node, TOOLTIP_FAST_SHOW_DELAY, TOOLTIP_SHOW_DURATION, Duration.ZERO, tooltip));
    }

    public static void installFastTooltip(Node node, String tooltip) {
        installFastTooltip(node, new Tooltip(tooltip));
    }

    public static void installSlowTooltip(Node node, Tooltip tooltip) {
        runInFX(() -> installTooltip(node, TOOLTIP_SLOW_SHOW_DELAY, TOOLTIP_SHOW_DURATION, Duration.ZERO, tooltip));
    }

    public static void installSlowTooltip(Node node, String tooltip) {
        installSlowTooltip(node, new Tooltip(tooltip));
    }

    public static void playAnimation(Node node, String animationKey, Animation animation) {
        animationKey = "hmcl.animations." + animationKey;
        if (node.getProperties().get(animationKey) instanceof Animation oldAnimation)
            oldAnimation.stop();
        animation.play();
        node.getProperties().put(animationKey, animation);
    }

    public static void openFolder(Path file) {
        if (file.getFileSystem() != FileSystems.getDefault()) {
            LOG.warning("Cannot open folder as the file system is not supported: " + file);
            return;
        }

        try {
            Files.createDirectories(file);
        } catch (IOException e) {
            LOG.warning("Failed to create directory " + file);
            return;
        }

        String path = FileUtils.getAbsolutePath(file);

        String openCommand;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            openCommand = "explorer.exe";
        else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
            openCommand = "/usr/bin/open";
        else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() && Files.exists(Path.of("/usr/bin/xdg-open")))
            openCommand = "/usr/bin/xdg-open";
        else
            openCommand = null;

        thread(() -> {
            if (openCommand != null) {
                try {
                    int exitCode = SystemUtils.callExternalProcess(openCommand, path);

                    // explorer.exe always return 1
                    if (exitCode == 0 || (exitCode == 1 && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS))
                        return;
                    else
                        LOG.warning("Open " + path + " failed with code " + exitCode);
                } catch (Throwable e) {
                    LOG.warning("Unable to open " + path + " by executing " + openCommand, e);
                }
            }

            // Fallback to java.awt.Desktop::open
            try {
                java.awt.Desktop.getDesktop().open(file.toFile());
            } catch (Throwable e) {
                LOG.error("Unable to open " + path + " by java.awt.Desktop.getDesktop()::open", e);
            }
        });
    }

    public static void showFileInExplorer(Path file) {
        String path = file.toAbsolutePath().toString();

        String[] openCommands;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            openCommands = new String[]{"explorer.exe", "/select,", path};
        else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
            openCommands = new String[]{"/usr/bin/open", "-R", path};
        else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() && SystemUtils.which("dbus-send") != null)
            openCommands = new String[]{
                    "dbus-send",
                    "--print-reply",
                    "--dest=org.freedesktop.FileManager1",
                    "/org/freedesktop/FileManager1",
                    "org.freedesktop.FileManager1.ShowItems",
                    "array:string:" + file.toAbsolutePath().toUri(),
                    "string:"
            };
        else
            openCommands = null;

        if (openCommands != null) {
            thread(() -> {
                try {
                    int exitCode = SystemUtils.callExternalProcess(openCommands);

                    // explorer.exe always return 1
                    if (exitCode == 0 || (exitCode == 1 && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS))
                        return;
                    else
                        LOG.warning("Show " + path + " in explorer failed with code " + exitCode);
                } catch (Throwable e) {
                    LOG.warning("Unable to show " + path + " in explorer", e);
                }

                // Fallback to open folder
                openFolder(file.getParent());
            });
        } else {
            // We do not have a universal method to show file in file manager.
            openFolder(file.getParent());
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

        String uri = NetworkUtils.encodeLocation(link);
        thread(() -> {
            try {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    Runtime.getRuntime().exec(new String[]{"rundll32.exe", "url.dll,FileProtocolHandler", uri});
                    return;
                } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                    Runtime.getRuntime().exec(new String[]{"open", uri});
                    return;
                } else {
                    for (String browser : linuxBrowsers) {
                        Path path = SystemUtils.which(browser);
                        if (path != null) {
                            try {
                                Runtime.getRuntime().exec(new String[]{path.toString(), uri});
                                return;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    LOG.warning("No known browser found");
                }
            } catch (Throwable e) {
                LOG.warning("Failed to open link: " + link + ", fallback to java.awt.Desktop", e);
            }

            try {
                java.awt.Desktop.getDesktop().browse(new URI(uri));
            } catch (Throwable e) {
                LOG.warning("Failed to open link: " + link, e);
            }
        });
    }

    public static <T> void bind(JFXTextField textField, Property<T> property, StringConverter<T> converter) {
        TextFieldBinding<T> binding = new TextFieldBinding<>(textField, property, converter);
        binding.updateTextField();
        textField.getProperties().put("FXUtils.bind.binding", binding);
        textField.focusedProperty().addListener(binding.focusedListener);
        textField.sceneProperty().addListener(binding.sceneListener);
        property.addListener(binding.propertyListener);
    }

    public static void bindInt(JFXTextField textField, Property<Number> property) {
        bind(textField, property, SafeStringConverter.fromInteger());
    }

    public static void bindString(JFXTextField textField, Property<String> property) {
        bind(textField, property, null);
    }

    public static void unbind(JFXTextField textField, Property<?> property) {
        TextFieldBinding<?> binding = (TextFieldBinding<?>) textField.getProperties().remove("FXUtils.bind.binding");
        if (binding != null) {
            textField.focusedProperty().removeListener(binding.focusedListener);
            textField.sceneProperty().removeListener(binding.sceneListener);
            property.removeListener(binding.propertyListener);
        }
    }

    private static final class TextFieldBinding<T> {
        private final JFXTextField textField;
        private final Property<T> property;
        private final StringConverter<T> converter;

        public final ChangeListener<Boolean> focusedListener;
        public final ChangeListener<Scene> sceneListener;
        public final InvalidationListener propertyListener;

        public TextFieldBinding(JFXTextField textField, Property<T> property, StringConverter<T> converter) {
            this.textField = textField;
            this.property = property;
            this.converter = converter;

            focusedListener = (observable, oldFocused, newFocused) -> {
                if (oldFocused && !newFocused) {
                    if (textField.validate()) {
                        updateProperty();
                    } else {
                        // Rollback to old value
                        updateTextField();
                    }
                }
            };

            sceneListener = (observable, oldScene, newScene) -> {
                if (oldScene != null && newScene == null) {
                    // Component is being removed from scene
                    if (textField.validate()) {
                        updateProperty();
                    }
                }
            };

            propertyListener = observable -> {
                updateTextField();
            };
        }

        public void updateProperty() {
            String newText = textField.getText();
            @SuppressWarnings("unchecked")
            T newValue = converter == null ? (T) newText : converter.fromString(newText);

            if (!Objects.equals(newValue, property.getValue())) {
                property.setValue(newValue);
            }
        }

        public void updateTextField() {
            T value = property.getValue();
            textField.setText(converter == null ? (String) value : converter.toString(value));
        }
    }

    private static final class EnumBidirectionalBinding<E extends Enum<E>> implements InvalidationListener, WeakListener {
        private final WeakReference<JFXComboBox<E>> comboBoxRef;
        private final WeakReference<Property<E>> propertyRef;
        private final int hashCode;

        private boolean updating = false;

        private EnumBidirectionalBinding(JFXComboBox<E> comboBox, Property<E> property) {
            this.comboBoxRef = new WeakReference<>(comboBox);
            this.propertyRef = new WeakReference<>(property);
            this.hashCode = System.identityHashCode(comboBox) ^ System.identityHashCode(property);
        }

        @Override
        public void invalidated(Observable sourceProperty) {
            if (!updating) {
                final JFXComboBox<E> comboBox = comboBoxRef.get();
                final Property<E> property = propertyRef.get();

                if (comboBox == null || property == null) {
                    if (comboBox != null) {
                        comboBox.getSelectionModel().selectedItemProperty().removeListener(this);
                    }

                    if (property != null) {
                        property.removeListener(this);
                    }
                } else {
                    updating = true;
                    try {
                        if (property == sourceProperty) {
                            E newValue = property.getValue();
                            comboBox.getSelectionModel().select(newValue);
                        } else {
                            E newValue = comboBox.getSelectionModel().getSelectedItem();
                            property.setValue(newValue);
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return comboBoxRef.get() == null || propertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof EnumBidirectionalBinding))
                return false;

            EnumBidirectionalBinding<?> that = (EnumBidirectionalBinding<?>) o;

            final JFXComboBox<E> comboBox = this.comboBoxRef.get();
            final Property<E> property = this.propertyRef.get();

            final JFXComboBox<?> thatComboBox = that.comboBoxRef.get();
            final Property<?> thatProperty = that.propertyRef.get();

            if (comboBox == null || property == null || thatComboBox == null || thatProperty == null)
                return false;

            return comboBox == thatComboBox && property == thatProperty;
        }
    }

    /**
     * Bind combo box selection with given enum property bidirectionally.
     * You should <b>only and always</b> use {@code bindEnum} as well as {@code unbindEnum} at the same time.
     *
     * @param comboBox the combo box being bound with {@code property}.
     * @param property the property being bound with {@code combo box}.
     * @see #unbindEnum(JFXComboBox, Property)
     * @see ExtendedProperties#selectedItemPropertyFor(ComboBox)
     */
    public static <T extends Enum<T>> void bindEnum(JFXComboBox<T> comboBox, Property<T> property) {
        EnumBidirectionalBinding<T> binding = new EnumBidirectionalBinding<>(comboBox, property);

        comboBox.getSelectionModel().selectedItemProperty().removeListener(binding);
        property.removeListener(binding);

        comboBox.getSelectionModel().select(property.getValue());
        comboBox.getSelectionModel().selectedItemProperty().addListener(binding);
        property.addListener(binding);
    }

    /**
     * Unbind combo box selection with given enum property bidirectionally.
     * You should <b>only and always</b> use {@code bindEnum} as well as {@code unbindEnum} at the same time.
     *
     * @param comboBox the combo box being bound with the property which can be inferred by {@code bindEnum}.
     * @see #bindEnum(JFXComboBox, Property)
     * @see ExtendedProperties#selectedItemPropertyFor(ComboBox)
     */
    public static <T extends Enum<T>> void unbindEnum(JFXComboBox<T> comboBox, Property<T> property) {
        EnumBidirectionalBinding<T> binding = new EnumBidirectionalBinding<>(comboBox, property);
        comboBox.getSelectionModel().selectedItemProperty().removeListener(binding);
        property.removeListener(binding);
    }

    private static final class PaintBidirectionalBinding implements InvalidationListener, WeakListener {
        private final WeakReference<ColorPicker> colorPickerRef;
        private final WeakReference<Property<Paint>> propertyRef;
        private final int hashCode;

        private boolean updating = false;

        private PaintBidirectionalBinding(ColorPicker colorPicker, Property<Paint> property) {
            this.colorPickerRef = new WeakReference<>(colorPicker);
            this.propertyRef = new WeakReference<>(property);
            this.hashCode = System.identityHashCode(colorPicker) ^ System.identityHashCode(property);
        }

        @Override
        public void invalidated(Observable sourceProperty) {
            if (!updating) {
                final ColorPicker colorPicker = colorPickerRef.get();
                final Property<Paint> property = propertyRef.get();

                if (colorPicker == null || property == null) {
                    if (colorPicker != null) {
                        colorPicker.valueProperty().removeListener(this);
                    }

                    if (property != null) {
                        property.removeListener(this);
                    }
                } else {
                    updating = true;
                    try {
                        if (property == sourceProperty) {
                            Paint newValue = property.getValue();
                            if (newValue instanceof Color)
                                colorPicker.setValue((Color) newValue);
                            else
                                colorPicker.setValue(null);
                        } else {
                            Paint newValue = colorPicker.getValue();
                            property.setValue(newValue);
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return colorPickerRef.get() == null || propertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof FXUtils.PaintBidirectionalBinding))
                return false;

            var that = (FXUtils.PaintBidirectionalBinding) o;

            final ColorPicker colorPicker = this.colorPickerRef.get();
            final Property<Paint> property = this.propertyRef.get();

            final ColorPicker thatColorPicker = that.colorPickerRef.get();
            final Property<?> thatProperty = that.propertyRef.get();

            if (colorPicker == null || property == null || thatColorPicker == null || thatProperty == null)
                return false;

            return colorPicker == thatColorPicker && property == thatProperty;
        }
    }

    public static void bindPaint(ColorPicker colorPicker, Property<Paint> property) {
        PaintBidirectionalBinding binding = new PaintBidirectionalBinding(colorPicker, property);

        colorPicker.valueProperty().removeListener(binding);
        property.removeListener(binding);

        if (property.getValue() instanceof Color)
            colorPicker.setValue((Color) property.getValue());
        else
            colorPicker.setValue(null);

        colorPicker.valueProperty().addListener(binding);
        property.addListener(binding);
    }

    private static final class WindowsSizeBidirectionalBinding implements InvalidationListener, WeakListener {
        private final WeakReference<JFXComboBox<String>> comboBoxRef;
        private final WeakReference<IntegerProperty> widthPropertyRef;
        private final WeakReference<IntegerProperty> heightPropertyRef;

        private final int hashCode;

        private boolean updating = false;

        private WindowsSizeBidirectionalBinding(JFXComboBox<String> comboBox,
                                                IntegerProperty widthProperty,
                                                IntegerProperty heightProperty) {
            this.comboBoxRef = new WeakReference<>(comboBox);
            this.widthPropertyRef = new WeakReference<>(widthProperty);
            this.heightPropertyRef = new WeakReference<>(heightProperty);
            this.hashCode = System.identityHashCode(comboBox)
                    ^ System.identityHashCode(widthProperty)
                    ^ System.identityHashCode(heightProperty);
        }

        @Override
        public void invalidated(Observable observable) {
            if (!updating) {
                var comboBox = this.comboBoxRef.get();
                var widthProperty = this.widthPropertyRef.get();
                var heightProperty = this.heightPropertyRef.get();

                if (comboBox == null || widthProperty == null || heightProperty == null) {
                    if (comboBox != null) {
                        comboBox.focusedProperty().removeListener(this);
                        comboBox.sceneProperty().removeListener(this);
                    }
                    if (widthProperty != null)
                        widthProperty.removeListener(this);
                    if (heightProperty != null)
                        heightProperty.removeListener(this);
                } else {
                    updating = true;
                    try {
                        int width = widthProperty.get();
                        int height = heightProperty.get();

                        if (observable instanceof ReadOnlyProperty<?>
                                && ((ReadOnlyProperty<?>) observable).getBean() == comboBox) {
                            String value = comboBox.valueProperty().get();
                            if (value == null)
                                value = "";
                            int idx = value.indexOf('x');
                            if (idx < 0)
                                idx = value.indexOf('*');

                            if (idx < 0) {
                                LOG.warning("Bad window size: " + value);
                                comboBox.setValue(width + "x" + height);
                                return;
                            }

                            String widthStr = value.substring(0, idx).trim();
                            String heightStr = value.substring(idx + 1).trim();

                            int newWidth;
                            int newHeight;
                            try {
                                newWidth = Integer.parseInt(widthStr);
                                newHeight = Integer.parseInt(heightStr);
                            } catch (NumberFormatException e) {
                                LOG.warning("Bad window size: " + value);
                                comboBox.setValue(width + "x" + height);
                                return;
                            }

                            widthProperty.set(newWidth);
                            heightProperty.set(newHeight);
                        } else {
                            comboBox.setValue(width + "x" + height);
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return this.comboBoxRef.get() == null
                    || this.widthPropertyRef.get() == null
                    || this.heightPropertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof WindowsSizeBidirectionalBinding))
                return false;

            var that = (WindowsSizeBidirectionalBinding) obj;

            var comboBox = this.comboBoxRef.get();
            var widthProperty = this.widthPropertyRef.get();
            var heightProperty = this.heightPropertyRef.get();

            var thatComboBox = that.comboBoxRef.get();
            var thatWidthProperty = that.widthPropertyRef.get();
            var thatHeightProperty = that.heightPropertyRef.get();

            if (comboBox == null || widthProperty == null || heightProperty == null
                    || thatComboBox == null || thatWidthProperty == null || thatHeightProperty == null) {
                return false;
            }

            return comboBox == thatComboBox
                    && widthProperty == thatWidthProperty
                    && heightProperty == thatHeightProperty;
        }
    }

    public static void bindWindowsSize(JFXComboBox<String> comboBox, IntegerProperty widthProperty, IntegerProperty heightProperty) {
        comboBox.setValue(widthProperty.get() + "x" + heightProperty.get());
        var binding = new WindowsSizeBidirectionalBinding(comboBox, widthProperty, heightProperty);
        comboBox.focusedProperty().addListener(binding);
        comboBox.sceneProperty().addListener(binding);
        widthProperty.addListener(binding);
        heightProperty.addListener(binding);
    }

    public static void unbindWindowsSize(JFXComboBox<String> comboBox, IntegerProperty widthProperty, IntegerProperty heightProperty) {
        var binding = new WindowsSizeBidirectionalBinding(comboBox, widthProperty, heightProperty);
        comboBox.focusedProperty().removeListener(binding);
        comboBox.sceneProperty().removeListener(binding);
        widthProperty.removeListener(binding);
        heightProperty.removeListener(binding);
    }

    public static void bindAllEnabled(BooleanProperty allEnabled, BooleanProperty... children) {
        int itemCount = children.length;
        int childSelectedCount = 0;
        for (BooleanProperty child : children) {
            if (child.get())
                childSelectedCount++;
        }

        allEnabled.set(childSelectedCount == itemCount);

        class Listener implements InvalidationListener {
            private int childSelectedCount;
            private boolean updating = false;

            public Listener(int childSelectedCount) {
                this.childSelectedCount = childSelectedCount;
            }

            @Override
            public void invalidated(Observable observable) {
                if (updating)
                    return;

                updating = true;
                try {
                    boolean value = ((BooleanProperty) observable).get();

                    if (observable == allEnabled) {
                        for (BooleanProperty child : children) {
                            child.setValue(value);
                        }
                        childSelectedCount = value ? itemCount : 0;
                    } else {
                        if (value)
                            childSelectedCount++;
                        else
                            childSelectedCount--;

                        allEnabled.set(childSelectedCount == itemCount);
                    }
                } finally {
                    updating = false;
                }
            }
        }

        InvalidationListener listener = new Listener(childSelectedCount);

        WeakInvalidationListener weakListener = new WeakInvalidationListener(listener);
        allEnabled.addListener(listener);
        for (BooleanProperty child : children) {
            child.addListener(weakListener);
        }
    }

    public static void setIcon(Stage stage) {
        String icon;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            icon = "/assets/img/icon.png";
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            icon = "/assets/img/icon-mac.png";
        } else {
            icon = "/assets/img/icon@4x.png";
        }
        stage.getIcons().add(newBuiltinImage(icon));
    }

    public static Image loadImage(Path path) throws Exception {
        return loadImage(path, 0, 0, false, false);
    }

    public static Image loadImage(Path path,
                                  int requestedWidth, int requestedHeight,
                                  boolean preserveRatio, boolean smooth) throws Exception {
        try (var input = new BufferedInputStream(Files.newInputStream(path))) {
            String ext = FileUtils.getExtension(path).toLowerCase(Locale.ROOT);
            ImageLoader loader = ImageUtils.EXT_TO_LOADER.get(ext);
            if (loader == null && !ImageUtils.DEFAULT_EXTS.contains(ext)) {
                input.mark(ImageUtils.HEADER_BUFFER_SIZE);
                byte[] headerBuffer = input.readNBytes(ImageUtils.HEADER_BUFFER_SIZE);
                input.reset();
                loader = ImageUtils.guessLoader(headerBuffer);
            }
            if (loader == null)
                loader = ImageUtils.DEFAULT;
            return loader.load(input, requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }

    public static Image loadImage(String url) throws Exception {
        URI uri = NetworkUtils.toURI(url);

        URLConnection connection = NetworkUtils.createConnection(uri);
        if (connection instanceof HttpURLConnection)
            connection = NetworkUtils.resolveConnection((HttpURLConnection) connection);

        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            String contentType = Objects.requireNonNull(connection.getContentType(), "");
            Matcher matcher = ImageUtils.CONTENT_TYPE_PATTERN.matcher(contentType);
            if (matcher.find())
                contentType = matcher.group("type");

            ImageLoader loader = ImageUtils.CONTENT_TYPE_TO_LOADER.get(contentType);
            if (loader == null && !ImageUtils.DEFAULT_CONTENT_TYPES.contains(contentType)) {
                input.mark(ImageUtils.HEADER_BUFFER_SIZE);
                byte[] headerBuffer = input.readNBytes(ImageUtils.HEADER_BUFFER_SIZE);
                input.reset();
                loader = ImageUtils.guessLoader(headerBuffer);
            }

            if (loader == null)
                loader = ImageUtils.DEFAULT;

            return loader.load(input, 0, 0, false, false);
        }
    }

    /**
     * Suppress IllegalArgumentException since the url is supposed to be correct definitely.
     *
     * @param url the url of image. The image resource should be a file within the jar.
     * @return the image resource within the jar.
     * @see org.jackhuang.hmcl.util.CrashReporter
     * @see ResourceNotFoundError
     */
    public static Image newBuiltinImage(String url) {
        try {
            return builtinImageCache.computeIfAbsent(url, Image::new);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundError("Cannot access image: " + url, e);
        }
    }

    /**
     * Suppress IllegalArgumentException since the url is supposed to be correct definitely.
     *
     * @param url             the url of image. The image resource should be a file within the jar.
     * @param requestedWidth  the image's bounding box width
     * @param requestedHeight the image's bounding box height
     * @param preserveRatio   indicates whether to preserve the aspect ratio of
     *                        the original image when scaling to fit the image within the
     *                        specified bounding box
     * @param smooth          indicates whether to use a better quality filtering
     *                        algorithm or a faster one when scaling this image to fit within
     *                        the specified bounding box
     * @return the image resource within the jar.
     * @see org.jackhuang.hmcl.util.CrashReporter
     * @see ResourceNotFoundError
     */
    public static Image newBuiltinImage(String url, double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        try {
            return new Image(url, requestedWidth, requestedHeight, preserveRatio, smooth);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundError("Cannot access image: " + url, e);
        }
    }

    public static Task<Image> getRemoteImageTask(String url, int requestedWidth, int requestedHeight, boolean preserveRatio, boolean smooth) {
        return new CacheFileTask(url)
                .setSignificance(Task.TaskSignificance.MINOR)
                .thenApplyAsync(file -> loadImage(file, requestedWidth, requestedHeight, preserveRatio, smooth))
                .setSignificance(Task.TaskSignificance.MINOR);
    }

    public static Task<Image> getRemoteImageTask(URI uri, int requestedWidth, int requestedHeight, boolean preserveRatio, boolean smooth) {
        return new CacheFileTask(uri)
                .setSignificance(Task.TaskSignificance.MINOR)
                .thenApplyAsync(file -> loadImage(file, requestedWidth, requestedHeight, preserveRatio, smooth))
                .setSignificance(Task.TaskSignificance.MINOR);
    }

    public static ObservableValue<Image> newRemoteImage(String url, int requestedWidth, int requestedHeight, boolean preserveRatio, boolean smooth) {
        var image = new SimpleObjectProperty<Image>();
        getRemoteImageTask(url, requestedWidth, requestedHeight, preserveRatio, smooth)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        image.set(result);
                    } else {
                        LOG.warning("An exception encountered while loading remote image: " + url, exception);
                    }
                })
                .setSignificance(Task.TaskSignificance.MINOR)
                .start();
        return image;
    }

    public static JFXButton newRaisedButton(String text) {
        JFXButton button = new JFXButton(text);
        button.getStyleClass().add("jfx-button-raised");
        button.setButtonType(JFXButton.ButtonType.RAISED);
        return button;
    }

    public static JFXButton newBorderButton(String text) {
        JFXButton button = new JFXButton(text);
        button.getStyleClass().add("jfx-button-border");
        return button;
    }

    public static JFXButton newToggleButton4(SVG icon) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add("toggle-icon4");
        button.setGraphic(icon.createIcon());
        return button;
    }

    public static Label newSafeTruncatedLabel() {
        Label label = new Label();
        label.setTextOverrun(OverrunStyle.CENTER_WORD_ELLIPSIS);
        showTooltipWhenTruncated(label);
        return label;
    }

    private static final String LABEL_FULL_TEXT_PROP_KEY = FXUtils.class.getName() + ".LABEL_FULL_TEXT";

    public static void showTooltipWhenTruncated(Labeled labeled) {
        ReadOnlyBooleanProperty textTruncatedProperty = textTruncatedProperty(labeled);
        if (textTruncatedProperty != null) {
            ChangeListener<Boolean> listener = (observable, oldValue, newValue) -> {
                var label = (Labeled) ((ReadOnlyProperty<?>) observable).getBean();
                var tooltip = (Tooltip) label.getProperties().get(LABEL_FULL_TEXT_PROP_KEY);

                if (newValue) {
                    if (tooltip == null) {
                        tooltip = new Tooltip();
                        tooltip.textProperty().bind(label.textProperty());
                        label.getProperties().put(LABEL_FULL_TEXT_PROP_KEY, tooltip);
                    }

                    FXUtils.installFastTooltip(label, tooltip);
                } else if (tooltip != null) {
                    Tooltip.uninstall(label, tooltip);
                }
            };
            listener.changed(textTruncatedProperty, false, textTruncatedProperty.get());
            textTruncatedProperty.addListener(listener);
        }
    }

    public static void applyDragListener(Node node, PathMatcher filter, Consumer<List<Path>> callback) {
        applyDragListener(node, filter, callback, null);
    }

    public static void applyDragListener(Node node, PathMatcher filter, Consumer<List<Path>> callback, Runnable dragDropped) {
        node.setOnDragOver(event -> {
            if (event.getGestureSource() != node && event.getDragboard().hasFiles()) {
                if (event.getDragboard().getFiles().stream().map(File::toPath).anyMatch(filter::matches))
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                List<Path> acceptFiles = files.stream().map(File::toPath).filter(filter::matches).toList();
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

    public static void onEscPressed(Node node, Runnable action) {
        node.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                action.run();
                e.consume();
            }
        });
    }

    public static void onClicked(Node node, Runnable action) {
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                action.run();
                e.consume();
            }
        });
    }

    public static void onSecondaryButtonClicked(Node node, Runnable action) {
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                action.run();
                e.consume();
            }
        });
    }

    public static <N extends Parent> N prepareNode(N node) {
        Scene dummyScene = new Scene(node);
        StyleSheets.init(dummyScene);
        node.applyCss();
        node.layout();
        return node;
    }

    public static void prepareOnMouseEnter(Node node, Runnable action) {
        node.addEventFilter(MouseEvent.MOUSE_ENTERED, new EventHandler<>() {
            @Override
            public void handle(MouseEvent e) {
                node.removeEventFilter(MouseEvent.MOUSE_ENTERED, this);
                action.run();
            }
        });
    }

    public static <T> void onScroll(Node node, List<T> list,
                                    ToIntFunction<List<T>> finder,
                                    Consumer<T> updater
    ) {
        node.addEventHandler(ScrollEvent.SCROLL, event -> {
            double deltaY = event.getDeltaY();
            if (deltaY == 0)
                return;

            int index = finder.applyAsInt(list);
            if (index < 0) return;
            if (deltaY > 0) // up
                index--;
            else // down
                index++;

            updater.accept(list.get((index + list.size()) % list.size()));
            event.consume();
        });
    }

    public static void copyOnDoubleClick(Labeled label) {
        label.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                String text = label.getText();
                if (text != null && !text.isEmpty()) {
                    copyText(label.getText());
                    e.consume();
                }
            }
        });
    }

    public static void copyText(String text) {
        copyText(text, i18n("message.copied"));
    }

    public static void copyText(String text, @Nullable String toastMessage) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        if (toastMessage != null && !Controllers.isStopped()) {
            Controllers.showToast(toastMessage);
        }
    }

    public static List<Node> parseSegment(String segment, Consumer<String> hyperlinkAction) {
        if (segment.indexOf('<') < 0)
            return Collections.singletonList(new Text(segment));

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader("<body>" + segment + "</body>")));
            Element r = doc.getDocumentElement();

            NodeList children = r.getChildNodes();
            List<Node> texts = new ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node node = children.item(i);

                if (node instanceof Element element) {
                    if ("a".equals(element.getTagName())) {
                        String href = element.getAttribute("href");
                        Text text = new Text(element.getTextContent());
                        text.getStyleClass().add("hyperlink");
                        onClicked(text, () -> {
                            String link = href;
                            try {
                                link = new URI(href).toASCIIString();
                            } catch (URISyntaxException ignored) {
                            }
                            hyperlinkAction.accept(link);
                        });
                        text.setCursor(Cursor.HAND);
                        text.setUnderline(true);
                        texts.add(text);
                    } else if ("b".equals(element.getTagName())) {
                        Text text = new Text(element.getTextContent());
                        text.getStyleClass().add("bold");
                        texts.add(text);
                    } else if ("br".equals(element.getTagName())) {
                        texts.add(new Text("\n"));
                    } else {
                        throw new IllegalArgumentException("unsupported tag " + element.getTagName());
                    }
                } else {
                    texts.add(new Text(node.getTextContent()));
                }
            }
            return texts;
        } catch (SAXException | ParserConfigurationException | IOException e) {
            LOG.warning("Failed to parse xml", e);
            return Collections.singletonList(new Text(segment));
        }
    }

    public static TextFlow segmentToTextFlow(final String segment, Consumer<String> hyperlinkAction) {
        TextFlow tf = new TextFlow();
        tf.getChildren().setAll(parseSegment(segment, hyperlinkAction));
        return tf;
    }

    public static String toWeb(Color color) {
        int r = (int) Math.round(color.getRed() * 255.0);
        int g = (int) Math.round(color.getGreen() * 255.0);
        int b = (int) Math.round(color.getBlue() * 255.0);

        return String.format("#%02x%02x%02x", r, g, b);
    }

    public static FileChooser.ExtensionFilter getImageExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("extension.png"),
                IMAGE_EXTENSIONS.stream().map(ext -> "*." + ext).toArray(String[]::new));
    }

    /**
     * Intelligently determines the popup position to prevent the menu from exceeding screen boundaries.
     * Supports multi-monitor setups by detecting the current screen where the component is located.
     * Now handles first-time popup display by forcing layout measurement.
     *
     * @param root          the root node to calculate position relative to
     * @param popupInstance the popup instance to position
     * @return the optimal vertical position for the popup menu
     */
    public static JFXPopup.PopupVPosition determineOptimalPopupPosition(Node root, JFXPopup popupInstance) {
        // Get the screen bounds in screen coordinates
        Bounds screenBounds = root.localToScreen(root.getBoundsInLocal());

        // Convert Bounds to Rectangle2D for getScreensForRectangle method
        Rectangle2D boundsRect = new Rectangle2D(
                screenBounds.getMinX(), screenBounds.getMinY(),
                screenBounds.getWidth(), screenBounds.getHeight()
        );

        // Find the screen that contains this component (supports multi-monitor)
        List<Screen> screens = Screen.getScreensForRectangle(boundsRect);
        Screen currentScreen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        Rectangle2D visualBounds = currentScreen.getVisualBounds();

        double screenHeight = visualBounds.getHeight();
        double screenMinY = visualBounds.getMinY();
        double itemScreenY = screenBounds.getMinY();

        // Calculate available space relative to the current screen
        double availableSpaceAbove = itemScreenY - screenMinY;
        double availableSpaceBelow = screenMinY + screenHeight - itemScreenY - root.getBoundsInLocal().getHeight();

        // Get popup content and ensure it's properly measured
        Region popupContent = popupInstance.getPopupContent();

        double menuHeight;
        if (popupContent.getHeight() <= 0) {
            // Force layout measurement if height is not yet available
            popupContent.autosize();
            popupContent.applyCss();
            popupContent.layout();

            // Get the measured height, or use a reasonable fallback
            menuHeight = popupContent.getHeight();
            if (menuHeight <= 0) {
                // Fallback: estimate based on number of menu items
                // Each menu item is roughly 36px height + separators + padding
                menuHeight = 300; // Conservative estimate for the current menu structure
            }
        } else {
            menuHeight = popupContent.getHeight();
        }

        // Add some margin for safety
        menuHeight += 20;

        return (availableSpaceAbove > menuHeight && availableSpaceBelow < menuHeight)
                ? JFXPopup.PopupVPosition.BOTTOM  // Show menu below the button, expanding downward
                : JFXPopup.PopupVPosition.TOP;    // Show menu above the button, expanding upward
    }

    public static void useJFXContextMenu(TextInputControl control) {
        control.setContextMenu(null);

        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);
        popup.setAutoHide(true);

        control.setOnContextMenuRequested(e -> {
            boolean hasNoSelection = control.getSelectedText().isEmpty();

            IconedMenuItem undo = new IconedMenuItem(SVG.UNDO, i18n("menu.undo"), control::undo, popup);
            IconedMenuItem redo = new IconedMenuItem(SVG.REDO, i18n("menu.redo"), control::redo, popup);
            IconedMenuItem cut = new IconedMenuItem(SVG.CONTENT_CUT, i18n("menu.cut"), control::cut, popup);
            IconedMenuItem copy = new IconedMenuItem(SVG.CONTENT_COPY, i18n("menu.copy"), control::copy, popup);
            IconedMenuItem paste = new IconedMenuItem(SVG.CONTENT_PASTE, i18n("menu.paste"), control::paste, popup);
            IconedMenuItem delete = new IconedMenuItem(SVG.DELETE, i18n("menu.deleteselection"), () -> control.replaceSelection(""), popup);
            IconedMenuItem selectall = new IconedMenuItem(SVG.SELECT_ALL, i18n("menu.selectall"), control::selectAll, popup);

            menu.getContent().setAll(undo, redo, new MenuSeparator(), cut, copy, paste, delete, new MenuSeparator(), selectall);

            undo.setDisable(!control.isUndoable());
            redo.setDisable(!control.isRedoable());
            cut.setDisable(hasNoSelection);
            delete.setDisable(hasNoSelection);
            copy.setDisable(hasNoSelection);
            paste.setDisable(!Clipboard.getSystemClipboard().hasString());
            selectall.setDisable(control.getText() == null || control.getText().isEmpty());

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(control, popup);
            popup.show(control, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - control.getHeight());

            e.consume();
        });
    }
}
