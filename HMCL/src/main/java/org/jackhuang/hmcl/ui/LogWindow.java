/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.jackhuang.hmcl.game.GameDumpGenerator;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.platform.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class LogWindow extends Stage {

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
        private final StringProperty[] buttonText = new StringProperty[LEVELS.length];
        private final BooleanProperty[] showLevel = new BooleanProperty[LEVELS.length];
        private final JFXComboBox<Integer> cboLines = new JFXComboBox<>();
        private final StackPane stackPane = new StackPane();

        LogWindowImpl() {
            getStyleClass().add("log-window");

            listView.getProperties().put("no-smooth-scrolling", true);
            listView.setItems(FXCollections.observableList(new CircularArrayList<>(logs.size())));

            for (int i = 0; i < LEVELS.length; i++) {
                buttonText[i] = new SimpleStringProperty();
                showLevel[i] = new SimpleBooleanProperty(true);
            }

            cboLines.getItems().setAll(500, 2000, 5000, 10000);
            cboLines.setValue(Log.getLogLines());
            cboLines.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> config().setLogLines(newValue));

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
                    var dialog = new MessageDialogPane.Builder(i18n("settings.launcher.launcher_log.export.success", logFile), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS).ok(null).build();
                    DialogUtils.show(stackPane, dialog);
                });

                FXUtils.showFileInExplorer(logFile);
            });
        }

        private void onExportDump(SpinnerPane pane) {
            assert SystemUtils.supportJVMAttachment();

            pane.setLoading(true);

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
                        var dialog = new MessageDialogPane.Builder(i18n("logwindow.export_dump") + "\n" + StringUtils.getStackTrace(e), i18n("message.error"), MessageDialogPane.MessageType.ERROR).ok(null).build();
                        DialogUtils.show(stackPane, dialog);
                    });
                }

                Platform.runLater(() -> pane.setLoading(false));
            });
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
        private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

        private final Set<ListCell<Log>> selected = new HashSet<>();

        LogWindowSkin(LogWindowImpl control) {
            super(control);

            VBox vbox = new VBox(3);
            vbox.setPadding(new Insets(3, 0, 3, 0));
            getSkinnable().stackPane.getChildren().setAll(vbox);
            getChildren().setAll(getSkinnable().stackPane);


            {
                BorderPane borderPane = new BorderPane();
                borderPane.setPadding(new Insets(0, 3, 0, 3));

                {
                    HBox hBox = new HBox(3);
                    hBox.setPadding(new Insets(0, 0, 0, 4));
                    hBox.setAlignment(Pos.CENTER_LEFT);

                    Label label = new Label(i18n("logwindow.show_lines"));
                    hBox.getChildren().setAll(label, control.cboLines);

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
                listView.getItems().addListener((InvalidationListener) observable -> {
                    if (!listView.getItems().isEmpty() && control.autoScroll.get())
                        listView.scrollTo(listView.getItems().size() - 1);
                });

                listView.setStyle("-fx-font-family: \"" + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT)
                        + "\"; -fx-font-size: " + config().getFontSize() + "px;");
                listView.setCellFactory(x -> new ListCell<>() {
                    {
                        x.setSelectionModel(new NoneMultipleSelectionModel<>());
                        getStyleClass().add("log-window-list-cell");
                        Region clippedContainer = (Region) listView.lookup(".clipped-container");
                        if (clippedContainer != null) {
                            maxWidthProperty().bind(clippedContainer.widthProperty());
                            prefWidthProperty().bind(clippedContainer.widthProperty());
                        }
                        setPadding(new Insets(2));
                        setWrapText(true);
                        setGraphic(null);

                        setOnMouseClicked(event -> {
                            if (event.getButton() != MouseButton.PRIMARY)
                                return;

                            if (!event.isControlDown()) {
                                for (ListCell<Log> logListCell : selected) {
                                    if (logListCell != this) {
                                        logListCell.pseudoClassStateChanged(SELECTED, false);
                                        if (logListCell.getItem() != null) {
                                            logListCell.getItem().setSelected(false);
                                        }
                                    }
                                }

                                selected.clear();
                            }

                            selected.add(this);
                            pseudoClassStateChanged(SELECTED, true);
                            if (getItem() != null) {
                                getItem().setSelected(true);
                            }

                            event.consume();
                        });
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
                        pseudoClassStateChanged(SELECTED, !empty && item.isSelected());

                        if (empty) {
                            setText(null);
                        } else {
                            setText(item.getLog());
                        }
                    }
                });

                listView.setOnKeyPressed(event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.C) {
                        StringBuilder stringBuilder = new StringBuilder();

                        for (Log item : listView.getItems()) {
                            if (item != null && item.isSelected()) {
                                if (item.getLog() != null)
                                    stringBuilder.append(item.getLog());
                                stringBuilder.append('\n');
                            }
                        }

                        FXUtils.copyText(stringBuilder.toString());
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

                JFXButton exportLogsButton = new JFXButton(i18n("button.export"));
                exportLogsButton.setOnAction(e -> getSkinnable().onExportLogs());

                JFXButton terminateButton = new JFXButton(i18n("logwindow.terminate_game"));
                terminateButton.setOnAction(e -> getSkinnable().onTerminateGame());

                SpinnerPane exportDumpPane = new SpinnerPane();
                JFXButton exportDumpButton = new JFXButton(i18n("logwindow.export_dump"));
                if (SystemUtils.supportJVMAttachment()) {
                    exportDumpButton.setOnAction(e -> getSkinnable().onExportDump(exportDumpPane));
                } else {
                    exportDumpButton.setTooltip(new Tooltip(i18n("logwindow.export_dump.no_dependency")));
                    exportDumpButton.setDisable(true);
                }
                exportDumpPane.setContent(exportDumpButton);

                JFXButton clearButton = new JFXButton(i18n("button.clear"));
                clearButton.setOnAction(e -> getSkinnable().onClear());
                hBox.getChildren().setAll(autoScrollCheckBox, exportLogsButton, terminateButton, exportDumpPane, clearButton);

                vbox.getChildren().add(bottom);
            }
        }
    }
}
