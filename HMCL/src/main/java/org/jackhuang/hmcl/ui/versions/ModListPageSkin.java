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
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
final class ModListPageSkin extends ToolbarListPageSkin<ModListPageSkin.ModInfoObject, ModListPage> {

    ModListPageSkin(ModListPage skinnable) {
        super(skinnable, true);

        listView.setCellFactory(x -> new ModInfoListCell(listView));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        listView.setOnContextMenuRequested(event -> {
            ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
            if (listView.getSelectionModel().getSelectedItems().size() == 1) {
                listView.getSelectionModel().clearSelection();
                Controllers.dialog(new ModInfoDialog(selectedItem));
            }
        });

        setupSkin(
                new Node[]{
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("mods.add"), SVG.ADD, skinnable::add),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(listView.getItems().stream().map(ModInfoObject::getModInfo).toList())
                        ),
                        createToolbarButton2(i18n("download"), SVG.DOWNLOAD, skinnable::download),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, this::startSearch)
                },
                new Node[]{
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                            }, null);
                        }),
                        createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                                skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                                skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(listView.getSelectionModel().getSelectedItems().stream().map(ModInfoObject::getModInfo).toList())
                        )
                }
        );

        Label notModdedLabel = new Label(i18n("mods.not_modded"));
        notModdedLabel.prefWidthProperty().bind(mainContainer.widthProperty().add(-100));

        FXUtils.onChangeAndOperate(skinnable.moddedProperty(), modded -> {
            if (modded) mainContainer.getChildren().setAll(rootList);
            else mainContainer.getChildren().setAll(notModdedLabel);
        });
    }

    @Override
    protected Predicate<ModInfoObject> updateSearchPredicate(String queryString) {
        if (StringUtils.isBlank(queryString)) return item -> true;
        try {
            Predicate<@Nullable String> predicate = StringUtils.compileQuery(queryString);
            return item -> {
                LocalModFile modInfo = item.getModInfo();
                return predicate.test(modInfo.getFileName())
                        || predicate.test(modInfo.getName())
                        || predicate.test(modInfo.getVersion())
                        || predicate.test(modInfo.getGameVersion())
                        || predicate.test(modInfo.getId())
                        || predicate.test(Objects.toString(modInfo.getModLoaderType()))
                        || predicate.test((item.getModTranslations() != null ? item.getModTranslations().getDisplayName() : null));
            };
        } catch (Throwable e) {
            LOG.warning("Illegal regular expression", e);
            return item -> true;
        }
    }

    @Override
    protected String getEmptyPlaceholderText() {
        return i18n("mods.empty");
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

        public void loadIcon(ImageContainer imageContainer, @Nullable WeakReference<ObjectProperty<ModInfoObject>> current) {
            SoftReference<CompletableFuture<Image>> iconCache = this.iconCache;
            CompletableFuture<Image> imageFuture;
            if (iconCache != null && (imageFuture = iconCache.get()) != null) {
                Image image = imageFuture.getNow(null);
                if (image != null) {
                    imageContainer.setImage(image);
                    return;
                }
            } else {
                imageFuture = CompletableFuture.supplyAsync(this::loadIcon, Schedulers.io());
                this.iconCache = new SoftReference<>(imageFuture);
            }
            imageContainer.setImage(VersionIconType.getIconType(localModFile.getModLoaderType()).getIcon());
            imageFuture.thenAcceptAsync(image -> {
                if (current != null) {
                    ObjectProperty<ModInfoObject> infoObjectProperty = current.get();
                    if (infoObjectProperty == null || infoObjectProperty.get() != this) {
                        // The current ListCell has already switched to another object
                        return;
                    }
                }

                imageContainer.setImage(image);
            }, Schedulers.javafx());
        }
    }

    final class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            titleContainer.setPadding(new Insets(0, 0, 12, 0));

            DoubleBinding widthBinding = Controllers.windowWidthProperty().multiply(0.7);
            prefWidthProperty().bind(widthBinding);
            maxWidthProperty().bind(widthBinding);

            var imageContainer = new ImageContainer(40);
            titleContainer.setAlignment(Pos.CENTER_LEFT);
            modInfo.loadIcon(imageContainer, null);

            TwoLineListItem title = new TwoLineListItem();
            if (modInfo.getModTranslations() != null && I18n.isUseChinese())
                title.setTitle(modInfo.getModTranslations().getDisplayName());
            else
                title.setTitle(modInfo.getModInfo().getName());

            StringJoiner subtitle = new StringJoiner("\n");
            subtitle.add(i18n("archive.file.name") + ": " + FileUtils.getName(modInfo.getModInfo().getFile()));
            if (StringUtils.isNotBlank(modInfo.getModInfo().getGameVersion())) {
                subtitle.add(i18n("mods.game.version") + ": " + modInfo.getModInfo().getGameVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                subtitle.add(i18n("archive.version") + ": " + modInfo.getModInfo().getVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getAuthors())) {
                subtitle.add(i18n("archive.author") + ": " + modInfo.getModInfo().getAuthors());
            }
            title.setSubtitle(subtitle.toString());

            titleContainer.getChildren().setAll(imageContainer, title);
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
                double maxHeight = Controllers.windowHeightProperty().get() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);

            if (StringUtils.isNotBlank(modInfo.getModInfo().getId())) {
                for (Pair<String, ? extends RemoteAddonRepository> item : Arrays.asList(
                        pair("addon.curseforge", CurseForgeRemoteAddonRepository.MODS),
                        pair("addon.modrinth", ModrinthRemoteAddonRepository.MODS)
                )) {
                    RemoteAddonRepository repository = item.getValue();
                    JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                    Task.runAsync(() -> {
                        Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(modInfo.getModInfo().getFile());
                        if (versionOptional.isPresent()) {
                            RemoteAddon remoteAddon = repository.getModById(DownloadProviders.getDownloadProvider(), versionOptional.get().modid());
                            FXUtils.runInFX(() -> {
                                for (ModLoaderType modLoaderType : versionOptional.get().loaders()) {
                                    String loaderName = switch (modLoaderType) {
                                        case FORGE -> i18n("install.installer.forge");
                                        case CLEANROOM -> i18n("install.installer.cleanroom");
                                        case LEGACY_FABRIC -> i18n("install.installer.legacyfabric");
                                        case NEO_FORGE -> i18n("install.installer.neoforge");
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
                                            repository instanceof CurseForgeRemoteAddonRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                            remoteAddon,
                                            new HMCLGameRepository.InstanceReference(ModListPageSkin.this.getSkinnable().getRepository(), ModListPageSkin.this.getSkinnable().getInstanceId()),
                                            org.jackhuang.hmcl.ui.download.DownloadPage.FOR_MOD
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
        ImageContainer imageContainer = new ImageContainer(32);
        TwoLineListItem content = new TwoLineListItem();
        JFXButton restoreButton = FXUtils.newToggleButton4(SVG.RESTORE);
        JFXButton infoButton = FXUtils.newToggleButton4(SVG.INFO);
        JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER);
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

            imageContainer.setImage(VersionIconType.COMMAND.getIcon());

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            container.getChildren().setAll(checkBox, imageContainer, content, restoreButton, revealButton, infoButton);

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

            dataItem.loadIcon(imageContainer, new WeakReference<>(this.itemProperty()));

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
                    case NEO_FORGE -> content.addTagWarning(i18n("install.installer.neoforge"));
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
