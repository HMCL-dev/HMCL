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
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
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
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonUpdatesPage<F extends LocalAddonFile> extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("addon.check_update")));

    private final LocalAddonManager<F> localAddonManager;
    private final ObservableList<AddonUpdateObject> objects;

    public AddonUpdatesPage(LocalAddonManager<F> localAddonManager, List<LocalAddonFile.AddonUpdate> updates) {
        this.localAddonManager = localAddonManager;

        getStyleClass().add("gray-background");

        objects = FXCollections.observableList(updates.stream().map(AddonUpdateObject::new).collect(Collectors.toList()));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        ListView<AddonUpdateObject> listView = new ListView<>(objects);
        listView.getStyleClass().add("no-horizontal-scrollbar");
        listView.setStyle("-fx-background-color: transparent;");
        listView.setCellFactory(x -> new ListCell<>() {
            private static final Insets PADDING = new Insets(3, 9, 0, 9);
            private static final Insets LAST_PADDING = new Insets(3, 9, 3, 9);

            private final StackPane wrapper = new StackPane();
            private final HBox container = new HBox(8);

            private AddonUpdateObject boundItem;

            private final JFXCheckBox enabledBox = new JFXCheckBox();
            private final ImageContainer imageContainer = new ImageContainer(40);
            private final TwoLineListItem content = new TwoLineListItem();
            private final Hyperlink changelog = new Hyperlink();

            {
                setPadding(PADDING);

                container.setPadding(new Insets(8));
                container.setAlignment(Pos.CENTER_LEFT);
                container.setOnMouseClicked(event -> {
                    if (event.getTarget() != enabledBox) {
                        AddonUpdateObject item = getItem();
                        if (item != null) {
                            item.setEnabled(!item.isEnabled());
                        }
                    }
                });

                imageContainer.setMouseTransparent(true);
                enabledBox.setMouseTransparent(false);

                container.getChildren().setAll(
                    enabledBox,
                    imageContainer,
                    content,
                    changelog
                );

                HBox.setHgrow(content, Priority.ALWAYS);

                wrapper.getChildren().setAll(container);
                wrapper.getStyleClass().add("card-no-padding");

                changelog.setText(i18n("addon.changelog"));
                changelog.setOnAction(event -> {
                    AddonUpdateObject item = getItem();
                    if (item != null) {
                        Controllers.dialog(new AddonChangelog(item));
                    }
                });

                setGraphic(wrapper);
            }

            @Override
            protected void updateItem(AddonUpdateObject item, boolean empty) {
                if (boundItem != null) {
                    enabledBox.selectedProperty().unbindBidirectional(boundItem.enabledProperty());
                    boundItem = null;
                }

                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                boundItem = item;

                enabledBox.selectedProperty().bindBidirectional(item.enabledProperty());

                setPadding(
                    getIndex() == getListView().getItems().size() - 1 ? LAST_PADDING : PADDING
                );

                item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

                if (item.getAddonInfo() instanceof LocalModFile modFile) {
                    content.setTitle(modFile.getName());

                    if (content.getTags().isEmpty()) {
                        content.addTag(modFile.getId());
                        content.addTag(item.getSource());
                    }
                }

                if (item.getAddonInfo() instanceof ResourcePackFile resourcePackFile) {
                    content.setTitle(resourcePackFile.getFileName());

                    if (content.getTags().isEmpty()) {
                        content.addTag(item.getSource());
                    }
                }

                content.setSubtitle(
                    item.getCurrentVersion()
                    + " → "
                    + item.getTargetVersion()
                );

                setGraphic(wrapper);
            }
        });

        scrollPane.setContent(listView);

        setCenter(scrollPane);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXCheckBox allEnabledBox = new JFXCheckBox();
        allEnabledBox.setText(i18n("button.select_all"));
        FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(), objects.stream().map(o -> o.enabled).toArray(BooleanProperty[]::new));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        JFXButton exportListButton = FXUtils.newRaisedButton(i18n("button.export"));
        exportListButton.setOnAction(e -> exportList());

        JFXButton nextButton = FXUtils.newRaisedButton(i18n("addon.check_update.confirm"));
        nextButton.setOnAction(e -> updateFiles());

        JFXButton cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        actions.getChildren().setAll(allEnabledBox, spacer, exportListButton, nextButton, cancelButton);
        setBottom(actions);
    }

    private <T> void setupCellValueFactory(TableColumn<AddonUpdateObject, T> column, Function<AddonUpdateObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> mapper.apply(param.getValue()));
    }

    private void updateFiles() {
        AddonUpdateTask task = new AddonUpdateTask(
                localAddonManager.getDirectory(),
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
        final LocalAddonFile.AddonUpdate data;
        final BooleanProperty enabled = new SimpleBooleanProperty();
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();
        final StringProperty source = new SimpleStringProperty();
        String changelog = null;

        final ItemPropertyAsyncCache<Image, AddonUpdateObject> iconCache;

        public AddonUpdateObject(LocalAddonFile.AddonUpdate data) {
            this.data = data;

            enabled.set(!data.localAddonFile().isDisabled());
            fileName.set(data.localAddonFile().getFileName());
            currentVersion.set(data.currentVersion().version());
            targetVersion.set(data.targetVersion().version());
            switch (data.currentVersion().self().getSource()) {
                case CURSEFORGE:
                    source.set(i18n("addon.curseforge"));
                    break;
                case MODRINTH:
                    source.set(i18n("addon.modrinth"));
            }

            iconCache = new ItemPropertyAsyncCache.Soft<>(
                this,
                this::loadIcon,
                this::getDefaultIcon
            );

        }

        public LocalAddonFile.AddonUpdate getData() {
            return data;
        }

        public boolean isEnabled() {
            return enabled.get();
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled.set(enabled);
        }

        public String getFileName() {
            return fileName.get();
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName.set(fileName);
        }

        public LocalAddonFile getAddonInfo() {
            return data.localAddonFile();
        }

        public String getCurrentVersion() {
            return currentVersion.get();
        }

        public StringProperty currentVersionProperty() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion.set(currentVersion);
        }

        public String getTargetVersion() {
            return targetVersion.get();
        }

        public StringProperty targetVersionProperty() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion.set(targetVersion);
        }

        public String getSource() {
            return source.get();
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

    private static final class AddonChangelog extends JFXDialogLayout {

        public AddonChangelog(AddonUpdateObject object) {
            RemoteAddon.Version targetVersion = object.data.targetVersion();

            this.setHeading(new HBox(new Label(i18n("addon.changelog") + " - " + targetVersion.name())));

            VBox box = new VBox(8);
            box.setPadding(new Insets(8));

            SpinnerPane spinnerPane = new SpinnerPane();
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            FXUtils.setOverflowHidden(scrollPane, 8);

            loadChangelog(object, spinnerPane, scrollPane);
            spinnerPane.setOnFailedAction(e -> loadChangelog(object, spinnerPane, scrollPane));

            spinnerPane.setContent(scrollPane);
            box.getChildren().add(spinnerPane);
            VBox.setVgrow(spinnerPane, Priority.SOMETIMES);

            this.setBody(box);

            JFXHyperlink versionPageBtn = new JFXHyperlink(i18n("mods.url"));
            versionPageBtn.setDisable(true);
            loadVersionPageUrl(object, versionPageBtn);

            JFXButton closeButton = new JFXButton(i18n("button.ok"));
            closeButton.getStyleClass().add("dialog-accept");
            closeButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            setActions(versionPageBtn, closeButton);

            this.prefWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.7));
            this.prefHeightProperty().bind(Controllers.getDecorator().contentHeightProperty().multiply(0.7));

            onEscPressed(this, closeButton::fire);
        }

        private void loadChangelog(AddonUpdateObject object, SpinnerPane spinnerPane, ScrollPane scrollPane) {
            spinnerPane.setLoading(true);
            RemoteAddonRepository repo = object.data.source().getRepoForType(object.data.repoType());
            Task.supplyAsync(() -> {
                if (object.changelog != null) {
                    return object.changelog;
                }
                RemoteAddon.Version version = object.data.targetVersion();
                if (repo == null) return null;
                return StringUtils.convertToHtml(
                        repo.getAddonChangelog(DownloadProviders.getDownloadProvider(), version.projectId(), version.versionId()),
                        "238222".equals(object.data.targetVersion().projectId())
                );
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception == null) {
                    object.changelog = StringUtils.isNotBlank(result) ? result : i18n("addon.changelog.empty");
                    scrollPane.setContent(FXUtils.renderAddonChangelog(object.changelog, repo == null ? "" : repo.getBaseUrl()));
                    FXUtils.smoothScrolling(scrollPane);
                    spinnerPane.setFailedReason(null);
                } else {
                    spinnerPane.setFailedReason(i18n("download.failed.refresh"));
                }
                spinnerPane.setLoading(false);
            }).start();
        }

        private void loadVersionPageUrl(AddonUpdateObject object, JFXHyperlink button) {
            Task.supplyAsync(() -> {
                RemoteAddonRepository repo = object.data.source().getRepoForType(object.data.repoType());
                return repo == null ? null : repo.getVersionPageUrl(object.data.targetVersion());
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception == null && StringUtils.isNotBlank(result)) {
                    button.setExternalLink(result);
                    button.setDisable(false);
                } else {
                    LOG.warning("Failed to load addon version page url", exception);
                }
            }).start();
        }
    }

    public static class AddonUpdateTask extends Task<Void> {
        private final Collection<Task<?>> dependents;
        private final List<LocalAddonFile> failedAddons = new ArrayList<>();

        AddonUpdateTask(Path addonDirectory, List<LocalAddonFile.AddonUpdate> addons) {
            setStage("addon.check_update.confirm");
            getProperties().put("total", addons.size());

            this.dependents = new ArrayList<>();
            for (LocalAddonFile.AddonUpdate addon : addons) {
                LocalAddonFile local = addon.localAddonFile();
                RemoteAddon.Version remote = addon.targetVersion();
                boolean isDisabled = local.isDisabled();
                String originalFileName = local.getFile().getFileName().toString();

                dependents.add(Task
                        .runAsync(Schedulers.javafx(), () -> local.setOld(true))
                        .thenComposeAsync(() -> {
                            String fileName = addon.useRemoteFileName() ? remote.file().filename() : originalFileName;
                            if (isDisabled)
                                fileName = StringUtils.addSuffix(fileName, LocalAddonManager.DISABLED_EXTENSION);

                            var task = new FileDownloadTask(
                                    remote.file().url(),
                                    addonDirectory.resolve(fileName)
                            );

                            task.setName(remote.name());
                            return task;
                        })
                        .whenComplete(Schedulers.javafx(), exception -> {
                            if (exception != null) {
                                // restore state if failed
                                local.setOld(false);
                                if (isDisabled)
                                    local.markDisabled();
                                failedAddons.add(local);
                            } else if (!local.keepOldFiles()) {
                                try {
                                    local.delete();
                                } catch (IOException e) {
                                    LOG.warning("Failed to delete outdated addon: " + local.getFile(), e);
                                }
                            }
                        })
                        .withCounter("addon.check_update.confirm"));
            }
        }

        public List<LocalAddonFile> getFailedAddons() {
            return failedAddons;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return dependents;
        }

        @Override
        public boolean doPreExecute() {
            return true;
        }

        @Override
        public void preExecute() {
            notifyPropertiesChanged();
        }

        @Override
        public boolean isRelyingOnDependents() {
            return false;
        }

        @Override
        public void execute() throws Exception {
            if (!isDependentsSucceeded())
                throw getException();
        }
    }
}
