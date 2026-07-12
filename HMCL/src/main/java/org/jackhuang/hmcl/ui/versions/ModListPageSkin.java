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
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.*;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.NestedJarInspector;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
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
import java.util.stream.Collectors;

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

    // Keeps the download page's cached installed-mods map in sync when a mod is enabled/disabled,
    // so dependency installation status there stays correct without a re-scan. Fires on the FX
    // thread (active changes come from user toggles), matching DownloadPage's cache-mutation contract.
    private final InvalidationListener activeChangeListener = observable -> {
        if (observable instanceof BooleanProperty property && property.getBean() instanceof LocalModFile mod) {
            // A mod id may have several files (versions); it counts as enabled while ANY is enabled.
            boolean anyActive = mod.getMod().getFiles().stream().anyMatch(LocalModFile::isActive);
            DownloadPage.setModActive(
                    new HMCLGameRepository.InstanceReference(getSkinnable().getRepository(), getSkinnable().getInstanceId()),
                    mod.getId(), anyActive);
        }
    };

    @FXThread
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
                    createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
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

            // reason for not using selectAll() is that selectAll() first clears all selected then selects all, causing the toolbar to flicker
            var selectAll = createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));

            ListChangeListener<Object> listener = change -> {
                selectAll.setDisable(!listView.getItems().isEmpty()
                        && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
            };

            listView.getSelectionModel().getSelectedItems().addListener(listener);
            listView.getItems().addListener(listener);

            toolbarSelecting.getChildren().setAll(
                    createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                        var selected = listView.getSelectionModel().getSelectedItems();
                        List<LocalModFile> targets = new ArrayList<>();
                        for (ModInfoObject item : selected)
                            if (item != null) targets.add(item.getModInfo());
                        List<ModInfoObject> dependents = findActiveDependents(targets);
                        if (dependents.isEmpty()) {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                                    () -> skinnable.removeSelected(selected), null);
                        } else {
                            List<ModInfoObject> selectedSnapshot = new ArrayList<>(selected);
                            Controllers.dialog(new DependencyWarningDialog(dependents, i18n("button.remove"), List.of(
                                    new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                                            () -> skinnable.removeSelected(FXCollections.observableArrayList(selectedSnapshot))),
                                    new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                                        for (ModInfoObject dependent : dependents)
                                            dependent.getModInfo().setActive(false);
                                        skinnable.removeSelected(FXCollections.observableArrayList(selectedSnapshot));
                                    }),
                                    new CascadeOption(i18n("addon.dependencies.warning.cascade.delete"), () -> {
                                        List<ModInfoObject> all = new ArrayList<>(selectedSnapshot);
                                        all.addAll(dependents);
                                        skinnable.removeSelected(FXCollections.observableArrayList(all));
                                    })
                            )));
                        }
                    }),
                    createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                            skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                    createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () -> {
                        var selected = listView.getSelectionModel().getSelectedItems();
                        List<LocalModFile> targets = new ArrayList<>();
                        for (ModInfoObject item : selected)
                            if (item != null) targets.add(item.getModInfo());
                        List<ModInfoObject> dependents = findActiveDependents(targets);
                        if (dependents.isEmpty()) {
                            skinnable.disableSelected(selected);
                        } else {
                            Controllers.dialog(new DependencyWarningDialog(dependents, i18n("mods.disable"), List.of(
                                    new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                                            () -> skinnable.disableSelected(selected)),
                                    new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                                        skinnable.disableSelected(selected);
                                        for (ModInfoObject dependent : dependents)
                                            dependent.getModInfo().setActive(false);
                                    })
                            )));
                        }
                    }),
                    createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                            skinnable.checkUpdates(
                                    listView.getSelectionModel().getSelectedItems().stream()
                                            .map(ModInfoObject::getModInfo)
                                            .toList()
                            )
                    ),
                    selectAll,
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

            FXUtils.setOverflowHidden(toolbarPane, 8);

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
                while (c.next()) {
                    for (ModInfoObject removed : c.getRemoved())
                        removed.active.removeListener(activeChangeListener);
                    for (ModInfoObject added : c.getAddedSubList())
                        added.active.addListener(activeChangeListener);
                }
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
            try {
                predicate = StringUtils.compileQuery(queryString);
            } catch (Throwable e) {
                LOG.warning("Illegal regular expression", e);
                return;
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
                        || predicate.test((item.getModTranslations() != null ? item.getModTranslations().getDisplayName() : null))
                        || modInfo.getBundledMods().stream().anyMatch(predicate)) {
                    listView.getItems().add(item);
                }
            }
        }
    }

    // Finds enabled mods (other than the targets themselves) that would lose a required dependency
    // if the targets are disabled/removed — i.e. mods that may break.
    //
    // Two subtleties are handled:
    //   * Last provider only: an id is treated as lost only when *every* still-enabled file providing
    //     it is among those being removed. If another enabled file also provides the id, the
    //     dependency is still satisfied and its dependents are left alone.
    //   * Transitive closure: once a dependent is scheduled for disabling, the ids it alone provided
    //     become lost too, cascading to whatever depended on them (A ← B ← C disables all of B, C).
    //
    // Matching is by exact mod id. A mod that satisfies a dependency under a different "provides"
    // alias (we don't parse "provides") won't be matched, so this errs on the side of fewer
    // warnings rather than false ones.
    private List<ModInfoObject> findActiveDependents(Collection<LocalModFile> targets) {
        // id -> all currently-enabled files providing it. A file provides its own id and, via
        // Jar-in-Jar, every mod bundled anywhere inside it (disabling the host removes those too).
        Map<String, Set<LocalModFile>> activeProviders = new HashMap<>();
        for (ModInfoObject item : getSkinnable().getItems()) {
            LocalModFile mod = item.getModInfo();
            if (!mod.isActive())
                continue;
            if (StringUtils.isNotBlank(mod.getId()))
                activeProviders.computeIfAbsent(mod.getId(), k -> new HashSet<>()).add(mod);
            for (String bundledId : mod.getAllBundledModIds())
                activeProviders.computeIfAbsent(bundledId, k -> new HashSet<>()).add(mod);
        }

        // Files that will end up gone: seed with the targets, then grow with dependents whose
        // required ids lose their last provider — iterating to a fixpoint for the transitive case.
        Set<LocalModFile> doomed = new HashSet<>(targets);
        List<ModInfoObject> dependents = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;

            Set<String> lostIds = new HashSet<>();
            for (Map.Entry<String, Set<LocalModFile>> entry : activeProviders.entrySet()) {
                if (doomed.containsAll(entry.getValue()))
                    lostIds.add(entry.getKey());
            }
            if (lostIds.isEmpty())
                break;

            for (ModInfoObject item : getSkinnable().getItems()) {
                LocalModFile mod = item.getModInfo();
                if (!mod.isActive() || doomed.contains(mod))
                    continue;
                if (mod.getDependencies().stream().anyMatch(lostIds::contains)) {
                    dependents.add(item);
                    doomed.add(mod);
                    changed = true;
                }
            }
        }
        return dependents;
    }

    static final class ModInfoObject {
        private final BooleanProperty active;
        // Whether the nested (Jar-in-Jar) mods of this entry are expanded in the list.
        // Stored on the data object so the state survives ListCell recycling.
        private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
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

        BooleanProperty expandedProperty() {
            return expanded;
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

            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            var imageContainer = new ImageContainer(40);
            modInfo.loadIcon(imageContainer, null);

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
                double maxHeight = stage.getHeight() * 0.5;
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

    // A selectable "what to do with the dependent mods" choice. action performs the full operation
    // (always acting on the target mods, plus optionally on the dependents).
    private record CascadeOption(String label, Runnable action) {
        @Override
        public String toString() {
            return label;
        }
    }

    final class DependencyWarningDialog extends JFXDialogLayout {

        DependencyWarningDialog(List<ModInfoObject> dependents, String confirmText, List<CascadeOption> options) {
            setHeading(new Label(i18n("addon.dependencies.warning.title")));

            Label message = new Label(i18n("addon.dependencies.warning"));
            message.setWrapText(true);

            ComponentList list = new ComponentList();
            list.getStyleClass().add("no-padding");
            for (ModInfoObject dependent : dependents) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8));
                row.setMouseTransparent(true);

                ImageContainer icon = new ImageContainer(32);
                dependent.loadIcon(icon, null);

                TwoLineListItem content = new TwoLineListItem();
                HBox.setHgrow(content, Priority.ALWAYS);
                content.setTitle(dependent.getModTranslations() != null && I18n.isUseChinese()
                        ? dependent.getModTranslations().getDisplayName()
                        : dependent.getModInfo().getName());
                StringJoiner subtitle = new StringJoiner(" | ");
                if (StringUtils.isNotBlank(dependent.getModInfo().getId()))
                    subtitle.add(dependent.getModInfo().getId());
                subtitle.add(FileUtils.getName(dependent.getModInfo().getFile()));
                content.setSubtitle(subtitle.toString());

                row.getChildren().setAll(icon, content);
                list.getContent().add(row);
            }

            ScrollPane scrollPane = new ScrollPane(list);
            scrollPane.setFitToWidth(true);
            scrollPane.setMaxHeight(300);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            setBody(new VBox(10, message, scrollPane));

            JFXComboBox<CascadeOption> cascade = new JFXComboBox<>();
            cascade.getItems().setAll(options);
            cascade.getSelectionModel().selectFirst();

            HBox cascadeBox = new HBox(8, new Label(i18n("addon.dependencies.warning.cascade.label")), cascade);
            cascadeBox.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            JFXButton confirmButton = new JFXButton(confirmText);
            confirmButton.getStyleClass().add("dialog-accept");
            confirmButton.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
                CascadeOption selected = cascade.getValue();
                if (selected != null)
                    selected.action().run();
            });

            getActions().setAll(cascadeBox, spacer, cancelButton, confirmButton);
            onEscPressed(this, cancelButton::fire);
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
        JFXButton expandButton = FXUtils.newToggleButton4(SVG.KEYBOARD_ARROW_DOWN);
        VBox nestedBox = new VBox();
        private final Rectangle nestedClip = new Rectangle();
        private Timeline nestedAnimation;
        // Listeners the nested panel attaches to dependency-provider activeProperties so their
        // (installed/disabled) status labels refresh live; removed whenever the panel is torn down.
        private final List<Runnable> nestedListenerCleanups = new ArrayList<>();
        // Suppresses the expand animation while a cell is being recycled to another item.
        private boolean suppressExpandAnimation;
        BooleanProperty booleanProperty;
        // Mirrors the bound ModInfoObject's expanded state, so the chevron and the
        // nested list react to it while this cell is showing that item.
        private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
        private BooleanProperty expandedBinding;

        Tooltip warningTooltip;

        ModInfoListCell(JFXListView<ModInfoObject> listView) {
            super(listView);

            this.getStyleClass().add("mod-info-list-cell");

            HBox header = new HBox(8);
            header.setPickOnBounds(false);
            header.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            // Allow the title to shrink/ellipsize instead of pushing the buttons out of view.
            content.setMinWidth(0);
            content.setMouseTransparent(true);
            setSelectable();

            imageContainer.setImage(VersionIconType.COMMAND.getIcon());

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));
            FXUtils.installFastTooltip(expandButton, i18n("addon.relations"));
            // Don't reserve layout space for the restore button when it is hidden,
            // otherwise it leaves a gap between the expand button and the other buttons.
            restoreButton.managedProperty().bind(restoreButton.visibleProperty());

            // The chevron rotation is animated together with the expansion (see animateExpansion);
            // its angle is set directly in snapExpansion when a cell is recycled.
            expandButton.getGraphic().setRotate(0);
            // Toggle on press. Use a bubbling handler (not a filter) so the button's own ripple
            // (triggered deeper/earlier) still plays for click feedback, then consume the event so
            // it never reaches the ListView's selection handler — expanding must not select the row.
            expandButton.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                expanded.set(!expanded.get());
                e.consume();
            });

            // When the background Jar-in-Jar deep scan finishes, rebuild this panel if it is open so the
            // temporary filename rows are replaced by the resolved nested-mod names/versions.
            getSkinnable().bundledScanGenerationProperty().addListener((InvalidationListener) o -> {
                if (expanded.get() && getItem() != null)
                    rebuildNested(getItem());
            });

            // Warn before disabling a mod that other enabled mods depend on.
            checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                ModInfoObject item = getItem();
                if (item == null || !checkBox.isSelected())
                    return; // enabling (or empty) — nothing to warn about
                List<LocalModFile> targets = List.of(item.getModInfo());
                List<ModInfoObject> dependents = findActiveDependents(targets);
                if (!dependents.isEmpty()) {
                    e.consume(); // block the immediate toggle; let the user confirm first
                    LocalModFile target = item.getModInfo();
                    Controllers.dialog(new DependencyWarningDialog(dependents, i18n("mods.disable"), List.of(
                            new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                                    () -> target.setActive(false)),
                            new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                                target.setActive(false);
                                for (ModInfoObject dependent : dependents)
                                    dependent.getModInfo().setActive(false);
                            })
                    )));
                }
            });

            nestedBox.getStyleClass().add("mod-nested-list");
            nestedBox.setVisible(false);
            nestedBox.setManaged(false);
            // Crop overflow while the box height is animating, so rows don't spill out.
            nestedClip.widthProperty().bind(nestedBox.widthProperty());
            nestedClip.heightProperty().bind(nestedBox.heightProperty());
            nestedBox.setClip(nestedClip);
            // Animate open/close on real user toggles; recycling snaps without animation.
            expanded.addListener((obs, was, now) -> {
                if (!suppressExpandAnimation)
                    animateExpansion(now);
            });

            header.getChildren().setAll(checkBox, imageContainer, content, expandButton, restoreButton, revealButton, infoButton);

            VBox container = new VBox(header, nestedBox);
            container.setPickOnBounds(false);
            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        private Node createNestedRow(String path, int indent) {
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

            Label label = new Label(name);
            HBox.setHgrow(label, Priority.ALWAYS);
            if (!name.equals(path))
                label.setTooltip(new Tooltip(path));

            HBox row = new HBox(8, SVG.STACKS.createIcon(16), label);
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            if (indent > 0)
                row.getChildren().add(0, indentSpacer(indent));
            return row;
        }

        /// Renders a level of the Jar-in-Jar tree, recursing into children (indented). Copies of the
        /// same mod for different Minecraft versions (multi-version "wrapper" jars) are grouped into a
        /// single row that highlights the copy matching this instance, so a 15-version wrapper reads as
        /// one entry instead of fifteen.
        private void appendBundledNodes(List<NestedJarInspector.NestedJar> nodes, int indent, String instanceMc) {
            // Group by id; nodes without a parsed id stay ungrouped (rendered as-is afterwards).
            LinkedHashMap<String, List<NestedJarInspector.NestedJar>> groups = new LinkedHashMap<>();
            List<NestedJarInspector.NestedJar> ungrouped = new ArrayList<>();
            for (NestedJarInspector.NestedJar node : nodes) {
                if (node.id() != null && !node.id().isBlank())
                    groups.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(node);
                else
                    ungrouped.add(node);
            }

            for (List<NestedJarInspector.NestedJar> versions : groups.values()) {
                if (versions.size() == 1) {
                    NestedJarInspector.NestedJar node = versions.get(0);
                    nestedBox.getChildren().add(createBundledRow(node, indent, null, null, matchesInstance(node, instanceMc)));
                    if (node.hasChildren())
                        appendBundledNodes(node.children(), indent + 1, instanceMc);
                } else {
                    // Multi-version: pick the copy that matches this instance (else the first) as the
                    // representative, and only recurse into it — the others are the same mod. The badge's
                    // tooltip lists every collapsed copy so the rest stay discoverable.
                    NestedJarInspector.NestedJar rep = versions.stream()
                            .filter(v -> matchesInstance(v, instanceMc)).findFirst().orElse(versions.get(0));
                    boolean active = matchesInstance(rep, instanceMc);
                    String tooltip = versions.stream()
                            .map(v -> v.version() != null && !v.version().isBlank() ? v.version() : v.fileName())
                            .collect(Collectors.joining("\n"));
                    nestedBox.getChildren().add(createBundledRow(rep, indent,
                            i18n("addon.bundled.versions", versions.size()), tooltip, active));
                    if (rep.hasChildren())
                        appendBundledNodes(rep.children(), indent + 1, instanceMc);
                }
            }

            for (NestedJarInspector.NestedJar node : ungrouped) {
                nestedBox.getChildren().add(createBundledRow(node, indent, null, null, false));
                if (node.hasChildren())
                    appendBundledNodes(node.children(), indent + 1, instanceMc);
            }
        }

        private Node createBundledRow(NestedJarInspector.NestedJar node, int indent, String versionsBadge, String badgeTooltip, boolean activeForInstance) {
            HBox row = new HBox(8, SVG.STACKS.createIcon(16));
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            if (indent > 0)
                row.getChildren().add(0, indentSpacer(indent));

            Label name = new Label(node.displayName());
            row.getChildren().add(name);

            if (node.version() != null && !node.version().isBlank()) {
                Label version = new Label(node.version());
                version.getStyleClass().add("mod-nested-version");
                row.getChildren().add(version);
            }
            if (versionsBadge != null) {
                Label badge = new Label(versionsBadge);
                badge.getStyleClass().add("mod-nested-badge");
                if (badgeTooltip != null && !badgeTooltip.isBlank())
                    badge.setTooltip(new Tooltip(badgeTooltip));
                row.getChildren().add(badge);
            }
            if (activeForInstance) {
                Label current = new Label(i18n("addon.bundled.current"));
                current.getStyleClass().add("mod-nested-current");
                row.getChildren().add(current);
            }
            return row;
        }

        /// Best-effort match of a nested jar against the instance's Minecraft version, for the
        /// multi-version highlight. The exact copy embeds the target version in its file name / mod
        /// version / declared constraint (e.g. {@code …-mc26.1.2-…}); we look for the instance version
        /// as a whole token so "1.21" doesn't match "1.21.2".
        private static boolean matchesInstance(NestedJarInspector.NestedJar node, String instanceMc) {
            if (instanceMc == null || instanceMc.isBlank())
                return false;
            return containsVersionToken(node.fileName(), instanceMc)
                    || containsVersionToken(node.version(), instanceMc)
                    || containsVersionToken(node.minecraftVersion(), instanceMc);
        }

        private static boolean containsVersionToken(String haystack, String version) {
            if (haystack == null)
                return false;
            int i = haystack.indexOf(version);
            while (i >= 0) {
                int end = i + version.length();
                char after = end < haystack.length() ? haystack.charAt(end) : ' ';
                if (after != '.' && !Character.isDigit(after)) // not a prefix of a longer version
                    return true;
                i = haystack.indexOf(version, i + 1);
            }
            return false;
        }

        // A fixed-width leading gap for one indentation level, so nested rows step inward without
        // overriding the row's own (CSS-defined) padding.
        private static Region indentSpacer(int indent) {
            Region spacer = new Region();
            double width = indent * 16;
            spacer.setMinWidth(width);
            spacer.setPrefWidth(width);
            spacer.setMaxWidth(width);
            return spacer;
        }

        private Node createSectionLabel(String text) {
            Label label = new Label(text);
            label.getStyleClass().add("mod-nested-section");
            return label;
        }

        private Node createDependencyRow(LocalModFile modInfo, String depId) {
            Label name = new Label(depId);

            var modManager = modInfo.getModManager();
            // A dependency may be installed under a compatible loader other than this mod's own
            // (e.g. a Quilt mod depending on a Fabric mod), so resolve it across every loader bucket
            // instead of only the depending mod's loaderType. Only call getLocalMod() when the mod
            // actually exists — it creates an entry otherwise.
            Set<LocalModFile> installedFiles = new HashSet<>();
            for (ModLoaderType type : ModLoaderType.values()) {
                if (modManager.hasMod(depId, type))
                    installedFiles.addAll(modManager.getLocalMod(depId, type).getFiles());
            }

            Label status = new Label();
            Runnable refresh = () -> {
                String statusText;
                if (installedFiles.isEmpty()) {
                    // Not installed separately — it may instead be bundled inside this mod via
                    // Jar-in-Jar. Prefer an exact id match against the scanned tree; fall back to the
                    // filename heuristic for nested jars whose metadata could not be parsed.
                    boolean bundled = modInfo.getAllBundledModIds().contains(depId)
                            || isBundledDependency(depId, modInfo.getBundledMods());
                    statusText = bundled
                            ? i18n("addon.dependencies.bundled")
                            : i18n("addon.dependencies.missing");
                } else if (installedFiles.stream().anyMatch(LocalModFile::isActive)) {
                    statusText = i18n("addon.dependencies.installed");
                } else {
                    statusText = i18n("addon.dependencies.disabled");
                }
                status.setText("(" + statusText + ")");
            };
            refresh.run();
            // Keep the status live while the panel stays open: re-evaluate whenever a provider is
            // enabled/disabled. Listeners are dropped when the panel is torn down (clearNested).
            for (LocalModFile file : installedFiles) {
                InvalidationListener listener = obs -> refresh.run();
                file.activeProperty().addListener(listener);
                nestedListenerCleanups.add(() -> file.activeProperty().removeListener(listener));
            }

            HBox row = new HBox(8, SVG.EXTENSION.createIcon(16), name, status);
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        // Heuristic: a dependency may be shipped inside the mod's own Jar-in-Jar payload,
        // in which case it won't show up as a separately installed mod. Match the dependency
        // id against the bundled jar file names.
        private boolean isBundledDependency(String depId, List<String> bundledMods) {
            if (bundledMods.isEmpty())
                return false;

            String id = depId.toLowerCase(Locale.ROOT);
            String idAlt = id.replace("-", "").replace("_", "");
            for (String path : bundledMods) {
                String fileName = (path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path)
                        .toLowerCase(Locale.ROOT);
                if (fileName.contains(id) || fileName.replace("-", "").replace("_", "").contains(idAlt))
                    return true;
            }
            return false;
        }

        // Tears down the nested panel's content, first detaching any live status-refresh listeners
        // so long-lived mod files don't pin recycled cells.
        private void clearNested() {
            for (Runnable cleanup : nestedListenerCleanups)
                cleanup.run();
            nestedListenerCleanups.clear();
            nestedBox.getChildren().clear();
        }

        private void rebuildNested(ModInfoObject item) {
            clearNested();
            if (item == null)
                return;
            LocalModFile modInfo = item.getModInfo();
            if (modInfo.hasBundledMods()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.bundled")));
                List<NestedJarInspector.NestedJar> tree = modInfo.getBundledTree();
                if (tree.isEmpty()) {
                    // Deep scan hasn't finished yet — show the declared filenames; this panel rebuilds
                    // when the background scan bumps bundledScanGeneration (see the cell's listener).
                    for (String path : modInfo.getBundledMods())
                        nestedBox.getChildren().add(createNestedRow(path, 0));
                } else {
                    appendBundledNodes(tree, 0, modInfo.getModManager().getGameVersion());
                }
            }
            if (modInfo.hasDependencies()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.dependencies")));
                for (String depId : modInfo.getDependencies())
                    nestedBox.getChildren().add(createDependencyRow(modInfo, depId));
            }
        }

        // Snap to the given state without animating — used when a cell is recycled to another item.
        private void snapExpansion(boolean expand) {
            if (nestedAnimation != null) {
                nestedAnimation.stop();
                nestedAnimation = null;
            }
            nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
            nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
            expandButton.getGraphic().setRotate(expand ? 180 : 0);
            if (expand) {
                rebuildNested(getItem());
                nestedBox.setManaged(true);
                nestedBox.setVisible(true);
            } else {
                nestedBox.setManaged(false);
                nestedBox.setVisible(false);
                clearNested();
            }
        }

        // Smoothly slide the nested list open/closed on a real user toggle.
        private void animateExpansion(boolean expand) {
            if (nestedAnimation != null) {
                nestedAnimation.stop();
                nestedAnimation = null;
            }
            if (!AnimationUtils.isAnimationEnabled()) {
                snapExpansion(expand);
                return;
            }
            if (expand) {
                rebuildNested(getItem());
                nestedBox.setManaged(true);
                nestedBox.setVisible(true);
                nestedBox.applyCss();
                nestedBox.layout();
                double target = nestedBox.prefHeight(-1);
                nestedBox.setMinHeight(0);
                nestedBox.setMaxHeight(0);
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200),
                        new KeyValue(nestedBox.minHeightProperty(), target, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(nestedBox.maxHeightProperty(), target, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(expandButton.getGraphic().rotateProperty(), 180, Motion.EASE_IN_OUT_CUBIC)));
                timeline.setOnFinished(e -> {
                    nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
                    nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    nestedAnimation = null;
                });
                nestedAnimation = timeline;
                timeline.play();
            } else {
                double current = nestedBox.getHeight();
                nestedBox.setMinHeight(current);
                nestedBox.setMaxHeight(current);
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200),
                        new KeyValue(nestedBox.minHeightProperty(), 0, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(nestedBox.maxHeightProperty(), 0, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(expandButton.getGraphic().rotateProperty(), 0, Motion.EASE_IN_OUT_CUBIC)));
                timeline.setOnFinished(e -> {
                    nestedBox.setManaged(false);
                    nestedBox.setVisible(false);
                    clearNested();
                    nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
                    nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    nestedAnimation = null;
                });
                nestedAnimation = timeline;
                timeline.play();
            }
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

            // Nested (Jar-in-Jar) mods & dependencies. Bind to this item's expanded state without
            // triggering the animation (the cell is being recycled, not toggled by the user).
            suppressExpandAnimation = true;
            if (expandedBinding != null) {
                expanded.unbindBidirectional(expandedBinding);
            }
            expanded.bindBidirectional(expandedBinding = dataItem.expandedProperty());
            suppressExpandAnimation = false;

            boolean expandable = modInfo.hasBundledMods() || modInfo.hasDependencies();
            expandButton.setVisible(expandable);
            expandButton.setManaged(expandable);
            if (modInfo.hasBundledMods())
                content.addTag(i18n("addon.bundled") + ": " + modInfo.getBundledMods().size());

            // Build the nested rows lazily — only when expanded — so collapsed rows don't create
            // nodes or compute dependency status on every render/scroll.
            snapExpansion(dataItem.expandedProperty().get());

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
