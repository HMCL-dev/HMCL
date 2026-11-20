// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3CheckBox;
import org.glavo.m3fx.controls.M3ComboBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaDistribution;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.download.java.JavaRemoteVersion;
import org.jackhuang.hmcl.download.java.disco.*;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.LauncherDialog;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Result;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.NativePlatform;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaDownloadDialog extends LauncherDialog {

    public static Runnable showDialogAction(DownloadProvider downloadProvider) {
        NativePlatform platform = SystemInfo.SYSTEM_PLATFORM;

        List<GameJavaVersion> supportedVersions = GameJavaVersion.getSupportedVersions(platform);

        EnumSet<DiscoJavaDistribution> distributions = EnumSet.noneOf(DiscoJavaDistribution.class);
        for (DiscoJavaDistribution distribution : DiscoJavaDistribution.values()) {
            if (distribution.isSupport(platform)) {
                distributions.add(distribution);
            }
        }

        return supportedVersions.isEmpty() && distributions.isEmpty()
                ? null
                : () -> Controllers.dialog(new JavaDownloadDialog(downloadProvider, platform, supportedVersions, distributions));
    }

    private final DownloadProvider downloadProvider;
    private final NativePlatform platform;
    private final List<GameJavaVersion> supportedGameJavaVersions;
    private final EnumSet<DiscoJavaDistribution> distributions;

    private JavaDownloadDialog(DownloadProvider downloadProvider, NativePlatform platform, List<GameJavaVersion> supportedGameJavaVersions, EnumSet<DiscoJavaDistribution> distributions) {
        this.downloadProvider = downloadProvider;
        this.platform = platform;
        this.supportedGameJavaVersions = supportedGameJavaVersions;
        this.distributions = distributions;

        getDialogPane().setHeadline(i18n("java.download.title"));
        if (!supportedGameJavaVersions.isEmpty()) {
            new DownloadMojangJava();
        } else {
            new DownloadDiscoJava();
        }
    }

    private final class DownloadMojangJava {
        private final List<M3CheckBox> checkboxes = new ArrayList<>();

        DownloadMojangJava() {
            var acceptButton = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
            acceptButton.setOnAction(e -> onAccept());
            var cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            cancelButton.setCancelButton(true);
            getDialogPane().getActions().setAll(cancelButton, acceptButton);

            VBox vbox = new VBox(16);
            Label prompt = new Label(i18n("java.download.prompt"));
            vbox.getChildren().add(prompt);

            acceptButton.setDisable(true);
            InvalidationListener updateValidStatus = e -> {
                for (M3CheckBox box : checkboxes) {
                    if (!box.isDisabled() && box.isSelected()) {
                        acceptButton.setDisable(false);
                        return;
                    }
                }
                acceptButton.setDisable(true);
            };

            for (GameJavaVersion version : supportedGameJavaVersions) {
                M3CheckBox button = new M3CheckBox("Java " + version.majorVersion());
                button.setUserData(version);

                if (JavaManager.REPOSITORY.isInstalled(platform, version)) {
                    button.setDisable(true);
                    button.setSelected(true);
                } else {
                    button.selectedProperty().addListener(updateValidStatus);
                }

                vbox.getChildren().add(button);
                checkboxes.add(button);
            }

            setFormContent(vbox);

            if (!distributions.isEmpty()) {
                JFXHyperlink more = new JFXHyperlink(i18n("java.download.more"));
                more.setOnAction(event -> new DownloadDiscoJava());
                vbox.getChildren().add(more);
            }
        }

        /// Starts downloads for the selected Java runtimes.
        private void onAccept() {
            closeDialog();

            List<GameJavaVersion> selectedVersions = new ArrayList<>();
            for (M3CheckBox box : checkboxes) {
                if (!box.isDisabled() && box.isSelected()) {
                    selectedVersions.add((GameJavaVersion) box.getUserData());
                }
            }

            if (selectedVersions.isEmpty())
                return;

            Task<Void> task = Task.allOf(selectedVersions.stream().map(javaVersion -> JavaManager.getDownloadJavaTask(downloadProvider, platform, javaVersion).wrapResult()).toList())
                    .whenComplete(Schedulers.javafx(), (results, exception) -> {
                        Throwable exceptionToDisplay;
                        if (exception == null) {
                            List<Throwable> exceptions = results.stream()
                                    .filter(Result::isFailure)
                                    .map(Result::getException)
                                    .map(Lang::resolveException)
                                    .filter(it -> !(it instanceof CancellationException))
                                    .toList();
                            if (!exceptions.isEmpty()) {
                                if (exceptions.size() == 1) {
                                    exceptionToDisplay = exceptions.get(0);
                                } else {
                                    exceptionToDisplay = new IOException("Failed to download Java");
                                    for (Throwable e : exceptions)
                                        exceptionToDisplay.addSuppressed(e);
                                }
                            } else {
                                exceptionToDisplay = null;
                            }
                        } else {
                            exceptionToDisplay = exception;
                        }

                        if (exceptionToDisplay != null) {
                            LOG.warning("Failed to download java", exceptionToDisplay);
                            Controllers.dialog(I18n.localizeErrorMessage(exceptionToDisplay), i18n("install.failed"));
                        }
                    });

            Controllers.taskDialog(task, i18n("download.java.process"), TaskCancellationAction.NORMAL);
        }
    }

    private final class DownloadDiscoJava {
        private final M3ComboBox<DiscoJavaDistribution> distributionBox;
        private final M3ComboBox<DiscoJavaRemoteVersion> remoteVersionBox;
        private final M3ComboBox<JavaPackageType> packageTypeBox;
        private final Label warningLabel = new Label();

        private final M3Button downloadButton;

        private final DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

        private final Map<DiscoJavaDistribution, DiscoJavaVersionList> javaVersionLists = new HashMap<>();
        private final ObjectProperty<DiscoJavaVersionList> currentJavaVersionList = new SimpleObjectProperty<>();

        DownloadDiscoJava() {
            assert !distributions.isEmpty();

            this.distributionBox = new M3ComboBox<>();
            this.distributionBox.setConverter(JavaDistribution::getDisplayName);

            this.remoteVersionBox = new M3ComboBox<>();
            this.remoteVersionBox.setConverter(JavaRemoteVersion::getDistributionVersion);
            this.remoteVersionBox.disableProperty().bind(Bindings.isNull(distributionBox.valueProperty()));

            this.packageTypeBox = new M3ComboBox<>();
            this.packageTypeBox.setConverter(JavaPackageType::getDisplayName);
            this.packageTypeBox.disableProperty().bind(Bindings.isNull(distributionBox.valueProperty()));

            this.downloadButton = new M3Button(i18n("download"), M3ButtonVariant.TEXT);
            downloadButton.setOnAction(e -> onDownload());
            var loading = FXUtils.newButtonLoadingProperty(downloadButton);
            downloadButton.disableProperty().bind(loading.or(remoteVersionBox.valueProperty().isNull()));

            M3Button cancelButton = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
            cancelButton.setCancelButton(true);

            GridPane body = new GridPane();
            body.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());
            body.setVgap(8);
            body.setHgap(16);

            body.addRow(0, new Label(i18n("java.download.distribution")), distributionBox);
            body.addRow(1, new Label(i18n("java.download.version")), remoteVersionBox);
            body.addRow(2, new Label(i18n("java.download.packageType")), packageTypeBox);

            distributionBox.getItems().setAll(distributions);

            FXUtils.onChange(packageTypeBox.valueProperty(), packageType -> {

                ObservableList<DiscoJavaRemoteVersion> versions;
                if (packageType == null
                        || currentJavaVersionList.get() == null
                        || (versions = currentJavaVersionList.get().versions.get(packageType)) == null) {
                    remoteVersionBox.setValue(null);
                    remoteVersionBox.getItems().clear();
                    return;
                }

                @Nullable DiscoJavaRemoteVersion oldVersion = remoteVersionBox.getValue();
                remoteVersionBox.getItems().setAll(versions);

                if (oldVersion != null) {
                    for (DiscoJavaRemoteVersion version : versions) {
                        if (Objects.equals(version.getDistributionVersion(), oldVersion.getDistributionVersion())) {
                            remoteVersionBox.setValue(version);
                            return;
                        }
                    }
                    for (DiscoJavaRemoteVersion version : versions) {
                        if (version.getJdkVersion() == oldVersion.getJdkVersion()) {
                            remoteVersionBox.setValue(version);
                            return;
                        }
                    }
                }

                for (DiscoJavaRemoteVersion version : versions) {
                    if (version.getJdkVersion() == GameJavaVersion.LATEST.majorVersion()) {
                        remoteVersionBox.setValue(version);
                        return;
                    }
                }

                for (DiscoJavaRemoteVersion version : versions) {
                    if (version.isLTS()) {
                        remoteVersionBox.setValue(version);
                        return;
                    }
                }

                remoteVersionBox.setValue(versions.isEmpty() ? null : versions.getFirst());
            });

            Consumer<DiscoJavaVersionList> updateListStatus = list -> {
                remoteVersionBox.setValue(null);
                remoteVersionBox.getItems().clear();
                packageTypeBox.setValue(null);
                packageTypeBox.getItems().clear();
                warningLabel.setText(null);

                if (list == null || (list.versions != null && list.versions.isEmpty()))
                    loading.set(false);
                else if (list.status == DiscoJavaVersionList.Status.LOADING)
                    loading.set(true);
                else {
                    loading.set(false);

                    if (list.status == DiscoJavaVersionList.Status.SUCCESS) {
                        packageTypeBox.getItems().setAll(list.versions.keySet());
                        packageTypeBox.setValue(packageTypeBox.getItems().getFirst());
                    } else
                        warningLabel.setText(i18n("java.download.load_list.failed"));
                }
            };

            currentJavaVersionList.addListener((observable, oldValue, newValue) -> {
                if (oldValue != null)
                    oldValue.listener = null;

                if (newValue != null) {
                    updateListStatus.accept(newValue);

                    if (newValue.status == DiscoJavaVersionList.Status.LOADING)
                        newValue.listener = updateListStatus;
                } else {
                    currentJavaVersionList.set(null);
                    updateListStatus.accept(null);
                }
            });

            FXUtils.onChange(distributionBox.valueProperty(),
                    it -> currentJavaVersionList.set(getJavaVersionList(it)));

            warningLabel.visibleProperty().bind(warningLabel.textProperty().isNotEmpty());
            warningLabel.managedProperty().bind(warningLabel.visibleProperty());
            var form = new VBox(8, body, warningLabel);
            setFormContent(form);
            getDialogPane().getActions().setAll(cancelButton, downloadButton);
            if (platform.os() == OperatingSystem.LINUX && platform.arch() == Architecture.RISCV64) {
                JFXHyperlink hyperlink = new JFXHyperlink(i18n("java.download.banshanjdk-8"));
                hyperlink.setExternalLink("https://www.zthread.cn/#product");
                form.getChildren().add(hyperlink);
            }
        }

        private void onDownload() {
            closeDialog();

            @Nullable DiscoJavaDistribution distribution = distributionBox.getValue();
            @Nullable DiscoJavaRemoteVersion version = remoteVersionBox.getValue();
            @Nullable JavaPackageType packageType = packageTypeBox.getValue();

            if (distribution == null || version == null || packageType == null)
                return;

            Controllers.taskDialog(new GetTask(downloadProvider.getDownloadCandidates(version.getLinks().pkgInfoUri()))
                    .setExecutor(Schedulers.io())
                    .thenComposeAsync(json -> {
                        DiscoResult<DiscoRemoteFileInfo> result = JsonUtils.fromNonNullJson(json, DiscoResult.typeOf(DiscoRemoteFileInfo.class));
                        if (result.getResult().size() != 1)
                            throw new IOException("Illegal result: " + json);

                        DiscoRemoteFileInfo fileInfo = result.getResult().get(0);
                        if (StringUtils.isNotBlank(fileInfo.checksumType())
                                && !fileInfo.checksumType().equals("sha1") && !fileInfo.checksumType().equals("sha256") && !fileInfo.checksumType().equals("md5"))
                            throw new IOException("Unsupported checksum type: " + fileInfo.checksumType());
                        if (StringUtils.isBlank(fileInfo.directDownloadUri()))
                            throw new IOException("Missing download URI: " + json);

                        Path targetFile = Files.createTempFile("hmcl-java-", "." + version.getArchiveType());
                        targetFile.toFile().deleteOnExit();

                        Task<FileDownloadTask.IntegrityCheck> getIntegrityCheck;
                        if (StringUtils.isNotBlank(fileInfo.checksum()))
                            getIntegrityCheck = Task.completed(new FileDownloadTask.IntegrityCheck(fileInfo.checksumType(), fileInfo.checksum()));
                        else if (StringUtils.isNotBlank(fileInfo.checksumUri())) {
                            getIntegrityCheck = new GetTask(downloadProvider.getDownloadCandidates(fileInfo.checksumUri()))
                                    .thenApplyAsync(checksum -> {
                                        checksum = checksum.trim();

                                        int idx = checksum.indexOf(' ');
                                        if (idx > 0)
                                            checksum = checksum.substring(0, idx);

                                        return new FileDownloadTask.IntegrityCheck(fileInfo.checksumType(), checksum);
                                    });
                        }
                        else
                            getIntegrityCheck = Task.completed(null);

                        return getIntegrityCheck
                                .thenComposeAsync(integrityCheck ->
                                {
                                    return new FileDownloadTask(downloadProvider.getDownloadCandidates(fileInfo.directDownloadUri()),
                                            targetFile, integrityCheck).setName(fileInfo.fileName());
                                })
                                .thenSupplyAsync(() -> targetFile);
                    })
                    .whenComplete(Schedulers.javafx(), ((result, exception) -> {
                        if (exception == null) {
                            String javaVersion = version.getJavaVersion();
                            JavaInfo info = new JavaInfo(platform, javaVersion, distribution.getVendor());

                            Map<String, Object> updateInfo = new LinkedHashMap<>();
                            updateInfo.put("type", "disco");
                            updateInfo.put("info", version);

                            int idx = javaVersion.indexOf('+');
                            if (idx > 0) {
                                javaVersion = javaVersion.substring(0, idx);
                            }
                            String defaultName = distribution.getApiParameter() + "-" + javaVersion + "-" + packageType.name().toLowerCase(Locale.ROOT);
                            Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller ->
                                    new JavaInstallPage(controller::onFinish, info, version, updateInfo, defaultName, result)));
                        } else {
                            LOG.warning("Failed to download java", exception);
                            Throwable resolvedException = resolveException(exception);
                            if (!(resolvedException instanceof CancellationException)) {
                                Controllers.dialog(I18n.localizeErrorMessage(resolvedException), i18n("install.failed"));
                            }
                        }
                    })), i18n("java.download"), TaskCancellationAction.NORMAL);

        }

        private DiscoJavaVersionList getJavaVersionList(DiscoJavaDistribution distribution) {
            if (distribution == null)
                return null;
            return javaVersionLists.computeIfAbsent(distribution, it -> {
                DiscoJavaVersionList versionList = new DiscoJavaVersionList(it);
                new DiscoFetchJavaListTask(downloadProvider, it, platform).setExecutor(Schedulers.io()).thenApplyAsync(versions -> {
                    EnumMap<JavaPackageType, ObservableList<DiscoJavaRemoteVersion>> result = new EnumMap<>(JavaPackageType.class);
                    if (versions.isEmpty())
                        return result;

                    for (Map.Entry<JavaPackageType, TreeMap<Integer, DiscoJavaRemoteVersion>> entry : versions.entrySet())
                        for (DiscoJavaRemoteVersion version : entry.getValue().values())
                            if (version.isLTS()
                                    || version.getJdkVersion() == entry.getValue().lastKey() // latest version
                                    || version.getJdkVersion() == 16)
                                result.computeIfAbsent(entry.getKey(), ignored -> FXCollections.observableArrayList())
                                        .add(version);

                    for (List<DiscoJavaRemoteVersion> l : result.values())
                        Collections.reverse(l);
                    return result;
                }).whenComplete(Schedulers.javafx(), ((result, exception) -> {
                    if (exception == null) {
                        versionList.status = DiscoJavaVersionList.Status.SUCCESS;
                        versionList.versions = result;
                    } else {
                        LOG.warning("Failed to load java list", exception);
                        versionList.status = DiscoJavaVersionList.Status.FAILED;
                    }
                    versionList.invalidate();
                })).start();
                return versionList;
            });
        }
    }

    private static final class DiscoJavaVersionList {
        enum Status {
            LOADING, SUCCESS, FAILED
        }

        final DiscoJavaDistribution distribution;

        Status status = Status.LOADING;
        EnumMap<JavaPackageType, ObservableList<DiscoJavaRemoteVersion>> versions;
        Consumer<DiscoJavaVersionList> listener;

        DiscoJavaVersionList(DiscoJavaDistribution distribution) {
            this.distribution = distribution;
        }

        void invalidate() {
            if (listener != null)
                listener.accept(this);
        }
    }
}
