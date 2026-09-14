/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPage.ModInfoObject> implements PageAware {
    private final ReentrantLock lock = new ReentrantLock();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private ModManager modManager;
    private @Nullable HMCLGameInstance gameInstance;
    private String gameVersion;

    final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);

    /// Creates a mod list that reloads when `instanceContext` changes.
    ///
    /// @param instanceContext the parent page's instance property
    public ModListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, it -> ModManager.MOD_EXTENSIONS.contains(FileUtils.getExtension(it).toLowerCase(Locale.ROOT)), mods -> {
            mods.forEach(it -> {
                try {
                    modManager.addMod(it);
                } catch (IOException | IllegalArgumentException e) {
                    LOG.warning("Unable to parse mod file " + it, e);
                }
            });
            loadMods(modManager);
        });

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModListPageSkin(this);
    }

    public void refresh() {
        loadMods(modManager);
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            return;
        }

        this.gameVersion = gameInstance.getVersion().toString();

        loadMods(gameInstance.getModManager());
    }

    private void loadMods(ModManager modManager) {
        setLoading(true);

        if (this.modManager != modManager) {
            getItems().clear();
        }
        this.modManager = modManager;
        CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                modManager.refresh();
                return modManager.getLocalFiles().stream().map(ModInfoObject::new).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }, Schedulers.io()).whenCompleteAsync((list, exception) -> {
            if (this.modManager != modManager) {
                return;
            }

            updateSupportedLoaders(modManager);

            if (exception == null) {
                getItems().setAll(list);
            } else {
                LOG.warning("Failed to load mods", exception);
                getItems().clear();
            }
            setLoading(false);
        }, Schedulers.javafx());
    }

    private void updateSupportedLoaders(ModManager modManager) {
        supportedLoaders.clear();

        GameComponentAnalyzer analyzer = modManager.getComponentAnalyzer();
        if (analyzer == null) {
            Collections.addAll(supportedLoaders, ModLoaderType.values());
            return;
        }

        for (GameComponentType type : GameComponentType.MOD_LOADERS) {
            if (type.isModLoader() && analyzer.has(type)) {
                ModLoaderType modLoaderType = type.getModLoaderType();
                if (modLoaderType != null) {
                    supportedLoaders.add(modLoaderType);

                    if (modLoaderType == ModLoaderType.CLEANROOM)
                        supportedLoaders.add(ModLoaderType.FORGE);
                }
            }
        }

        if (analyzer.has(GameComponentType.NEO_FORGE) && "1.20.1".equals(gameVersion)) {
            supportedLoaders.add(ModLoaderType.FORGE);
        }

        if (analyzer.has(GameComponentType.QUILT)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(GameComponentType.LEGACY_FABRIC)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(GameComponentType.FABRIC) && modManager.hasMod("kilt", ModLoaderType.FABRIC)) {
            supportedLoaders.add(ModLoaderType.FORGE);
            supportedLoaders.add(ModLoaderType.NEO_FORGE);
        }

        // Sinytra Connector
        if (analyzer.has(GameComponentType.NEO_FORGE) && (modManager.hasMod("connector", ModLoaderType.NEO_FORGE) || modManager.hasMod("connectormod", ModLoaderType.NEO_FORGE))
                || "1.20.1".equals(gameVersion) && analyzer.has(GameComponentType.FORGE) && modManager.hasMod("connectormod", ModLoaderType.FORGE)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.mod"), "*.jar", "*.zip", "*.litemod"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);

        if (res == null) return;

        // It's guaranteed that succeeded and failed are thread safe here.
        List<String> succeeded = new ArrayList<>(res.size());
        List<String> failed = new ArrayList<>();

        Task.runAsync(() -> {
            for (Path file : res) {
                try {
                    modManager.addMod(file);
                    succeeded.add(FileUtils.getName(file));
                } catch (Exception e) {
                    LOG.warning("Unable to add mod " + file, e);
                    failed.add(FileUtils.getName(file));

                    // Actually addMod will not throw exceptions because FileChooser has already filtered files.
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            List<String> prompt = new ArrayList<>(1);
            if (!succeeded.isEmpty())
                prompt.add(i18n("mods.add.success", String.join(", ", succeeded)));
            if (!failed.isEmpty())
                prompt.add(i18n("mods.add.failed", String.join(", ", failed)));
            Controllers.dialog(String.join("\n", prompt), i18n("mods.add"));
            loadMods(modManager);
        }).start();
    }

    void removeSelected(ObservableList<ModInfoObject> selectedItems) {
        try {
            modManager.removeMods(selectedItems.stream()
                    .filter(Objects::nonNull)
                    .map(ModInfoObject::getModInfo)
                    .toArray(LocalModFile[]::new));
            loadMods(modManager);
        } catch (IOException ignore) {
            // Fail to remove mods if the game is running or the mod is absent.
        }
    }

    void enableSelected(ObservableList<ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    void disableSelected(ObservableList<ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public void openModFolder() {
        if (gameInstance != null) {
            FXUtils.openFolder(gameInstance.getModsDirectory());
        }
    }

    public void checkUpdates(Collection<LocalModFile> mods) {
        Objects.requireNonNull(mods);
        if (isLoading() || gameInstance == null) {
            return;
        }

        HMCLGameInstance gameInstance = this.gameInstance;
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            GameVersionNumber version = gameInstance.getVersion();
                            return version != GameVersionNumber.unknown()
                                    ? new AddonCheckUpdatesTask<>(
                                            DownloadProviders.getDownloadProvider(), version.toString(), mods)
                                    : null;
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception instanceof CancellationException) return;
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("addon.check_update.failed_check"), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("addon.check_update.empty"));
                            } else {
                                Controllers.navigateForward(new AddonUpdatesPage<>(modManager, result));
                            }
                        })
                        .withStagesHints("update.checking"),
                i18n("addon.check_update"), TaskCancellationAction.NORMAL);

        if (gameInstance.isModpack()) {
            Controllers.confirm(
                    i18n("mods.update_modpack_mod.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    public void download() {
        if (gameInstance == null) {
            return;
        }
        Controllers.getDownloadPage().showModDownloads().selectInstance(gameInstance.getId());
        Controllers.navigate(Controllers.getDownloadPage());
    }

    public void rollback(LocalModFile from, LocalModFile to) {
        try {
            modManager.rollback(from, to);
            refresh();
        } catch (IOException ex) {
            Controllers.showToast(i18n("message.failed"));
        }
    }

    public GameDirectory getGameDirectory() {
        return gameInstance != null ? gameInstance.getRepository().getGameDirectory() : null;
    }

    public HMCLGameRepository getRepository() {
        return gameInstance != null ? gameInstance.getRepository() : null;
    }

    public GameInstanceID getInstanceId() {
        return gameInstance != null ? gameInstance.getId() : null;
    }

    @NotNullByDefault
    private static final class ModListPageSkin extends SkinBase<ModListPage> {

        private final TransitionPane toolbarPane;
        private final HBox searchBar;
        private final HBox toolbarNormal;
        private final HBox toolbarSelecting;

        private final JFXListView<ModListPage.ModInfoObject> listView;

        /// Whether the search mechanism is currently active.
        private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

        private final JFXTextField searchField;

        /// Timer for debouncing search input to avoid executing search on every keystroke.
        private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

        public ModListPageSkin(ModListPage skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            pane.getChildren().setAll(root);
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
                searchPause.setOnFinished(e -> search());
                searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                        searchPause.setRate(1);
                        searchPause.playFromStart();
                    }
                });

                JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                        () -> {
                            changeToolbar(toolbarNormal);

                            searchField.clear();
                            searchPause.stop();

                            isSearching.set(false);
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
                                                .map(ModListPage.ModInfoObject::getModInfo)
                                                .toList()
                                )
                        ),
                        createToolbarButton2(i18n("mods.download"), SVG.DOWNLOAD, skinnable::download),
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
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                            }, null);
                        }),
                        createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                                skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                                skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(
                                        listView.getSelectionModel().getSelectedItems().stream()
                                                .map(ModListPage.ModInfoObject::getModInfo)
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
                                changeToolbar(isSearching.get() ? searchBar : toolbarNormal);
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

                listView.setCellFactory(x -> new ModListPage.ModInfoListCell(listView, skinnable));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                StackPane placeholderContainer = new StackPane();
                placeholderContainer.getStyleClass().add("notice-pane");
                Label placeholderLabel = new Label(i18n("mods.empty"));
                placeholderLabel.textProperty().bind(
                    Bindings.createStringBinding(() -> {
                        if (isSearching.get()) {
                            return i18n("search.no_results_found");
                        } else {
                            return i18n("mods.empty");
                        }
                    },
                    isSearching)
                );
                placeholderContainer.getChildren().add(placeholderLabel);
                listView.setPlaceholder(placeholderContainer);

                Bindings.bindContent(listView.getItems(), skinnable.getItems());
                skinnable.getItems().addListener((ListChangeListener<? super ModListPage.ModInfoObject>) c -> {
                    if (isSearching.get()) {
                        search();
                    }
                });

                listView.setOnContextMenuRequested(event -> {
                    ModListPage.ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ModListPage.ModInfoDialog(selectedItem));
                    }
                });

                // ListViewBehavior would consume ESC pressed event, preventing us from handling it
                // So we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                center.setContent(listView);
                root.getContent().add(center);
            }

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
            isSearching.set(true);

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
                for (ModListPage.ModInfoObject item : getSkinnable().getItems()) {
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

    }

    public static final class ModInfoObject {
        private final BooleanProperty active;
        private final LocalModFile localModFile;
        private final @Nullable ModTranslations.Mod modTranslations;

        private final ItemPropertyAsyncCache<Image, ModInfoObject> iconCache;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();

            this.modTranslations = ModTranslations.MOD.getMod(localModFile.getId(), localModFile.getName());

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        public LocalModFile getModInfo() {
            return localModFile;
        }

        public @Nullable ModTranslations.Mod getModTranslations() {
            return modTranslations;
        }

        private Image getDefaultIcon() {
            return GameInstanceIconType.getIconType(this.localModFile.getModLoaderType()).getIcon();
        }

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

            return getDefaultIcon();
        }
    }

    private static final class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            titleContainer.setPadding(new Insets(0, 0, 12, 0));

            DoubleBinding widthBinding = Controllers.getDecorator().contentWidthProperty().multiply(0.7);
            prefWidthProperty().bind(widthBinding);
            maxWidthProperty().bind(widthBinding);

            var imageContainer = new ImageContainer(40);
            titleContainer.setAlignment(Pos.CENTER_LEFT);
            modInfo.iconCache.attachValue(imageContainer.imageProperty(), null);

            TwoLineListItem title = new TwoLineListItem();
            title.getTitleLabel().setWrapText(true);
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
                double maxHeight = Controllers.getDecorator().contentHeightProperty().get() * 0.5;
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
                            RemoteAddon remoteAddon = repository.getAddonById(DownloadProviders.getDownloadProvider(), versionOptional.get().projectId());
                            FXUtils.runInFX(() -> {
                                Set<String> tags = new LinkedHashSet<>();
                                for (AddonLoader loader : versionOptional.get().loaders()) {
                                    String tag = I18n.translateLoaderName(loader);
                                    if (tag != null) tags.add(tag);
                                }
                                title.addTagsIfNotExist(tags);

                                button.setExternalLink(remoteAddon.pageUrl());
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
                officialPageButton.setExternalLink(modInfo.getModInfo().getUrl());
                getActions().add(officialPageButton);
            }

            if (modInfo.getModTranslations() == null || StringUtils.isBlank(modInfo.getModTranslations().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setExternalLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                        pair("key", modInfo.getModInfo().getName()),
                        pair("site", "all"),
                        pair("filter", "0")
                )));
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setExternalLink(ModTranslations.MOD.getMcmodUrl(modInfo.getModTranslations()));
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

    private static final class ModInfoListCell extends MDListCell<ModInfoObject> {
        private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

        private final ModListPage page;

        private final JFXCheckBox checkBox = new JFXCheckBox();
        private final ImageContainer imageContainer = new ImageContainer(32);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton restoreButton = FXUtils.newToggleButton4(SVG.RESTORE);
        private final JFXButton infoButton = FXUtils.newToggleButton4(SVG.INFO);
        private final JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER);

        private BooleanProperty booleanProperty;

        private Tooltip warningTooltip;

        ModInfoListCell(JFXListView<ModInfoObject> listView, ModListPage page) {
            super(listView);
            this.page = page;

            this.getStyleClass().add("mod-info-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageContainer.setImage(GameInstanceIconType.COMMAND.getIcon());

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

            dataItem.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

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
            } else if (!page.supportedLoaders.contains(modLoaderType)) {
                warning.add(i18n("mods.warning.loader_mismatch"));
                content.addTagWarning(I18n.translateLoaderType(dataItem.getModInfo().getModLoaderType()));
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
                                () -> page.rollback(modInfo, localModFile),
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
