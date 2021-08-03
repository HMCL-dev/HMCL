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
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.FloatListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final ListProperty<CurseAddon.LatestFile> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final CurseAddon addon;
    private final Profile.ProfileVersion version;
    private final DownloadCallback callback;

    public ModDownloadPage(CurseAddon addon, Profile.ProfileVersion version, DownloadCallback callback) {
        this.addon = addon;
        this.version = version;
        this.callback = callback;

        File versionJar = StringUtils.isNotBlank(version.getVersion())
                ? version.getProfile().getRepository().getVersionJar(version.getVersion())
                : null;

        Task.runAsync(() -> {
            if (StringUtils.isNotBlank(version.getVersion())) {
                Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
                if (gameVersion.isPresent()) {
                    List<CurseAddon.LatestFile> files = CurseModManager.getFiles(addon);
                    items.setAll(files.stream()
                            .filter(file -> file.getGameVersion().contains(gameVersion.get()))
                            .collect(Collectors.toList()));
                    return;
                }
            }
            List<CurseAddon.LatestFile> files = CurseModManager.getFiles(addon);
            items.setAll(files);
        }).start();

        this.state.set(State.fromTitle(i18n("mods.download.title", addon.getName())));
    }

    public CurseAddon getAddon() {
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

    public void download(CurseAddon.LatestFile file) {
        this.callback.download(version.getProfile(), version.getVersion(), file);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadPageSkin(this);
    }

    private static class ModDownloadPageSkin extends SkinBase<ModDownloadPage> {

        protected ModDownloadPageSkin(ModDownloadPage control) {
            super(control);

            BorderPane pane = new BorderPane();

            HBox descriptionPane = new HBox(8);
            descriptionPane.setAlignment(Pos.CENTER);
            pane.setTop(descriptionPane);
            descriptionPane.getStyleClass().add("card");
            BorderPane.setMargin(descriptionPane, new Insets(11, 11, 0, 11));

            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setTitle(getSkinnable().addon.getName());
            content.setSubtitle(getSkinnable().addon.getSummary());
            content.getTags().setAll(getSkinnable().addon.getCategories().stream()
                    .map(category -> i18n("curse.category." + category.getCategoryId()))
                    .collect(Collectors.toList()));

            ImageView imageView = new ImageView();
            for (CurseAddon.Attachment attachment : getSkinnable().addon.getAttachments()) {
                if (attachment.isDefault()) {
                    imageView.setImage(new Image(attachment.getThumbnailUrl(), 40, 40, true, true, true));
                }
            }

            JFXButton openUrlButton = new JFXButton();
            openUrlButton.getStyleClass().add("toggle-icon4");
            openUrlButton.setGraphic(SVG.launchOutline(Theme.blackFillBinding(), -1, -1));
            openUrlButton.setOnAction(e -> FXUtils.openLink(getSkinnable().addon.getWebsiteUrl()));

            descriptionPane.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content, openUrlButton);


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

                JFXListView<CurseAddon.LatestFile> listView = new JFXListView<>();
                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setCellFactory(x -> new FloatListCell<CurseAddon.LatestFile>() {
                    TwoLineListItem content = new TwoLineListItem();
                    StackPane graphicPane = new StackPane();

                    {
                        Region clippedContainer = (Region)listView.lookup(".clipped-container");
                        setPrefWidth(0);
                        HBox container = new HBox(8);
                        container.setAlignment(Pos.CENTER_LEFT);
                        pane.getChildren().add(container);
                        if (clippedContainer != null) {
                            maxWidthProperty().bind(clippedContainer.widthProperty());
                            prefWidthProperty().bind(clippedContainer.widthProperty());
                            minWidthProperty().bind(clippedContainer.widthProperty());
                        }

                        container.getChildren().setAll(graphicPane, content);
                    }

                    @Override
                    protected void updateControl(CurseAddon.LatestFile dataItem, boolean empty) {
                        if (empty) return;
                        content.setTitle(dataItem.getDisplayName());
                        content.getTags().setAll(dataItem.getGameVersion());

                        switch (dataItem.getReleaseType()) {
                            case 1: // release
                                graphicPane.getChildren().setAll(SVG.releaseCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.release"));
                                break;
                            case 2: // beta
                                graphicPane.getChildren().setAll(SVG.betaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                            case 3: // alpha
                                graphicPane.getChildren().setAll(SVG.alphaCircleOutline(Theme.blackFillBinding(), 24, 24));
                                content.getTags().add(i18n("version.game.snapshot"));
                                break;
                        }
                    }
                });

                listView.setOnMouseClicked(e -> {
                    if (listView.getSelectionModel().getSelectedIndex() < 0)
                        return;
                    CurseAddon.LatestFile selectedItem = listView.getSelectionModel().getSelectedItem();
                    getSkinnable().download(selectedItem);
                });
            }

            getChildren().setAll(pane);
        }
    }

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, CurseAddon.LatestFile file);
    }
}
