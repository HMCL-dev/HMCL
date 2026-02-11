/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2023  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.setting.StyleSheets;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class GameCrashWindow extends Stage {
    private final Version version;
    private final String memory;
    private final String total_memory;
    private final String java;
    private final LibraryAnalyzer analyzer;
    private final TextFlow reasonTextFlow = new TextFlow(new Text(i18n("game.crash.reason.unknown")));
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final TextFlow feedbackTextFlow = new TextFlow();

    private final ManagedProcess managedProcess;
    private final DefaultGameRepository repository;
    private final ProcessListener.ExitType exitType;
    private final LaunchOptions launchOptions;
    private final View view;
    private final StackPane stackPane;

    private final List<Log> logs;

    public GameCrashWindow(ManagedProcess managedProcess, ProcessListener.ExitType exitType, DefaultGameRepository repository, Version version, LaunchOptions launchOptions, List<Log> logs) {
        Themes.applyNativeDarkMode(this);

        this.managedProcess = managedProcess;
        this.exitType = exitType;
        this.repository = repository;
        this.version = version;
        this.launchOptions = launchOptions;
        this.logs = logs;
        this.analyzer = LibraryAnalyzer.analyze(version, repository.getGameVersion(version).orElse(null));

        memory = Optional.ofNullable(launchOptions.getMaxMemory()).map(i -> i + " " + i18n("settings.memory.unit.mib")).orElse("-");

        total_memory = MEGABYTES.formatBytes(SystemInfo.getTotalMemorySize());

        this.java = launchOptions.getJava().getArchitecture() == Architecture.SYSTEM_ARCH
                ? launchOptions.getJava().getVersion()
                : launchOptions.getJava().getVersion() + " (" + launchOptions.getJava().getArchitecture().getDisplayName() + ")";

        this.view = new View();

        this.stackPane = new StackPane(view);
        this.feedbackTextFlow.getChildren().addAll(FXUtils.parseSegment(i18n("game.crash.feedback"), Controllers::onHyperlinkAction));

        setScene(new Scene(stackPane, 800, 480));
        StyleSheets.init(getScene());
        setTitle(i18n("game.crash.title"));
        FXUtils.setIcon(this);

        analyzeCrashReport();
    }

    @SuppressWarnings("unchecked")
    private void analyzeCrashReport() {
        loading.set(true);
        Task.allOf(Task.supplyAsync(() -> {
            String rawLog = logs.stream().map(Log::getLog).collect(Collectors.joining("\n"));

            // Get the crash-report from the crash-reports/xxx, or the output of console.
            String crashReport = null;
            try {
                crashReport = CrashReportAnalyzer.findCrashReport(rawLog);
            } catch (IOException e) {
                LOG.warning("Failed to read crash report", e);
            }
            if (crashReport == null) {
                crashReport = CrashReportAnalyzer.extractCrashReport(rawLog);
            }

            return pair(CrashReportAnalyzer.analyze(rawLog), crashReport != null ? CrashReportAnalyzer.findKeywordsFromCrashReport(crashReport) : new HashSet<>());
        }), Task.supplyAsync(() -> {
            Path latestLog = repository.getRunDirectory(version.getId()).resolve("logs/latest.log");
            if (!Files.isReadable(latestLog)) {
                return pair(new HashSet<CrashReportAnalyzer.Result>(), new HashSet<String>());
            }

            String log;
            try {
                log = FileUtils.readTextMaybeNativeEncoding(latestLog);
            } catch (IOException e) {
                LOG.warning("Failed to read logs/latest.log", e);
                return pair(new HashSet<CrashReportAnalyzer.Result>(), new HashSet<String>());
            }

            return pair(CrashReportAnalyzer.analyze(log), CrashReportAnalyzer.findKeywordsFromCrashReport(log));
        })).whenComplete(Schedulers.javafx(), (taskResult, exception) -> {
            loading.set(false);

            if (exception != null) {
                LOG.warning("Failed to analyze crash report", exception);
                reasonTextFlow.getChildren().setAll(FXUtils.parseSegment(i18n("game.crash.reason.unknown"), Controllers::onHyperlinkAction));
            } else {
                EnumMap<CrashReportAnalyzer.Rule, CrashReportAnalyzer.Result> results = new EnumMap<>(CrashReportAnalyzer.Rule.class);
                Set<String> keywords = new HashSet<>();
                for (Pair<Set<CrashReportAnalyzer.Result>, Set<String>> pair : (List<Pair<Set<CrashReportAnalyzer.Result>, Set<String>>>) (List<?>) taskResult) {
                    for (CrashReportAnalyzer.Result result : pair.getKey()) {
                        results.put(result.getRule(), result);
                    }
                    keywords.addAll(pair.getValue());
                }

                List<Node> segments = new ArrayList<>(FXUtils.parseSegment(i18n("game.crash.feedback"), Controllers::onHyperlinkAction));

                LOG.info("Number of reasons: " + results.size());
                if (results.size() > 1) {
                    segments.add(new Text("\n"));
                    segments.addAll(FXUtils.parseSegment(i18n("game.crash.reason.multiple"), Controllers::onHyperlinkAction));
                } else {
                    segments.add(new Text("\n\n"));
                }

                for (CrashReportAnalyzer.Result result : results.values()) {
                    String message;
                    switch (result.getRule()) {
                        case TOO_OLD_JAVA:
                            message = i18n("game.crash.reason.too_old_java", CrashReportAnalyzer.getJavaVersionFromMajorVersion(Integer.parseInt(result.getMatcher().group("expected"))));
                            break;
                        case MOD_RESOLUTION_CONFLICT:
                        case MOD_RESOLUTION_MISSING:
                        case MOD_RESOLUTION_COLLECTION:
                            message = i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    translateFabricModId(result.getMatcher().group("sourcemod")),
                                    parseFabricModId(result.getMatcher().group("destmod")),
                                    parseFabricModId(result.getMatcher().group("destmod")));
                            break;
                        case MOD_RESOLUTION_MISSING_MINECRAFT:
                            message = i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    translateFabricModId(result.getMatcher().group("mod")),
                                    result.getMatcher().group("version"));
                            break;
                        case MOD_FOREST_OPTIFINE:
                        case TWILIGHT_FOREST_OPTIFINE:
                        case PERFORMANT_FOREST_OPTIFINE:
                        case JADE_FOREST_OPTIFINE:
                        case NEOFORGE_FOREST_OPTIFINE:
                            message = i18n("game.crash.reason.mod", "OptiFine");
                            LOG.info("Crash cause: " + result.getRule() + ": " + i18n("game.crash.reason.mod", "OptiFine"));
                            break;
                        default:
                            message = i18n("game.crash.reason." + result.getRule().name().toLowerCase(Locale.ROOT),
                                    Arrays.stream(result.getRule().getGroupNames()).map(groupName -> result.getMatcher().group(groupName))
                                            .toArray());
                            break;
                    }
                    LOG.info("Crash cause: " + result.getRule() + ": " + message);
                    segments.addAll(FXUtils.parseSegment(message, Controllers::onHyperlinkAction));
                    segments.add(new Text("\n\n"));
                }
                if (results.isEmpty()) {
                    if (!keywords.isEmpty()) {
                        reasonTextFlow.getChildren().setAll(new Text(i18n("game.crash.reason.stacktrace", String.join(", ", keywords))));
                        LOG.info("Crash reason unknown, but some log keywords have been found: " + String.join(", ", keywords));
                    } else {
                        reasonTextFlow.getChildren().setAll(FXUtils.parseSegment(i18n("game.crash.reason.unknown"), Controllers::onHyperlinkAction));
                        LOG.info("Crash reason unknown");
                    }
                } else {
                    feedbackTextFlow.setVisible(false);
                    reasonTextFlow.getChildren().setAll(segments);
                }
            }
        }).start();
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
        LogWindow logWindow = new LogWindow(managedProcess);

        logWindow.logLine(new Log(Logger.filterForbiddenToken("Command: " + new CommandBuilder().addAll(managedProcess.getCommands())), Log4jLevel.INFO));
        if (managedProcess.getClasspath() != null)
            logWindow.logLine(new Log("ClassPath: " + managedProcess.getClasspath(), Log4jLevel.INFO));
        logWindow.logLines(logs);
        logWindow.show();
    }

    private void exportGameCrashInfo() {
        Path logFile = Paths.get("minecraft-exported-crash-info-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".zip").toAbsolutePath();

        CompletableFuture.supplyAsync(() ->
                        logs.stream().map(Log::getLog).collect(Collectors.joining("\n")))
                .thenComposeAsync(logs -> {
                    long processStartTime = managedProcess.getProcess().info()
                            .startInstant()
                            .map(Instant::toEpochMilli).orElseGet(() -> {
                                try {
                                    return ManagementFactory.getRuntimeMXBean().getStartTime();
                                } catch (Throwable e) {
                                    LOG.warning("Failed to get process start time", e);
                                    return 0L;
                                }
                            });

                    return LogExporter.exportLogs(logFile, repository, launchOptions.getVersionName(), logs,
                            new CommandBuilder().addAll(managedProcess.getCommands()).toString(),
                            path -> {
                                try {
                                    FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                                    return lastModifiedTime.toMillis() >= processStartTime;
                                } catch (Throwable e) {
                                    LOG.warning("Failed to read file attributes", e);
                                    return false;
                                }
                            });
                })
                .handleAsync((result, exception) -> {
                    if (exception == null) {
                        FXUtils.showFileInExplorer(logFile);
                        var dialog = new MessageDialogPane.Builder(i18n("settings.launcher.launcher_log.export.success", logFile), i18n("message.success"), MessageDialogPane.MessageType.SUCCESS).ok(null).build();
                        DialogUtils.show(stackPane, dialog);
                    } else {
                        LOG.warning("Failed to export game crash info", exception);
                        var dialog = new MessageDialogPane.Builder(i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(exception), i18n("message.error"), MessageDialogPane.MessageType.ERROR).ok(null).build();
                        DialogUtils.show(stackPane, dialog);
                    }

                    return null;
                }, Schedulers.javafx());
    }

    private final class View extends VBox {

        View() {
            this.getStyleClass().add("game-crash-window");

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
                    case SIGKILL:
                        title.setText(i18n("launch.failed.sigkill"));
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

                TwoLineListItem launcher = new TwoLineListItem();
                launcher.getStyleClass().setAll("two-line-item-second-large");
                launcher.setTitle(i18n("launcher"));
                launcher.setSubtitle(Metadata.VERSION);

                TwoLineListItem version = new TwoLineListItem();
                version.getStyleClass().setAll("two-line-item-second-large");
                version.setTitle(i18n("game.version"));
                version.setSubtitle(GameCrashWindow.this.version.getId());

                TwoLineListItem total_memory = new TwoLineListItem();
                total_memory.getStyleClass().setAll("two-line-item-second-large");
                total_memory.setTitle(i18n("settings.physical_memory"));
                total_memory.setSubtitle(GameCrashWindow.this.total_memory);

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
                os.setSubtitle(Lang.requireNonNullElse(OperatingSystem.OS_RELEASE_NAME, OperatingSystem.SYSTEM_NAME));

                TwoLineListItem arch = new TwoLineListItem();
                arch.getStyleClass().setAll("two-line-item-second-large");
                arch.setTitle(i18n("system.architecture"));
                arch.setSubtitle(Architecture.SYSTEM_ARCH.getDisplayName());

                infoPane.getChildren().setAll(launcher, version, total_memory, memory, java, os, arch);
            }

            HBox moddedPane = new HBox(8);
            {
                moddedPane.setPadding(new Insets(8));
                moddedPane.setAlignment(Pos.CENTER_LEFT);

                for (LibraryAnalyzer.LibraryType type : LibraryAnalyzer.LibraryType.values()) {
                    if (!type.getPatchId().isEmpty()) {
                        analyzer.getVersion(type).ifPresent(ver -> {
                            TwoLineListItem item = new TwoLineListItem();
                            item.getStyleClass().setAll("two-line-item-second-large");
                            item.setTitle(i18n("install.installer." + type.getPatchId()));
                            item.setSubtitle(ver);
                            moddedPane.getChildren().add(item);
                        });
                    }
                }
            }

            VBox gameDirPane = new VBox(8);
            {
                TwoLineListItem gameDir = new TwoLineListItem();
                gameDir.getStyleClass().setAll("two-line-item-second-large");
                gameDir.setTitle(i18n("game.directory"));
                gameDir.setSubtitle(launchOptions.getGameDir().toAbsolutePath().toString());
                FXUtils.installFastTooltip(gameDir, i18n("game.directory"));

                TwoLineListItem javaDir = new TwoLineListItem();
                javaDir.getStyleClass().setAll("two-line-item-second-large");
                javaDir.setTitle(i18n("settings.game.java_directory"));
                javaDir.setSubtitle(launchOptions.getJava().getBinary().toAbsolutePath().toString());
                FXUtils.installFastTooltip(javaDir, i18n("settings.game.java_directory"));

                Label reasonTitle = new Label(i18n("game.crash.reason"));
                reasonTitle.getStyleClass().add("two-line-item-second-large-title");

                ScrollPane reasonPane = new ScrollPane(reasonTextFlow);
                reasonTextFlow.getStyleClass().add("crash-reason-text-flow");
                reasonPane.setFitToWidth(true);
                reasonPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                reasonPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

                feedbackTextFlow.getStyleClass().add("crash-reason-text-flow");

                gameDirPane.setPadding(new Insets(8));
                VBox.setVgrow(gameDirPane, Priority.ALWAYS);
                FXUtils.onChangeAndOperate(feedbackTextFlow.visibleProperty(), visible -> {
                    if (visible) {
                        gameDirPane.getChildren().setAll(gameDir, javaDir, new VBox(reasonTitle, reasonPane, feedbackTextFlow));
                    } else {
                        gameDirPane.getChildren().setAll(gameDir, javaDir, new VBox(reasonTitle, reasonPane));
                    }
                });
            }

            HBox toolBar = new HBox();
            VBox.setMargin(toolBar, new Insets(0, 0, 4, 0));
            {
                JFXButton exportGameCrashInfoButton = FXUtils.newRaisedButton(i18n("logwindow.export_game_crash_logs"));
                exportGameCrashInfoButton.setOnAction(e -> exportGameCrashInfo());

                JFXButton logButton = FXUtils.newRaisedButton(i18n("logwindow.title"));
                logButton.setOnAction(e -> showLogWindow());

                JFXButton helpButton = FXUtils.newRaisedButton(i18n("help"));
                helpButton.setOnAction(e -> FXUtils.openLink(Metadata.CONTACT_URL));
                FXUtils.installFastTooltip(helpButton, i18n("logwindow.help"));

                toolBar.setPadding(new Insets(8));
                toolBar.setSpacing(8);
                toolBar.getStyleClass().add("jfx-tool-bar");
                toolBar.getChildren().setAll(exportGameCrashInfoButton, logButton, helpButton);
            }

            getChildren().setAll(titlePane, infoPane, moddedPane, gameDirPane, toolBar);
        }

    }
}
