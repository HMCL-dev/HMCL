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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.download.fabric.FabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltAPIRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltRemoteVersion;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.HintPane;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.HMCLService;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionsPage extends BorderPane implements WizardPage, Refreshable {
    private final String gameVersion;
    private final String libraryId;
    private final String title;
    private final Navigation navigation;

    private final JFXListView<RemoteVersion> list;
    private final JFXSpinner spinner;
    private final StackPane failedPane;
    private final StackPane emptyPane;
    private final TransitionPane root;
    private final JFXCheckBox chkRelease;
    private final JFXCheckBox chkSnapshot;
    private final JFXCheckBox chkOld;
    private final JFXButton btnRefresh;
    private final HBox checkPane;
    private final ComponentList centrePane;
    private final StackPane center;

    private final VersionList<?> versionList;
    private CompletableFuture<?> executor;

    public VersionsPage(Navigation navigation, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.libraryId = libraryId;
        this.navigation = navigation;

        HintPane hintPane = new HintPane();
        hintPane.setText(i18n("sponsor.bmclapi"));
        hintPane.getStyleClass().add("sponsor-pane");
        hintPane.setOnMouseClicked(e -> onSponsor());
        BorderPane.setMargin(hintPane, new Insets(10, 10, 0, 10));
        this.setTop(hintPane);

        root = new TransitionPane();
        {
            spinner = new JFXSpinner();

            center = new StackPane();
            center.setStyle("-fx-padding: 10;");
            {
                centrePane = new ComponentList();
                centrePane.getStyleClass().add("no-padding");
                {
                    checkPane = new HBox();
                    checkPane.setSpacing(10);
                    {
                        chkRelease = new JFXCheckBox(i18n("version.game.releases"));
                        chkRelease.setSelected(true);
                        HBox.setMargin(chkRelease, new Insets(10, 0, 10, 0));

                        chkSnapshot = new JFXCheckBox(i18n("version.game.snapshots"));
                        HBox.setMargin(chkSnapshot, new Insets(10, 0, 10, 0));

                        chkOld = new JFXCheckBox(i18n("version.game.old"));
                        HBox.setMargin(chkOld, new Insets(10, 0, 10, 0));

                        HBox pane = new HBox();
                        HBox.setHgrow(pane, Priority.ALWAYS);

                        btnRefresh = new JFXButton(i18n("button.refresh"));
                        btnRefresh.getStyleClass().add("jfx-tool-bar-button");
                        btnRefresh.setOnAction(e -> onRefresh());

                        checkPane.getChildren().setAll(chkRelease, chkSnapshot, chkOld, pane, btnRefresh);
                    }

                    list = new JFXListView<>();
                    list.getStyleClass().add("jfx-list-view-float");
                    VBox.setVgrow(list, Priority.ALWAYS);

                    centrePane.getContent().setAll(checkPane, list);
                }

                center.getChildren().setAll(centrePane);
            }

            failedPane = new StackPane();
            failedPane.getStyleClass().add("notice-pane");
            {
                Label label = new Label(i18n("download.failed.refresh"));
                label.setOnMouseClicked(e -> onRefresh());

                failedPane.getChildren().setAll(label);
            }

            emptyPane = new StackPane();
            emptyPane.getStyleClass().add("notice-pane");
            {
                Label label = new Label(i18n("download.failed.empty"));
                label.setOnMouseClicked(e -> onBack());

                emptyPane.getChildren().setAll(label);
            }
        }
        this.setCenter(root);

        versionList = downloadProvider.getVersionListById(libraryId);
        if (versionList.hasType()) {
            centrePane.getContent().setAll(checkPane, list);
        } else {
            centrePane.getContent().setAll(list);
        }
        ComponentList.setVgrow(list, Priority.ALWAYS);

        InvalidationListener listener = o -> list.getItems().setAll(loadVersions());
        chkRelease.selectedProperty().addListener(listener);
        chkSnapshot.selectedProperty().addListener(listener);
        chkOld.selectedProperty().addListener(listener);

        btnRefresh.setGraphic(wrap(SVG.refresh(Theme.blackFillBinding(), -1, -1)));

        Holder<RemoteVersionListCell> lastCell = new Holder<>();
        EnumMap<VersionIconType, Image> icons = new EnumMap<>(VersionIconType.class);
        list.setCellFactory(listView -> new RemoteVersionListCell(lastCell, icons));

        list.setOnMouseClicked(e -> {
            if (list.getSelectionModel().getSelectedIndex() < 0)
                return;
            navigation.getSettings().put(libraryId, list.getSelectionModel().getSelectedItem());
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
        System.gc();
    }

    private void onRefresh() {
        refresh();
        System.gc();
    }

    private void onBack() { navigation.onPrev(true); }

    private void onSponsor() {
        HMCLService.openRedirectLink("bmclapi_sponsor");
    }

    private static class RemoteVersionListCell extends ListCell<RemoteVersion> {
        final IconedTwoLineListItem content = new IconedTwoLineListItem();
        final RipplerContainer ripplerContainer = new RipplerContainer(content);
        final StackPane pane = new StackPane();

        private final Holder<RemoteVersionListCell> lastCell;
        private final EnumMap<VersionIconType, Image> icons;

        RemoteVersionListCell(Holder<RemoteVersionListCell> lastCell, EnumMap<VersionIconType, Image> icons) {
            this.lastCell = lastCell;
            this.icons = icons;

            pane.getStyleClass().add("md-list-cell");
            StackPane.setMargin(content, new Insets(10, 16, 10, 16));
            pane.getChildren().setAll(ripplerContainer);
        }

        private Image getIcon(VersionIconType type) {
            return icons.computeIfAbsent(type, iconType -> new Image(iconType.getResourceUrl()));
        }

        @Override
        public void updateItem(RemoteVersion remoteVersion, boolean empty) {
            super.updateItem(remoteVersion, empty);

            // https://mail.openjdk.org/pipermail/openjfx-dev/2022-July/034764.html
            if (this == lastCell.value && !isVisible())
                return;
            lastCell.value = this;

            if (empty) {
                setGraphic(null);
                return;
            }
            setGraphic(pane);

            content.setTitle(remoteVersion.getSelfVersion());
            if (remoteVersion.getReleaseDate() != null) {
                content.setSubtitle(Locales.DATE_TIME_FORMATTER.get().format(remoteVersion.getReleaseDate().toInstant()));
            } else {
                content.setSubtitle(null);
            }

            if (remoteVersion instanceof GameRemoteVersion) {
                switch (remoteVersion.getVersionType()) {
                    case RELEASE:
                        content.getTags().setAll(i18n("version.game.release"));
                        content.setImage(getIcon(VersionIconType.GRASS));
                        break;
                    case SNAPSHOT:
                        content.getTags().setAll(i18n("version.game.snapshot"));
                        content.setImage(getIcon(VersionIconType.COMMAND));
                        break;
                    default:
                        content.getTags().setAll(i18n("version.game.old"));
                        content.setImage(getIcon(VersionIconType.CRAFT_TABLE));
                        break;
                }
            } else {
                VersionIconType iconType;
                if (remoteVersion instanceof LiteLoaderRemoteVersion)
                    iconType = VersionIconType.CHICKEN;
                else if (remoteVersion instanceof OptiFineRemoteVersion)
                    iconType = VersionIconType.COMMAND;
                else if (remoteVersion instanceof ForgeRemoteVersion)
                    iconType = VersionIconType.FORGE;
                else if (remoteVersion instanceof FabricRemoteVersion || remoteVersion instanceof FabricAPIRemoteVersion)
                    iconType = VersionIconType.FABRIC;
                else if (remoteVersion instanceof QuiltRemoteVersion || remoteVersion instanceof QuiltAPIRemoteVersion)
                    iconType = VersionIconType.QUILT;
                else
                    iconType = null;

                content.setImage(iconType != null ? getIcon(iconType) : null);
                if (content.getSubtitle() == null)
                    content.setSubtitle(remoteVersion.getGameVersion());
                else
                    content.getTags().setAll(remoteVersion.getGameVersion());
            }
        }
    }
}
