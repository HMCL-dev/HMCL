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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.download.fabric.FabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionsPage extends BorderPane implements WizardPage, Refreshable {
    private final String gameVersion;
    private final String libraryId;
    private final String title;
    private final WizardController controller;

    @FXML
    private JFXListView<RemoteVersion> list;
    @FXML
    private JFXSpinner spinner;
    @FXML
    private StackPane failedPane;
    @FXML
    private StackPane emptyPane;
    @FXML
    private TransitionPane root;
    @FXML
    private JFXCheckBox chkRelease;
    @FXML
    private JFXCheckBox chkSnapshot;
    @FXML
    private JFXCheckBox chkOld;
    @FXML
    private HBox checkPane;
    @FXML
    private ComponentList centrePane;
    @FXML
    private StackPane center;

    private VersionList<?> versionList;
    private CompletableFuture<?> executor;

    public VersionsPage(WizardController controller, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.libraryId = libraryId;
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/versions.fxml");

        versionList = downloadProvider.getVersionListById(libraryId);
        if (versionList.hasType()) {
            centrePane.getContent().setAll(checkPane, list);
        } else {
            centrePane.getContent().setAll(list);
        }
        list.getProperties().put("vgrow", true);

        InvalidationListener listener = o -> list.getItems().setAll(loadVersions());
        chkRelease.selectedProperty().addListener(listener);
        chkSnapshot.selectedProperty().addListener(listener);
        chkOld.selectedProperty().addListener(listener);

        list.setCellFactory(listView -> new ListCell<RemoteVersion>() {
            IconedTwoLineListItem content = new IconedTwoLineListItem();
            RipplerContainer ripplerContainer = new RipplerContainer(content);
            StackPane pane = new StackPane();

            {
                pane.getStyleClass().add("md-list-cell");
                StackPane.setMargin(content, new Insets(10, 16, 10, 16));
                pane.getChildren().setAll(ripplerContainer);
            }

            @Override
            public void updateItem(RemoteVersion remoteVersion, boolean empty) {
                super.updateItem(remoteVersion, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                setGraphic(pane);

                content.setTitle(remoteVersion.getSelfVersion());
                if (remoteVersion.getReleaseDate() != null) {
                    content.setSubtitle(Locales.DATE_TIME_FORMATTER.get().format(remoteVersion.getReleaseDate()));
                } else {
                    content.setSubtitle("");
                }

                if (remoteVersion instanceof GameRemoteVersion) {
                    switch (remoteVersion.getVersionType()) {
                        case RELEASE:
                            content.getTags().setAll(i18n("version.game.release"));
                            content.setImage(new Image("/assets/img/grass.png", 32, 32, false, true));
                            break;
                        case SNAPSHOT:
                            content.getTags().setAll(i18n("version.game.snapshot"));
                            content.setImage(new Image("/assets/img/command.png", 32, 32, false, true));
                            break;
                        default:
                            content.getTags().setAll(i18n("version.game.old"));
                            content.setImage(new Image("/assets/img/craft_table.png", 32, 32, false, true));
                            break;
                    }
                } else if (remoteVersion instanceof LiteLoaderRemoteVersion) {
                    content.setImage(new Image("/assets/img/chicken.png", 32, 32, false, true));
                    if (StringUtils.isNotBlank(content.getSubtitle())) {
                        content.getTags().setAll(remoteVersion.getGameVersion());
                    } else {
                        content.setSubtitle(remoteVersion.getGameVersion());
                    }
                } else if (remoteVersion instanceof OptiFineRemoteVersion) {
                    content.setImage(new Image("/assets/img/command.png", 32, 32, false, true));
                    if (StringUtils.isNotBlank(content.getSubtitle())) {
                        content.getTags().setAll(remoteVersion.getGameVersion());
                    } else {
                        content.setSubtitle(remoteVersion.getGameVersion());
                    }
                } else if (remoteVersion instanceof ForgeRemoteVersion) {
                    content.setImage(new Image("/assets/img/forge.png", 32, 32, false, true));
                    if (StringUtils.isNotBlank(content.getSubtitle())) {
                        content.getTags().setAll(remoteVersion.getGameVersion());
                    } else {
                        content.setSubtitle(remoteVersion.getGameVersion());
                    }
                } else if (remoteVersion instanceof FabricRemoteVersion) {
                    content.setImage(new Image("/assets/img/fabric.png", 32, 32, false, true));
                    if (StringUtils.isNotBlank(content.getSubtitle())) {
                        content.getTags().setAll(remoteVersion.getGameVersion());
                    } else {
                        content.setSubtitle(remoteVersion.getGameVersion());
                    }
                } else if (remoteVersion instanceof FabricAPIRemoteVersion) {
                    content.setImage(new Image("/assets/img/fabric.png", 32, 32, false, true));
                    if (StringUtils.isNotBlank(content.getSubtitle())) {
                        content.getTags().setAll(remoteVersion.getGameVersion());
                    } else {
                        content.setSubtitle(remoteVersion.getGameVersion());
                    }
                }
            }
        });

        list.setOnMouseClicked(e -> {
            if (list.getSelectionModel().getSelectedIndex() < 0)
                return;
            controller.getSettings().put(libraryId, list.getSelectionModel().getSelectedItem());
            callback.run();
        });

        refresh();
    }

    private List<RemoteVersion> loadVersions() {
        return versionList.getVersions(gameVersion).stream()
                .filter(it -> {
                    switch (it.getVersionType()) {
                        case RELEASE:
                            return chkRelease.isSelected();
                        case SNAPSHOT:
                            return chkSnapshot.isSelected();
                        case OLD:
                            return chkOld.isSelected();
                        default:
                            return true;
                    }
                })
                .sorted().collect(Collectors.toList());
    }

    @Override
    public void refresh() {
        VersionList<?> currentVersionList = versionList;
        root.setContent(spinner, ContainerAnimations.FADE.getAnimationProducer());
        executor = currentVersionList.refreshAsync(gameVersion).whenComplete((result, exception) -> {
            if (exception == null) {
                List<RemoteVersion> items = loadVersions();

                Platform.runLater(() -> {
                    if (versionList != currentVersionList) return;
                    if (currentVersionList.getVersions(gameVersion).isEmpty()) {
                        root.setContent(emptyPane, ContainerAnimations.FADE.getAnimationProducer());
                    } else {
                        if (items.isEmpty()) {
                            chkRelease.setSelected(true);
                            chkSnapshot.setSelected(true);
                            chkOld.setSelected(true);
                        } else {
                            list.getItems().setAll(items);
                        }
                        root.setContent(center, ContainerAnimations.FADE.getAnimationProducer());
                    }
                });
            } else {
                LOG.log(Level.WARNING, "Failed to fetch versions list", exception);
                Platform.runLater(() -> {
                    if (versionList != currentVersionList) return;
                    root.setContent(failedPane, ContainerAnimations.FADE.getAnimationProducer());
                });
            }

            // https://github.com/huanghongxun/HMCL/issues/938
            System.gc();
        });
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(libraryId);
        if (executor != null)
            executor.cancel(true);
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onBack() { controller.onPrev(true); }

    @FXML
    private void onSponsor() {
        FXUtils.openLink("https://hmcl.huangyuhui.net/api/redirect/bmclapi_sponsor");
    }
}
