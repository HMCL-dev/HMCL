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

import com.jfoenix.controls.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.download.cleanroom.CleanroomRemoteVersion;
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
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionsPage extends Control implements WizardPage, Refreshable {
    private final String gameVersion;
    private final String libraryId;
    private final String title;
    private final Navigation navigation;
    private final VersionList<?> versionList;
    private final Runnable callback;

    private final ObservableList<RemoteVersion> versions = FXCollections.observableArrayList();
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.LOADING);

    public VersionsPage(Navigation navigation, String title, String gameVersion, DownloadProvider downloadProvider, String libraryId, Runnable callback) {
        this.title = title;
        this.gameVersion = gameVersion;
        this.libraryId = libraryId;
        this.navigation = navigation;
        this.versionList = downloadProvider.getVersionListById(libraryId);
        this.callback = callback;

        refresh();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new VersionsPageSkin(this);
    }

    @Override
    public void refresh() {
        status.set(Status.LOADING);
        Task<?> task = versionList.refreshAsync(gameVersion)
                .thenSupplyAsync(() -> versionList.getVersions(gameVersion).stream().sorted().collect(Collectors.toList()))
                .whenComplete(Schedulers.javafx(), (items, exception) -> {
                    if (exception == null) {
                        versions.setAll(items);
                        status.set(Status.SUCCESS);
                    } else {
                        LOG.warning("Failed to fetch versions list", exception);
                        status.set(Status.FAILED);
                    }
                });
        task.start();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(libraryId);
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

    private enum Status {
        LOADING,
        FAILED,
        SUCCESS,
    }

    private enum VersionTypeFilter {
        ALL,
        RELEASE,
        SNAPSHOTS,
        APRIL_FOOLS,
        OLD
    }

    private static class RemoteVersionListCell extends ListCell<RemoteVersion> {
        private final IconedTwoLineListItem content = new IconedTwoLineListItem();
        private final RipplerContainer ripplerContainer = new RipplerContainer(content);
        private final StackPane pane = new StackPane();

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
                String wikiSuffix = getWikiUrlSuffix(remoteVersion.getGameVersion());
                switch (remoteVersion.getVersionType()) {
                    case RELEASE:
                        content.getTags().setAll(i18n("version.game.release"));
                        content.setImage(VersionIconType.GRASS.getIcon());
                        content.setExternalLink(i18n("wiki.version.game", wikiSuffix));
                        break;
                    case PENDING:
                    case SNAPSHOT:
                        if (GameVersionNumber.asGameVersion(remoteVersion.getGameVersion()).isSpecial()) {
                            content.getTags().setAll(i18n("version.game.april_fools"));
                            content.setImage(VersionIconType.APRIL_FOOLS.getIcon());
                        } else {
                            content.getTags().setAll(i18n("version.game.snapshot"));
                            content.setImage(VersionIconType.COMMAND.getIcon());
                        }
                        content.setExternalLink(i18n("wiki.version.game", wikiSuffix));
                        break;
                    default:
                        content.getTags().setAll(i18n("version.game.old"));
                        content.setImage(VersionIconType.CRAFT_TABLE.getIcon());
                        content.setExternalLink(i18n("wiki.version.game", wikiSuffix));
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
                else if (remoteVersion instanceof CleanroomRemoteVersion)
                    iconType = VersionIconType.CLEANROOM;
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

        private String getWikiUrlSuffix(String gameVersion) {
            String id = gameVersion.toLowerCase(Locale.ROOT);

            switch (id) {
                case "0.30-1":
                case "0.30-2":
                case "c0.30_01c":
                    return i18n("wiki.version.game.search", "Classic_0.30");
                case "in-20100206-2103":
                    return i18n("wiki.version.game.search", "Indev_20100206");
                case "inf-20100630-1":
                    return i18n("wiki.version.game.search", "Infdev_20100630");
                case "inf-20100630-2":
                    return i18n("wiki.version.game.search", "Alpha_v1.0.0");
                case "1.19_deep_dark_experimental_snapshot-1":
                    return "1.19-exp1";
                case "in-20100130":
                    return i18n("wiki.version.game.search", "Indev_0.31_20100130");
                case "b1.6-tb3":
                    return i18n("wiki.version.game.search", "Beta_1.6_Test_Build_3");
                case "1.14_combat-212796":
                    return i18n("wiki.version.game.search", "1.14.3_-_Combat_Test");
                case "1.14_combat-0":
                    return i18n("wiki.version.game.search", "Combat_Test_2");
                case "1.14_combat-3":
                    return i18n("wiki.version.game.search", "Combat_Test_3");
                case "1_15_combat-1":
                    return i18n("wiki.version.game.search", "Combat_Test_4");
                case "1_15_combat-6":
                    return i18n("wiki.version.game.search", "Combat_Test_5");
                case "1_16_combat-0":
                    return i18n("wiki.version.game.search", "Combat_Test_6");
                case "1_16_combat-1":
                    return i18n("wiki.version.game.search", "Combat_Test_7");
                case "1_16_combat-2":
                    return i18n("wiki.version.game.search", "Combat_Test_7b");
                case "1_16_combat-3":
                    return i18n("wiki.version.game.search", "Combat_Test_7c");
                case "1_16_combat-4":
                    return i18n("wiki.version.game.search", "Combat_Test_8");
                case "1_16_combat-5":
                    return i18n("wiki.version.game.search", "Combat_Test_8b");
                case "1_16_combat-6":
                    return i18n("wiki.version.game.search", "Combat_Test_8c");
            }

            if (id.startsWith("1.0.0-rc2")) return "RC2";
            if (id.startsWith("2.0")) return i18n("wiki.version.game.search", "2.0");
            if (id.startsWith("b1.8-pre1")) return "Beta_1.8-pre1";
            if (id.startsWith("b1.1-")) return i18n("wiki.version.game.search", "Beta_1.1");
            if (id.startsWith("a1.1.0")) return "Alpha_v1.1.0";
            if (id.startsWith("a1.0.14")) return "Alpha_v1.0.14";
            if (id.startsWith("a1.0.13_01")) return "Alpha_v1.0.13_01";
            if (id.startsWith("in-20100214")) return i18n("wiki.version.game.search", "Indev_20100214");

            if (id.contains("experimental-snapshot")) {
                return id.replace("_experimental-snapshot-", "-exp");
            }

            if (id.startsWith("inf-")) return id.replace("inf-", "Infdev_");
            if (id.startsWith("in-")) return id.replace("in-", "Indev_");
            if (id.startsWith("rd-")) return "pre-Classic_" + id;
            if (id.startsWith("b")) return id.replace("b", "Beta_");
            if (id.startsWith("a")) return id.replace("a", "Alpha_v");
            if (id.startsWith("c")) return id.replace("c", "Classic_").replace("st", "SURVIVAL_TEST");

            return i18n("wiki.version.game.search", id);
        }
    }

    private static final class VersionsPageSkin extends SkinBase<VersionsPage> {
        private final JFXListView<RemoteVersion> list;

        private final TransitionPane transitionPane;
        private final JFXSpinner spinner;

        private final JFXTextField nameField;
        private final JFXComboBox<VersionTypeFilter> categoryField = new JFXComboBox<>();

        VersionsPageSkin(VersionsPage control) {
            super(control);

            BorderPane root = new BorderPane();

            GridPane searchPane = new GridPane();
            root.setTop(searchPane);
            searchPane.getStyleClass().addAll("card");
            BorderPane.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints nameColumn = new ColumnConstraints();
            nameColumn.setMinWidth(USE_PREF_SIZE);
            ColumnConstraints column1 = new ColumnConstraints();
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setMaxWidth(150);
            ColumnConstraints column3 = new ColumnConstraints();

            if (control.versionList.hasType())
                searchPane.getColumnConstraints().setAll(nameColumn, column1, nameColumn, column2, column3);
            else
                searchPane.getColumnConstraints().setAll(nameColumn, column1, column3);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                int rowIndex = 0;

                {
                    nameField = new JFXTextField();
                    nameField.setPromptText(i18n("version.search.prompt"));
                    nameField.textProperty().addListener(o -> updateList());

                    if ("game".equals(control.libraryId)) {
                        categoryField.getItems().setAll(
                                VersionTypeFilter.ALL,
                                VersionTypeFilter.RELEASE,
                                VersionTypeFilter.SNAPSHOTS,
                                VersionTypeFilter.APRIL_FOOLS,
                                VersionTypeFilter.OLD
                        );
                        categoryField.getSelectionModel().select(VersionTypeFilter.RELEASE);
                    } else {
                        categoryField.getItems().setAll(
                                VersionTypeFilter.ALL,
                                VersionTypeFilter.RELEASE,
                                VersionTypeFilter.SNAPSHOTS
                        );
                        categoryField.getSelectionModel().select(VersionTypeFilter.ALL);
                    }
                    categoryField.setConverter(stringConverter(type -> i18n("version.game." + type.name().toLowerCase(Locale.ROOT))));
                    categoryField.getSelectionModel().selectedItemProperty().addListener(o -> updateList());

                    JFXButton refreshButton = FXUtils.newRaisedButton(i18n("button.refresh"));
                    refreshButton.setOnAction(event -> control.onRefresh());

                    if (control.versionList.hasType()) {
                        searchPane.addRow(rowIndex++,
                                new Label(i18n("version.search")), nameField,
                                new Label(i18n("version.game.type")), categoryField,
                                refreshButton
                        );
                    } else {
                        searchPane.addRow(rowIndex++,
                                new Label(i18n("version.search")), nameField,
                                refreshButton
                        );
                    }
                }
//                {
//                    HBox actionsBox = new HBox(8);
//                    GridPane.setColumnSpan(actionsBox, 4);
//                    actionsBox.setAlignment(Pos.CENTER_RIGHT);
//
//                    JFXButton refreshButton = FXUtils.newRaisedButton(i18n("button.refresh"));
//                    refreshButton.setOnAction(event -> control.onRefresh());
//
//                    actionsBox.getChildren().setAll(refreshButton);
//
//                    searchPane.addRow(rowIndex++, actionsBox);
//                }
            }

            {
                SpinnerPane spinnerPane = new SpinnerPane();
                root.setCenter(spinnerPane);

                transitionPane = new TransitionPane();
                spinner = new JFXSpinner();

                StackPane centerWrapper = new StackPane();
                centerWrapper.setStyle("-fx-padding: 10;");
                {
                    ComponentList centrePane = new ComponentList();
                    centrePane.getStyleClass().add("no-padding");
                    {
                        list = new JFXListView<>();
                        list.getStyleClass().add("jfx-list-view-float");
                        VBox.setVgrow(list, Priority.ALWAYS);

                        control.versions.addListener((InvalidationListener) o -> updateList());

                        Holder<RemoteVersionListCell> lastCell = new Holder<>();
                        list.setCellFactory(listView -> new RemoteVersionListCell(lastCell, control.libraryId));

                        FXUtils.onClicked(list, () -> {
                            if (list.getSelectionModel().getSelectedIndex() < 0)
                                return;
                            control.navigation.getSettings().put(control.libraryId, list.getSelectionModel().getSelectedItem());
                            control.callback.run();
                        });

                        ComponentList.setVgrow(list, Priority.ALWAYS);

                        // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                        ignoreEvent(list, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                        centrePane.getContent().setAll(list);
                    }

                    centerWrapper.getChildren().setAll(centrePane);
                }

                StackPane failedPane = new StackPane();
                failedPane.getStyleClass().add("notice-pane");
                {
                    Label label = new Label(i18n("download.failed.refresh"));
                    FXUtils.onClicked(label, control::onRefresh);

                    failedPane.getChildren().setAll(label);
                }

                StackPane emptyPane = new StackPane();
                emptyPane.getStyleClass().add("notice-pane");
                {
                    Label label = new Label(i18n("download.failed.empty"));
                    FXUtils.onClicked(label, control::onBack);

                    emptyPane.getChildren().setAll(label);
                }

                FXUtils.onChangeAndOperate(control.status, status -> {
                    if (status == Status.LOADING)
                        transitionPane.setContent(spinner, ContainerAnimations.FADE);
                    else if (status == Status.SUCCESS)
                        transitionPane.setContent(centerWrapper, ContainerAnimations.FADE);
                    else // if (status == Status.FAILED)
                        transitionPane.setContent(failedPane, ContainerAnimations.FADE);
                });

                root.setCenter(transitionPane);
            }

            this.getChildren().setAll(root);
        }

        private void updateList() {
            Stream<RemoteVersion> versions = getSkinnable().versions.stream();

            VersionTypeFilter filter = categoryField.getSelectionModel().getSelectedItem();
            if (filter != null)
                versions = versions.filter(it -> {
                    RemoteVersion.Type versionType = it.getVersionType();
                    switch (filter) {
                        case RELEASE:
                            return versionType == RemoteVersion.Type.RELEASE;
                        case SNAPSHOTS:
                            return versionType == RemoteVersion.Type.SNAPSHOT
                                    || versionType == RemoteVersion.Type.PENDING;
                        case APRIL_FOOLS:
                            return versionType == RemoteVersion.Type.SNAPSHOT
                                    && GameVersionNumber.asGameVersion(it.getGameVersion()).isSpecial();
                        case OLD:
                            return versionType == RemoteVersion.Type.OLD;
                        case ALL:
                        default:
                            return true;
                    }
                });

            String nameQuery = nameField.getText();
            if (!StringUtils.isBlank(nameQuery)) {
                if (nameQuery.startsWith("regex:")) {
                    try {
                        Pattern pattern = Pattern.compile(nameQuery.substring("regex:".length()));
                        versions = versions.filter(it -> pattern.matcher(it.getSelfVersion()).find());
                    } catch (Throwable e) {
                        LOG.warning("Illegal regular expression: " + nameQuery, e);
                    }
                } else {
                    String lowerQueryString = nameQuery.toLowerCase(Locale.ROOT);
                    versions = versions.filter(it -> it.getSelfVersion().toLowerCase(Locale.ROOT).contains(lowerQueryString));
                }
            }

            //noinspection DataFlowIssue
            list.getItems().setAll(versions.collect(Collectors.toList()));
            list.scrollTo(0);
        }
    }
}
