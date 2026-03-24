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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.EnumCommonDirectory;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.AprilFools;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.CentralProcessor;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jackhuang.hmcl.util.platform.hardware.StorageStatus;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SettingsPage extends ScrollPane {

    @SuppressWarnings("FieldCanBeLocal")
    private final ToggleGroup updateChannelGroup;
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    private VBox systemInfoContainer;

    public SettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox();
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        ComponentList settingsPane = new ComponentList();
        {
            {
                ComponentSublist sponsorPane = new ComponentSublist(() -> {
                    ComponentList content = new ComponentList();

                    var githubButton = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
                    githubButton.setTitle(i18n("settings.launcher.github_repository"));
                    FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode ->
                            githubButton.setLeading(darkMode
                                    ? FXUtils.newBuiltinImage("/assets/img/github-white.png")
                                    : FXUtils.newBuiltinImage("/assets/img/github.png")));

                    content.getContent().add(githubButton);

                    content.getContent().add(createSystemInfoSection());

                    return content.getContent();
                });

                String sponsorText = i18n("sponsor.hmcl");
                int bracketStart = sponsorText.indexOf('[');
                int bracketEnd = sponsorText.lastIndexOf(']');
                if (bracketStart >= 0 && bracketEnd > bracketStart) {
                    sponsorPane.setTitle(sponsorText.substring(0, bracketStart).trim());
                    sponsorPane.setSubtitle(sponsorText.substring(bracketStart + 1, bracketEnd).trim());
                    sponsorPane.setHasSubtitle(true);
                } else {
                    sponsorPane.setTitle(sponsorText);
                }
                settingsPane.getContent().add(sponsorPane);
            }

            {
                ComponentSublist updatePane = new ComponentSublist();
                updatePane.setTitle(i18n("update"));
                updatePane.setHasSubtitle(true);

                final Label lblUpdate;
                final Label lblUpdateSub;
                {
                    VBox headerLeft = new VBox();

                    lblUpdate = new Label(i18n("update"));
                    lblUpdate.getStyleClass().add("title-label");
                    lblUpdateSub = new Label();
                    lblUpdateSub.getStyleClass().add("subtitle-label");

                    headerLeft.getChildren().setAll(lblUpdate, lblUpdateSub);
                    updatePane.setHeaderLeft(headerLeft);
                }

                {
                    JFXButton btnUpdate = FXUtils.newToggleButton4(SVG.UPDATE, 20);
                    btnUpdate.setOnAction(e -> onUpdate());
                    FXUtils.installFastTooltip(btnUpdate, i18n("update.tooltip"));

                    updateListener = any -> {
                        btnUpdate.setVisible(UpdateChecker.isOutdated());

                        if (UpdateChecker.isOutdated()) {
                            lblUpdateSub.setText(i18n("update.newest_version", UpdateChecker.getLatestVersion().getVersion()));
                            lblUpdateSub.getStyleClass().setAll("update-label");

                            lblUpdate.setText(i18n("update.found"));
                            lblUpdate.getStyleClass().setAll("update-label");
                        } else if (UpdateChecker.isCheckingUpdate()) {
                            lblUpdateSub.setText(i18n("update.checking"));
                            lblUpdateSub.getStyleClass().setAll("subtitle-label");

                            lblUpdate.setText(i18n("update"));
                            lblUpdate.getStyleClass().setAll("title-label");
                        } else {
                            lblUpdateSub.setText(i18n("update.latest"));
                            lblUpdateSub.getStyleClass().setAll("subtitle-label");

                            lblUpdate.setText(i18n("update"));
                            lblUpdate.getStyleClass().setAll("title-label");
                        }
                    };
                    UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
                    updateListener.invalidated(null);

                    updatePane.setHeaderRight(btnUpdate);
                }

                {
                    VBox content = new VBox(12);
                    content.setPadding(new Insets(8, 0, 0, 0));

                    updateChannelGroup = new ToggleGroup();

                    JFXRadioButton chkUpdateStable = new JFXRadioButton(i18n("update.channel.stable"));
                    chkUpdateStable.setUserData(UpdateChannel.STABLE);
                    chkUpdateStable.setToggleGroup(updateChannelGroup);

                    JFXRadioButton chkUpdateDev = new JFXRadioButton(i18n("update.channel.dev"));
                    chkUpdateDev.setUserData(UpdateChannel.DEVELOPMENT);
                    chkUpdateDev.setToggleGroup(updateChannelGroup);

                    Label noteWrapper = new Label(i18n("update.note"));
                    VBox.setMargin(noteWrapper, new Insets(8, 0, 0, 0));

                    content.getChildren().setAll(chkUpdateStable, chkUpdateDev, noteWrapper);

                    updatePane.getContent().add(content);
                }
                settingsPane.getContent().add(updatePane);
            }

            {
                LineToggleButton previewPane = new LineToggleButton();
                previewPane.setTitle(i18n("update.preview"));
                previewPane.setSubtitle(i18n("update.preview.subtitle"));
                previewPane.selectedProperty().bindBidirectional(config().acceptPreviewUpdateProperty());

                ObjectProperty<UpdateChannel> updateChannel = selectedItemPropertyFor(updateChannelGroup, UpdateChannel.class);
                updateChannel.set(UpdateChannel.getChannel());
                InvalidationListener checkUpdateListener = e -> {
                    UpdateChecker.requestCheckUpdate(updateChannel.get(), previewPane.isSelected());
                };
                updateChannel.addListener(checkUpdateListener);
                previewPane.selectedProperty().addListener(checkUpdateListener);

                settingsPane.getContent().add(previewPane);
            }

            {
                LineToggleButton disableAutoShowUpdateDialogPane = new LineToggleButton();
                disableAutoShowUpdateDialogPane.setTitle(i18n("update.disable_auto_show_update_dialog"));
                disableAutoShowUpdateDialogPane.setSubtitle(i18n("update.disable_auto_show_update_dialog.subtitle"));
                disableAutoShowUpdateDialogPane.selectedProperty().bindBidirectional(config().disableAutoShowUpdateDialogProperty());
                settingsPane.getContent().add(disableAutoShowUpdateDialogPane);
            }

            if (AprilFools.isShowAprilFoolsSettings()) {
                LineToggleButton disableAprilFools = new LineToggleButton();
                disableAprilFools.setTitle(i18n("settings.launcher.disable_april_fools"));
                disableAprilFools.setSubtitle(i18n("settings.take_effect_after_restart"));
                disableAprilFools.selectedProperty().bindBidirectional(config().disableAprilFoolsProperty());
                settingsPane.getContent().add(disableAprilFools);
            }

            {
                MultiFileItem<EnumCommonDirectory> fileCommonLocation = new MultiFileItem<>();
                fileCommonLocation.loadChildren(Arrays.asList(
                        new MultiFileItem.Option<>(i18n("launcher.cache_directory.default"), EnumCommonDirectory.DEFAULT),
                        new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumCommonDirectory.CUSTOM)
                                .setChooserTitle(i18n("launcher.cache_directory.choose"))
                                .setDirectory(true)
                                .bindBidirectional(config().commonDirectoryProperty())
                ));
                fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());

                ComponentSublist fileCommonLocationSublist = new ComponentSublist();
                fileCommonLocationSublist.getContent().add(fileCommonLocation);
                fileCommonLocationSublist.setTitle(i18n("launcher.cache_directory"));
                fileCommonLocationSublist.setHasSubtitle(true);
                fileCommonLocationSublist.subtitleProperty().bind(
                        Bindings.createObjectBinding(() -> Optional.ofNullable(Settings.instance().getCommonDirectory())
                                        .orElse(i18n("launcher.cache_directory.disabled")),
                                config().commonDirectoryProperty(), config().commonDirTypeProperty()));

                JFXButton cleanButton = FXUtils.newBorderButton(i18n("launcher.cache_directory.clean"));
                cleanButton.setOnAction(e -> clearCacheDirectory());
                fileCommonLocationSublist.setHeaderRight(cleanButton);

                settingsPane.getContent().add(fileCommonLocationSublist);
            }

            {
                var chooseLanguagePane = new LineSelectButton<SupportedLocale>();
                chooseLanguagePane.setTitle(i18n("settings.launcher.language"));
                chooseLanguagePane.setSubtitle(i18n("settings.take_effect_after_restart"));

                SupportedLocale currentLocale = I18n.getLocale();
                chooseLanguagePane.setConverter(locale -> {
                    if (locale.isDefault())
                        return locale.getDisplayName(currentLocale);
                    else if (locale.isSameLanguage(currentLocale))
                        return locale.getDisplayName(locale);
                    else
                        return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                });
                chooseLanguagePane.setItems(SupportedLocale.getSupportedLocales());
                chooseLanguagePane.valueProperty().bindBidirectional(config().localizationProperty());

                settingsPane.getContent().add(chooseLanguagePane);
            }

            {
                LineToggleButton disableAutoGameOptionsPane = new LineToggleButton();
                disableAutoGameOptionsPane.setTitle(i18n("settings.launcher.disable_auto_game_options"));
                disableAutoGameOptionsPane.selectedProperty().bindBidirectional(config().disableAutoGameOptionsProperty());

                settingsPane.getContent().add(disableAutoGameOptionsPane);
            }

            {
                BorderPane debugPane = new BorderPane();

                Label left = new Label(i18n("settings.launcher.debug"));
                BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                debugPane.setLeft(left);

                JFXButton openLogFolderButton = new JFXButton(i18n("settings.launcher.launcher_log.reveal"));
                openLogFolderButton.setOnAction(e -> openLogFolder());
                openLogFolderButton.getStyleClass().add("jfx-button-border");
                if (LOG.getLogFile() == null)
                    openLogFolderButton.setDisable(true);

                SpinnerPane exportLogPane = new SpinnerPane();

                JFXButton logButton = FXUtils.newBorderButton(i18n("settings.launcher.launcher_log.export"));
                exportLogPane.setContent(logButton);
                logButton.setOnAction(e -> {
                    exportLogPane.showSpinner();
                    onExportLogs().whenCompleteAsync((result, exception) -> {
                        exportLogPane.hideSpinner();
                        if (exception == null) {
                            Controllers.dialog(i18n("settings.launcher.launcher_log.export.success", result));
                            FXUtils.showFileInExplorer(result);
                        } else {
                            LOG.warning("Failed to export logs", exception);
                            Controllers.dialog(
                                    i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(exception),
                                    null,
                                    MessageType.ERROR
                            );
                        }
                    }, Schedulers.javafx());
                });

                HBox buttonBox = new HBox();
                buttonBox.setSpacing(10);
                buttonBox.getChildren().addAll(openLogFolderButton, exportLogPane);
                BorderPane.setAlignment(buttonBox, Pos.CENTER_RIGHT);
                debugPane.setRight(buttonBox);

                settingsPane.getContent().add(debugPane);
            }

            rootPane.getChildren().add(settingsPane);
        }
    }

    private void openLogFolder() {
        FXUtils.openFolder(LOG.getLogFile().getParent());
    }

    private void onUpdate() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    private static String getEntryName(Set<String> entryNames, String name) {
        if (entryNames.add(name)) {
            return name;
        }

        for (long i = 1; ; i++) {
            String newName = name + "." + i;
            if (entryNames.add(newName)) {
                return newName;
            }
        }
    }

    /// This method guarantees to close both `input` and the current zip entry.
    ///
    /// If no exception occurs, this method returns `true`;
    /// If an exception occurs while reading from `input`, this method returns `false`;
    /// If an exception occurs while writing to `output`, this method will throw it as is.
    private static boolean exportLogFile(ZipOutputStream output,
                                         Path file, // For logging
                                         String entryName,
                                         InputStream input,
                                         byte[] buffer) throws IOException {
        //noinspection TryFinallyCanBeTryWithResources
        try {
            output.putNextEntry(new ZipEntry(entryName));
            int read;
            while (true) {
                try {
                    read = input.read(buffer);
                    if (read <= 0)
                        return true;
                } catch (Throwable ex) {
                    LOG.warning("Failed to decompress log file " + file, ex);
                    return false;
                }

                output.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (Throwable ex) {
                LOG.warning("Failed to close log file " + file, ex);
            }
            output.closeEntry();
        }
    }

    private CompletableFuture<Path> onExportLogs() {
        return CompletableFuture.supplyAsync(Lang.wrap(() -> {
            String nameBase = "hmcl-exported-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"));
            List<Path> recentLogFiles = LOG.findRecentLogFiles(5);

            Path outputFile;
            if (recentLogFiles.isEmpty()) {
                outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".log");

                LOG.info("Exporting latest logs to " + outputFile);
                try (OutputStream output = Files.newOutputStream(outputFile)) {
                    LOG.exportLogs(output);
                }
            } else {
                outputFile = Metadata.CURRENT_DIRECTORY.resolve(nameBase + ".zip");

                LOG.info("Exporting latest logs to " + outputFile);

                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                try (var os = Files.newOutputStream(outputFile);
                     var zos = new ZipOutputStream(os)) {

                    Set<String> entryNames = new HashSet<>();

                    for (Path path : recentLogFiles) {
                        String fileName = FileUtils.getName(path);
                        String extension = StringUtils.substringAfterLast(fileName, '.');

                        if ("gz".equals(extension) || "xz".equals(extension)) {
                            // If an exception occurs while decompressing the input file, we should
                            // ensure the input file and the current zip entry are closed,
                            // then copy the compressed file content as-is into a new entry in the zip file.

                            InputStream input = null;
                            try {
                                input = Files.newInputStream(path);
                                input = "gz".equals(extension)
                                        ? new GZIPInputStream(input)
                                        : new XZInputStream(input);
                            } catch (Throwable ex) {
                                LOG.warning("Failed to open log file " + path, ex);
                                IOUtils.closeQuietly(input, ex);
                                input = null;
                            }

                            String entryName = getEntryName(entryNames, StringUtils.substringBeforeLast(fileName, "."));
                            if (input != null && exportLogFile(zos, path, entryName, input, buffer))
                                continue;
                        }

                        // Copy the log file content as-is into a new entry in the zip file.
                        // If an exception occurs while decompressing the input file, we should
                        // ensure the input file and the current zip entry are closed.

                        InputStream input;
                        try {
                            input = Files.newInputStream(path);
                        } catch (Throwable ex) {
                            LOG.warning("Failed to open log file " + path, ex);
                            continue;
                        }

                        exportLogFile(zos, path, getEntryName(entryNames, fileName), input, buffer);
                    }

                    zos.putNextEntry(new ZipEntry(getEntryName(entryNames, "hmcl-latest.log")));
                    LOG.exportLogs(zos);
                    zos.closeEntry();
                }
            }

            return outputFile;
        }), Schedulers.io());
    }

    private void onSponsor() {
        FXUtils.openLink("https://github.com/HMCL-dev/HMCL");
    }

    private void clearCacheDirectory() {
        String commonDirectory = Settings.instance().getCommonDirectory();
        if (commonDirectory != null) {
            FileUtils.cleanDirectoryQuietly(Path.of(commonDirectory, "cache"));
        }
    }

    private Node createSystemInfoSection() {
        VBox container = new VBox(4);
        container.setPadding(new Insets(8, 0, 0, 0));
        systemInfoContainer = container;

        // 设备信息标题行
        HBox titleRow = new HBox();
        titleRow.setSpacing(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(i18n("settings.launcher.system_info.title"));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        JFXButton refreshButton = new JFXButton();
        refreshButton.setGraphic(SVG.REFRESH.createIcon(16));
        refreshButton.setText(i18n("button.refresh"));
        refreshButton.getStyleClass().add("jfx-tool-bar-button");
        refreshButton.setOnAction(e -> refreshSystemInfo());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(titleLabel, spacer, refreshButton);
        container.getChildren().add(titleRow);

        // 刷新系统信息
        refreshSystemInfo();

        return container;
    }

    private void refreshSystemInfo() {
        if (systemInfoContainer == null) return;

        // 保留标题行，移除其他内容（索引从1开始，因为索引0是标题行）
        while (systemInfoContainer.getChildren().size() > 1) {
            systemInfoContainer.getChildren().remove(1);
        }

        // 电脑名（主机名）
        String computerName;
        try {
            computerName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            computerName = i18n("settings.launcher.system_info.unknown");
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.computer_name"), computerName);

        // 用户名
        String username = System.getProperty("user.name");
        // 检测是否以管理员/超级用户运行
        boolean isAdmin = false;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"net", "session"});
                process.getOutputStream().close();
                int exitCode = process.waitFor();
                isAdmin = (exitCode == 0);
            } catch (Exception ignored) {
                isAdmin = false;
            }
        } else {
            // Linux/MacOS: 检查是否以root用户运行
            isAdmin = (System.getProperty("user.name").equals("root"));
        }
        String usernameDisplay = username;
        if (isAdmin) {
            usernameDisplay = username + " (" + i18n("settings.launcher.system_info.admin") + ")";
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.username"), usernameDisplay);

        // 操作系统
        String osName = OperatingSystem.SYSTEM_NAME;
        String osVersion = OperatingSystem.SYSTEM_VERSION.toString();
        String osInfo = osName + " " + osVersion;
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            String prettyName = OperatingSystem.OS_RELEASE_PRETTY_NAME;
            if (prettyName != null && !prettyName.isEmpty()) {
                osInfo = prettyName + " (" + osVersion + ")";
            }
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.os"), osInfo);

        // 主板型号 (Windows only)
        String motherboard = i18n("settings.launcher.system_info.unknown");
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"wmic", "baseboard", "get", "product"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.equalsIgnoreCase("Product")) {
                        motherboard = line;
                        break;
                    }
                }
                process.destroy();
            } catch (Exception ignored) {
            }
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.motherboard"), motherboard);

        // 系统内核
        String kernel = System.getProperty("os.name") + " " + System.getProperty("os.version");
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            kernel = "NT " + osVersion;
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.kernel"), kernel);

        // 主程序窗口大小
        String windowSize = "";
        try {
            javafx.stage.Stage stage = Controllers.getStage();
            if (stage != null) {
                windowSize = (int) stage.getWidth() + " x " + (int) stage.getHeight() + " px";
            }
        } catch (Exception ignored) {
        }
        if (windowSize.isEmpty()) {
            windowSize = i18n("settings.launcher.system_info.unknown");
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.window_size"), windowSize);

        // Java 版本
        String javaVendor = System.getProperty("java.vendor", "");
        String javaVersion = System.getProperty("java.version", "");
        String javaInfo = javaVersion + " (" + javaVendor + ")";
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.java"), javaInfo);

        // JavaFX 版本
        String jfxVersion = System.getProperty("javafx.version", i18n("settings.launcher.system_info.unknown"));
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.javafx"), jfxVersion);

        // CPU 信息
        CentralProcessor cpu = SystemInfo.getCentralProcessor();
        if (cpu != null) {
            String cpuCores = "";
            var cores = cpu.getCores();
            if (cores != null) {
                cpuCores = "\n    " + i18n("settings.launcher.system_info.cpu.cores", cores.physical, cores.logical);
            }
            addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.cpu"), cpu.getName() + cpuCores);
        } else {
            addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.cpu"), i18n("settings.launcher.system_info.unknown"));
        }

        // GPU 信息
        List<GraphicsCard> gpus = SystemInfo.getGraphicsCards();
        String gpuInfo;
        String renderPipeline = FXUtils.GRAPHICS_PIPELINE;
        
        if (gpus != null && !gpus.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < gpus.size(); i++) {
                if (i > 0) sb.append("\n");
                // 使用 toString() 方法，它已包含 GPU 类型信息 [Integrated] 或 [Discrete]
                sb.append(gpus.get(i).toString());
            }
            gpuInfo = sb.toString();
        } else {
            gpuInfo = i18n("settings.launcher.system_info.unknown");
        }
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.gpu"), gpuInfo);

        // 渲染管线信息
        if (renderPipeline != null && !renderPipeline.isEmpty()) {
            String pipelineDisplay = renderPipeline;
            if (renderPipeline.contains(".SWPipeline")) {
                pipelineDisplay = renderPipeline + " (" + i18n("settings.launcher.system_info.gpu.software") + ")";
            } else if (renderPipeline.contains(".D3D")) {
                pipelineDisplay = "Direct3D";
            } else if (renderPipeline.contains(".GL")) {
                pipelineDisplay = "OpenGL";
            }
            addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.render_pipeline"), pipelineDisplay);
        }

        // 内存信息
        PhysicalMemoryStatus memStatus = SystemInfo.getPhysicalMemoryStatus();
        if (memStatus != PhysicalMemoryStatus.INVALID) {
            long total = memStatus.getTotal();
            long used = memStatus.getUsed();
            String memInfo = String.format("%.2f / %.2f GiB (%.2f%%)",
                    used / (1024.0 * 1024 * 1024),
                    total / (1024.0 * 1024 * 1024),
                    total > 0 ? (double) used / total * 100 : 0);
            addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.memory"), memInfo);
        }

        // 存储信息
        StorageStatus storageStatus = SystemInfo.getStorageStatus();
        if (storageStatus != null && storageStatus.isValid()) {
            StringBuilder storageInfo = new StringBuilder();
            for (StorageStatus.DriveInfo drive : storageStatus.getDrives()) {
                if (storageInfo.length() > 0) {
                    storageInfo.append("\n");
                }

                String mountPoint = drive.getMountPoint();
                // 格式化显示：分区(C:\) 或 /home
                String displayName;
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    displayName = i18n("settings.launcher.system_info.storage.partition", mountPoint);
                } else {
                    displayName = mountPoint;
                }

                long total = drive.getTotal();
                long used = drive.getUsed();
                long available = drive.getAvailable();
                String fileSystem = drive.getFileSystem();

                double usedPercent = total > 0 ? (double) used / total * 100 : 0;

                String fsInfo = fileSystem != null && !fileSystem.isEmpty() ? " - " + fileSystem : "";

                storageInfo.append(String.format("%s %.2f / %.2f GiB (%.2f%%)%s",
                        displayName,
                        used / (1024.0 * 1024 * 1024),
                        total / (1024.0 * 1024 * 1024),
                        usedPercent,
                        fsInfo));
            }
            addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.storage"), storageInfo.toString());
        }

        // 网卡信息
        try {
            java.util.Enumeration<java.net.NetworkInterface> networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces();
            StringBuilder ipv4Info = new StringBuilder();
            StringBuilder ipv6Info = new StringBuilder();

            while (networkInterfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = networkInterfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        // IPv4
                        String ip = addr.getHostAddress();
                        // 获取子网掩码位数
                        try {
                            java.net.NetworkInterface parent = ni;
                            java.net.InterfaceAddress interfaceAddr = null;
                            for (java.net.InterfaceAddress ia : parent.getInterfaceAddresses()) {
                                if (ia.getAddress().equals(addr)) {
                                    interfaceAddr = ia;
                                    break;
                                }
                            }
                            if (interfaceAddr != null) {
                                int prefixLength = interfaceAddr.getNetworkPrefixLength();
                                ip = ip + "/" + prefixLength;
                            }
                        } catch (Exception ignored) {
                        }
                        if (ipv4Info.length() > 0) ipv4Info.append("\n");
                        ipv4Info.append(ip);
                    } else if (addr instanceof java.net.Inet6Address) {
                        java.net.Inet6Address addr6 = (java.net.Inet6Address) addr;
                        // 排除链路本地地址和临时地址
                        if (addr6.isLinkLocalAddress() || addr6.isSiteLocalAddress()) {
                            String ip = addr.getHostAddress();
                            // 找到 IPv6 地址的 prefix length
                            try {
                                java.net.NetworkInterface parent = ni;
                                java.net.InterfaceAddress interfaceAddr = null;
                                for (java.net.InterfaceAddress ia : parent.getInterfaceAddresses()) {
                                    if (ia.getAddress().equals(addr)) {
                                        interfaceAddr = ia;
                                        break;
                                    }
                                }
                                if (interfaceAddr != null) {
                                    int prefixLength = interfaceAddr.getNetworkPrefixLength();
                                    // IPv6 地址格式: ip/prefixLength
                                    // 移除末尾的 %interface 部分
                                    int percentIndex = ip.indexOf('%');
                                    if (percentIndex > 0) {
                                        ip = ip.substring(0, percentIndex);
                                    }
                                    ip = ip + "/" + prefixLength;
                                }
                            } catch (Exception ignored) {
                            }
                            if (ipv6Info.length() > 0) ipv6Info.append("\n");
                            ipv6Info.append(ip);
                        }
                    }
                }
            }

            if (ipv4Info.length() > 0) {
                addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.ipv4"), ipv4Info.toString());
            }
            if (ipv6Info.length() > 0) {
                addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.ipv6"), ipv6Info.toString());
            }
        } catch (Exception ignored) {
        }

        // 时区
        String timezone = java.util.TimeZone.getDefault().getDisplayName(false, java.util.TimeZone.LONG);
        String timezoneOffset = String.format("UTC%+d", java.util.TimeZone.getDefault().getRawOffset() / (60 * 60 * 1000));
        String timezoneInfo = timezone + " (" + timezoneOffset + ")";
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.timezone"), timezoneInfo);

        // 程序语言
        String locale = I18n.getLocale().getLocale().toLanguageTag();
        addSystemInfoRow(systemInfoContainer, i18n("settings.launcher.system_info.language"), locale);
    }

    private void addSystemInfoRow(VBox container, String label, String value) {
        HBox row = new HBox();
        row.setSpacing(8);

        Label labelNode = new Label(label + ":");
        labelNode.setMinWidth(120);
        labelNode.setStyle("-fx-font-weight: bold;");

        Label valueNode = new Label(value != null ? value : i18n("settings.launcher.system_info.unknown"));
        valueNode.setWrapText(true);

        row.getChildren().addAll(labelNode, valueNode);
        container.getChildren().add(row);
    }
}
