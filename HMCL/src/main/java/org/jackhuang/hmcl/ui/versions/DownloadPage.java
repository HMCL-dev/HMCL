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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.DownloadManager;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final ListProperty<DownloadManager.Version> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final DownloadManager.Mod addon;
    private final ModTranslations.Mod mod;
    private final Profile.ProfileVersion version;
    private final DownloadCallback callback;
    private final DownloadListPage page;

    public DownloadPage(DownloadListPage page, DownloadManager.Mod addon, Profile.ProfileVersion version, @Nullable DownloadCallback callback) {
        this.page = page;
        this.addon = addon;
        this.mod = ModTranslations.getModByCurseForgeId(addon.getSlug());
        this.version = version;
        this.callback = callback;

        File versionJar = StringUtils.isNotBlank(version.getVersion())
                ? version.getProfile().getRepository().getVersionJar(version.getVersion())
                : null;

        setLoading(true);
        setFailed(false);
        Task.supplyAsync(() -> {
            if (StringUtils.isNotBlank(version.getVersion())) {
                Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
                if (gameVersion.isPresent()) {
                    return addon.getData().loadVersions()
                            .filter(file -> file.getGameVersions().contains(gameVersion.get()));
                }
            }
            return addon.getData().loadVersions();
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                items.setAll(result
                        .sorted(Comparator.comparing(DownloadManager.Version::getDatePublished).reversed())
                        .collect(Collectors.toList()));
                setFailed(false);
            } else {
                setFailed(true);
            }
            setLoading(false);
        }).start();

        this.state.set(State.fromTitle(addon.getTitle()));
    }

    public DownloadManager.Mod getAddon() {
        return addon;
    }

    public Profile.ProfileVersion getVersion() {
        return version;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public void download(DownloadManager.Version file) {
        if (this.callback == null) {
            saveAs(file);
        } else {
            this.callback.download(version.getProfile(), version.getVersion(), file);
        }
    }

    public void saveAs(DownloadManager.Version file) {
        String extension = StringUtils.substringAfterLast(file.getFile().getFilename(), '.');

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("button.save_as"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("file"), "*." + extension));
        fileChooser.setInitialFileName(file.getFile().getFilename());
        File dest = fileChooser.showSaveDialog(Controllers.getStage());
        if (dest == null) {
            return;
        }

        Controllers.taskDialog(
                new FileDownloadTask(NetworkUtils.toURL(file.getFile().getUrl()), dest).executor(true),
                i18n("message.downloading")
        );
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadPageSkin(this);
    }

    private static class ModDownloadPageSkin extends SkinBase<DownloadPage> {

        protected ModDownloadPageSkin(DownloadPage control) {
            super(control);

            BorderPane pane = new BorderPane();

            HBox descriptionPane = new HBox(8);
            descriptionPane.setAlignment(Pos.CENTER);
            pane.setTop(descriptionPane);
            descriptionPane.getStyleClass().add("card");
            BorderPane.setMargin(descriptionPane, new Insets(11, 11, 0, 11));

            ImageView imageView = new ImageView();
            if (StringUtils.isNotBlank(getSkinnable().addon.getIconUrl())) {
                imageView.setImage(new Image(getSkinnable().addon.getIconUrl(), 40, 40, true, true, true));
            }
            descriptionPane.getChildren().add(FXUtils.limitingSize(imageView, 40, 40));

            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            ModTranslations.Mod mod = ModTranslations.getModByCurseForgeId(getSkinnable().addon.getSlug());
            content.setTitle(mod != null ? mod.getDisplayName() : getSkinnable().addon.getTitle());
            content.setSubtitle(getSkinnable().addon.getDescription());
            content.getTags().setAll(getSkinnable().addon.getCategories().stream()
                    .map(category -> getSkinnable().page.getLocalizedCategory(category))
                    .collect(Collectors.toList()));
            descriptionPane.getChildren().add(content);

            if (getSkinnable().mod != null) {
                JFXHyperlink openMcmodButton = new JFXHyperlink(i18n("mods.mcmod"));
                openMcmodButton.setOnAction(e -> FXUtils.openLink(ModManager.getMcmodUrl(getSkinnable().mod.getMcmod())));
                descriptionPane.getChildren().add(openMcmodButton);

                if (StringUtils.isNotBlank(getSkinnable().mod.getMcbbs())) {
                    JFXHyperlink openMcbbsButton = new JFXHyperlink(i18n("mods.mcbbs"));
                    openMcbbsButton.setOnAction(e -> FXUtils.openLink(ModManager.getMcbbsUrl(getSkinnable().mod.getMcbbs())));
                    descriptionPane.getChildren().add(openMcbbsButton);
                }
            }

            JFXHyperlink openUrlButton = new JFXHyperlink(control.page.getLocalizedOfficialPage());
            openUrlButton.setOnAction(e -> FXUtils.openLink(getSkinnable().addon.getPageUrl()));
            descriptionPane.getChildren().add(openUrlButton);


            SpinnerPane spinnerPane = new SpinnerPane();
            pane.setCenter(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));

                JFXListView<DownloadManager.Version> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setCellFactory(x -> new FloatListCell<DownloadManager.Version>(listView) {
                    TwoLineListItem content = new TwoLineListItem();
                    StackPane graphicPane = new StackPane();
                    JFXButton saveAsButton = new JFXButton();

                    {
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);

                        saveAsButton.getStyleClass().add("toggle-icon4");
                        saveAsButton.setGraphic(SVG.contentSaveMoveOutline(Theme.blackFillBinding(), -1, -1));

                        HBox.setHgrow(content, Priority.ALWAYS);
                        container.getChildren().setAll(graphicPane, content, saveAsButton);
                    }

                    @Override
                    protected void updateControl(DownloadManager.Version dataItem, boolean empty) {
                        if (empty) return;
                        content.setTitle(dataItem.getName());
                        content.setSubtitle(FORMATTER.format(dataItem.getDatePublished()));
                        content.getTags().setAll(dataItem.getGameVersions());
                        saveAsButton.setOnMouseClicked(e -> getSkinnable().saveAs(dataItem));

                        switch (dataItem.getVersionType()) {
                            case Release:
                                graphicPane.getChildren().setAll(SVG.releaseCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.release"));
                                break;
                            case Beta:
                                graphicPane.getChildren().setAll(SVG.betaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                            case Alpha:
                                graphicPane.getChildren().setAll(SVG.alphaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                        }
                    }
                });

                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    DownloadManager.Version selectedItem = listView.getSelectionModel().getSelectedItem();
                    getSkinnable().download(selectedItem);
                });
            }

            getChildren().setAll(pane);
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, DownloadManager.Version file);
    }
}
