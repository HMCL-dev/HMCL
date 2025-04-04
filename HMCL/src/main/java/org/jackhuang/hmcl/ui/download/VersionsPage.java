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
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.download.fabric.FabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.neoforge.NeoForgeRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltAPIRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltRemoteVersion;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.FXUtils;
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
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.wrap;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
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
    private final ComponentList centrePane;
    private final StackPane center;

    private final VersionList<?> versionList;
    private CompletableFuture<?> executor;

    private final HBox searchBar;
    private final StringProperty queryString = new SimpleStringProperty();

    public VersionsPage(Navigation navigation, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.libraryId = libraryId;
        this.navigation = navigation;

        HintPane hintPane = new HintPane();
        hintPane.setText(i18n("sponsor.bmclapi"));
        hintPane.getStyleClass().add("sponsor-pane");
        FXUtils.onClicked(hintPane, this::onSponsor);
        BorderPane.setMargin(hintPane, new Insets(10, 10, 0, 10));
        this.setTop(hintPane);

        root = new TransitionPane();
        BorderPane toolbarPane = new BorderPane();
        JFXButton btnRefresh;
        {
            spinner = new JFXSpinner();

            center = new StackPane();
            center.setStyle("-fx-padding: 10;");
            {
                centrePane = new ComponentList();
                centrePane.getStyleClass().add("no-padding");
                {
                    HBox checkPane = new HBox();
                    checkPane.setSpacing(10);
                    {
                        chkRelease = new JFXCheckBox(i18n("version.game.releases"));
                        chkRelease.setSelected(true);
                        HBox.setMargin(chkRelease, new Insets(10, 0, 10, 0));

                        chkSnapshot = new JFXCheckBox(i18n("version.game.snapshots"));
                        HBox.setMargin(chkSnapshot, new Insets(10, 0, 10, 0));

                        chkOld = new JFXCheckBox(i18n("version.game.old"));
                        HBox.setMargin(chkOld, new Insets(10, 0, 10, 0));

                        checkPane.getChildren().setAll(chkRelease, chkSnapshot, chkOld);
                    }

                    list = new JFXListView<>();
                    list.getStyleClass().add("jfx-list-view-float");
                    VBox.setVgrow(list, Priority.ALWAYS);

                    TransitionPane rightToolbarPane = new TransitionPane();
                    {
                        HBox refreshPane = new HBox();
                        refreshPane.setAlignment(Pos.CENTER_RIGHT);

                        btnRefresh = new JFXButton(i18n("button.refresh"));
                        btnRefresh.getStyleClass().add("jfx-tool-bar-button");
                        btnRefresh.setOnAction(e -> onRefresh());

                        JFXButton btnSearch = new JFXButton(i18n("search"));
                        btnSearch.getStyleClass().add("jfx-tool-bar-button");
                        btnSearch.setGraphic(wrap(SVG.SEARCH.createIcon(Theme.blackFill(), -1)));

                        searchBar = new HBox();
                        {
                            searchBar.setAlignment(Pos.CENTER);
                            searchBar.setPadding(new Insets(0, 5, 0, 0));

                            JFXTextField searchField = new JFXTextField();
                            searchField.setPromptText(i18n("search"));
                            HBox.setHgrow(searchField, Priority.ALWAYS);

                            JFXButton closeSearchBar = new JFXButton();
                            closeSearchBar.getStyleClass().add("jfx-tool-bar-button");
                            closeSearchBar.setGraphic(wrap(SVG.CLOSE.createIcon(Theme.blackFill(), -1)));
                            closeSearchBar.setOnAction(e -> {
                                searchField.clear();
                                rightToolbarPane.setContent(refreshPane, ContainerAnimations.FADE);
                            });
                            onEscPressed(searchField, closeSearchBar::fire);
                            PauseTransition pause = new PauseTransition(Duration.millis(100));
                            pause.setOnFinished(e -> queryString.set(searchField.getText()));
                            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                                pause.setRate(1);
                                pause.playFromStart();
                            });

                            searchBar.getChildren().setAll(searchField, closeSearchBar);

                            btnSearch.setOnAction(e -> {
                                rightToolbarPane.setContent(searchBar, ContainerAnimations.FADE);
                                searchField.requestFocus();
                            });
                        }

                        refreshPane.getChildren().setAll(new HBox(btnSearch, btnRefresh));
                        rightToolbarPane.setContent(refreshPane, ContainerAnimations.NONE);
                    }

                    // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                    ignoreEvent(list, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                    toolbarPane.setLeft(checkPane);
                    toolbarPane.setRight(rightToolbarPane);

                    centrePane.getContent().setAll(toolbarPane, list);
                }

                center.getChildren().setAll(centrePane);
            }

            failedPane = new StackPane();
            failedPane.getStyleClass().add("notice-pane");
            {
                Label label = new Label(i18n("download.failed.refresh"));
                FXUtils.onClicked(label, this::onRefresh);

                failedPane.getChildren().setAll(label);
            }

            emptyPane = new StackPane();
            emptyPane.getStyleClass().add("notice-pane");
            {
                Label label = new Label(i18n("download.failed.empty"));
                FXUtils.onClicked(label, this::onBack);

                emptyPane.getChildren().setAll(label);
            }
        }
        this.setCenter(root);

        versionList = downloadProvider.getVersionListById(libraryId);
        boolean hasType = versionList.hasType();
        chkRelease.setManaged(hasType);
        chkRelease.setVisible(hasType);
        chkSnapshot.setManaged(hasType);
        chkSnapshot.setVisible(hasType);
        chkOld.setManaged(hasType);
        chkOld.setVisible(hasType);

        if (hasType) {
            centrePane.getContent().setAll(toolbarPane, list);
        } else {
            centrePane.getContent().setAll(list);
        }
        ComponentList.setVgrow(list, Priority.ALWAYS);

        InvalidationListener listener = o -> {
            List<RemoteVersion> versions = loadVersions();
            String query = queryString.get();
            if (!StringUtils.isBlank(query)) {
                Predicate<RemoteVersion> predicate;
                if (query.startsWith("regex:")) {
                    try {
                        Pattern pattern = Pattern.compile(query.substring("regex:".length()));
                        predicate = it -> pattern.matcher(it.getSelfVersion()).find();
                    } catch (Throwable e) {
                        LOG.warning("Illegal regular expression", e);
                        return;
                    }
                } else {
                    String lowerQueryString = query.toLowerCase(Locale.ROOT);
                    predicate = it -> it.getSelfVersion().toLowerCase(Locale.ROOT).contains(lowerQueryString);
                }

                versions = versions.stream().filter(predicate).collect(Collectors.toList());
            }

            list.getItems().setAll(versions);
        };
        chkRelease.selectedProperty().addListener(listener);
        chkSnapshot.selectedProperty().addListener(listener);
        chkOld.selectedProperty().addListener(listener);
        queryString.addListener(listener);

        btnRefresh.setGraphic(wrap(SVG.REFRESH.createIcon(Theme.blackFill(), -1)));

        Holder<RemoteVersionListCell> lastCell = new Holder<>();
        list.setCellFactory(listView -> new RemoteVersionListCell(lastCell, libraryId));

        FXUtils.onClicked(list, () -> {
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
                        case PENDING:
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
        root.setContent(spinner, ContainerAnimations.FADE);
        executor = currentVersionList.refreshAsync(gameVersion).whenComplete((result, exception) -> {
            if (exception == null) {
                List<RemoteVersion> items = loadVersions();

                Platform.runLater(() -> {
                    if (versionList != currentVersionList) return;
                    if (currentVersionList.getVersions(gameVersion).isEmpty()) {
                        root.setContent(emptyPane, ContainerAnimations.FADE);
                    } else {
                        if (items.isEmpty()) {
                            chkRelease.setSelected(true);
                            chkSnapshot.setSelected(true);
                            chkOld.setSelected(true);
                        } else {
                            list.getItems().setAll(items);
                        }
                        root.setContent(center, ContainerAnimations.FADE);
                    }
                });
            } else {
                LOG.warning("Failed to fetch versions list", exception);
                Platform.runLater(() -> {
                    if (versionList != currentVersionList) return;
                    root.setContent(failedPane, ContainerAnimations.FADE);
                });
            }

            // https://github.com/HMCL-dev/HMCL/issues/938
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

    private void onRefresh() {
        refresh();
    }

    private void onBack() {
        navigation.onPrev(true);
    }

    private void onSponsor() {
        FXUtils.openLink("https://bmclapidoc.bangbang93.com");
    }

    private static class RemoteVersionListCell extends ListCell<RemoteVersion> {
        final IconedTwoLineListItem content = new IconedTwoLineListItem();
        final RipplerContainer ripplerContainer = new RipplerContainer(content);
        final StackPane pane = new StackPane();

        private final Holder<RemoteVersionListCell> lastCell;

        RemoteVersionListCell(Holder<RemoteVersionListCell> lastCell, String libraryId) {
            this.lastCell = lastCell;
            if ("game".equals(libraryId)) {
                content.getExternalLinkButton().setGraphic(SVG.GLOBE_BOOK.createIcon(Theme.blackFill(), -1));
                FXUtils.installFastTooltip(content.getExternalLinkButton(), i18n("wiki.tooltip"));
            }

            pane.getStyleClass().add("md-list-cell");
            StackPane.setMargin(content, new Insets(10, 16, 10, 16));
            pane.getChildren().setAll(ripplerContainer);
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
                content.setSubtitle(I18n.formatDateTime(remoteVersion.getReleaseDate()));
            } else {
                content.setSubtitle(null);
            }

            if (remoteVersion instanceof GameRemoteVersion) {
                switch (remoteVersion.getVersionType()) {
                    case RELEASE:
                        content.getTags().setAll(i18n("version.game.release"));
                        content.setImage(VersionIconType.GRASS.getIcon());
                        content.setExternalLink(i18n("wiki.version.game.release", remoteVersion.getGameVersion()));
                        break;
                    case PENDING:
                    case SNAPSHOT:
                        content.getTags().setAll(i18n("version.game.snapshot"));
                        content.setImage(VersionIconType.COMMAND.getIcon());
                        content.setExternalLink(i18n("wiki.version.game.snapshot", remoteVersion.getGameVersion()));
                        break;
                    default:
                        content.getTags().setAll(i18n("version.game.old"));
                        content.setImage(VersionIconType.CRAFT_TABLE.getIcon());
                        content.setExternalLink(null);
                        break;
                }
            } else {
                VersionIconType iconType;
                if (remoteVersion instanceof LiteLoaderRemoteVersion)
                    iconType = VersionIconType.CHICKEN;
                else if (remoteVersion instanceof OptiFineRemoteVersion)
                    iconType = VersionIconType.OPTIFINE;
                else if (remoteVersion instanceof ForgeRemoteVersion)
                    iconType = VersionIconType.FORGE;
                else if (remoteVersion instanceof NeoForgeRemoteVersion)
                    iconType = VersionIconType.NEO_FORGE;
                else if (remoteVersion instanceof FabricRemoteVersion || remoteVersion instanceof FabricAPIRemoteVersion)
                    iconType = VersionIconType.FABRIC;
                else if (remoteVersion instanceof QuiltRemoteVersion || remoteVersion instanceof QuiltAPIRemoteVersion)
                    iconType = VersionIconType.QUILT;
                else
                    iconType = null;

                content.setImage(iconType != null ? iconType.getIcon() : null);
                if (content.getSubtitle() == null)
                    content.setSubtitle(remoteVersion.getGameVersion());
                else
                    content.getTags().setAll(remoteVersion.getGameVersion());
                content.setExternalLink(null);
            }
        }
    }
}
