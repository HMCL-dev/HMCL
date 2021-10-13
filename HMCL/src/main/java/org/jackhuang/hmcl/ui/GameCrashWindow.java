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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Lang;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameCrashWindow extends Stage {
    private final Version version;
    private final String memory;
    private final String java;
    private final LibraryAnalyzer analyzer;
    private final StringProperty os = new SimpleStringProperty(OperatingSystem.SYSTEM_NAME);
    private final StringProperty arch = new SimpleStringProperty(Architecture.SYSTEM_ARCH.getDisplayName());
    private final StringProperty reason = new SimpleStringProperty(i18n("game.crash.reason.unknown"));
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final Label feedbackLabel = new Label(i18n("game.crash.feedback"));

    private final ManagedProcess managedProcess;
    private final DefaultGameRepository repository;
    private final ProcessListener.ExitType exitType;
    private final LaunchOptions launchOptions;
    private final View view;

    private final LinkedList<Pair<String, Log4jLevel>> logs;

    public GameCrashWindow(ManagedProcess managedProcess, ProcessListener.ExitType exitType, DefaultGameRepository repository, Version version, LaunchOptions launchOptions, LinkedList<Pair<String, Log4jLevel>> logs) {
        this.managedProcess = managedProcess;
        this.exitType = exitType;
        this.repository = repository;
        this.version = version;
        this.launchOptions = launchOptions;
        this.logs = logs;
        this.analyzer = LibraryAnalyzer.analyze(version);

        memory = Optional.ofNullable(launchOptions.getMaxMemory()).map(i -> i + " MB").orElse("-");
        java = launchOptions.getJava().getVersion();

        this.view = new View();

        setScene(new Scene(view, 800, 480));
        getScene().getStylesheets().addAll(config().getTheme().getStylesheets(config().getLauncherFontFamily()));
        setTitle(i18n("game.crash.title"));
        getIcons().add(newImage("/assets/img/icon.png"));

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
                        case MOD_RESOLUTION_CONFLICT:
                        case MOD_RESOLUTION_MISSING:
                        case MOD_RESOLUTION_COLLECTION:
                            reasonText.append(i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    translateFabricModId(result.getMatcher().group("sourcemod")),
                                    parseFabricModId(result.getMatcher().group("destmod")),
                                    parseFabricModId(result.getMatcher().group("destmod"))));
                            break;
                        case MOD_RESOLUTION_MISSING_MINECRAFT:
                            reasonText.append(i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    translateFabricModId(result.getMatcher().group("mod")),
                                    result.getMatcher().group("version")));
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

                    feedbackLabel.setVisible(true);
                } else {
                    reason.set(reasonText.toString());
                }
            }
        }, Schedulers.javafx()).exceptionally(Lang::handleUncaughtException);
    }

    private static final Pattern FABRIC_MOD_ID = Pattern.compile("\\{(?<modid>.*?) @ (?<version>.*?)}");

    private String translateFabricModId(String modName) {
        switch (modName) {
            case "fabricloader":
                return "Fabric";
            case "fabric":
                return "Fabric API";
            case "minecraft":
                return "Minecraft";
            default:
                return modName;
        }
    }

    private String parseFabricModId(String modName) {
        Matcher matcher = FABRIC_MOD_ID.matcher(modName);
        if (matcher.find()) {
            String modid = matcher.group("modid");
            String version = matcher.group("version");
            if ("[*]".equals(version)) {
                return i18n("game.crash.reason.mod_resolution_mod_version.any", translateFabricModId(modid));
            } else {
                return i18n("game.crash.reason.mod_resolution_mod_version", translateFabricModId(modid), version);
            }
        }
        return translateFabricModId(modName);
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

            HBox infoPane = new HBox(8);
            {
                infoPane.setPadding(new Insets(8));
                infoPane.setAlignment(Pos.CENTER_LEFT);

                TwoLineListItem version = new TwoLineListItem();
                version.getStyleClass().setAll("two-line-item-second-large");
                version.setTitle(i18n("archive.game_version"));
                version.setSubtitle(GameCrashWindow.this.version.getId());

                TwoLineListItem memory = new TwoLineListItem();
                memory.getStyleClass().setAll("two-line-item-second-large");
                memory.setTitle(i18n("settings.memory"));
                memory.setSubtitle(GameCrashWindow.this.memory);

                TwoLineListItem java = new TwoLineListItem();
                java.getStyleClass().setAll("two-line-item-second-large");
                java.setTitle("Java");
                java.setSubtitle(GameCrashWindow.this.java);

                TwoLineListItem os = new TwoLineListItem();
                os.getStyleClass().setAll("two-line-item-second-large");
                os.setTitle(i18n("system.operating_system"));
                os.subtitleProperty().bind(GameCrashWindow.this.os);

                TwoLineListItem arch = new TwoLineListItem();
                arch.getStyleClass().setAll("two-line-item-second-large");
                arch.setTitle(i18n("system.architecture"));
                arch.subtitleProperty().bind(GameCrashWindow.this.arch);

                infoPane.getChildren().setAll(version, memory, java, os, arch);
            }

            HBox moddedPane = new HBox(8);
            {
                moddedPane.setPadding(new Insets(8));
                moddedPane.setAlignment(Pos.CENTER_LEFT);

                for (LibraryAnalyzer.LibraryType type : LibraryAnalyzer.LibraryType.values()) {
                    analyzer.getVersion(type).ifPresent(ver -> {
                        TwoLineListItem item = new TwoLineListItem();
                        item.getStyleClass().setAll("two-line-item-second-large");
                        item.setTitle(i18n("install.installer." + type.getPatchId()));
                        item.setSubtitle(ver);
                        moddedPane.getChildren().add(item);
                    });
                }
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
                toolBar.getChildren().setAll(exportGameCrashInfoButton, logButton, feedbackLabel);
            }

            getChildren().setAll(titlePane, infoPane, moddedPane, gameDirPane, toolBar);
        }

    }
}
