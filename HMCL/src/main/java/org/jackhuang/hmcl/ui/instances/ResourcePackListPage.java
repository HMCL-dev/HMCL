// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.*;
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
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.addon.resourcepack.ResourcePackManager;
import org.jackhuang.hmcl.addon.update.AddonCheckUpdatesTask;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.image.ImageLoaders;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcePackListPage extends ListPaneBase<ResourcePackListPage.ResourcePackInfoObject> {
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
    private final ObservableList<ResourcePackInfoObject> sourceItems = FXCollections.observableArrayList();

    /// Source items matching the current search.
    private final FilteredList<ResourcePackInfoObject> filteredItems = new FilteredList<>(sourceItems);

    private static final String TIP_KEY = "resourcePackWarning";
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;

    private Path resourcePackDirectory;
    private ResourcePackManager resourcePackManager;

    private final ReentrantLock lock = new ReentrantLock();

    /// Creates a resource-pack list that reloads when `instanceContext` changes.
    ///
    /// @param instanceContext the parent page's instance property
    public ResourcePackListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, ResourcePackFile::isFileResourcePack, this::addFiles);

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));

        initializeView();
    }

    @Override
    protected ItemCell<ResourcePackInfoObject> createItemCell(ListView<ResourcePackInfoObject> listView) {
        return new ResourcePackCell(listView, this);
    }

    private static @Nullable String getWarning(ResourcePackFile.Compatibility compatibility) {
        return switch (compatibility) {
            case TOO_NEW -> i18n("resourcepack.warning.too_new");
            case TOO_OLD -> i18n("resourcepack.warning.too_old");
            case INVALID -> i18n("resourcepack.warning.invalid");
            case MISSING_PACK_META -> i18n("resourcepack.warning.missing_pack_meta");
            case MISSING_GAME_META -> i18n("resourcepack.warning.missing_game_meta");
            default -> null;
        };
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
                    createToolbarButton(i18n("button.remove"), SVG.DELETE_FOREVER, () ->
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                            removeSelected(listView.getSelectionModel().getSelectedItems()), null)),
                    createToolbarButton(i18n("button.enable"), SVG.CHECK, () ->
                            setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), true)),
                    createToolbarButton(i18n("button.disable"), SVG.CLOSE, () ->
                            setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false)),
                    createToolbarButton(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                            checkUpdates(
                                    listView.getSelectionModel().getSelectedItems().stream().map(ResourcePackInfoObject::getFile).toList()
                            )
                    ),
                    selectAllButton,
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
                    createToolbarButton(i18n("resourcepack.add"), SVG.ADD, this::onAddFiles),
                    createToolbarButton(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, this::onOpenFolder),
                    createToolbarButton(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                            checkUpdates(listView.getItems().stream().map(ResourcePackInfoObject::getFile).toList())
                    ),
                    createToolbarButton(i18n("resourcepack.download"), SVG.DOWNLOAD, this::onDownload),
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
            Label placeholderLabel = new Label(i18n("resourcepack.empty"));
            placeholderLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    if (isSearching.get()) {
                        return i18n("search.no_results_found");
                    } else {
                        return i18n("resourcepack.empty");
                    }
                },
                isSearching)
            );
            placeholderContainer.getChildren().add(placeholderLabel);
            listView.setPlaceholder(placeholderContainer);

            listView.setOnContextMenuRequested(event -> {
                ResourcePackInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                    listView.getSelectionModel().clearSelection();
                    Controllers.dialog(new ResourcePackInfoDialog(selectedItem));
                }
            });
        }
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            this.resourcePackManager = null;
            this.resourcePackDirectory = null;
            sourceItems.clear();
            return;
        }

        this.resourcePackManager = gameInstance.getResourcePackManager();
        this.resourcePackDirectory = this.resourcePackManager.getDirectory();

        refresh();
    }

    public void refresh() {
        if (resourcePackManager == null) return;
        setDisable(false);
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            lock.lock();
            try {
                if (!ResourcePackManager.isMcVersionSupported(resourcePackManager.getMinecraftVersion())) return null;
                resourcePackManager.refresh();
                return resourcePackManager.arePacksEnabled(resourcePackManager.getLocalFiles().stream())
                        .map(ResourcePackInfoObject::new)
                        .toList();
            } finally {
                lock.unlock();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                if (result != null) {
                    sourceItems.setAll(result);
                } else { // Unsupported mc version
                    sourceItems.clear();
                    setDisable(true);
                }
            } else {
                LOG.warning("Failed to load resource packs", exception);
                sourceItems.clear();
            }
            setLoading(false);
        }).start();
    }

    public void addFiles(List<Path> files) {
        if (resourcePackManager == null) return;

        List<Path> failures = new ArrayList<>();
        Task.runAsync(() -> {
            for (Path file : files) {
                try {
                    resourcePackManager.importResourcePack(file);
                } catch (Exception e) {
                    LOG.warning("Failed to add resource pack", e);
                    failures.add(file);
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            if (!failures.isEmpty()) {
                StringBuilder failure = new StringBuilder(i18n("resourcepack.add.failed"));
                for (Path file: failures) {
                    failure.append("\n").append(file.toString());
                }
                Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
            refresh();
        }).start();

    }

    public void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("resourcepack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("resourcepack"), "*.zip"));
        List<Path> files = Controllers.showOpenMultipleDialog(fileChooser);
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void onDownload() {
        if (gameInstance == null) {
            return;
        }
        Controllers.getDownloadPage().showResourcePackDownloads().selectInstance(gameInstance.getId());
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (resourcePackDirectory != null) {
            FXUtils.openFolder(resourcePackDirectory);
        }
    }

    private void setSelectedEnabled(List<ResourcePackInfoObject> selectedItems, boolean enabled) {
        if (!Boolean.TRUE.equals(SettingsManager.state().getShownTips().get(TIP_KEY)) && enabled && !selectedItems.stream().map(ResourcePackInfoObject::getFile).allMatch(ResourcePackFile::isCompatible)) {
            Controllers.confirm(
                    i18n("resourcepack.warning.manipulate"),
                    i18n("message.warning"),
                    MessageDialogPane.MessageType.WARNING,
                    () -> {
                        SettingsManager.state().getShownTips().put(TIP_KEY, true);
                        setSelectedEnabled(selectedItems, true);
                    }, null);
        } else {
            if (resourcePackManager == null) return;
            if (enabled) {
                resourcePackManager.enableResourcePacks(selectedItems.stream().map(ResourcePackInfoObject::getFile).toList());
            } else {
                resourcePackManager.disableResourcePacks(selectedItems.stream().map(ResourcePackInfoObject::getFile).toList());
            }
            for (ResourcePackInfoObject item : selectedItems) {
                item.enabledProperty().set(enabled);
            }
        }
    }

    private void removeSelected(List<ResourcePackInfoObject> selectedItems) {
        try {
            if (resourcePackManager != null) {
                if (resourcePackManager.removeResourcePacks(selectedItems.stream().map(ResourcePackInfoObject::getFile).toList())) {
                    refresh();
                }
            }
        } catch (IOException e) {
            Controllers.dialog(i18n("resourcepack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to delete resource packs", e);
        }
    }

    /// Checks the supplied resource packs for updates and opens the results for the current instance.
    /// The target directory is retained even if this page switches instances during the check.
    /// Does nothing when no instance is selected.
    ///
    /// @param resourcePacks the resource packs to check
    public void checkUpdates(Collection<ResourcePackFile> resourcePacks) {
        @Nullable HMCLGameInstance gameInstance = this.gameInstance;
        if (gameInstance == null) {
            return;
        }

        Path resourcePackDirectory = gameInstance.getResourcePackManager().getDirectory();
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            GameVersionNumber version = gameInstance.getVersion();
                            if (version != GameVersionNumber.unknown()) {
                                return new AddonCheckUpdatesTask(DownloadProviders.getDownloadProvider(), version.toString(), resourcePacks);
                            } else {
                                LOG.warning("Failed to check for updates, due to unable to get instance game version");
                                return null;
                            }
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("addon.check_update.failed_check"), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.commonUpdates().isEmpty()) {
                                Controllers.dialog(i18n("addon.check_update.empty"));
                            } else {
                                Controllers.navigateForward(new AddonUpdatesPage(DownloadProviders.getDownloadProvider(), resourcePackDirectory, result));
                            }
                        })
                        .withStagesHints("update.checking"),
                i18n("addon.check_update"), TaskCancellationAction.NORMAL);

        if (gameInstance.isModpack()) {
            Controllers.confirm(
                    i18n("resourcepack.update_in_modpack.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    /// Shows the requested toolbar and focuses the search input when appropriate.
    private void changeToolbar(HBox newToolbar) {
        Node oldToolbar = toolbarPane.getCurrentNode();
        if (newToolbar != oldToolbar) {
            toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
            if (newToolbar == searchBar) {
                Platform.runLater(searchField::requestFocus);
            }
        }
    }

    /// Applies the search text to the displayed items.
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
                ResourcePackFile resourcePack = item.getFile();
                @Nullable LocalAddonFile.Description description = resourcePack.getDescription();
                Stream<String> descriptionParts = description == null
                        ? Stream.empty()
                        : description.parts().stream().map(LocalAddonFile.Description.Part::text);
                return predicate.test(resourcePack.getFileNameWithExtension())
                        || predicate.test(resourcePack.getFileName())
                        || descriptionParts.anyMatch(predicate);
            });
        }
    }

    public static final class ResourcePackInfoObject {

        private final ResourcePackFile file;
        private final BooleanProperty enabled;

        private final ItemPropertyAsyncCache<Image, ResourcePackInfoObject> iconCache;

        public ResourcePackInfoObject(Pair<ResourcePackFile, Boolean> pair) {
            this.file = pair.key();
            this.enabled = new SimpleBooleanProperty(this, "enabled", pair.value());

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        public ResourcePackFile getFile() {
            return file;
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        private Image getDefaultIcon() {
            return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
        }

        /// Loads the resource-pack icon, using the default icon if it is absent or invalid.
        private Image loadIcon() {
            Image icon = file.loadIcon(ImageLoaders.DEFAULT);
            return FXUtils.isValidIcon(icon) ? icon : getDefaultIcon();
        }
    }

    private static final class ResourcePackCell extends ItemCell<ResourcePackInfoObject> {
        private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

        private final ResourcePackListPage page;

        private final ImageContainer imageContainer = new ImageContainer(32);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = FXUtils.newToggleButton4(SVG.FOLDER);
        private final JFXButton btnInfo = FXUtils.newToggleButton4(SVG.INFO);
        private final JFXSwitch activeSwitch;

        /// The current item property bound to the switch, or null while the cell is empty.
        private @Nullable BooleanProperty boundEnabled;

        /// Creates a selectable pack row with compatibility confirmation and file actions.
        public ResourcePackCell(ListView<ResourcePackInfoObject> listView, ResourcePackListPage page) {
            super(listView);
            this.page = page;

            getStyleClass().add("resource-pack-list-cell");

            HBox root = new HBox(8);
            root.setPickOnBounds(false);
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(0, 8, 0, 8));

            activeSwitch = new JFXSwitch() {
                @Override
                public void fire() {
                    if (!Boolean.TRUE.equals(SettingsManager.state().getShownTips().get(TIP_KEY)) && !isSelected() && getItem() != null && !getItem().getFile().isCompatible()) {
                        Controllers.confirm(
                                i18n("resourcepack.warning.manipulate"),
                                i18n("message.info"),
                                MessageDialogPane.MessageType.INFO,
                                () -> {
                                    super.fire();
                                    SettingsManager.state().getShownTips().put(TIP_KEY, true);
                                }, null);
                    } else {
                        super.fire();
                    }
                }
            };
            activeSwitch.setOnAction(e -> {
                if (getItem() != null) getItem().getFile().setEnabled(activeSwitch.isSelected());
            });
            activeSwitch.setSize(20);
            activeSwitch.setMinSize(30, 30);
            activeSwitch.setPrefSize(30, 30);
            activeSwitch.setMaxSize(30, 30);
            HBox.setMargin(activeSwitch, new Insets(0, 0, 0, 6));

            HBox.setHgrow(content, Priority.ALWAYS);

            HBox actions = new HBox();
            actions.setAlignment(Pos.CENTER);
            actions.getChildren().setAll(btnReveal, btnInfo, activeSwitch);

            root.getChildren().setAll(imageContainer, content, actions);

            setSelectable();

            StackPane.setMargin(root, new Insets(8));
            setContent(root);
        }

        /// Updates pack details and reuses the compatibility tag when a warning remains.
        @Override
        protected void updateListItem(@Nullable ResourcePackInfoObject item) {
            pseudoClassStateChanged(WARNING, false);

            if (boundEnabled != null) {
                activeSwitch.selectedProperty().unbindBidirectional(boundEnabled);
                boundEnabled = null;
            }
            if (item == null) return;

            ResourcePackFile file = item.getFile();
            item.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(itemProperty()));

            content.setTitle(file.getFileName());
            {
                var description = file.getDescription();
                content.setSubtitle(description != null ? description.toStringSingleLine() : "");
            }

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getFile()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ResourcePackInfoDialog(item)));

            activeSwitch.selectedProperty().bindBidirectional(boundEnabled = item.enabledProperty());

            {
                var compatibility = file.getCompatibility();
                if (compatibility != ResourcePackFile.Compatibility.COMPATIBLE) {
                    pseudoClassStateChanged(WARNING, true);
                    content.getTags().setAll(Tag.warning(getWarning(compatibility)));
                } else {
                    content.getTags().clear();
                }
            }
        }
    }

    /// Displays resource pack metadata with a scrollable description.
    private static final class ResourcePackInfoDialog extends JFXDialogLayout {

        /// Creates a dialog whose description viewport follows its content and the window height.
        ResourcePackInfoDialog(ResourcePackInfoObject packInfoObject) {
            ResourcePackFile pack = packInfoObject.getFile();

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            ImageContainer imageContainer = new ImageContainer(40);
            packInfoObject.iconCache.attachValue(imageContainer.imageProperty(), null);

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(pack.getFileName());
            title.setSubtitle(pack.getFileNameWithExtension());
            var compatibility = pack.getCompatibility();
            if (compatibility != ResourcePackFile.Compatibility.COMPATIBLE) {
                title.addTagWarning(getWarning(compatibility));
            }

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label(Objects.requireNonNullElse(pack.getDescription(), "").toString());
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
                    Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(packInfoObject.getFile().getFile());
                    if (versionOptional.isPresent()) {
                        RemoteAddon remoteAddon = repository.getAddonById(DownloadProviders.getDownloadProvider(), versionOptional.get().projectId());
                        FXUtils.runInFX(() -> {
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
