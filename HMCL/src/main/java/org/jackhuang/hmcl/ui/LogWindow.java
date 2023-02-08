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
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.util.CircularArrayList;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.StringUtils.parseEscapeSequence;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 *
 * @author huangyuhui
 */
public final class LogWindow extends Stage {

    private final ArrayDeque<Log> logs = new ArrayDeque<>();
    private final Map<Log4jLevel, SimpleIntegerProperty> levelCountMap = new EnumMap<Log4jLevel, SimpleIntegerProperty>(Log4jLevel.class) {
        {
            for (Log4jLevel level : Log4jLevel.values()) put(level, new SimpleIntegerProperty());
        }
    };
    private final Map<Log4jLevel, SimpleBooleanProperty> levelShownMap = new EnumMap<Log4jLevel, SimpleBooleanProperty>(Log4jLevel.class) {
        {
            for (Log4jLevel level : Log4jLevel.values()) {
                SimpleBooleanProperty property = new SimpleBooleanProperty(true);
                put(level, property);
            }
        }
    };
    private final LogWindowImpl impl = new LogWindowImpl();
    private final ChangeListener<Number> logLinesListener = FXUtils.onWeakChange(config().logLinesProperty(), logLines -> checkLogCount());

    private Consumer<String> exportGameCrashInfoCallback;

    private boolean stopCheckLogCount = false;

    public LogWindow() {
        setScene(new Scene(impl, 854, 480));
        getScene().getStylesheets().addAll(Theme.getTheme().getStylesheets(config().getLauncherFontFamily()));
        setTitle(i18n("logwindow.title"));
        getIcons().add(newImage("/assets/img/icon.png"));

        levelShownMap.values().forEach(property -> property.addListener((a, b, newValue) -> shakeLogs()));
    }

    public void logLine(String filteredLine, Log4jLevel level) {
        Log log = new Log(parseEscapeSequence(filteredLine), level);
        logs.add(log);
        if (levelShownMap.get(level).get())
            impl.listView.getItems().add(log);

        levelCountMap.get(level).setValue(levelCountMap.get(level).getValue() + 1);
        if (!stopCheckLogCount) checkLogCount();
    }

    public void showGameCrashReport(Consumer<String> exportGameCrashInfoCallback) {
        this.exportGameCrashInfoCallback = exportGameCrashInfoCallback;
        this.impl.showCrashReport.set(true);
        stopCheckLogCount = true;
        for (Log log : impl.listView.getItems()) {
            if (log.log.contains("Minecraft Crash Report")) {
                Platform.runLater(() -> {
                    impl.listView.scrollTo(log);
                });
                break;
            }
        }
        show();
    }

    public void showNormal() {
        this.impl.showCrashReport.set(false);
        show();
    }

    private void shakeLogs() {
        impl.listView.getItems().setAll(logs.stream().filter(log -> levelShownMap.get(log.level).get()).collect(Collectors.toList()));
    }

    private void checkLogCount() {
        while (logs.size() > config().getLogLines()) {
            Log removedLog = logs.removeFirst();
            if (!impl.listView.getItems().isEmpty() && impl.listView.getItems().get(0) == removedLog) {
                impl.listView.getItems().remove(0);
            }
        }
    }

    private static class Log {
        private final String log;
        private final Log4jLevel level;

        public Log(String log, Log4jLevel level) {
            this.log = log;
            this.level = level;
        }
    }

    public class LogWindowImpl extends Control {

        private final ListView<Log> listView = new JFXListView<>();
        private final BooleanProperty autoScroll = new SimpleBooleanProperty();
        private final List<StringProperty> buttonText = IntStream.range(0, 5).mapToObj(x -> new SimpleStringProperty()).collect(Collectors.toList());
        private final List<BooleanProperty> showLevel = IntStream.range(0, 5).mapToObj(x -> new SimpleBooleanProperty(true)).collect(Collectors.toList());
        private final JFXComboBox<String> cboLines = new JFXComboBox<>();
        private final BooleanProperty showCrashReport = new SimpleBooleanProperty();

        LogWindowImpl() {
            getStyleClass().add("log-window");

            listView.setItems(FXCollections.observableList(new CircularArrayList<>(config().getLogLines() + 1)));

            boolean flag = false;
            cboLines.getItems().setAll("10000", "5000", "2000", "500");
            for (String i : cboLines.getItems())
                if (Integer.toString(config().getLogLines()).equals(i)) {
                    cboLines.getSelectionModel().select(i);
                    flag = true;
                }

            cboLines.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> {
                config().setLogLines(newValue == null ? 100 : Integer.parseInt(newValue));
            });

            if (!flag)
                cboLines.getSelectionModel().select(0);

            Log4jLevel[] levels = new Log4jLevel[]{Log4jLevel.FATAL, Log4jLevel.ERROR, Log4jLevel.WARN, Log4jLevel.INFO, Log4jLevel.DEBUG};
            String[] suffix = new String[]{"fatals", "errors", "warns", "infos", "debugs"};
            for (int i = 0; i < 5; ++i) {
                buttonText.get(i).bind(Bindings.concat(levelCountMap.get(levels[i]), " " + suffix[i]));
                levelShownMap.get(levels[i]).bind(showLevel.get(i));
            }
        }

        private void onTerminateGame() {
            LauncherHelper.stopManagedProcesses();
        }

        private void onClear() {
            impl.listView.getItems().clear();
            logs.clear();
        }

        private void onExportLogs() {
            thread(() -> {
                Path logFile = Paths.get("minecraft-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".log").toAbsolutePath();
                try {
                    Files.write(logFile, logs.stream().map(x -> x.log).collect(Collectors.toList()));
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to export logs", e);
                    return;
                }

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n("settings.launcher.launcher_log.export.success", logFile));
                    alert.setTitle(i18n("settings.launcher.launcher_log.export"));
                    alert.showAndWait();
                });

                FXUtils.showFileInExplorer(logFile);
            });
        }

        private void onExportGameCrashInfo() {
            if (exportGameCrashInfoCallback == null) return;
            exportGameCrashInfoCallback.accept(logs.stream().map(x -> x.log).collect(Collectors.joining(OperatingSystem.LINE_SEPARATOR)));
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new LogWindowSkin(this);
        }
    }

    private static class LogWindowSkin extends SkinBase<LogWindowImpl> {
        private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
        private static final PseudoClass FATAL = PseudoClass.getPseudoClass("fatal");
        private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
        private static final PseudoClass WARN = PseudoClass.getPseudoClass("warn");
        private static final PseudoClass INFO = PseudoClass.getPseudoClass("info");
        private static final PseudoClass DEBUG = PseudoClass.getPseudoClass("debug");
        private static final PseudoClass TRACE = PseudoClass.getPseudoClass("trace");

        private static ToggleButton createToggleButton(String backgroundColor, StringProperty buttonText, BooleanProperty showLevel) {
            ToggleButton button = new ToggleButton();
            button.setStyle("-fx-background-color: " + backgroundColor + ";");
            button.getStyleClass().add("log-toggle");
            button.textProperty().bind(buttonText);
            button.setSelected(true);
            showLevel.bind(button.selectedProperty());
            return button;
        }

        protected LogWindowSkin(LogWindowImpl control) {
            super(control);

            VBox vbox = new VBox(3);
            vbox.setPadding(new Insets(3, 0, 3, 0));
            vbox.setStyle("-fx-background-color: white");
            getChildren().setAll(vbox);

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
                    hBox.getChildren().setAll(
                            createToggleButton("#F7A699", control.buttonText.get(0), control.showLevel.get(0)),
                            createToggleButton("#FFCCBB", control.buttonText.get(1), control.showLevel.get(1)),
                            createToggleButton("#FFEECC", control.buttonText.get(2), control.showLevel.get(2)),
                            createToggleButton("#FBFBFB", control.buttonText.get(3), control.showLevel.get(3)),
                            createToggleButton("#EEE9E0", control.buttonText.get(4), control.showLevel.get(4))
                    );
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

                listView.setStyle("-fx-font-family: " + Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT)
                        + "; -fx-font-size: " + config().getFontSize() + "px;");
                MutableObject<Object> lastCell = new MutableObject<>();
                listView.setCellFactory(x -> new ListCell<Log>() {
                    {
                        getStyleClass().add("log-window-list-cell");
                        Region clippedContainer = (Region)listView.lookup(".clipped-container");
                        if (clippedContainer != null) {
                            maxWidthProperty().bind(clippedContainer.widthProperty());
                            prefWidthProperty().bind(clippedContainer.widthProperty());
                        }
                        setPadding(new Insets(2));
                        setWrapText(true);
                        setGraphic(null);
                    }

                    @Override
                    protected void updateItem(Log item, boolean empty) {
                        super.updateItem(item, empty);

                        // https://mail.openjdk.org/pipermail/openjfx-dev/2022-July/034764.html
                        if (this == lastCell.getValue() && !isVisible())
                            return;
                        lastCell.setValue(this);

                        pseudoClassStateChanged(EMPTY, empty);
                        pseudoClassStateChanged(FATAL, !empty && item.level == Log4jLevel.FATAL);
                        pseudoClassStateChanged(ERROR, !empty && item.level == Log4jLevel.ERROR);
                        pseudoClassStateChanged(WARN, !empty && item.level == Log4jLevel.WARN);
                        pseudoClassStateChanged(INFO, !empty && item.level == Log4jLevel.INFO);
                        pseudoClassStateChanged(DEBUG, !empty && item.level == Log4jLevel.DEBUG);
                        pseudoClassStateChanged(TRACE, !empty && item.level == Log4jLevel.TRACE);
                        if (empty) {
                            setText(null);
                        } else {
                            setText(item.log);
                        }
                    }
                });

                VBox.setVgrow(listView, Priority.ALWAYS);
                vbox.getChildren().add(listView);
            }

            {
                BorderPane bottom = new BorderPane();

                JFXButton exportGameCrashInfoButton = new JFXButton(i18n("logwindow.export_game_crash_logs"));
                exportGameCrashInfoButton.setOnMouseClicked(e -> getSkinnable().onExportGameCrashInfo());
                exportGameCrashInfoButton.visibleProperty().bind(getSkinnable().showCrashReport);
                bottom.setLeft(exportGameCrashInfoButton);

                HBox hBox = new HBox(3);
                bottom.setRight(hBox);
                hBox.setAlignment(Pos.CENTER_RIGHT);
                hBox.setPadding(new Insets(0, 3, 0, 3));

                JFXCheckBox autoScrollCheckBox = new JFXCheckBox(i18n("logwindow.autoscroll"));
                autoScrollCheckBox.setSelected(true);
                control.autoScroll.bind(autoScrollCheckBox.selectedProperty());

                JFXButton terminateButton = new JFXButton(i18n("logwindow.terminate_game"));
                terminateButton.setOnMouseClicked(e -> getSkinnable().onTerminateGame());

                JFXButton exportLogsButton = new JFXButton(i18n("button.export"));
                exportLogsButton.setOnMouseClicked(e -> getSkinnable().onExportLogs());

                JFXButton clearButton = new JFXButton(i18n("button.clear"));
                clearButton.setOnMouseClicked(e -> getSkinnable().onClear());
                hBox.getChildren().setAll(autoScrollCheckBox, exportLogsButton, terminateButton, clearButton);

                vbox.getChildren().add(bottom);
            }
        }
    }
}
