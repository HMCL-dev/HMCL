/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaDistribution;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.download.java.JavaRemoteVersion;
import org.jackhuang.hmcl.download.java.disco.*;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class JavaDownloadDialog extends StackPane {

    public static Runnable showDialogAction(DownloadProvider downloadProvider) {
        Platform platform = Platform.SYSTEM_PLATFORM;

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
    private final Platform platform;
    private final List<GameJavaVersion> supportedGameJavaVersions;
    private final EnumSet<DiscoJavaDistribution> distributions;

    private JavaDownloadDialog(DownloadProvider downloadProvider, Platform platform, List<GameJavaVersion> supportedGameJavaVersions, EnumSet<DiscoJavaDistribution> distributions) {
        this.downloadProvider = downloadProvider;
        this.platform = platform;
        this.supportedGameJavaVersions = supportedGameJavaVersions;
        this.distributions = distributions;

        if (!supportedGameJavaVersions.isEmpty()) {
            this.getChildren().add(new DownloadMojangJava());
        } else {
            this.getChildren().add(new DownloadDiscoJava());
        }
    }

    private final class DownloadMojangJava extends DialogPane {
        private final ToggleGroup toggleGroup = new ToggleGroup();

        DownloadMojangJava() {
            setTitle(i18n("java.download"));
            validProperty().bind(toggleGroup.selectedToggleProperty().isNotNull());

            VBox vbox = new VBox(16);
            Label prompt = new Label(i18n("java.download.prompt"));
            vbox.getChildren().add(prompt);

            for (GameJavaVersion version : supportedGameJavaVersions) {
                JFXRadioButton button = new JFXRadioButton("Java " + version.getMajorVersion());
                button.setUserData(version);
                vbox.getChildren().add(button);
                toggleGroup.getToggles().add(button);
            }

            setBody(vbox);

            if (!distributions.isEmpty()) {
                JFXHyperlink more = new JFXHyperlink(i18n("java.download.more"));
                more.setOnAction(event -> JavaDownloadDialog.this.getChildren().setAll(new DownloadDiscoJava()));
                setActions(warningLabel, more, acceptPane, cancelButton);
            } else
                setActions(warningLabel, acceptPane, cancelButton);
        }

        private Task<Void> downloadTask(GameJavaVersion javaVersion) {
            return JavaManager.downloadJava(downloadProvider, platform, javaVersion).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception != null) {
                    Throwable resolvedException = resolveException(exception);
                    LOG.warning("Failed to download java", exception);
                    if (!(resolvedException instanceof CancellationException)) {
                        Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                    }
                }
            });
        }

        @Override
        protected void onAccept() {
            fireEvent(new DialogCloseEvent());

            GameJavaVersion javaVersion = (GameJavaVersion) toggleGroup.getSelectedToggle().getUserData();
            if (javaVersion == null)
                return;

            if (JavaManager.REPOSITORY.isInstalled(platform, javaVersion))
                Controllers.confirm(i18n("download.java.override"), null, () -> {
                    Controllers.taskDialog(Task.supplyAsync(() -> JavaManager.REPOSITORY.getJavaExecutable(platform, javaVersion))
                            .thenComposeAsync(Schedulers.javafx(), realPath -> {
                                if (realPath != null) {
                                    JavaManager.removeJava(realPath);
                                }
                                return downloadTask(javaVersion);
                            }), i18n("download.java"), TaskCancellationAction.NORMAL);
                }, null);
            else
                Controllers.taskDialog(downloadTask(javaVersion), i18n("download.java"), TaskCancellationAction.NORMAL);
        }
    }

    private final class DownloadDiscoJava extends JFXDialogLayout {

        private boolean isLTS(int major) {
            if (major <= 8) {
                return true;
            }

            if (major < 21) {
                return major == 11 || major == 17;
            }

            return major % 4 == 1;
        }

        private final JFXComboBox<DiscoJavaDistribution> distributionBox;
        private final JFXComboBox<DiscoJavaRemoteVersion> remoteVersionBox;
        private final JFXComboBox<JavaPackageType> packageTypeBox;
        private final Label warningLabel = new Label();

        private final JFXButton downloadButton;
        private final StackPane downloadButtonPane = new StackPane();

        private final DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

        private final ObjectProperty<DiscoJavaVersionList> currentDiscoJavaVersionList = new SimpleObjectProperty<>();

        private final Map<Pair<DiscoJavaDistribution, JavaPackageType>, DiscoJavaVersionList> javaVersionLists = new HashMap<>();

        private boolean changingDistribution = false;

        DownloadDiscoJava() {
            assert !distributions.isEmpty();

            this.distributionBox = new JFXComboBox<>();
            this.distributionBox.setConverter(FXUtils.stringConverter(JavaDistribution::getDisplayName));

            this.remoteVersionBox = new JFXComboBox<>();
            this.remoteVersionBox.setConverter(FXUtils.stringConverter(JavaRemoteVersion::getDistributionVersion));

            this.packageTypeBox = new JFXComboBox<>();

            this.downloadButton = new JFXButton(i18n("download"));
            downloadButton.setOnAction(e -> onDownload());
            downloadButton.getStyleClass().add("dialog-accept");
            downloadButton.disableProperty().bind(Bindings.isNull(remoteVersionBox.getSelectionModel().selectedItemProperty()));
            downloadButtonPane.getChildren().setAll(downloadButton);

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            cancelButton.getStyleClass().add("dialog-cancel");
            onEscPressed(this, cancelButton::fire);

            GridPane body = new GridPane();
            body.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());
            body.setVgap(8);
            body.setHgap(16);

            body.addRow(0, new Label(i18n("java.download.distribution")), distributionBox);
            body.addRow(1, new Label(i18n("java.download.version")), remoteVersionBox);
            body.addRow(2, new Label(i18n("java.download.packageType")), packageTypeBox);

            distributionBox.setItems(FXCollections.observableList(new ArrayList<>(distributions)));
            ChangeListener<DiscoJavaVersionList.Status> updateStatusListener = (observable, oldValue, newValue) -> updateStatus(newValue);
            this.currentDiscoJavaVersionList.addListener((observable, oldValue, newValue) -> {
                if (oldValue != null) {
                    oldValue.status.removeListener(updateStatusListener);
                }

                if (newValue != null) {
                    newValue.status.addListener(updateStatusListener);
                    updateStatus(newValue.status.get());
                } else {
                    updateStatus(null);
                }
            });

            packageTypeBox.getSelectionModel().selectedItemProperty().addListener(ignored -> updateVersions());
            FXUtils.onChangeAndOperate(distributionBox.getSelectionModel().selectedItemProperty(), distribution -> {
                if (distribution != null) {
                    changingDistribution = true;
                    packageTypeBox.setItems(FXCollections.observableList(new ArrayList<>(distribution.getSupportedPackageTypes())));
                    packageTypeBox.getSelectionModel().select(0);
                    changingDistribution = false;
                    updateVersions();
                    packageTypeBox.setDisable(false);
                    remoteVersionBox.setDisable(false);
                } else {
                    packageTypeBox.setItems(null);
                    updateVersions();
                    remoteVersionBox.setItems(null);
                    packageTypeBox.setDisable(true);
                    remoteVersionBox.setDisable(true);
                }
            });

            setHeading(new Label(i18n("java.download")));
            setBody(body);
            setActions(warningLabel, downloadButtonPane, cancelButton);
        }

        private void updateStatus(DiscoJavaVersionList.Status status) {
            if (status == DiscoJavaVersionList.Status.LOADING) {
                downloadButtonPane.getChildren().setAll(new JFXSpinner());
                remoteVersionBox.setDisable(true);
                warningLabel.setText(null);
            } else {
                downloadButtonPane.getChildren().setAll(downloadButton);
                if (status == DiscoJavaVersionList.Status.SUCCESS || status == null) {
                    remoteVersionBox.setDisable(false);
                    warningLabel.setText(null);
                } else if (status == DiscoJavaVersionList.Status.FAILED) {
                    remoteVersionBox.setDisable(true);
                    warningLabel.setText(i18n("java.download.load_list.failed"));
                }
            }
        }

        private void onDownload() {
            fireEvent(new DialogCloseEvent());

            DiscoJavaDistribution distribution = distributionBox.getSelectionModel().getSelectedItem();
            DiscoJavaRemoteVersion version = remoteVersionBox.getSelectionModel().getSelectedItem();

            if (version == null)
                return;

            Controllers.taskDialog(new GetTask(downloadProvider.injectURLWithCandidates(version.getLinks().getPkgInfoUri()))
                    .setExecutor(Schedulers.io())
                    .thenComposeAsync(json -> {
                        DiscoResult<DiscoRemoteFileInfo> result = JsonUtils.fromNonNullJson(json, DiscoResult.typeOf(DiscoRemoteFileInfo.class));
                        if (result.getResult().size() != 1)
                            throw new IOException("Illegal result: " + json);

                        DiscoRemoteFileInfo fileInfo = result.getResult().get(0);
                        if (!fileInfo.getChecksumType().equals("sha1") && !fileInfo.getChecksumType().equals("sha256"))
                            throw new IOException("Unsupported checksum type: " + fileInfo.getChecksumType());
                        if (StringUtils.isBlank(fileInfo.getDirectDownloadUri()))
                            throw new IOException("Missing download URI: " + json);

                        File targetFile = File.createTempFile("hmcl-java-", "." + version.getArchiveType());
                        targetFile.deleteOnExit();

                        Task<FileDownloadTask.IntegrityCheck> getIntegrityCheck;
                        if (StringUtils.isNotBlank(fileInfo.getChecksum()))
                            getIntegrityCheck = Task.completed(new FileDownloadTask.IntegrityCheck(fileInfo.getChecksumType(), fileInfo.getChecksum()));
                        else if (StringUtils.isNotBlank(fileInfo.getChecksumUri()))
                            getIntegrityCheck = new GetTask(downloadProvider.injectURLWithCandidates(fileInfo.getChecksumUri()))
                                    .thenApplyAsync(checksum ->
                                            new FileDownloadTask.IntegrityCheck(fileInfo.getChecksumType(), checksum.trim()));
                        else
                            throw new IOException("Unable to get checksum for file");

                        return getIntegrityCheck
                                .thenComposeAsync(integrityCheck ->
                                        new FileDownloadTask(downloadProvider.injectURLWithCandidates(fileInfo.getDirectDownloadUri()),
                                                targetFile, integrityCheck))
                                .thenSupplyAsync(targetFile::toPath);
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
                            String defaultName = distribution.getApiParameter() + "-" + javaVersion;
                            Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller ->
                                    new JavaInstallPage(controller::onFinish, info, version, updateInfo, defaultName, result)));
                        } else {
                            LOG.warning("Failed to download java", exception);
                            Throwable resolvedException = resolveException(exception);
                            if (!(resolvedException instanceof CancellationException)) {
                                Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                            }
                        }
                    })), i18n("java.download"), TaskCancellationAction.NORMAL);

        }

        private void updateVersions() {
            if (changingDistribution) return;

            DiscoJavaDistribution distribution = distributionBox.getSelectionModel().getSelectedItem();
            if (distribution == null) {
                this.currentDiscoJavaVersionList.set(null);
                return;
            }

            JavaPackageType packageType = packageTypeBox.getSelectionModel().getSelectedItem();

            DiscoJavaVersionList list = javaVersionLists.computeIfAbsent(Pair.pair(distribution, packageType), pair -> {
                DiscoJavaVersionList res = new DiscoJavaVersionList();
                new DiscoFetchJavaListTask(downloadProvider, distribution, platform, packageType).setExecutor(Schedulers.io()).thenApplyAsync(versions -> {
                    if (versions.isEmpty()) return Collections.<DiscoJavaRemoteVersion>emptyList();

                    int lastLTS = -1;
                    for (int v : versions.keySet()) {
                        if (isLTS(v)) {
                            lastLTS = v;
                        }
                    }

                    ArrayList<DiscoJavaRemoteVersion> remoteVersions = new ArrayList<>();
                    for (Map.Entry<Integer, DiscoJavaRemoteVersion> entry : versions.entrySet()) {
                        int v = entry.getKey();
                        if (v >= lastLTS || isLTS(v) || v == 16) {
                            remoteVersions.add(entry.getValue());
                        }
                    }
                    Collections.reverse(remoteVersions);
                    return remoteVersions;
                }).whenComplete(Schedulers.javafx(), ((result, exception) -> {
                    if (exception == null) {
                        res.status.set(DiscoJavaVersionList.Status.SUCCESS);
                        res.versions.setAll(result);
                        selectLTS(res);
                    } else {
                        LOG.warning("Failed to load java list", exception);
                        res.status.set(DiscoJavaVersionList.Status.FAILED);
                    }
                })).start();
                return res;
            });
            this.currentDiscoJavaVersionList.set(list);
            this.remoteVersionBox.setItems(list.versions);
            selectLTS(list);
        }

        private void selectLTS(DiscoJavaVersionList list) {
            if (remoteVersionBox.getItems() == list.versions) {
                for (int i = 0; i < list.versions.size(); i++) {
                    JavaRemoteVersion item = list.versions.get(i);
                    if (item.getJdkVersion() == GameJavaVersion.LATEST.getMajorVersion()) {
                        remoteVersionBox.getSelectionModel().select(i);
                        break;
                    }
                }
            }
        }
    }

    private static final class DiscoJavaVersionList {
        enum Status {
            LOADING, SUCCESS, FAILED
        }

        final ObservableList<DiscoJavaRemoteVersion> versions = FXCollections.observableArrayList();
        final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.LOADING);
    }
}
