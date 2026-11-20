// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXDatePicker;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.Event;
import javafx.event.EventDispatcher;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.TableViewSkin;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.PercentageStringConverter;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3Divider;
import org.glavo.m3fx.controls.M3MenuItem;
import org.glavo.m3fx.controls.M3PasswordField;
import org.glavo.m3fx.controls.M3ProgressIndicator;
import org.glavo.m3fx.controls.M3Slider;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputLayout;
import org.glavo.m3fx.controls.M3TextInputValidator;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.LauncherDialog;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.ResourceNotFoundError;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.javafx.Subscription;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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

    public static final String DEFAULT_MONOSPACE_FONT = SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS ? "Consolas" : "Monospace";

    public static final List<String> IMAGE_EXTENSIONS = List.of(
            "png", "jpg", "jpeg", "bmp", "gif", "webp", "svg", "apng"
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

    /// Creates a continuous slider over [0, 1] with a whole-percent value indicator.
    public static M3Slider newPercentageSlider() {
        var slider = new M3Slider(0, 1, 0);
        slider.setBlockIncrement(0.01);
        slider.setShowValueIndicator(true);
        slider.setLabelFormatter(new StringConverter<>() {
            /// Displays the whole percentage of the normalized value.
            @Override
            public String toString(@Nullable Double value) {
                return value == null ? "" : (int) (value * 100) + "%";
            }

            /// Parses a percentage label back into a normalized value.
            @Override
            public @Nullable Double fromString(@Nullable String text) {
                @Nullable Number value = new PercentageStringConverter(Locale.ROOT).fromString(text);
                return value == null ? null : value.doubleValue();
            }
        });
        return slider;
    }

    /// Creates an outlined text field with the launcher's localized editing menu.
    public static M3TextField newTextField() {
        return newTextField(M3TextInputVariant.OUTLINED);
    }

    /// Creates a text field with the given variant and the launcher's localized editing menu.
    public static M3TextField newTextField(M3TextInputVariant variant) {
        M3TextField field = new M3TextField();
        field.setVariant(variant);
        installTextContextMenu(field);
        return field;
    }

    /// Creates an outlined password field with the launcher's localized editing menu.
    public static M3PasswordField newPasswordField() {
        return newPasswordField(M3TextInputVariant.OUTLINED);
    }

    /// Creates a masked password field with the given variant and the launcher's localized editing menu.
    public static M3PasswordField newPasswordField(M3TextInputVariant variant) {
        M3PasswordField field = new M3PasswordField();
        field.setVariant(variant);
        installTextContextMenu(field);
        return field;
    }

    /// Displays validation errors below the field, including for its initial value.
    public static M3TextInputLayout newValidatedTextField(TextField field, M3TextInputValidator... validators) {
        M3TextInputLayout layout = new M3TextInputLayout(field);
        layout.getValidators().addAll(validators);
        layout.validate();
        return layout;
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

    @FXThread
    public static void installTooltip(Node node, Duration showDelay, Duration showDuration, Duration hideDelay, Tooltip tooltip) {
        checkFxUserThread();
        tooltip.setShowDelay(showDelay);
        tooltip.setShowDuration(showDuration);
        tooltip.setHideDelay(hideDelay);
        Tooltip.install(node, tooltip);
    }

    @FXThread
    public static void installFastTooltip(Node node, Tooltip tooltip) {
        installTooltip(node, TOOLTIP_FAST_SHOW_DELAY, TOOLTIP_SHOW_DURATION, Duration.ZERO, tooltip);
    }

    @FXThread
    public static void installFastTooltip(Node node, String tooltip) {
        installFastTooltip(node, new Tooltip(tooltip));
    }

    @FXThread
    public static void installSlowTooltip(Node node, Tooltip tooltip) {
        installTooltip(node, TOOLTIP_SLOW_SHOW_DELAY, TOOLTIP_SHOW_DURATION, Duration.ZERO, tooltip);
    }

    @FXThread
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
        if (SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS)
            openCommand = "explorer.exe";
        else if (SystemInfo.CURRENT_OS == OperatingSystem.MACOS)
            openCommand = "/usr/bin/open";
        else if (SystemInfo.isXdgPreferred() && Files.exists(Path.of("/usr/bin/xdg-open")))
            openCommand = "/usr/bin/xdg-open";
        else
            openCommand = null;

        thread(() -> {
            if (openCommand != null) {
                try {
                    int exitCode = SystemUtils.callExternalProcess(openCommand, path);

                    // explorer.exe always return 1
                    if (exitCode == 0 || (exitCode == 1 && SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS))
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
        if (SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS)
            openCommands = new String[]{"explorer.exe", "/select,", path};
        else if (SystemInfo.CURRENT_OS == OperatingSystem.MACOS)
            openCommands = new String[]{"/usr/bin/open", "-R", path};
        else if (SystemInfo.isXdgPreferred() && SystemUtils.which("dbus-send") != null)
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
                    if (exitCode == 0 || (exitCode == 1 && SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS))
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

        String url = NetworkUtils.encodeLocation(link);
        thread(() -> {
            try {
                if (SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS) {
                    Runtime.getRuntime().exec(new String[]{"rundll32.exe", "url.dll,FileProtocolHandler", url});
                    return;
                } else if (SystemInfo.CURRENT_OS == OperatingSystem.MACOS) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                    return;
                } else {
                    for (String browser : linuxBrowsers) {
                        Path path = SystemUtils.which(browser);
                        if (path != null) {
                            try {
                                Runtime.getRuntime().exec(new String[]{path.toString(), url});
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
                java.awt.Desktop.getDesktop().browse(new URI(url));
            } catch (Throwable e) {
                LOG.warning("Failed to open link: " + link, e);
            }
        });
    }

    public static <T> void bind(M3TextField textField, Property<T> property, @Nullable StringConverter<T> converter) {
        TextFieldBinding<T> binding = new TextFieldBinding<>(textField, property, converter);
        binding.updateTextField();
        textField.getProperties().put("FXUtils.bind.binding", binding);
        textField.focusedProperty().addListener(binding.focusedListener);
        textField.sceneProperty().addListener(binding.sceneListener);
        property.addListener(binding.propertyListener);
    }

    public static void bindInt(M3TextField textField, Property<Number> property) {
        bind(textField, property, SafeStringConverter.fromInteger());
    }

    public static void bindString(M3TextField textField, Property<String> property) {
        bind(textField, property, null);
    }

    public static void unbind(M3TextField textField, Property<?> property) {
        TextFieldBinding<?> binding = (TextFieldBinding<?>) textField.getProperties().remove("FXUtils.bind.binding");
        if (binding != null) {
            textField.focusedProperty().removeListener(binding.focusedListener);
            textField.sceneProperty().removeListener(binding.sceneListener);
            property.removeListener(binding.propertyListener);
        }
    }

    private static final class TextFieldBinding<T> {
        private final M3TextField textField;
        private final Property<T> property;
        private final @Nullable StringConverter<T> converter;

        public final ChangeListener<Boolean> focusedListener;
        public final ChangeListener<Scene> sceneListener;
        public final InvalidationListener propertyListener;

        public TextFieldBinding(M3TextField textField, Property<T> property, @Nullable StringConverter<T> converter) {
            this.textField = textField;
            this.property = property;
            this.converter = converter;

            focusedListener = (observable, oldFocused, newFocused) -> {
                if (oldFocused && !newFocused) {
                    if (!textField.isError()) {
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
                    if (!textField.isError()) {
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

    public static List<Subscription> bindAllEnabled(BooleanProperty allEnabled, BooleanProperty... children) {
        int itemCount = children.length;
        int childSelectedCount = 0;
        for (BooleanProperty child : children) {
            if (child.get())
                childSelectedCount++;
        }

        allEnabled.set(childSelectedCount == itemCount);

        class AllListener {
            private int childSelectedCount;
            private boolean updating = false;
            private final List<Subscription> subscriptions = new ArrayList<>(children.length + 1);

            public AllListener(int childSelectedCount) {
                this.childSelectedCount = childSelectedCount;
            }

            {
                subscriptions.add(Subscription.subscribe(allEnabled, () -> {
                    if (updating) return;
                    updating = true;
                    try {
                        boolean value = allEnabled.get();
                        for (BooleanProperty child : children) {
                            child.set(value);
                        }
                        childSelectedCount = value ? itemCount : 0;
                    } finally {
                        updating = false;
                    }
                }));
                for (var child : children) {
                    subscriptions.add(Subscription.subscribe(child, () -> {
                        if (updating) return;
                        updating = true;
                        try {
                            if (child.get()) childSelectedCount++;
                            else childSelectedCount--;
                            allEnabled.set(childSelectedCount == itemCount);
                        } finally {
                            updating = false;
                        }
                    }));
                }
            }
        }

        return new AllListener(childSelectedCount).subscriptions;
    }

    public static void setIcon(Stage stage) {
        String icon;
        if (SystemInfo.CURRENT_OS == OperatingSystem.WINDOWS) {
            icon = "/assets/img/icon.png";
        } else if (SystemInfo.CURRENT_OS == OperatingSystem.MACOS) {
            icon = "/assets/img/icon-mac.png";
        } else {
            icon = "/assets/img/icon@4x.png";
        }
        stage.getIcons().add(newBuiltinImage(icon));
    }

    @Contract("null -> false")
    public static boolean isValidImage(@Nullable Image image) {
        return image != null && !image.isError() && image.getWidth() > 0 && image.getHeight() > 0 && Math.abs(image.getWidth() - image.getHeight()) < 1;
    }

    @Contract("null -> false")
    public static boolean isValidIcon(@Nullable Image image) {
        return isValidImage(image) && Math.abs(image.getWidth() - image.getHeight()) < 1;
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
                .thenApplyAsync(file -> ImageLoaders.DEFAULT.load(file, requestedWidth, requestedHeight, preserveRatio, smooth))
                .setSignificance(Task.TaskSignificance.MINOR);
    }

    /// Creates a task that caches an image from HTTP(S) candidates and loads it with the requested sizing options.
    public static Task<Image> getRemoteImageTask(DownloadCandidates candidates, int requestedWidth, int requestedHeight, boolean preserveRatio, boolean smooth) {
        return new CacheFileTask(candidates)
                .setSignificance(Task.TaskSignificance.MINOR)
                .thenApplyAsync(file -> ImageLoaders.DEFAULT.load(file, requestedWidth, requestedHeight, preserveRatio, smooth))
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

    /// Creates an indeterminate circular indicator that keeps its preferred diameter in resizable layouts.
    public static M3ProgressIndicator newSpinner() {
        M3ProgressIndicator spinner = new M3ProgressIndicator();
        spinner.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return spinner;
    }

    /// Binds a button's graphic to a loading flag, restoring its initial graphic while idle.
    /// Callers can combine the flag with their own validation when binding the disabled state.
    public static BooleanProperty newButtonLoadingProperty(ButtonBase button) {
        BooleanProperty loading = new SimpleBooleanProperty(false);
        @Nullable Node idleGraphic = button.getGraphic();
        button.graphicProperty().bind(loading.map(active -> {
            if (!active)
                return idleGraphic;
            M3ProgressIndicator indicator = newSpinner();
            indicator.setIndicatorSize(18);
            indicator.setTrackThickness(2);
            return indicator;
        }));
        return loading;
    }

    public static Label newSafeTruncatedLabel() {
        Label label = new Label();
        label.setTextOverrun(OverrunStyle.CENTER_WORD_ELLIPSIS);
        showTooltipWhenTruncated(label);
        return label;
    }

    private static final String LABEL_FULL_TEXT_PROP_KEY = FXUtils.class.getName() + ".LABEL_FULL_TEXT";

    private static final ChangeListener<Boolean> TEXT_TRUNCATED_LISTENER = (observable, oldValue, newValue) -> {
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

    public static void showTooltipWhenTruncated(Labeled labeled) {
        ReadOnlyBooleanProperty textTruncatedProperty = textTruncatedProperty(labeled);
        if (textTruncatedProperty != null) {
            TEXT_TRUNCATED_LISTENER.changed(textTruncatedProperty, false, textTruncatedProperty.get());
            textTruncatedProperty.addListener(TEXT_TRUNCATED_LISTENER);
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

    public static void onDoubleClicked(Node node, Runnable action) {
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                action.run();
                e.consume();
            }
        });
    }

    public static void addMacOSCloseWindowHandler(Window window, @Nullable Supplier<Boolean> restriction) {
        window.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (SystemInfo.CURRENT_OS == OperatingSystem.MACOS
                    && event.isMetaDown()
                    && event.getCode() == KeyCode.W
                    && (restriction == null || restriction.get())) {
                window.fireEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
                event.consume();
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

    public static void copyFiles(List<Path> paths) {
        copyFiles(paths.stream().map(Path::toFile).toList(), i18n("message.copied"));
    }

    public static void copyFiles(List<File> files, @Nullable String toastMessage) {
        ClipboardContent content = new ClipboardContent();
        content.putFiles(files);
        Clipboard.getSystemClipboard().setContent(content);

        if (toastMessage != null && !Controllers.isStopped()) {
            Controllers.showToast(toastMessage);
        }
    }

    public static void copyImage(Image image) {
        copyImage(image, i18n("message.copied"));
    }

    public static void copyImage(Image image, @Nullable String toastMessage) {
        ClipboardContent content = new ClipboardContent();
        content.putImage(image);
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

    /// Installs the localized Material editing menu for a text input.
    public static void installTextContextMenu(TextInputControl control) {
        control.setContextMenu(null);

        control.setOnContextMenuRequested(e -> {
            PopupMenu popup = new PopupMenu();
            boolean hasNoSelection = control.getSelectedText().isEmpty();
            boolean isPassword = control instanceof PasswordField;

            M3MenuItem undo = PopupMenu.item(SVG.UNDO, i18n("menu.undo"), control::undo);
            M3MenuItem redo = PopupMenu.item(SVG.REDO, i18n("menu.redo"), control::redo);
            M3MenuItem cut = PopupMenu.item(SVG.CONTENT_CUT, i18n("menu.cut"), control::cut);
            M3MenuItem copy = PopupMenu.item(SVG.CONTENT_COPY, i18n("menu.copy"), control::copy);
            M3MenuItem paste = PopupMenu.item(SVG.CONTENT_PASTE, i18n("menu.paste"), control::paste);
            M3MenuItem delete = PopupMenu.item(SVG.DELETE, i18n("menu.deleteselection"), () -> control.replaceSelection(""));
            M3MenuItem selectall = PopupMenu.item(SVG.SELECT_ALL, i18n("menu.selectall"), control::selectAll);

            popup.getItems().setAll(undo, redo, new M3Divider(), cut, copy, paste, delete, new M3Divider(), selectall);

            undo.setDisable(!control.isUndoable());
            redo.setDisable(!control.isRedoable());
            cut.setDisable(hasNoSelection || isPassword);
            delete.setDisable(hasNoSelection);
            copy.setDisable(hasNoSelection || isPassword);
            paste.setDisable(!Clipboard.getSystemClipboard().hasString());
            selectall.setDisable(control.getText() == null || control.getText().isEmpty());

            popup.showAt(control, e.getScreenX(), e.getScreenY());

            e.consume();
        });
    }

    public static TextFlow renderAddonChangelog(String changelogHtml, String baseUri) {
        var textFlow = new HTMLRenderer(Controllers::openUriOrCopy).appendNode(Jsoup.parse(changelogHtml, baseUri)).mergeLineBreaks().render();
        textFlow.getStyleClass().add("addon-changelog");
        return textFlow;
    }

    /// Be cautious when dealing with large amounts of data
    public static <S> TableView<S> newAutoHeightTable(ObservableList<S> items) {
        return new AutoHeightTableView<>(items);
    }

    private static final class AutoHeightTableView<S> extends TableView<S> {

        public AutoHeightTableView() {
            super();
        }

        public AutoHeightTableView(ObservableList<S> items) {
            super(items);
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new AutoSizeSkin<>(this);
        }

        private static final class AutoSizeSkin<S> extends TableViewSkin<S> {

            private final VirtualFlow<TableRow<S>> flow;

            public AutoSizeSkin(TableView<S> control) {
                super(control);
                this.flow = getVirtualFlow();
            }

            @Override
            protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
                double fixedCellSize = getSkinnable().getFixedCellSize();
                double w = width - rightInset - leftInset;
                int itemCount = getItemCount();
                double headerHeight = getTableHeaderRow().prefHeight(w);

                double prefHeight = topInset + bottomInset + headerHeight;
                if (fixedCellSize > 0 || itemCount == 0) return prefHeight + fixedCellSize * itemCount;
                int i = 0;
                TableRow<S> r;
                while (i < itemCount && !(r = flow.getCell(i)).isEmpty() && r.getItem() != null) {
                    prefHeight += r.prefHeight(w);
                    i++;
                }
                return prefHeight;
            }

        }

    }

    public static void chooseDateRange(Consumer<@NotNull Pair<@Nullable LocalDate, @Nullable LocalDate>> onConfirm) {
        Controllers.dialog(new DateRangeDialog(onConfirm));
    }

    private static class DateRangeDialog extends LauncherDialog {

        private final JFXDatePicker fromPicker, toPicker;

        public DateRangeDialog(Consumer<Pair<LocalDate, LocalDate>> onConfirm) {
            super();

            getDialogPane().setHeadline(i18n("button.select_date"));

            GridPane body = new GridPane();
            body.setHgap(4);
            body.setVgap(8);
            {
                Label fromLabel = new Label(i18n("button.select_date.from"));
                this.fromPicker = new JFXDatePicker(LocalDate.now());
                fromPicker.setOverLay(true);
                fromPicker.setDialogParent(Controllers.getDecorator().getContentRoot());
                body.add(fromLabel, 0, 0);
                body.add(fromPicker, 1, 0);
            }
            {
                Label toLabel = new Label(i18n("button.select_date.to"));
                this.toPicker = new JFXDatePicker(LocalDate.now());
                toPicker.setOverLay(true);
                toPicker.setDialogParent(Controllers.getDecorator().getContentRoot());
                body.add(toLabel, 0, 1);
                body.add(toPicker, 1, 1);
            }
            getDialogPane().setContent(body);

            M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            M3Button okButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
            okButton.setOnAction(e -> {
                if (onConfirm != null) onConfirm.accept(Pair.pair(fromPicker.getValue(), toPicker.getValue()));
            });

            getDialogPane().getActions().setAll(cancelButton, okButton);
            cancelButton.setCancelButton(true);
        }

    }
}
