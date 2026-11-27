// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import org.jackhuang.hmcl.addon.shader.ShaderPackFile;
import org.jackhuang.hmcl.addon.shader.ShaderPackManager;
import org.jackhuang.hmcl.addon.update.AddonCheckUpdatesTask;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ShaderPackListPage extends ListPaneBase<ShaderPackListPage.ShaderPackInfoObject> {

    /// Switches between toolbar modes.
    private final TransitionPane toolbarPane = new TransitionPane();
    /// Actions available while browsing.
    private final HBox toolbarNormal = new HBox();
    /// Actions on the selected items.
    private final HBox toolbarSelecting = new HBox();
    /// Toolbar containing the search input.
    private final HBox searchBar = new HBox();
    /// Text used to filter the list.
    private final JFXTextField searchField = new JFXTextField();
    /// Whether the search mechanism is currently active.
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    /// Timer for debouncing search input to avoid executing search on every keystroke.
    private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

    /// All loaded items before search filtering.
    private final ObservableList<ShaderPackInfoObject> sourceItems = FXCollections.observableArrayList();

    /// Source items matching the current search.
    private final FilteredList<ShaderPackInfoObject> filteredItems = new FilteredList<>(sourceItems);

    private final ReentrantLock lock = new ReentrantLock();

    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;

    private Path shaderPackDir;
    private ShaderPackManager shaderPackManager;

    public ShaderPackListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, ShaderPackFile::isFileShaderPack, this::addFiles);

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));

        initializeView();
    }

    @Override
    protected ItemCell<ShaderPackInfoObject> createItemCell(@NotNull ListView<ShaderPackInfoObject> listView) {
        return new ShaderPackListCell(listView);
    }

    /// Configures the list and its page actions.
    private void initializeView() {
        getStyleClass().add("notice-pane");
        var listView = getListView();
        listView.setItems(filteredItems);

        {

            // Toolbar Selecting
            // selectAll() clears the selection first, causing the toolbar to flicker.
            var selectAllButton = createToolbarButton(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                    listView.getSelectionModel().selectRange(0, listView.getItems().size()));

            ListChangeListener<Object> selectionListener = change -> selectAllButton.setDisable(!listView.getItems().isEmpty()
                    && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
            listView.getSelectionModel().getSelectedItems().addListener(selectionListener);
            listView.getItems().addListener(selectionListener);

            toolbarSelecting.getChildren().setAll(
                    createToolbarButton(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                                        removeSelected(listView.getSelectionModel().getSelectedItems()),
                                null);
                    }),
                    createToolbarButton(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                            checkUpdates(listView.getSelectionModel().getSelectedItems().stream().map(ShaderPackInfoObject::getFile).toList())
                    ),
                    createToolbarButton(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                            listView.getSelectionModel().selectAll()),
                    createToolbarButton(i18n("button.cancel"), SVG.CANCEL, () ->
                            listView.getSelectionModel().clearSelection())
            );

            // Search Bar
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchPause.setOnFinished(e -> search());
            FXUtils.onChange(searchField.textProperty(), newValue -> {
                if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                    searchPause.setRate(1);
                    searchPause.playFromStart();
                }
            });

            JFXButton closeSearchBar = createToolbarButton(null, SVG.CLOSE,
                    () -> {
                        changeToolbar(toolbarNormal);

                        searchField.clear();
                        searchPause.stop();

                        isSearching.set(false);
                        filteredItems.setPredicate(null);
                    });

            onEscPressed(searchField, closeSearchBar::fire);

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            // Toolbar Normal
            toolbarNormal.setAlignment(Pos.CENTER_LEFT);
            toolbarNormal.setPickOnBounds(false);
            toolbarNormal.getChildren().setAll(
                    createToolbarButton(i18n("button.refresh"), SVG.REFRESH, this::refresh),
                    createToolbarButton(i18n("shaderpack.add"), SVG.ADD, this::onAddFiles),
                    createToolbarButton(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, this::onOpenFolder),
                    createToolbarButton(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                            checkUpdates(listView.getItems().stream().map(ShaderPackInfoObject::getFile).toList())
                    ),
                    createToolbarButton(i18n("download"), SVG.DOWNLOAD, this::onDownload),
                    createToolbarButton(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
            );

            FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                    selectedItem -> {
                        if (selectedItem == null)
                            changeToolbar(isSearching.get() ? searchBar : toolbarNormal);
                        else
                            changeToolbar(toolbarSelecting);
                    });
            FXUtils.setOverflowHidden(toolbarPane, 8);
            setToolbar(toolbarPane);

            // Clear selection when pressing ESC
            addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
        }

        {
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            StackPane placeholderContainer = new StackPane();
            placeholderContainer.getStyleClass().add("notice-pane");
            Label placeholderLabel = new Label(i18n("shaderpack.empty"));
            placeholderLabel.textProperty().bind(
                    Bindings.createStringBinding(() -> {
                                if (isSearching.get()) {
                                    return i18n("search.no_results_found");
                                } else {
                                    return i18n("shaderpack.empty");
                                }
                            },
                            isSearching)
            );
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);

            listView.setOnContextMenuRequested(event -> {
                ShaderPackInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                    listView.getSelectionModel().clearSelection();
                    Controllers.dialog(new ShaderPackInfoDialog(selectedItem));
                }
            });
        }
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            this.shaderPackDir = null;
            this.shaderPackManager = null;
            getItems().clear();
            return;
        }

        this.shaderPackDir = gameInstance.getShadersDirectory();
        this.shaderPackManager = gameInstance.getShaderPackManager();

        refresh();
    }

    public void refresh() {
        if (shaderPackManager == null) return;
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            lock.lock();
            try {
                shaderPackManager.refresh();
                return shaderPackManager.getLocalFiles().stream().map(ShaderPackInfoObject::new).toList();
            } finally {
                lock.unlock();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                sourceItems.setAll(result);
            } else {
                LOG.warning("Failed to load shader packs", exception);
                sourceItems.clear();
            }
            setLoading(false);
        }).start();
    }

    public void addFiles(List<Path> files) {
        if (shaderPackManager == null) return;

        List<Path> failures = new ArrayList<>();
        Task.runAsync(() -> {
            for (Path file : files) {
                try {
                    shaderPackManager.importShaderPack(file);
                } catch (Exception e) {
                    LOG.warning("Failed to add shader pack " + file, e);
                    failures.add(file);
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            if (!failures.isEmpty()) {
                StringBuilder failure = new StringBuilder(i18n("shaderpack.add.failed"));
                for (Path file: failures) {
                    failure.append("\n").append(file.toString());
                }
                Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
            refresh();
        }).start();
    }

    private void removeSelected(List<ShaderPackInfoObject> selectedItems) {
        try {
            shaderPackManager.removeShaderPacks(selectedItems.stream().map(ShaderPackInfoObject::getFile).toList());
        } catch (IOException e) {
            Controllers.dialog(i18n("shaderpack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to delete shader packs", e);
        }
        refresh();
    }

    public void checkUpdates(Collection<ShaderPackFile> shaderPacks) {
        if (gameInstance != null)
            AddonCheckUpdatesTask.openAddonsCheckUpdateDialog(gameInstance, shaderPacks, shaderPackDir);
    }

    private void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("shaderpack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.shaderpack"), "*.zip"));
        List<Path> files = FileUtils.toPaths(fileChooser.showOpenMultipleDialog(Controllers.getStage()));
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void onDownload() {
        if (gameInstance == null) return;
        Controllers.getDownloadPage().showShaderDownloads().selectInstance(gameInstance.getId());
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (shaderPackDir != null) {
            FXUtils.openFolder(shaderPackDir);
        }
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

        String queryString = searchField.getText();
        if (StringUtils.isBlank(queryString)) {
            filteredItems.setPredicate(null);
        } else {
            Predicate<@Nullable String> predicate;
            try {
                predicate = StringUtils.compileQuery(queryString);
            } catch (Throwable e) {
                LOG.warning("Illegal regular expression", e);
                filteredItems.setPredicate(item -> false);
                return;
            }

            filteredItems.setPredicate(item -> {
                var file = item.getFile();
                var meta = file.getMeta();
                return predicate.test(file.getFile().getFileName().toString())
                        || predicate.test(file.getName())
                        || predicate.test(meta.version())
                        || predicate.test(meta.description());
            });
        }
    }

    public static final class ShaderPackInfoObject {
        private final ShaderPackFile file;

        private final ItemPropertyAsyncCache<Image, ShaderPackInfoObject> iconCache;

        public ShaderPackInfoObject(ShaderPackFile file) {
            this.file = file;

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        public ShaderPackFile getFile() {
            return file;
        }

        private Image getDefaultIcon() {
            return switch (file.getShaderLoaderType()) {
                case OPTIFINE_IRIS -> FXUtils.newBuiltinImage("/assets/img/opti-iris.png");
                default -> FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
            };
        }

        private Image loadIcon() {
            Image icon = file.loadIcon(ImageLoaders.DEFAULT);
            return FXUtils.isValidIcon(icon) ? icon : getDefaultIcon();
        }
    }

    private static final class ShaderPackListCell extends ListPaneBase.ItemCell<ShaderPackInfoObject> {

        private final ImageContainer imageContainer = new ImageContainer(24);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = FXUtils.newToggleButton4(SVG.FOLDER);
        private final JFXButton btnInfo = FXUtils.newToggleButton4(SVG.INFO);

        public ShaderPackListCell(ListView<ShaderPackInfoObject> listView) {
            super(listView);

            HBox root = new HBox(8);
            root.setPickOnBounds(false);
            root.setAlignment(Pos.CENTER_LEFT);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            root.getChildren().setAll(imageContainer, content, btnReveal, btnInfo);

            setSelectable();

            StackPane.setMargin(root, new Insets(8));
            setContent(root);
        }

        @Override
        protected void updateListItem(ShaderPackInfoObject item) {
            if (item == null) return;

            ShaderPackFile file = item.file;

            item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(itemProperty()));

            content.getTags().clear();
            content.setTitle(file.getFileName());
            content.setSubtitle(file.getMeta().description() == null ? "" : file.getMeta().description());
            content.addTag(switch (file.getShaderLoaderType()) {
                case OPTIFINE_IRIS -> i18n("shaderpack.loader.optifine_iris");
                case APERTURE -> i18n("shaderpack.loader.aperture");
            });
            if (file.getMeta().version() != null)
                content.addTag(file.getMeta().version());

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getFile()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ShaderPackInfoDialog(item)));
        }
    }

    private static final class ShaderPackInfoDialog extends JFXDialogLayout {

        public ShaderPackInfoDialog(ShaderPackInfoObject infoObject) {
            ShaderPackFile shaderPackFile = infoObject.file;

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            titleContainer.setPadding(new Insets(0, 0, 12, 0));

            maxWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.7));

            ImageContainer imageContainer = new ImageContainer(40);
            infoObject.iconCache.attachValue(imageContainer.imageProperty(), null);

            TwoLineListItem title = new TwoLineListItem();
            title.getTitleLabel().setWrapText(true);
            title.setTitle(shaderPackFile.getFileName());
            title.setSubtitle(shaderPackFile.getFile().getFileName().toString());
            title.addTag(switch (shaderPackFile.getShaderLoaderType()) {
                case OPTIFINE_IRIS -> i18n("shaderpack.loader.optifine_iris");
                case APERTURE -> i18n("shaderpack.loader.aperture");
            });

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label();
            if (shaderPackFile.getMeta().description() != null)
                description.setText(shaderPackFile.getMeta().description());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            ScrollUtils.installSmoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            // Use the wrapped content height to request a readable viewport within the dialog size limit.
            descriptionPane.prefViewportHeightProperty().bind(Bindings.min(
                    description.heightProperty(),
                    Controllers.getDecorator().contentHeightProperty().multiply(0.5)));

            setBody(descriptionPane);

            for (Pair<String, ? extends RemoteAddonRepository> item : Arrays.asList(
                    pair("addon.curseforge", CurseForgeRemoteAddonRepository.getInstance()),
                    pair("addon.modrinth", ModrinthRemoteAddonRepository.getInstance())
            )) {
                RemoteAddonRepository repository = item.getValue();
                JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                Task.runAsync(() -> {
                    Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(shaderPackFile.getFile());
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

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }

    }
}
