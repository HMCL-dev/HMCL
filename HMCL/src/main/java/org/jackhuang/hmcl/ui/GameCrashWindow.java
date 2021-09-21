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

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jackhuang.hmcl.game.CrashReportAnalyzer;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.game.LogExporter;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameCrashWindow extends Stage {
    private final StringProperty version = new SimpleStringProperty();
    private final StringProperty memory = new SimpleStringProperty();
    private final StringProperty java = new SimpleStringProperty();
    private final StringProperty os = new SimpleStringProperty(System.getProperty("os.name"));
    private final StringProperty arch = new SimpleStringProperty(Architecture.SYSTEM_ARCHITECTURE);
    private final StringProperty reason = new SimpleStringProperty(i18n("game.crash.reason.unknown"));
    private final BooleanProperty loading = new SimpleBooleanProperty();

    private final ManagedProcess managedProcess;
    private final DefaultGameRepository repository;
    private final ProcessListener.ExitType exitType;
    private final LaunchOptions launchOptions;
    private final View view;

    private final LinkedList<Pair<String, Log4jLevel>> logs;

    public GameCrashWindow(ManagedProcess managedProcess, ProcessListener.ExitType exitType, DefaultGameRepository repository, LaunchOptions launchOptions, LinkedList<Pair<String, Log4jLevel>> logs) {
        this.managedProcess = managedProcess;
        this.exitType = exitType;
        this.repository = repository;
        this.launchOptions = launchOptions;
        this.logs = logs;
        this.view = new View();

        setScene(new Scene(view, 800, 480));
        getScene().getStylesheets().addAll(config().getTheme().getStylesheets(config().getLauncherFontFamily()));
        setTitle(i18n("game.crash.title"));
        getIcons().add(newImage("/assets/img/icon.png"));

        version.set(launchOptions.getVersionName());
        memory.set(Optional.ofNullable(launchOptions.getMaxMemory()).map(i -> i + " MB").orElse("-"));
        java.set(launchOptions.getJava().getVersion());

        analyzeCrashReport();
    }

    private void analyzeCrashReport() {
        loading.set(true);
        CompletableFuture.supplyAsync(() -> {
            String rawLog = logs.stream().map(Pair::getKey).collect(Collectors.joining("\n"));
            Set<String> keywords = Collections.emptySet();
            try {
                String crashReport = CrashReportAnalyzer.findCrashReport(rawLog);
                if (crashReport != null) {
                    keywords = CrashReportAnalyzer.findKeywordsFromCrashReport(crashReport);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to read crash report", e);
            }
            return pair(
                    CrashReportAnalyzer.anaylze(rawLog),
                    keywords);
        }).whenCompleteAsync((pair, exception) -> {
            loading.set(false);

            if (exception != null) {
                LOG.log(Level.WARNING, "Failed to analyze crash report", exception);
                reason.set(i18n("game.crash.reason.unknown"));
            } else {
                List<CrashReportAnalyzer.Result> results = pair.getKey();
                Set<String> keywords = pair.getValue();
                StringBuilder reasonText = new StringBuilder();
                for (CrashReportAnalyzer.Result result : results) {
                    switch (result.getRule()) {
                        case TOO_OLD_JAVA:
                            reasonText.append(i18n("game.crash.reason.too_old_java",
                                    CrashReportAnalyzer.getJavaVersionFromMajorVersion(Integer.parseInt(result.getMatcher().group("expected")))))
                                    .append("\n");
                            break;
                        default:
                            reasonText.append(i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    Arrays.stream(result.getRule().getGroupNames()).map(groupName -> result.getMatcher().group(groupName))
                                            .toArray()))
                                    .append("\n");
                            break;
                    }
                }
                if (results.isEmpty()) {
                    if (!keywords.isEmpty()) {
                        reason.set(i18n("game.crash.reason.stacktrace", String.join(", ", keywords)));
                    } else {
                        reason.set(i18n("game.crash.reason.unknown"));
                    }
                } else {
                    reason.set(reasonText.toString());
                }
            }
        }, Schedulers.javafx());
    }

    private void showLogWindow() {
        LogWindow logWindow = new LogWindow();

        logWindow.logLine("Command: " + new CommandBuilder().addAll(managedProcess.getCommands()).toString(), Log4jLevel.INFO);
        for (Map.Entry<String, Log4jLevel> entry : logs)
            logWindow.logLine(entry.getKey(), entry.getValue());

        logWindow.showNormal();
    }

    private void exportGameCrashInfo() {
        Path logFile = Paths.get("minecraft-exported-crash-info-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".zip").toAbsolutePath();

        CompletableFuture.supplyAsync(() ->
                        logs.stream().map(Pair::getKey).collect(Collectors.joining(OperatingSystem.LINE_SEPARATOR)))
                .thenComposeAsync(logs ->
                        LogExporter.exportLogs(logFile, repository, launchOptions.getVersionName(), logs, new CommandBuilder().addAll(managedProcess.getCommands()).toString()))
                .thenRunAsync(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n("settings.launcher.launcher_log.export.success", logFile));
                    alert.setTitle(i18n("settings.launcher.launcher_log.export"));
                    alert.showAndWait();
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(logFile.toFile());
                        } catch (IOException | IllegalArgumentException ignored) {
                        }
                    }
                }, Schedulers.javafx())
                .exceptionally(e -> {
                    LOG.log(Level.WARNING, "Failed to export game crash info", e);
                    return null;
                });
    }

    private final class View extends VBox {

        View() {
            setStyle("-fx-background-color: white");

            HBox titlePane = new HBox();
            {
                Label title = new Label();
                HBox.setHgrow(title, Priority.ALWAYS);

                switch (exitType) {
                    case JVM_ERROR:
                        title.setText(i18n("launch.failed.cannot_create_jvm"));
                        break;
                    case APPLICATION_ERROR:
                        title.setText(i18n("launch.failed.exited_abnormally"));
                        break;
                }

                titlePane.setAlignment(Pos.CENTER);
                titlePane.getStyleClass().addAll("jfx-tool-bar-second", "depth-1", "padding-8");
                titlePane.getChildren().setAll(title);
            }

            HBox infoPane = new HBox();
            {
                TwoLineListItem version = new TwoLineListItem();
                version.getStyleClass().setAll("two-line-item-second-large");
                version.setTitle(i18n("archive.game_version"));
                version.subtitleProperty().bind(GameCrashWindow.this.version);
                StackPane versionCard = new StackPane(version);

                TwoLineListItem memory = new TwoLineListItem();
                memory.getStyleClass().setAll("two-line-item-second-large");
                memory.setTitle(i18n("settings.memory"));
                memory.subtitleProperty().bind(GameCrashWindow.this.memory);
                StackPane memoryCard = new StackPane(memory);

                TwoLineListItem java = new TwoLineListItem();
                java.getStyleClass().setAll("two-line-item-second-large");
                java.setTitle("Java");
                java.subtitleProperty().bind(GameCrashWindow.this.java);
                StackPane javaCard = new StackPane(java);

                TwoLineListItem os = new TwoLineListItem();
                os.getStyleClass().setAll("two-line-item-second-large");
                os.setTitle(i18n("system.operating_system"));
                os.subtitleProperty().bind(GameCrashWindow.this.os);
                StackPane osCard = new StackPane(os);

                TwoLineListItem arch = new TwoLineListItem();
                arch.getStyleClass().setAll("two-line-item-second-large");
                arch.setTitle(i18n("system.architecture"));
                arch.subtitleProperty().bind(GameCrashWindow.this.arch);
                StackPane archCard = new StackPane(arch);

                infoPane.setPadding(new Insets(8));
                infoPane.getChildren().setAll(versionCard, memoryCard, javaCard, osCard, archCard);
            }

            VBox gameDirPane = new VBox(8);
            {
                TwoLineListItem gameDir = new TwoLineListItem();
                gameDir.getStyleClass().setAll("two-line-item-second-large");
                gameDir.setTitle(i18n("game.directory"));
                gameDir.setSubtitle(launchOptions.getGameDir().getAbsolutePath());

                TwoLineListItem javaDir = new TwoLineListItem();
                javaDir.getStyleClass().setAll("two-line-item-second-large");
                javaDir.setTitle(i18n("settings.game.java_directory"));
                javaDir.setSubtitle(launchOptions.getJava().getBinary().toAbsolutePath().toString());

                TwoLineListItem reason = new TwoLineListItem();
                reason.getStyleClass().setAll("two-line-item-second-large", "wrap-text");
                reason.setTitle(i18n("game.crash.reason"));
                reason.subtitleProperty().bind(GameCrashWindow.this.reason);

                gameDirPane.setPadding(new Insets(8));
                VBox.setVgrow(gameDirPane, Priority.ALWAYS);
                gameDirPane.getChildren().setAll(gameDir, javaDir, reason);
            }

            HBox toolBar = new HBox();
            {
                JFXButton exportGameCrashInfoButton = new JFXButton(i18n("logwindow.export_game_crash_logs"));
                exportGameCrashInfoButton.setButtonType(JFXButton.ButtonType.RAISED);
                exportGameCrashInfoButton.getStyleClass().add("jfx-button-raised");
                exportGameCrashInfoButton.setOnMouseClicked(e -> exportGameCrashInfo());

                JFXButton logButton = new JFXButton(i18n("logwindow.title"));
                logButton.setButtonType(JFXButton.ButtonType.RAISED);
                logButton.getStyleClass().add("jfx-button-raised");
                logButton.setOnMouseClicked(e -> showLogWindow());

                toolBar.setPadding(new Insets(8));
                toolBar.setSpacing(8);
                toolBar.getStyleClass().add("jfx-tool-bar");
                toolBar.getChildren().setAll(exportGameCrashInfoButton, logButton);
            }

            getChildren().setAll(titlePane, infoPane, gameDirPane, toolBar);
        }

    }
}
