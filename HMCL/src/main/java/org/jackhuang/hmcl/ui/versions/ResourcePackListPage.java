package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.resourcepack.ResourcePackManager;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcePackListPage extends ListPageBase<ResourcePackListPage.ResourcePackInfoObject> implements VersionPage.VersionLoadable {

    private static final String TIP_KEY = "resourcePackWarning";
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

    private Profile profile;
    private String instanceId;

    private Path resourcePackDirectory;
    private ResourcePackManager resourcePackManager;

    public ResourcePackListPage() {
        FXUtils.applyDragListener(this, ResourcePackFile::isFileResourcePack, this::addFiles);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ResourcePackListPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.profile = profile;
        this.instanceId = version;
        this.resourcePackManager = new ResourcePackManager(profile.getRepository(), version);
        this.resourcePackDirectory = this.resourcePackManager.getDirectory();

        try {
            if (!Files.exists(resourcePackDirectory)) {
                Files.createDirectories(resourcePackDirectory);
            }
        } catch (IOException e) {
            LOG.warning("Failed to create resource pack directory: " + resourcePackDirectory, e);
        }
        refresh();
    }

    public void refresh() {
        if (resourcePackManager == null || !Files.isDirectory(resourcePackDirectory)) return;
        setDisable(false);
        if (!ResourcePackManager.isMcVersionSupported(resourcePackManager.getMinecraftVersion())) {
            getItems().clear();
            setDisable(true);
            return;
        }
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            resourcePackManager.refresh();
            return resourcePackManager.getLocalFiles()
                    .stream()
                    .map(ResourcePackInfoObject::new)
                    .toList();
        }).whenComplete(Schedulers.javafx(), ((result, exception) -> {
            if (exception == null) {
                getItems().setAll(result);
            } else {
                LOG.warning("Failed to load resource packs", exception);
                getItems().clear();
            }
            setLoading(false);
        })).start();
    }

    public void addFiles(List<Path> files) {
        if (resourcePackManager == null) return;

        List<Path> failures = new ArrayList<>();
        for (Path file : files) {
            try {
                resourcePackManager.importResourcePack(file);
            } catch (Exception e) {
                LOG.warning("Failed to add resource pack", e);
                failures.add(file);
            }
        }
        if (!failures.isEmpty()) {
            StringBuilder failure = new StringBuilder(i18n("resourcepack.add.failed"));
            for (Path file: failures) {
                failure.append("\n").append(file.toString());
            }
            Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
        }

        refresh();
    }

    public void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("resourcepack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("resourcepack"), "*.zip"));
        List<Path> files = FileUtils.toPaths(fileChooser.showOpenMultipleDialog(Controllers.getStage()));
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void onDownload() {
        Controllers.getDownloadPage().showResourcePackDownloads().selectVersion(instanceId);
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (resourcePackDirectory != null) {
            FXUtils.openFolder(resourcePackDirectory);
        }
    }

    private void setSelectedEnabled(List<ResourcePackInfoObject> selectedItems, boolean enabled) {
        if (!ConfigHolder.config().getShownTips().containsKey(TIP_KEY) && enabled && !selectedItems.stream().map(ResourcePackInfoObject::getFile).allMatch(ResourcePackFile::isCompatible)) {
            Controllers.confirm(
                    i18n("resourcepack.warning.manipulate"),
                    i18n("message.warning"),
                    MessageDialogPane.MessageType.WARNING,
                    () -> {
                        ConfigHolder.config().getShownTips().put(TIP_KEY, 0);
                        setSelectedEnabled(selectedItems, true);
                    }, null);
        } else {
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

    public void checkUpdates(Collection<ResourcePackFile> resourcePacks) {
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            Optional<String> gameVersion = profile.getRepository().getGameVersion(instanceId);
                            return gameVersion.map(g -> new AddonCheckUpdatesTask<>(g, resourcePacks)).orElse(null);
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("mods.check_updates.failed_check"), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("mods.check_updates.empty"));
                            } else {
                                Controllers.navigateForward(new AddonUpdatesPage<>(resourcePackManager, result));
                            }
                        })
                        .withStagesHints("update.checking"),
                i18n("mods.check_updates"), TaskCancellationAction.NORMAL);

        if (profile.getRepository().isModpack(instanceId)) {
            Controllers.confirm(
                    i18n("resourcepack.update_in_modpack.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    private static final class ResourcePackListPageSkin extends SkinBase<ResourcePackListPage> {
        private final JFXListView<ResourcePackInfoObject> listView;
        private final JFXTextField searchField = new JFXTextField();

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        private boolean isSearching;

        private ResourcePackListPageSkin(ResourcePackListPage control) {
            super(control);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            listView = new JFXListView<>();

            {

                // Toolbar Selecting
                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                control.removeSelected(listView.getSelectionModel().getSelectedItems());
                            }, null);
                        }),
                        createToolbarButton2(i18n("button.enable"), SVG.CHECK, () ->
                                control.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), true)),
                        createToolbarButton2(i18n("button.disable"), SVG.CLOSE, () ->
                                control.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false)),
                        createToolbarButton2(i18n("mods.check_updates.button"), SVG.UPDATE, () ->
                                control.checkUpdates(
                                        listView.getSelectionModel().getSelectedItems().stream().map(ResourcePackInfoObject::getFile).toList()
                                )
                        ),
                        createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                                listView.getSelectionModel().selectAll()),
                        createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                                listView.getSelectionModel().clearSelection())
                );

                // Search Bar
                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(new Insets(0, 5, 0, 5));
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                PauseTransition pause = new PauseTransition(Duration.millis(100));
                pause.setOnFinished(e -> search());
                FXUtils.onChange(searchField.textProperty(), newValue -> {
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
                toolbarNormal.setAlignment(Pos.CENTER_LEFT);
                toolbarNormal.setPickOnBounds(false);
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, control::refresh),
                        createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, control::onAddFiles),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, control::onOpenFolder),
                        createToolbarButton2(i18n("mods.check_updates.button"), SVG.UPDATE, () ->
                                control.checkUpdates(listView.getItems().stream().map(ResourcePackInfoObject::getFile).toList())
                        ),
                        createToolbarButton2(i18n("download"), SVG.DOWNLOAD, control::onDownload),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
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
                center.loadingProperty().bind(control.loadingProperty());

                listView.setCellFactory(x -> new ResourcePackListCell(listView, control));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                Bindings.bindContent(listView.getItems(), control.getItems());

                listView.setOnContextMenuRequested(event -> {
                    ResourcePackInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ResourcePackInfoDialog(control, selectedItem));
                    }
                });

                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
                listView.getStyleClass().add("no-horizontal-scrollbar");

                center.setContent(listView);
                root.getContent().add(center);
            }

            pane.getChildren().setAll(root);
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
                for (ResourcePackInfoObject item : getSkinnable().getItems()) {
                    ResourcePackFile resourcePack = item.getFile();
                    var description = resourcePack.getDescription();
                    var descriptionParts = description == null
                            ? Stream.<String>empty()
                            : description.getParts().stream().map(LocalModFile.Description.Part::getText);
                    if (predicate.test(resourcePack.getFileNameWithExtension())
                            || predicate.test(resourcePack.getFileName())
                            || descriptionParts.anyMatch(predicate)) {
                        listView.getItems().add(item);
                    }
                }
            }
        }
    }

    public static class ResourcePackInfoObject {
        private final ResourcePackFile file;
        private final BooleanProperty enabled;
        private WeakReference<Image> iconCache;

        public ResourcePackInfoObject(ResourcePackFile file) {
            this.file = file;
            this.enabled = new SimpleBooleanProperty(this, "enabled", file.isEnabled());
            FXUtils.onChange(this.enabled, file::setEnabled);
        }

        public ResourcePackFile getFile() {
            return file;
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        Image getIcon() {
            Image image = null;
            if (iconCache != null && (image = iconCache.get()) != null) {
                return image;
            }
            byte[] iconData = file.getIcon();
            if (iconData != null) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(iconData)) {
                    image = new Image(inputStream, 64, 64, true, true);
                } catch (Exception e) {
                    LOG.warning("Failed to load resource pack icon " + file.getFile(), e);
                }
            }

            if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0 ||
                    (Math.abs(image.getWidth() - image.getHeight()) >= 1)) {
                image = FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
            }
            iconCache = new WeakReference<>(image);
            return image;
        }
    }

    private static final class ResourcePackListCell extends MDListCell<ResourcePackInfoObject> {
        private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

        private final ResourcePackListPage page;

        private final JFXCheckBox checkBox;
        private final ImageContainer imageContainer = new ImageContainer(24);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnInfo = new JFXButton();

        private ResourcePackInfoObject object = null;

        private BooleanProperty booleanProperty = null;

        public ResourcePackListCell(JFXListView<ResourcePackInfoObject> listView, ResourcePackListPage page) {
            super(listView);
            this.page = page;

            getStyleClass().add("resource-pack-list-cell");

            HBox root = new HBox(8);
            root.setPickOnBounds(false);
            root.setAlignment(Pos.CENTER_LEFT);

            checkBox = new JFXCheckBox() {
                @Override
                public void fire() {
                    if (!ConfigHolder.config().getShownTips().containsKey(TIP_KEY) && !isSelected() && object != null && !object.getFile().isCompatible()) {
                        Controllers.confirm(
                                i18n("resourcepack.warning.manipulate"),
                                i18n("message.info"),
                                MessageDialogPane.MessageType.INFO,
                                () -> {
                                    super.fire();
                                    ConfigHolder.config().getShownTips().put(TIP_KEY, 0);
                                }, null);
                    } else {
                        super.fire();
                    }
                }
            };

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER.createIcon());

            btnInfo.getStyleClass().add("toggle-icon4");
            btnInfo.setGraphic(SVG.INFO.createIcon());

            root.getChildren().setAll(checkBox, imageContainer, content, btnReveal, btnInfo);

            setSelectable();

            StackPane.setMargin(root, new Insets(8));
            getContainer().getChildren().add(root);
        }

        @Override
        protected void updateControl(ResourcePackInfoObject item, boolean empty) {
            pseudoClassStateChanged(WARNING, false);

            if (empty || item == null) {
                return;
            }
            this.object = item;
            ResourcePackFile file = item.getFile();
            imageContainer.setImage(item.getIcon());

            content.setTitle(file.getFileName());
            content.setSubtitle(file.getFileNameWithExtension());

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getFile()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ResourcePackInfoDialog(this.page, item)));

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = item.enabledProperty());

            {
                var compatibility = file.getCompatibility();
                if (compatibility == ResourcePackFile.Compatibility.COMPATIBLE) {
                    content.addTag(i18n("resourcepack.compatible"));
                } else {
                    pseudoClassStateChanged(WARNING, true);
                    content.addTagWarning(getWarning(compatibility));
                }
            }
        }
    }

    private static final class ResourcePackInfoDialog extends JFXDialogLayout {

        ResourcePackInfoDialog(ResourcePackListPage page, ResourcePackInfoObject packInfoObject) {
            ResourcePackFile pack = packInfoObject.getFile();

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            ImageContainer imageContainer = new ImageContainer(40);
            imageContainer.setImage(packInfoObject.getIcon());

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(pack.getFileName());
            title.setSubtitle(pack.getFileNameWithExtension());
            var compatibility = pack.getCompatibility();
            if (compatibility == ResourcePackFile.Compatibility.COMPATIBLE) {
                title.addTag(i18n("resourcepack.compatible"));
            } else {
                title.addTagWarning(getWarning(compatibility));
            }

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label(Objects.requireNonNullElse(pack.getDescription(), "").toString());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            FXUtils.onChange(description.heightProperty(), newVal -> {
                double maxHeight = stage.getHeight() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);

            for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                    pair("mods.curseforge", CurseForgeRemoteModRepository.RESOURCE_PACKS),
                    pair("mods.modrinth", ModrinthRemoteModRepository.RESOURCE_PACKS)
            )) {
                RemoteModRepository repository = item.getValue();
                JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                Task.runAsync(() -> {
                    Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(packInfoObject.getFile().getFile());
                    if (versionOptional.isPresent()) {
                        RemoteMod remoteMod = repository.getModById(versionOptional.get().getModid());
                        FXUtils.runInFX(() -> {
                            button.setOnAction(e -> {
                                fireEvent(new DialogCloseEvent());
                                Controllers.navigate(new DownloadPage(
                                        repository instanceof CurseForgeRemoteModRepository ? HMCLLocalizedDownloadListPage.ofCurseForgeMod(null, false) : HMCLLocalizedDownloadListPage.ofModrinthMod(null, false),
                                        remoteMod,
                                        new Profile.ProfileVersion(page.profile, page.instanceId),
                                        org.jackhuang.hmcl.ui.download.DownloadPage.FOR_RESOURCE_PACK
                                ));
                            });
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
