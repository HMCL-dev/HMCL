// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3OverlayPane;
import org.glavo.m3fx.controls.M3Snackbar;
import org.jackhuang.hmcl.game.GameDumpGenerator;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.construct.MessageDialogBuilder;
import org.jackhuang.hmcl.ui.construct.MessageType;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class LogWindow extends Stage {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private static final Log4jLevel[] LEVELS = {Log4jLevel.FATAL, Log4jLevel.ERROR, Log4jLevel.WARN, Log4jLevel.INFO, Log4jLevel.DEBUG};

    private final CircularArrayList<Log> logs;
    private final Map<Log4jLevel, SimpleIntegerProperty> levelCountMap = new EnumMap<>(Log4jLevel.class);
    private final Map<Log4jLevel, SimpleBooleanProperty> levelShownMap = new EnumMap<>(Log4jLevel.class);

    {
        for (Log4jLevel level : Log4jLevel.values()) {
            levelCountMap.put(level, new SimpleIntegerProperty());
            levelShownMap.put(level, new SimpleBooleanProperty(true));
        }
    }

    private final LogWindowImpl impl;
    private final ManagedProcess gameProcess;

    public LogWindow(ManagedProcess gameProcess) {
        this(gameProcess, new CircularArrayList<>());
    }

    public LogWindow(ManagedProcess gameProcess, CircularArrayList<Log> logs) {
        Themes.applyNativeDarkMode(this);

        this.logs = logs;
        this.impl = new LogWindowImpl();
        setScene(new Scene(impl, 800, 480));
        StyleSheets.init(getScene());
        setTitle(i18n("logwindow.title"));
        FXUtils.setIcon(this);

        for (SimpleBooleanProperty property : levelShownMap.values()) {
            property.addListener(o -> shakeLogs());
        }

        this.gameProcess = gameProcess;

        FXUtils.addMacOSCloseWindowHandler(this, () -> !gameProcess.isRunning());
    }

    public void logLine(Log log) {
        Log4jLevel level = log.getLevel();
        logs.add(log);
        if (levelShownMap.get(level).get())
            impl.listView.getItems().add(log);

        SimpleIntegerProperty property = levelCountMap.get(log.getLevel());
        property.set(property.get() + 1);
        checkLogCount();
        autoScroll();
    }

    public void logLines(List<Log> logs) {
        for (Log log : logs) {
            Log4jLevel level = log.getLevel();
            this.logs.add(log);
            if (levelShownMap.get(level).get())
                impl.listView.getItems().add(log);

            SimpleIntegerProperty property = levelCountMap.get(log.getLevel());
            property.set(property.get() + 1);
        }
        checkLogCount();
        autoScroll();
    }

    private void shakeLogs() {
        impl.listView.getItems().setAll(logs.stream().filter(log -> levelShownMap.get(log.getLevel()).get()).collect(Collectors.toList()));
        autoScroll();
    }

    private void checkLogCount() {
        int nRemove = logs.size() - Log.getLogLines();
        if (nRemove <= 0)
            return;

        ObservableList<Log> items = impl.listView.getItems();
        int itemsSize = items.size();
        int count = 0;

        for (int i = 0; i < nRemove; i++) {
            Log removedLog = logs.removeFirst();
            if (itemsSize > count && items.get(count) == removedLog)
                count++;
        }

        items.remove(0, count);
    }

    private void autoScroll() {
        if (!impl.listView.getItems().isEmpty() && impl.autoScroll.get())
            impl.listView.scrollTo(impl.listView.getItems().size() - 1);
    }

    private final class LogWindowImpl extends Control {

        private final ListView<Log> listView = new JFXListView<>();
        private final BooleanProperty autoScroll = new SimpleBooleanProperty();
        private final BooleanProperty wrapText = new SimpleBooleanProperty(true);
        private final StringProperty[] buttonText = new StringProperty[LEVELS.length];
        private final BooleanProperty[] showLevel = new BooleanProperty[LEVELS.length];
        private final M3IconButton btnAlwaysOnTop = new M3IconButton(SVG.KEEP.createIcon(20));
        private final Stage stage = LogWindow.this;
        private final JFXComboBox<Integer> cboLines = new JFXComboBox<>();
        /// Hosts log content, dialogs, and queued messages.
        private final M3OverlayPane overlayPane = new M3OverlayPane();

        LogWindowImpl() {
            getStyleClass().add("log-window");

            listView.getProperties().put("no-smooth-scrolling", true);
            listView.setItems(FXCollections.observableList(new CircularArrayList<>(logs.size())));

            for (int i = 0; i < LEVELS.length; i++) {
                buttonText[i] = new SimpleStringProperty();
                showLevel[i] = new SimpleBooleanProperty(true);
            }

            btnAlwaysOnTop.setOnAction(e -> stage.setAlwaysOnTop(!stage.isAlwaysOnTop()));
            btnAlwaysOnTop.getStyleClass().add("always-on-top-button");
            stage.alwaysOnTopProperty().addListener((observable, oldValue, newValue) -> {
                btnAlwaysOnTop.pseudoClassStateChanged(SELECTED, newValue);
            });

            cboLines.getItems().setAll(500, 2000, 5000, 10000);
            cboLines.setValue(Log.getLogLines());
            cboLines.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> settings().logLinesProperty().set(newValue));

            for (int i = 0; i < LEVELS.length; ++i) {
                buttonText[i].bind(Bindings.concat(levelCountMap.get(LEVELS[i]), " " + LEVELS[i].name().toLowerCase(Locale.ROOT) + "s"));
                levelShownMap.get(LEVELS[i]).bind(showLevel[i]);
            }
        }

        private void onTerminateGame() {
            LogWindow.this.gameProcess.stop();
        }

        private void onClear() {
            impl.listView.getItems().clear();
            logs.clear();
        }

        /// Exports the current logs and reports the saved path while this window remains visible.
        private void onExportLogs() {
            thread(() -> {
                Path logFile = Paths.get("minecraft-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toAbsolutePath();
                try {
                    Files.write(logFile, logs.stream().map(Log::getLog).collect(Collectors.toList()));
                } catch (IOException e) {
                    LOG.warning("Failed to export logs", e);
                    return;
                }

                Platform.runLater(() -> {
                    var dialog = new MessageDialogBuilder(i18n("settings.launcher.launcher_log.export.success", logFile), i18n("message.success"), MessageType.SUCCESS).ok(null).build();
                    if (stage.isShowing())
                        overlayPane.showDialog(dialog);
                });

                FXUtils.showFileInExplorer(logFile);
            });
        }

        /// Exports a game dump, showing progress and reporting failure in this window.
        private void onExportDump(BooleanProperty exporting) {
            assert SystemUtils.supportJVMAttachment();

            exporting.set(true);

            thread(() -> {
                Path dumpFile = Paths.get("minecraft-exported-jstack-dump-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toAbsolutePath();

                try {
                    if (gameProcess.isRunning()) {
                        GameDumpGenerator.writeDumpTo(gameProcess.getProcess().pid(), dumpFile);
                        FXUtils.showFileInExplorer(dumpFile);
                    }
                } catch (Throwable e) {
                    LOG.warning("Failed to create minecraft jstack dump", e);

                    Platform.runLater(() -> {
                        var dialog = new MessageDialogBuilder(i18n("logwindow.export_dump") + "\n" + StringUtils.getStackTrace(e), i18n("message.error"), MessageType.ERROR).ok(null).build();
                        if (stage.isShowing())
                            overlayPane.showDialog(dialog);
                    });
                }

                Platform.runLater(() -> exporting.set(false));
            });
        }

        private ManagedProcess getGameProcess() {
            return gameProcess;
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new LogWindowSkin(this);
        }
    }

    private static final class LogWindowSkin extends SkinBase<LogWindowImpl> {
        private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
        private static final PseudoClass FATAL = PseudoClass.getPseudoClass("fatal");
        private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
        private static final PseudoClass WARN = PseudoClass.getPseudoClass("warn");
        private static final PseudoClass INFO = PseudoClass.getPseudoClass("info");
        private static final PseudoClass DEBUG = PseudoClass.getPseudoClass("debug");
        private static final PseudoClass TRACE = PseudoClass.getPseudoClass("trace");

        /// Builds the log viewer with an M3FX overlay host for dialogs and messages.
        LogWindowSkin(LogWindowImpl control) {
            super(control);

            VBox vbox = new VBox(3);
            vbox.setPadding(new Insets(3, 0, 3, 0));
            getSkinnable().overlayPane.setContent(vbox);
            getChildren().setAll(getSkinnable().overlayPane);
            getSkinnable().overlayPane.setSnackbarDisplayDuration(Duration.seconds(1.5));

            {
                BorderPane borderPane = new BorderPane();
                borderPane.setPadding(new Insets(0, 3, 0, 3));

                {
                    HBox hBox = new HBox(3);
                    hBox.setPadding(new Insets(0, 0, 0, 4));
                    hBox.setAlignment(Pos.CENTER_LEFT);

                    Label label = new Label(i18n("logwindow.show_lines"));

                    FXUtils.installFastTooltip(control.btnAlwaysOnTop, i18n("logwindow.always_on_top"));
                    hBox.getChildren().setAll(control.btnAlwaysOnTop, label, control.cboLines);

                    borderPane.setLeft(hBox);
                }

                {
                    HBox hBox = new HBox(3);
                    for (int i = 0; i < LEVELS.length; i++) {
                        ToggleButton button = new ToggleButton();
                        button.getStyleClass().addAll("log-toggle", LEVELS[i].name().toLowerCase(Locale.ROOT));
                        button.textProperty().bind(control.buttonText[i]);
                        button.setSelected(true);
                        control.showLevel[i].bind(button.selectedProperty());
                        hBox.getChildren().add(button);
                    }

                    borderPane.setRight(hBox);
                }

                vbox.getChildren().add(borderPane);
            }

            {
                ListView<Log> listView = control.listView;
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                listView.getItems().addListener((InvalidationListener) observable -> {
                    if (!listView.getItems().isEmpty() && control.autoScroll.get())
                        listView.scrollTo(listView.getItems().size() - 1);
                });

                listView.setStyle("-fx-font-family: \"" + Objects.requireNonNullElse(settings().logFontFamilyProperty().get(), FXUtils.DEFAULT_MONOSPACE_FONT)
                        + "\"; -fx-font-size: " + settings().logFontSizeProperty().get() + "px;");
                listView.setCellFactory(x -> new ListCell<>() {
                    {
                        getStyleClass().add("log-window-list-cell");
                        Region clippedContainer = (Region) listView.lookup(".clipped-container");
                        if (clippedContainer != null) {
                            wrapTextProperty().addListener((obs, oldWrap, nowWrap) -> {
                                if (nowWrap) {
                                    maxWidthProperty().bind(clippedContainer.widthProperty());
                                    prefWidthProperty().bind(clippedContainer.widthProperty());
                                    listView.getStyleClass().add("no-horizontal-scrollbar");
                                } else {
                                    maxWidthProperty().unbind();
                                    prefWidthProperty().unbind();

                                    setMaxWidth(Region.USE_PREF_SIZE);
                                    setPrefWidth(Region.USE_COMPUTED_SIZE);
                                    listView.getStyleClass().remove("no-horizontal-scrollbar");
                                }

                                // only for horizontal scrollbar
                                listView.requestLayout();
                            });
                        }
                        setPadding(new Insets(2));
                        wrapTextProperty().bind(control.wrapText);
                        setGraphic(null);
                    }

                    @Override
                    protected void updateItem(Log item, boolean empty) {
                        super.updateItem(item, empty);

                        pseudoClassStateChanged(EMPTY, empty);
                        pseudoClassStateChanged(FATAL, !empty && item.getLevel() == Log4jLevel.FATAL);
                        pseudoClassStateChanged(ERROR, !empty && item.getLevel() == Log4jLevel.ERROR);
                        pseudoClassStateChanged(WARN, !empty && item.getLevel() == Log4jLevel.WARN);
                        pseudoClassStateChanged(INFO, !empty && item.getLevel() == Log4jLevel.INFO);
                        pseudoClassStateChanged(DEBUG, !empty && item.getLevel() == Log4jLevel.DEBUG);
                        pseudoClassStateChanged(TRACE, !empty && item.getLevel() == Log4jLevel.TRACE);

                        if (empty) {
                            setText(null);
                        } else {
                            setText(item.getLog());
                        }
                    }
                });

                listView.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        if (listView.getSelectionModel().isEmpty())
                            return;

                        StringBuilder stringBuilder = new StringBuilder();

                        for (Log item : listView.getSelectionModel().getSelectedItems()) {
                            if (item != null) {
                                if (item.getLog() != null)
                                    stringBuilder.append(item.getLog());
                                stringBuilder.append('\n');
                            }
                        }

                        FXUtils.copyText(stringBuilder.toString(), null);
                        getSkinnable().overlayPane.enqueueSnackbar(new M3Snackbar(i18n("message.copied")));
                    }
                });

                VBox.setVgrow(listView, Priority.ALWAYS);
                vbox.getChildren().add(listView);
            }

            {
                BorderPane bottom = new BorderPane();

                HBox hBox = new HBox(3);
                bottom.setRight(hBox);
                hBox.setAlignment(Pos.CENTER_RIGHT);
                hBox.setPadding(new Insets(0, 3, 0, 3));

                JFXCheckBox autoScrollCheckBox = new JFXCheckBox(i18n("logwindow.autoscroll"));
                autoScrollCheckBox.setSelected(true);
                control.autoScroll.bind(autoScrollCheckBox.selectedProperty());

                JFXCheckBox wrapTextCheckBox = new JFXCheckBox(i18n("logwindow.wrap_text"));
                wrapTextCheckBox.setSelected(true);
                control.wrapText.bind(wrapTextCheckBox.selectedProperty());

                M3Button exportLogsButton = new M3Button(i18n("button.export"), M3ButtonVariant.TEXT);
                exportLogsButton.setOnAction(e -> getSkinnable().onExportLogs());

                M3Button terminateButton = new M3Button(i18n("logwindow.terminate_game"), M3ButtonVariant.TEXT);
                terminateButton.setOnAction(e -> getSkinnable().onTerminateGame());

                M3Button exportDumpButton = new M3Button(i18n("logwindow.export_dump"), M3ButtonVariant.TEXT);
                Node dumpAction = exportDumpButton;
                if (SystemUtils.supportJVMAttachment()) {
                    var exporting = FXUtils.newButtonLoadingProperty(exportDumpButton);
                    exportDumpButton.disableProperty().bind(exporting);
                    exportDumpButton.setOnAction(e -> getSkinnable().onExportDump(exporting));
                } else {
                    dumpAction = new StackPane(exportDumpButton);
                    FXUtils.installFastTooltip(dumpAction, i18n("logwindow.export_dump.no_dependency"));
                    exportDumpButton.setDisable(true);
                }

                M3Button clearButton = new M3Button(i18n("button.clear"), M3ButtonVariant.TEXT);
                clearButton.setOnAction(e -> getSkinnable().onClear());
                hBox.getChildren().setAll(autoScrollCheckBox, wrapTextCheckBox, exportLogsButton, terminateButton, dumpAction, clearButton);

                control.getGameProcess().getProcess()
                        .onExit()
                        .thenRunAsync(() -> {
                            terminateButton.setDisable(true);
                            exportDumpButton.setDisable(true);
                        }, Schedulers.javafx());

                vbox.getChildren().add(bottom);
            }
        }
    }
}
