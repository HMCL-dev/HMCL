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

import com.jfoenix.controls.*;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.isNotBlank;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

class ModListPageSkin extends SkinBase<ModListPage> {

    private final TransitionPane toolbarPane;
    private final HBox searchBar;
    private final HBox toolbarNormal;
    private final HBox toolbarSelecting;

    private final JFXListView<ModInfoObject> listView;
    private final JFXTextField searchField;

    // FXThread
    private boolean isSearching = false;

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable);

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        listView = new JFXListView<>();

        {
            toolbarPane = new TransitionPane();

            searchBar = new HBox();
            toolbarNormal = new HBox();
            toolbarSelecting = new HBox();

            // Search Bar
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchField.setOnAction(e -> search());

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(toolbarNormal);

                        isSearching = false;
                        searchField.clear();
                        Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                    });

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            // Toolbar Normal
            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("mods.add"), SVG.PLUS, skinnable::add),
                    createToolbarButton2(i18n("folder.mod"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                    createToolbarButton2(i18n("mods.check_updates"), SVG.UPDATE, skinnable::checkUpdates),
                    createToolbarButton2(i18n("download"), SVG.DOWNLOAD_OUTLINE, skinnable::download),
                    createToolbarButton2(i18n("search"), SVG.MAGNIFY, () -> changeToolbar(searchBar))
            );

            // Toolbar Selecting
            toolbarSelecting.getChildren().setAll(
                    createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                            skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                        }, null);
                    }),
                    createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                            skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                            skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                            listView.getSelectionModel().selectAll()),
                    createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem == null)
                            changeToolbar(isSearching ? searchBar : toolbarNormal);
                        else
                            changeToolbar(toolbarSelecting);
                    });
            root.getContent().add(toolbarPane);
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(skinnable.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new ModInfoListCell(listView, lastCell));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());
            skinnable.getItems().addListener((ListChangeListener<? super ModInfoObject>) c -> {
                if (isSearching) {
                    search();
                }
            });

            center.setContent(listView);
            root.getContent().add(center);
        }

        Label label = new Label(i18n("mods.not_modded"));
        label.prefWidthProperty().bind(pane.widthProperty().add(-100));

        FXUtils.onChangeAndOperate(skinnable.moddedProperty(), modded -> {
            if (modded) pane.getChildren().setAll(root);
            else pane.getChildren().setAll(label);
        });

        getChildren().setAll(pane);
    }

    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE.getAnimationProducer());
            if (newToolbar == searchBar) {
                searchField.requestFocus();
            }
        }
    }

    private void search() {
        isSearching = true;

        Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            listView.getItems().setAll(getSkinnable().getItems());
        } else {
            listView.getItems().clear();

            Predicate<String> predicate;
            if (queryString.startsWith("regex:")) {
                try {
                    Pattern pattern = Pattern.compile(queryString.substring("regex:".length()));
                    predicate = s -> pattern.matcher(s).find();
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }
            } else {
                String lowerQueryString = queryString.toLowerCase(Locale.ROOT);
                predicate = s -> s.toLowerCase(Locale.ROOT).contains(lowerQueryString);
            }

            // Do we need to search in the background thread?
            for (ModInfoObject item : getSkinnable().getItems()) {
                if (predicate.test(item.getModInfo().getFileName())) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    static class ModInfoObject extends RecursiveTreeObject<ModInfoObject> implements Comparable<ModInfoObject> {
        private final BooleanProperty active;
        private final LocalModFile localModFile;
        private final String title;
        private final String message;
        private final ModTranslations.Mod mod;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();

            StringBuilder title = new StringBuilder(localModFile.getName());
            if (isNotBlank(localModFile.getVersion()))
                title.append(" ").append(localModFile.getVersion());
            this.title = title.toString();

            StringBuilder message = new StringBuilder(localModFile.getFileName());
            if (isNotBlank(localModFile.getGameVersion()))
                message.append(", ").append(i18n("game.version")).append(": ").append(localModFile.getGameVersion());
            if (isNotBlank(localModFile.getAuthors()))
                message.append(", ").append(i18n("archive.author")).append(": ").append(localModFile.getAuthors());
            this.message = message.toString();

            this.mod = ModTranslations.MOD.getModById(localModFile.getId());
        }

        String getTitle() {
            return title;
        }

        String getSubtitle() {
            return message;
        }

        LocalModFile getModInfo() {
            return localModFile;
        }

        public ModTranslations.Mod getMod() {
            return mod;
        }

        @Override
        public int compareTo(@NotNull ModListPageSkin.ModInfoObject o) {
            return localModFile.getFileName().toLowerCase().compareTo(o.localModFile.getFileName().toLowerCase());
        }
    }

    class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            ImageView imageView = new ImageView();
            if (StringUtils.isNotBlank(modInfo.getModInfo().getLogoPath())) {
                Task.supplyAsync(() -> {
                    try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modInfo.getModInfo().getFile())) {
                        Path iconPath = fs.getPath(modInfo.getModInfo().getLogoPath());
                        if (Files.exists(iconPath)) {
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            Files.copy(iconPath, stream);
                            return new ByteArrayInputStream(stream.toByteArray());
                        }
                    }
                    return null;
                }).whenComplete(Schedulers.javafx(), (stream, exception) -> {
                    if (stream != null) {
                        imageView.setImage(new Image(stream, 40, 40, true, true));
                    } else {
                        imageView.setImage(FXUtils.newBuiltinImage("/assets/img/command.png", 40, 40, true, true));
                    }
                }).start();
            }

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(modInfo.getModInfo().getName());
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                title.getTags().setAll(modInfo.getModInfo().getVersion());
            }
            title.setSubtitle(FileUtils.getName(modInfo.getModInfo().getFile()));

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(modInfo.getModInfo().getDescription().toString());
            setBody(description);

            if (StringUtils.isNotBlank(modInfo.getModInfo().getId())) {
                for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                        pair("mods.curseforge", CurseForgeRemoteModRepository.MODS),
                        pair("mods.modrinth", ModrinthRemoteModRepository.MODS)
                )) {
                    RemoteModRepository repository = item.getValue();
                    JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                    Task.runAsync(() -> {
                        Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(modInfo.getModInfo(), modInfo.getModInfo().getFile());
                        if (versionOptional.isPresent()) {
                            RemoteMod remoteMod = repository.getModById(versionOptional.get().getModid());
                            FXUtils.runInFX(() -> {
                                for (ModLoaderType modLoaderType : versionOptional.get().getLoaders()) {
                                    String loaderName;
                                    switch (modLoaderType) {
                                        case FORGE:
                                            loaderName = i18n("install.installer.forge");
                                            break;
                                        case NEO_FORGED:
                                            loaderName = i18n("install.installer.neoforge");
                                            break;
                                        case FABRIC:
                                            loaderName = i18n("install.installer.fabric");
                                            break;
                                        case LITE_LOADER:
                                            loaderName = i18n("install.installer.liteloader");
                                            break;
                                        case QUILT:
                                            loaderName = i18n("install.installer.quilt");
                                            break;
                                        default:
                                            continue;
                                    }
                                    List<String> tags = title.getTags();
                                    if (!tags.contains(loaderName)) {
                                        tags.add(loaderName);
                                    }
                                }

                                button.setOnAction(e -> {
                                    fireEvent(new DialogCloseEvent());
                                    Controllers.navigate(new DownloadPage(
                                            repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                            remoteMod,
                                            new Profile.ProfileVersion(ModListPageSkin.this.getSkinnable().getProfile(), ModListPageSkin.this.getSkinnable().getVersionId()),
                                            null
                                    ));
                                });
                                button.setDisable(false);
                            });
                        }
                    }).start();
                    button.setDisable(true);
                    getActions().add(button);
                }
            }

            if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                JFXHyperlink officialPageButton = new JFXHyperlink(i18n("mods.url"));
                officialPageButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(modInfo.getModInfo().getUrl());
                });

                getActions().add(officialPageButton);
            }

            if (modInfo.getMod() == null || StringUtils.isBlank(modInfo.getMod().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", modInfo.getModInfo().getName()),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getMod()));
                });
                getActions().add(mcmodButton);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    private static final Lazy<PopupMenu> menu = new Lazy<>(PopupMenu::new);
    private static final Lazy<JFXPopup> popup = new Lazy<>(() -> new JFXPopup(menu.get()));

    final class ModInfoListCell extends MDListCell<ModInfoObject> {
        JFXCheckBox checkBox = new JFXCheckBox();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton restoreButton = new JFXButton();
        JFXButton infoButton = new JFXButton();
        JFXButton revealButton = new JFXButton();
        BooleanProperty booleanProperty;

        ModInfoListCell(JFXListView<ModInfoObject> listView, Holder<Object> lastCell) {
            super(listView, lastCell);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            restoreButton.getStyleClass().add("toggle-icon4");
            restoreButton.setGraphic(FXUtils.limitingSize(SVG.RESTORE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(FXUtils.limitingSize(SVG.FOLDER_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.INFORMATION_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));

            container.getChildren().setAll(checkBox, content, restoreButton, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            if (empty) return;
            content.setTitle(dataItem.getTitle());
            content.getTags().clear();
            switch (dataItem.getModInfo().getModLoaderType()) {
                case FORGE:
                    content.getTags().add(i18n("install.installer.forge"));
                    break;
                case NEO_FORGED:
                    content.getTags().add(i18n("install.installer.neoforge"));
                    break;
                case FABRIC:
                    content.getTags().add(i18n("install.installer.fabric"));
                    break;
                case LITE_LOADER:
                    content.getTags().add(i18n("install.installer.liteloader"));
                    break;
                case QUILT:
                    content.getTags().add(i18n("install.installer.quilt"));
                    break;
            }
            if (dataItem.getMod() != null && I18n.isUseChinese()) {
                content.getTags().add(dataItem.getMod().getDisplayName());
            }
            content.setSubtitle(dataItem.getSubtitle());
            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            restoreButton.setVisible(!dataItem.getModInfo().getMod().getOldFiles().isEmpty());
            restoreButton.setOnMouseClicked(e -> {
                menu.get().getContent().setAll(dataItem.getModInfo().getMod().getOldFiles().stream()
                        .map(localModFile -> new IconedMenuItem(null, localModFile.getVersion(),
                                () -> getSkinnable().rollback(dataItem.getModInfo(), localModFile),
                                popup.get()))
                        .collect(Collectors.toList())
                );

                popup.get().show(restoreButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, restoreButton.getHeight());
            });
            revealButton.setOnMouseClicked(e -> {
                FXUtils.showFileInExplorer(dataItem.getModInfo().getFile());
            });
            infoButton.setOnMouseClicked(e -> {
                Controllers.dialog(new ModInfoDialog(dataItem));
            });
        }
    }
}
