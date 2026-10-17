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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.addon.update.AddonUpdate;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.addon.update.AddonUpdateTask;
import org.jackhuang.hmcl.addon.update.AddonUpdates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import org.jackhuang.hmcl.util.javafx.Subscription;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonUpdatesPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("addon.check_update")));

    private final DownloadProvider downloadProvider;
    private final Path localAddonDirectory;
    private final ObservableList<AddonUpdateObject> objects;

    private final JFXListView<AddonUpdateObject> listView;

    public AddonUpdatesPage(DownloadProvider downloadProvider, Path localAddonDirectory, AddonUpdates updates) {
        this.downloadProvider = downloadProvider;
        this.localAddonDirectory = localAddonDirectory;

        getStyleClass().add("gray-background");

        var common = new Lazy<>(() -> updates.commonUpdates().stream().map(AddonUpdateObject::new).toList());
        var release = new Lazy<>(() -> updates.releaseUpdates().stream().map(AddonUpdateObject::new).toList());

        objects = FXCollections.observableArrayList();

        {
            StackPane centerWrapper = new StackPane();
            centerWrapper.setStyle("-fx-padding: 10;");

            ComponentList centrePane = new ComponentList();
            centrePane.getStyleClass().add("no-padding");

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label(i18n("addon.check_update.only_preview"));
            placeholderContainer.getChildren().add(placeholderLabel);

            listView = new JFXListView<>();
            listView.setItems(objects);
            listView.getStyleClass().add("jfx-list-view-float");
            listView.setPlaceholder(placeholderContainer);

            setMargin(listView, new Insets(0, 10, 0, 10));
            VBox.setVgrow(listView, Priority.ALWAYS);

            listView.setCellFactory(listView -> new AddonUpdateListCell());

            ComponentList.setVgrow(listView, Priority.ALWAYS);

            centrePane.getContent().setAll(listView);

            centerWrapper.getChildren().setAll(centrePane);

            setCenter(centerWrapper);
        }

        {
            HBox actions = new HBox(8);
            actions.setPadding(new Insets(8));
            actions.setAlignment(Pos.CENTER_RIGHT);

            JFXCheckBox allEnabledBox = new JFXCheckBox();
            allEnabledBox.setText(i18n("button.select_all"));
            FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(), objects.stream().map(o -> o.enabled).toArray(BooleanProperty[]::new));

            JFXCheckBox showPreviewCheck = new JFXCheckBox(i18n("addon.check_update.preview"));
            {
                List<Subscription> subscriptions = new ArrayList<>();
                FXUtils.onChangeAndOperate(showPreviewCheck.selectedProperty(), preview -> {
                    if (preview) objects.setAll(common.get());
                    else objects.setAll(release.get());
                    subscriptions.forEach(Subscription::unsubscribe);
                    subscriptions.clear();
                    subscriptions.addAll(FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(), objects.stream().map(AddonUpdateObject::enabledProperty).toArray(BooleanProperty[]::new)));
                });
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            JFXButton exportListButton = FXUtils.newRaisedButton(i18n("button.export"));
            exportListButton.setOnAction(e -> exportList());

            JFXButton nextButton = FXUtils.newRaisedButton(i18n("addon.check_update.confirm"));
            nextButton.setOnAction(e -> updateFiles());

            JFXButton cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
            onEscPressed(this, cancelButton::fire);
            onEscPressed(listView, cancelButton::fire);

            actions.getChildren().setAll(allEnabledBox, showPreviewCheck, spacer, exportListButton, nextButton, cancelButton);

            setBottom(actions);
        }
    }

    /// Starts downloading the target version selected for every enabled addon.
    private void updateFiles() {
        AddonUpdateTask task = new AddonUpdateTask(
                downloadProvider,
                localAddonDirectory,
                objects.stream()
                        .filter(AddonUpdateObject::isEnabled)
                        .map(AddonUpdateObject::getData)
                        .toList()
        );
        Controllers.taskDialog(
                task.whenComplete(Schedulers.javafx(), exception -> {
                    fireEvent(new PageCloseEvent());
                    if (!task.getFailedAddons().isEmpty()) {
                        Controllers.dialog(i18n("addon.check_update.failed_download") + "\n" +
                                        task.getFailedAddons().stream().map(LocalAddonFile::getFileName).collect(Collectors.joining("\n")),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.ERROR);
                    }

                    if (exception == null) {
                        Controllers.dialog(i18n("install.success"));
                    }
                }),
                i18n("addon.check_update"),
                TaskCancellationAction.NORMAL);
    }

    private void exportList() {
        Path path = Paths.get("hmcl-mod-update-list-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".csv").toAbsolutePath();

        Controllers.taskDialog(Task.runAsync(() -> {
            CSVTable csvTable = new CSVTable();

            csvTable.set(0, 0, "Source File Name");
            csvTable.set(1, 0, "Current Version");
            csvTable.set(2, 0, "Target Version");
            csvTable.set(3, 0, "Update Source");

            for (int i = 0; i < objects.size(); i++) {
                csvTable.set(0, i + 1, objects.get(i).fileName.get());
                csvTable.set(1, i + 1, objects.get(i).currentVersion.get());
                csvTable.set(2, i + 1, objects.get(i).targetVersion.get());
                csvTable.set(3, i + 1, objects.get(i).source.get());
            }

            csvTable.write(path);

            FXUtils.showFileInExplorer(path);
        }).whenComplete(Schedulers.javafx(), exception -> {
            if (exception == null) {
                Controllers.dialog(path.toString(), i18n("message.success"));
            } else {
                Controllers.dialog("", i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }), i18n("button.export"), TaskCancellationAction.NORMAL);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    private static final class AddonUpdateObject {
        final AddonUpdate data;

        /// The remote version selected by the user as the update target, always one of [AddonUpdate#availableVersions()].
        final ObjectProperty<RemoteAddon.Version> selectedVersion = new SimpleObjectProperty<>();

        final BooleanProperty enabled = new SimpleBooleanProperty();
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();
        final StringProperty targetVersionType = new SimpleStringProperty();
        final StringProperty source = new SimpleStringProperty();

        final ItemPropertyAsyncCache<Image, AddonUpdateObject> iconCache;

        public AddonUpdateObject(AddonUpdate data) {
            this.data = data;

            enabled.set(!data.localAddonFile().isDisabled());
            fileName.set(data.localAddonFile().getFileName());
            currentVersion.set(data.currentVersion().version());
            FXUtils.onChange(selectedVersion, version -> {
                if (version != null) {
                    targetVersion.set(version.version());
                    targetVersionType.set(version.versionType().name());
                }
            });
            selectedVersion.set(data.targetVersion());
            source.set(switch (data.currentVersion().source()) {
                case CURSEFORGE -> i18n("addon.curseforge");
                case MODRINTH -> i18n("addon.modrinth");
            });

            iconCache = new ItemPropertyAsyncCache.Soft<>(
                this,
                this::loadIcon,
                this::getDefaultIcon
            );
        }

        public AddonUpdate getData() {
            return data.withTargetVersion(selectedVersion.get());
        }

        public boolean isEnabled() {
            return enabled.get();
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName.set(fileName);
        }

        public LocalAddonFile getLocalAddonFile() {
            return data.localAddonFile();
        }

        public String getCurrentVersion() {
            return currentVersion.get();
        }

        public StringProperty currentVersionProperty() {
            return currentVersion;
        }

        public StringProperty targetVersionProperty() {
            return targetVersion;
        }

        public StringProperty targetVersionTypeProperty() {
            return targetVersionType;
        }

        public StringProperty sourceProperty() {
            return source;
        }

        public void setSource(String source) {
            this.source.set(source);
        }

        private Image getDefaultIcon() {
            if (data.localAddonFile() instanceof LocalModFile localModFile) {
                return GameInstanceIconType
                    .getIconType(localModFile.getModLoaderType())
                    .getIcon();
            }

            return null;
        }

        private Image loadIcon() {
            if (data.localAddonFile() instanceof LocalModFile localModFile) {
                List<String> iconPaths = new ArrayList<>();

                if (StringUtils.isNotBlank(localModFile.getLogoPath())) {
                    iconPaths.add(localModFile.getLogoPath());
                }

                try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(localModFile.getFile())) {

                    for (String path : iconPaths) {
                        Path iconPath = fs.getPath(path);

                        if (Files.exists(iconPath)) {
                            Image image = FXUtils.loadImage(
                                iconPath,
                                80,
                                80,
                                true,
                                true
                            );

                            if (!image.isError()
                                && image.getWidth() > 0
                                && image.getHeight() > 0
                                && Math.abs(image.getWidth() - image.getHeight()) < 1
                            ) {
                                return image;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to load addon icons", e);
                }
            }
            
            if (data.localAddonFile() instanceof ResourcePackFile resourcePackFile) {
                Image image = resourcePackFile.loadIcon();

                if (image != null
                    && !image.isError()
                    && image.getWidth() > 0
                    && image.getHeight() > 0
                    && Math.abs(image.getWidth() - image.getHeight()) < 1
                ) {
                    return image;
                }
            }

            return getDefaultIcon();
        }
    }

    private static class AddonUpdateListCell extends ListCell<AddonUpdateObject> {
        private final StackPane pane = new StackPane();
        private final RipplerContainer ripplerContainer;

        private final JFXCheckBox enabledBox = new JFXCheckBox();
        private final ImageContainer imageContainer = new ImageContainer(40);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton changelogButton = FXUtils.newToggleButton4(SVG.INFO);

        AddonUpdateListCell() {
            HBox hbox = new HBox(16);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setAlignment(Pos.CENTER);
            hbox.setAlignment(Pos.CENTER);

            hbox.getChildren().setAll(enabledBox, imageContainer, content, changelogButton);

            pane.getStyleClass().add("md-list-cell");
            StackPane.setMargin(hbox, new Insets(10, 16, 10, 16));

            ripplerContainer = new RipplerContainer(hbox);
            pane.getChildren().setAll(ripplerContainer);

            enabledBox.setMouseTransparent(false);
            imageContainer.setMouseTransparent(true);

            FXUtils.installFastTooltip(changelogButton, i18n("addon.changelog"));
            changelogButton.setOnAction(event -> {
                AddonUpdateObject item = getItem();
                if (item != null) {
                    Controllers.dialog(new AddonChangelog(item));
                }
            });

            FXUtils.onClicked(this, () -> {
                AddonUpdateObject item = getItem();
                if (item != null) {
                    item.enabled.set(!item.isEnabled());
                }
            });
        }

        @Override
        public void updateItem(AddonUpdateObject item, boolean empty) {
            AddonUpdateObject oldItem = getItem();

            if (oldItem != null) {
                enabledBox.selectedProperty().unbindBidirectional(oldItem.enabledProperty());
                content.subtitleProperty().unbind();
            }

            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            enabledBox.selectedProperty().bindBidirectional(item.enabledProperty());

            item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

            LocalAddonFile addonFile = item.getLocalAddonFile();

            if (oldItem != item) {
                content.getTags().clear();

                if (addonFile instanceof LocalModFile modFile) {
                    content.addTag(modFile.getId());
                    content.addTag(item.source.get());
                } else if (addonFile instanceof ResourcePackFile) {
                    content.addTag(item.source.get());
                }
            }

            if (addonFile instanceof LocalModFile modFile) {
                content.setTitle(modFile.getName());
            } else if (addonFile instanceof ResourcePackFile resourcePackFile) {
                content.setTitle(resourcePackFile.getFileName());
            }

            // `targetVersion` and `targetVersionType` syncs with `currentVersion`, so only bind `currentVersion`
            content.subtitleProperty().bind(Bindings.createStringBinding(
                    () -> item.getCurrentVersion()
                            + " → "
                            + item.targetVersion.get()
                            + " | "
                            + item.targetVersionType.get(),
                    item.currentVersionProperty()));

            setGraphic(pane);
        }
    }

    private static final class AddonChangelog extends JFXDialogLayout {
        /// Changelogs already loaded by this dialog, keyed by version id. Accessed from the IO threads too.
        private final Map<String, String> changelogCache = new ConcurrentHashMap<>();
        /// ID of the most recently started changelog request. Only the result of that request may update the UI.
        @FXThread
        private int changelogRequestId = 0;
        /// ID of the most recently started version page request. Only the result of that request may update the UI.
        @FXThread
        private int versionPageRequestId = 0;

        public AddonChangelog(AddonUpdateObject object) {
            this.setHeading(new Label(i18n("addon.select_target_version")));

            VBox box = new VBox(8);
            box.setPadding(new Insets(8));

            JFXComboBox<RemoteAddon.Version> versionComboBox = new JFXComboBox<>();
            versionComboBox.getItems().setAll(object.data.availableVersions());
            versionComboBox.setConverter(FXUtils.stringConverter(RemoteAddon.Version::name));
            versionComboBox.getSelectionModel().select(object.selectedVersion.get());
            versionComboBox.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(versionComboBox, Priority.ALWAYS);

            HBox versionSelector = new HBox(8, new Label(i18n("addon.changelog.select_version")), versionComboBox);
            versionSelector.setAlignment(Pos.CENTER_LEFT);
            versionSelector.setPadding(new Insets(0, 0, 4, 0));

            SpinnerPane spinnerPane = new SpinnerPane();
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            FXUtils.setOverflowHidden(scrollPane, 8);

            spinnerPane.setContent(scrollPane);
            box.getChildren().addAll(versionSelector, spinnerPane);
            VBox.setVgrow(spinnerPane, Priority.SOMETIMES);

            this.setBody(box);

            JFXHyperlink versionPageBtn = new JFXHyperlink(i18n("mods.url"));
            versionPageBtn.setDisable(true);

            loadChangelog(versionComboBox.getSelectionModel().getSelectedItem(), spinnerPane, scrollPane);
            loadVersionPageUrl(versionComboBox.getSelectionModel().getSelectedItem(), versionPageBtn);

            spinnerPane.setOnFailedAction(e -> {
                RemoteAddon.Version version = versionComboBox.getSelectionModel().getSelectedItem();
                loadChangelog(version, spinnerPane, scrollPane);
                loadVersionPageUrl(version, versionPageBtn);
            });

            versionComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldVersion, newVersion) -> {
                if (newVersion == null) return;

                object.selectedVersion.set(newVersion);
                loadChangelog(newVersion, spinnerPane, scrollPane);
                loadVersionPageUrl(newVersion, versionPageBtn);
            });

            JFXButton closeButton = new JFXButton(i18n("button.ok"));
            closeButton.getStyleClass().add("dialog-accept");
            closeButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            setActions(versionPageBtn, closeButton);

            this.prefWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.7));
            this.prefHeightProperty().bind(Controllers.getDecorator().contentHeightProperty().multiply(0.7));

            onEscPressed(this, closeButton::fire);
        }

        /// Loads and renders the changelog of `version` in `scrollPane`.
        ///
        /// Results of requests superseded by a newer selection are discarded, so that a slow request
        /// cannot overwrite the changelog of the version the user selected last.
        private void loadChangelog(RemoteAddon.Version version, SpinnerPane spinnerPane, ScrollPane scrollPane) {
            int requestId = ++changelogRequestId;
            RemoteAddonRepository repo = version.source().getRepository();

            spinnerPane.setLoading(true);
            Task.supplyAsync(() -> {
                String cached = changelogCache.get(version.versionId());
                if (cached != null) return cached;

                String html = StringUtils.convertToHtml(
                        repo.getAddonChangelog(DownloadProviders.getDownloadProvider(), version.projectId(), version.versionId()),
                        "238222".equals(version.projectId()));
                String text = html != null ? html : "";
                changelogCache.put(version.versionId(), text);
                return text;
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                // Comparing versions is not enough: switching A -> B -> A starts two requests for A,
                // and the older one may finish last.
                if (requestId != changelogRequestId) return;

                if (exception == null) {
                    String html = StringUtils.isNotBlank(result) ? result : i18n("addon.changelog.empty");
                    scrollPane.setContent(FXUtils.renderAddonChangelog(html, repo.getBaseUrl()));
                    FXUtils.smoothScrolling(scrollPane);
                    spinnerPane.setFailedReason(null);
                } else {
                    spinnerPane.setFailedReason(i18n("download.failed.refresh"));
                }
                spinnerPane.setLoading(false);
            }).start();
        }

        /// Loads the page URL of `version` into `button`, keeping the button disabled until the URL is available.
        ///
        /// Results of requests superseded by a newer selection are discarded.
        private void loadVersionPageUrl(RemoteAddon.Version version, JFXHyperlink button) {
            button.setDisable(true);
            int requestId = ++versionPageRequestId;
            Task.supplyAsync(() -> version.source().getRepository().getVersionPageUrl(version))
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (requestId != versionPageRequestId) {
                            return;
                        }

                        if (exception == null && StringUtils.isNotBlank(result)) {
                            button.setExternalLink(result);
                            button.setDisable(false);
                        } else {
                            // Keep the button disabled so that a stale link can never be opened.
                            button.setDisable(true);
                            LOG.warning("Failed to load addon version page url", exception);
                        }
                    }).start();
        }
    }
}
