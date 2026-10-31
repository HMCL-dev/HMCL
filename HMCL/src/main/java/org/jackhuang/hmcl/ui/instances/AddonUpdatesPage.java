// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;

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
import org.jackhuang.hmcl.addon.shader.ShaderPackFile;
import org.jackhuang.hmcl.addon.update.AddonUpdate;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
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
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;

import java.lang.ref.WeakReference;

import org.jackhuang.hmcl.util.javafx.Subscription;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
        final String targetVersionType;
        final String source;
        String changelog = null;

        final BooleanProperty enabled = new SimpleBooleanProperty();

        final ItemPropertyAsyncCache<Image, AddonUpdateObject> iconCache;

        public AddonUpdateObject(AddonUpdate data) {
            this.data = data;

            this.targetVersionType = switch (data.targetVersion().versionType()) {
                case Release -> i18n("addon.channel.release");
                case Beta -> i18n("addon.channel.beta");
                case Alpha -> i18n("addon.channel.alpha");
            };
            this.source = switch (data.currentVersion().source()) {
                case CURSEFORGE -> i18n("addon.curseforge");
                case MODRINTH -> i18n("addon.modrinth");
            };

            this.enabled.set(!data.localAddonFile().isDisabled());

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        public AddonUpdate getData() {
            return data;
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
            return data.targetVersion().version();
        }

        public String getTargetVersionType() {
            return targetVersionType;
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

            if (data.localAddonFile() instanceof ShaderPackFile shaderPackFile) {
                return switch (shaderPackFile.getShaderLoaderType()) {
                    case OPTIFINE_IRIS -> FXUtils.newBuiltinImage("/assets/img/opti-iris.png");
                    default -> FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
                };
            }

            return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
        }

        private Image loadIcon() {
            Image icon = data.localAddonFile().loadIcon(80D, 80D, true, true);
            if (icon != null && !icon.isError() && icon.getWidth() > 0 && icon.getHeight() > 0 && Math.abs(icon.getWidth() - icon.getHeight()) < 1) {
                return icon;
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
            boolean oldEmpty = isEmpty();

            super.updateItem(item, empty);

            if (oldItem == item && oldEmpty == empty) {
                return;
            }

            ripplerContainer.resetFeedback();

            if (oldItem != null) {
                enabledBox.selectedProperty().unbindBidirectional(oldItem.enabledProperty());
            }

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            enabledBox.selectedProperty().bindBidirectional(item.enabledProperty());

            item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

            LocalAddonFile<?> addonFile = item.getLocalAddonFile();

            content.getTags().clear();
            if (addonFile instanceof LocalModFile modFile)
                content.addTag(modFile.getId());
            content.addTag(item.getSource());
            content.addTag(item.getTargetVersionType());

            content.setTitle(addonFile.getName());
            content.setSubtitle(item.getCurrentVersion() + " → " + item.getTargetVersion());

            setGraphic(pane);
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
            RemoteAddonRepository repo = object.data.targetVersion().source().getRepository();
            Task.supplyAsync(() -> {
                if (object.changelog != null) return object.changelog;
                RemoteAddon.Version version = object.data.targetVersion();
                return StringUtils.convertToHtml(
                        repo.getAddonChangelog(DownloadProviders.getDownloadProvider(), version.projectId(), version.versionId()),
                        "238222".equals(object.data.targetVersion().projectId())
                );
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception == null) {
                    object.changelog = StringUtils.isNotBlank(result) ? result : i18n("addon.changelog.empty");
                    scrollPane.setContent(FXUtils.renderAddonChangelog(object.changelog, repo.getBaseUrl()));
                    FXUtils.smoothScrolling(scrollPane);
                    spinnerPane.setFailedReason(null);
                } else {
                    spinnerPane.setFailedReason(i18n("download.failed.refresh"));
                }
                spinnerPane.setLoading(false);
            }).start();
        }

        private void loadVersionPageUrl(AddonUpdateObject object, JFXHyperlink button) {
            Task.supplyAsync(() ->
                    object.data.targetVersion().source().getRepository().getVersionPageUrl(object.data.targetVersion())
            ).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception == null && StringUtils.isNotBlank(result)) {
                    button.setExternalLink(result);
                    button.setDisable(false);
                } else {
                    LOG.warning("Failed to load addon version page url", exception);
                }
            }).start();
        }
    }
}
