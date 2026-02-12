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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ModListPageSkin extends SkinBase<ModListPage> {

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
        listView.getStyleClass().add("no-horizontal-scrollbar");

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
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(e -> search());
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                pause.setRate(1);
                pause.playFromStart();
            });

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(toolbarNormal);

                        isSearching = false;
                        searchField.clear();
                        Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                    });

            onEscPressed(searchField, closeSearchBar::fire);

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            // Toolbar Normal
            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("mods.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                    createToolbarButton2(i18n("mods.check_updates.button"), SVG.UPDATE, () ->
                            skinnable.checkUpdates(
                                    listView.getItems().stream()
                                            .map(ModInfoObject::getModInfo)
                                            .toList()
                            )
                    ),
                    createToolbarButton2(i18n("download"), SVG.DOWNLOAD, skinnable::download),
                    createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
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
                    createToolbarButton2(i18n("mods.check_updates.button"), SVG.UPDATE, () ->
                            skinnable.checkUpdates(
                                    listView.getSelectionModel().getSelectedItems().stream()
                                            .map(ModInfoObject::getModInfo)
                                            .toList()
                            )
                    ),
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

            // Clear selection when pressing ESC
            root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
        }

        {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(skinnable.loadingProperty());

            listView.setCellFactory(x -> new ModInfoListCell(listView));
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            Bindings.bindContent(listView.getItems(), skinnable.getItems());
            skinnable.getItems().addListener((ListChangeListener<? super ModInfoObject>) c -> {
                if (isSearching) {
                    search();
                }
            });

            listView.setOnContextMenuRequested(event -> {
                ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                    listView.getSelectionModel().clearSelection();
                    Controllers.dialog(new ModInfoDialog(selectedItem));
                }
            });

            // ListViewBehavior would consume ESC pressed event, preventing us from handling it
            // So we ignore it here
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

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
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                Platform.runLater(searchField::requestFocus);
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

            Predicate<@Nullable String> predicate;
            if (queryString.startsWith("regex:")) {
                try {
                    Pattern pattern = Pattern.compile(queryString.substring("regex:".length()));
                    predicate = s -> s != null && pattern.matcher(s).find();
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }
            } else {
                String lowerQueryString = queryString.toLowerCase(Locale.ROOT);
                predicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerQueryString);
            }

            // Do we need to search in the background thread?
            for (ModInfoObject item : getSkinnable().getItems()) {
                LocalModFile modInfo = item.getModInfo();
                if (predicate.test(modInfo.getFileName())
                        || predicate.test(modInfo.getName())
                        || predicate.test(modInfo.getVersion())
                        || predicate.test(modInfo.getGameVersion())
                        || predicate.test(modInfo.getId())
                        || predicate.test(Objects.toString(modInfo.getModLoaderType()))
                        || predicate.test((item.getModTranslations() != null ? item.getModTranslations().getDisplayName() : null))) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    static final class ModInfoObject {
        private final BooleanProperty active;
        private final LocalModFile localModFile;
        private final @Nullable ModTranslations.Mod modTranslations;

        private SoftReference<CompletableFuture<Image>> iconCache;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();

            this.modTranslations = ModTranslations.MOD.getMod(localModFile.getId(), localModFile.getName());
        }

        LocalModFile getModInfo() {
            return localModFile;
        }

        public @Nullable ModTranslations.Mod getModTranslations() {
            return modTranslations;
        }

        @FXThread
        private Image loadIcon() {
            List<String> iconPaths = new ArrayList<>();

            if (StringUtils.isNotBlank(this.localModFile.getLogoPath())) {
                iconPaths.add(this.localModFile.getLogoPath());
            }

            iconPaths.addAll(List.of(
                    "icon.png",
                    "logo.png",
                    "mod_logo.png",
                    "pack.png",
                    "logoFile.png",
                    "assets/icon.png",
                    "assets/logo.png",
                    "assets/mod_icon.png",
                    "assets/mod_logo.png",
                    "META-INF/icon.png",
                    "META-INF/logo.png",
                    "META-INF/mod_icon.png",
                    "textures/icon.png",
                    "textures/logo.png",
                    "textures/mod_icon.png",
                    "resources/icon.png",
                    "resources/logo.png",
                    "resources/mod_icon.png"
            ));

            String modId = this.localModFile.getId();
            if (StringUtils.isNotBlank(modId)) {
                iconPaths.addAll(List.of(
                        "assets/" + modId + "/icon.png",
                        "assets/" + modId + "/logo.png",
                        "assets/" + modId.replace("-", "") + "/icon.png",
                        "assets/" + modId.replace("-", "") + "/logo.png",
                        modId + ".png",
                        modId + "-logo.png",
                        modId + "-icon.png",
                        modId + "_logo.png",
                        modId + "_icon.png",
                        "textures/" + modId + "/icon.png",
                        "textures/" + modId + "/logo.png",
                        "resources/" + modId + "/icon.png",
                        "resources/" + modId + "/logo.png"
                ));
            }

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(this.localModFile.getFile())) {
                for (String path : iconPaths) {
                    Path iconPath = fs.getPath(path);
                    if (Files.exists(iconPath)) {
                        Image image = FXUtils.loadImage(iconPath, 80, 80, true, true);
                        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0 &&
                                Math.abs(image.getWidth() - image.getHeight()) < 1) {
                            return image;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to load mod icons", e);
            }

            return VersionIconType.getIconType(this.localModFile.getModLoaderType()).getIcon();
        }

        public void loadIcon(ImageView imageView, @Nullable WeakReference<ObjectProperty<ModInfoObject>> current) {
            SoftReference<CompletableFuture<Image>> iconCache = this.iconCache;
            CompletableFuture<Image> imageFuture;
            if (iconCache != null && (imageFuture = iconCache.get()) != null) {
                Image image = imageFuture.getNow(null);
                if (image != null) {
                    imageView.setImage(image);
                    return;
                }
            } else {
                imageFuture = CompletableFuture.supplyAsync(this::loadIcon, Schedulers.io());
                this.iconCache = new SoftReference<>(imageFuture);
            }
            imageView.setImage(VersionIconType.getIconType(localModFile.getModLoaderType()).getIcon());
            imageFuture.thenAcceptAsync(image -> {
                if (current != null) {
                    ObjectProperty<ModInfoObject> infoObjectProperty = current.get();
                    if (infoObjectProperty == null || infoObjectProperty.get() != this) {
                        // The current ListCell has already switched to another object
                        return;
                    }
                }

                imageView.setImage(image);
            }, Schedulers.javafx());
        }
    }

    final class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            ImageView imageView = new ImageView();
            FXUtils.limitSize(imageView, 40, 40);
            modInfo.loadIcon(imageView, null);

            TwoLineListItem title = new TwoLineListItem();
            if (modInfo.getModTranslations() != null && I18n.isUseChinese())
                title.setTitle(modInfo.getModTranslations().getDisplayName());
            else
                title.setTitle(modInfo.getModInfo().getName());

            StringJoiner subtitle = new StringJoiner(" | ");
            subtitle.add(FileUtils.getName(modInfo.getModInfo().getFile()));
            if (StringUtils.isNotBlank(modInfo.getModInfo().getGameVersion())) {
                subtitle.add(modInfo.getModInfo().getGameVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                subtitle.add(modInfo.getModInfo().getVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getAuthors())) {
                subtitle.add(i18n("archive.author") + ": " + modInfo.getModInfo().getAuthors());
            }
            title.setSubtitle(subtitle.toString());

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(modInfo.getModInfo().getDescription().toString());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            description.heightProperty().addListener((obs, oldVal, newVal) -> {
                double maxHeight = stage.getHeight() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);

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
                                    String loaderName = switch (modLoaderType) {
                                        case FORGE -> i18n("install.installer.forge");
                                        case CLEANROOM -> i18n("install.installer.cleanroom");
                                        case LEGACY_FABRIC -> i18n("install.installer.legacyfabric");
                                        case NEO_FORGED -> i18n("install.installer.neoforge");
                                        case FABRIC -> i18n("install.installer.fabric");
                                        case LITE_LOADER -> i18n("install.installer.liteloader");
                                        case QUILT -> i18n("install.installer.quilt");
                                        default -> null;
                                    };
                                    if (loaderName == null)
                                        continue;
                                    if (title.getTags()
                                            .stream()
                                            .noneMatch(it -> it.getText().equals(loaderName))) {
                                        title.addTag(loaderName);
                                    }
                                }

                                button.setOnAction(e -> {
                                    fireEvent(new DialogCloseEvent());
                                    Controllers.navigate(new DownloadPage(
                                            repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                            remoteMod,
                                            new Profile.ProfileVersion(ModListPageSkin.this.getSkinnable().getProfile(), ModListPageSkin.this.getSkinnable().getInstanceId()),
                                            (profile, version, mod, file) -> org.jackhuang.hmcl.ui.download.DownloadPage.download(profile, version, file, "mods")
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

            if (modInfo.getModTranslations() == null || StringUtils.isBlank(modInfo.getModTranslations().getMcmod())) {
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
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getModTranslations()));
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
        private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

        JFXCheckBox checkBox = new JFXCheckBox();
        ImageView imageView = new ImageView();
        TwoLineListItem content = new TwoLineListItem();
        JFXButton restoreButton = new JFXButton();
        JFXButton infoButton = new JFXButton();
        JFXButton revealButton = new JFXButton();
        BooleanProperty booleanProperty;

        Tooltip warningTooltip;

        ModInfoListCell(JFXListView<ModInfoObject> listView) {
            super(listView);

            this.getStyleClass().add("mod-info-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);
            imageView.setImage(VersionIconType.COMMAND.getIcon());

            restoreButton.getStyleClass().add("toggle-icon4");
            restoreButton.setGraphic(SVG.RESTORE.createIcon());

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            revealButton.getStyleClass().add("toggle-icon4");
            revealButton.setGraphic(SVG.FOLDER.createIcon());

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(SVG.INFO.createIcon());

            container.getChildren().setAll(checkBox, imageView, content, restoreButton, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            pseudoClassStateChanged(WARNING, false);
            if (warningTooltip != null) {
                Tooltip.uninstall(this, warningTooltip);
                warningTooltip = null;
            }

            if (empty) return;

            List<String> warning = new ArrayList<>();

            content.getTags().clear();

            LocalModFile modInfo = dataItem.getModInfo();
            ModTranslations.Mod modTranslations = dataItem.getModTranslations();

            ModLoaderType modLoaderType = modInfo.getModLoaderType();

            dataItem.loadIcon(imageView, new WeakReference<>(this.itemProperty()));

            String displayName = modInfo.getName();
            if (modTranslations != null && I18n.isUseChinese()) {
                String chineseName = modTranslations.getName();
                if (StringUtils.containsChinese(chineseName)) {
                    if (StringUtils.containsEmoji(chineseName)) {
                        StringBuilder builder = new StringBuilder();

                        chineseName.codePoints().forEach(ch -> {
                            if (ch < 0x1F300 || ch > 0x1FAFF)
                                builder.appendCodePoint(ch);
                        });

                        chineseName = builder.toString().trim();
                    }

                    if (StringUtils.isNotBlank(chineseName) && !displayName.equalsIgnoreCase(chineseName)) {
                        displayName = displayName + " (" + chineseName + ")";
                    }
                }
            }
            content.setTitle(displayName);

            StringJoiner joiner = new StringJoiner(" | ");
            if (modLoaderType != ModLoaderType.UNKNOWN && StringUtils.isNotBlank(modInfo.getId()))
                joiner.add(modInfo.getId());

            joiner.add(FileUtils.getName(modInfo.getFile()));

            content.setSubtitle(joiner.toString());

            if (modLoaderType == ModLoaderType.UNKNOWN) {
                content.addTagWarning(i18n("mods.unknown"));
            } else if (!ModListPageSkin.this.getSkinnable().supportedLoaders.contains(modLoaderType)) {
                warning.add(i18n("mods.warning.loader_mismatch"));
                switch (dataItem.getModInfo().getModLoaderType()) {
                    case FORGE -> content.addTagWarning(i18n("install.installer.forge"));
                    case LEGACY_FABRIC -> content.addTagWarning(i18n("install.installer.legacyfabric"));
                    case CLEANROOM -> content.addTagWarning(i18n("install.installer.cleanroom"));
                    case NEO_FORGED -> content.addTagWarning(i18n("install.installer.neoforge"));
                    case FABRIC -> content.addTagWarning(i18n("install.installer.fabric"));
                    case LITE_LOADER -> content.addTagWarning(i18n("install.installer.liteloader"));
                    case QUILT -> content.addTagWarning(i18n("install.installer.quilt"));
                }
            }

            String modVersion = modInfo.getVersion();
            if (StringUtils.isNotBlank(modVersion) && !"${version}".equals(modVersion)) {
                content.addTag(modVersion);
            }

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            restoreButton.setVisible(!modInfo.getMod().getOldFiles().isEmpty());
            restoreButton.setOnAction(e -> {
                menu.get().getContent().setAll(modInfo.getMod().getOldFiles().stream()
                        .map(localModFile -> new IconedMenuItem(null, localModFile.getVersion(),
                                () -> getSkinnable().rollback(modInfo, localModFile),
                                popup.get()))
                        .toList()
                );

                popup.get().show(restoreButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, restoreButton.getHeight());
            });
            revealButton.setOnAction(e -> FXUtils.showFileInExplorer(modInfo.getFile()));
            infoButton.setOnAction(e -> Controllers.dialog(new ModInfoDialog(dataItem)));

            if (!warning.isEmpty()) {
                pseudoClassStateChanged(WARNING, true);

                //noinspection ConstantValue
                this.warningTooltip = warning.size() == 1
                        ? new Tooltip(warning.get(0))
                        : new Tooltip(String.join("\n", warning));
                FXUtils.installFastTooltip(this, warningTooltip);
            }
        }
    }
}
