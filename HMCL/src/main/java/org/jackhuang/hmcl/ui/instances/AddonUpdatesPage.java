// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;

import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
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
import java.util.function.Consumer;
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
                csvTable.set(0, i + 1, objects.get(i).getFileName());
                csvTable.set(1, i + 1, objects.get(i).getCurrentVersion());
                csvTable.set(2, i + 1, objects.get(i).getTargetVersion());
                csvTable.set(3, i + 1, objects.get(i).getSource());
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
        final String source;

        final BooleanProperty enabled = new SimpleBooleanProperty();

        final ItemPropertyAsyncCache<Image, AddonUpdateObject> iconCache;

        public AddonUpdateObject(AddonUpdate data) {
            this.data = data;

            this.enabled.set(!data.localAddonFile().isDisabled());
            this.source = switch (data.currentVersion().source()) {
                case CURSEFORGE -> i18n("addon.curseforge");
                case MODRINTH -> i18n("addon.modrinth");
            };
            this.selectedVersion.set(data.targetVersion());

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
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

        public LocalAddonFile<?> getLocalAddonFile() {
            return data.localAddonFile();
        }

        public String getFileName() {
            return data.localAddonFile().getFileName();
        }

        public String getCurrentVersion() {
            return data.currentVersion().version();
        }

        public String getTargetVersion() {
            return selectedVersion.get().version();
        }

        public String getTargetVersionType() {
            return switch (selectedVersion.get().versionType()) {
                case Release -> i18n("addon.channel.release");
                case Beta -> i18n("addon.channel.beta");
                case Alpha -> i18n("addon.channel.alpha");
            };
        }

        public String getSource() {
            return source;
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
        private final JFXButton editButton = FXUtils.newToggleButton4(SVG.EDIT);

        /// Keeps the version type tag in sync when the user selects another target version in the dialog.
        private final ChangeListener<RemoteAddon.Version> selectedVersionListener =
                (observable, oldVersion, newVersion) -> {
                    AddonUpdateObject item = getItem();
                    if (item != null) {
                        updateTags(item);
                    }
                };

        AddonUpdateListCell() {
            HBox hbox = new HBox(16);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setAlignment(Pos.CENTER);
            hbox.setAlignment(Pos.CENTER);

            hbox.getChildren().setAll(enabledBox, imageContainer, content, editButton);

            pane.getStyleClass().add("md-list-cell");
            StackPane.setMargin(hbox, new Insets(10, 16, 10, 16));

            ripplerContainer = new RipplerContainer(hbox);
            pane.getChildren().setAll(ripplerContainer);

            enabledBox.setMouseTransparent(false);
            imageContainer.setMouseTransparent(true);

            FXUtils.installFastTooltip(editButton, i18n("addon.select_target_version"));
            editButton.setOnAction(event -> {
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

        /// Rebuilds the tags of `item`, including the channel of the currently selected target version.
        private void updateTags(AddonUpdateObject item) {
            content.getTags().clear();

            LocalAddonFile<?> addonFile = item.getLocalAddonFile();
            if (addonFile instanceof LocalModFile modFile) {
                content.addTag(modFile.getId());
            }
            content.addTag(item.getSource());
            content.addTag(item.getTargetVersionType());

            content.setSubtitle(item.getCurrentVersion() + " → " + item.getTargetVersion());
        }

        @Override
        public void updateItem(AddonUpdateObject item, boolean empty) {
            AddonUpdateObject oldItem = getItem();
            boolean oldEmpty = isEmpty();

            super.updateItem(item, empty);

            if (oldItem == item && oldEmpty == empty) {
                return;
            }

            ripplerContainer.resetFeedback();

            if (oldItem != null) {
                enabledBox.selectedProperty().unbindBidirectional(oldItem.enabledProperty());
                oldItem.selectedVersion.removeListener(selectedVersionListener);
                content.subtitleProperty().unbind();
            }

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            enabledBox.selectedProperty().bindBidirectional(item.enabledProperty());

            item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

            LocalAddonFile<?> addonFile = item.getLocalAddonFile();

            updateTags(item);

            if (addonFile instanceof LocalModFile modFile) {
                content.setTitle(modFile.getName());
            } else if (addonFile instanceof ResourcePackFile resourcePackFile) {
                content.setTitle(resourcePackFile.getFileName());
            }

            item.selectedVersion.addListener(selectedVersionListener);

            setGraphic(pane);
        }
    }

    private static final class AddonChangelog extends JFXDialogLayout {
        /// Maintained in this page instead of [AddonUpdateObject] to allow freeing cached changelogs after closing
        /// this page.
        private final Map<String, String> changelogCache = new ConcurrentHashMap<>();
        /// ID of the most recent request. Only the result of that request may update the UI.
        @FXThread
        private int changelogRequestId = 0;
        /// ID of the most recent request. Only the result of that request may update the UI.
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

            Consumer<RemoteAddon.Version> onSelectedVersionChange = (version) -> {
                loadChangelog(version, spinnerPane, scrollPane);
                loadVersionPageUrl(version, versionPageBtn);
            };

            spinnerPane.setOnFailedAction(e -> onSelectedVersionChange.accept(versionComboBox.getSelectionModel().getSelectedItem()));

            object.selectedVersion.bind(versionComboBox.getSelectionModel().selectedItemProperty());
            FXUtils.onChangeAndOperate(versionComboBox.getSelectionModel().selectedItemProperty(), onSelectedVersionChange);

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
            Task.supplyAsync(() ->
                    version.source().getRepository().getVersionPageUrl(version)
            ).whenComplete(Schedulers.javafx(), (result, exception) -> {
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
