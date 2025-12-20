package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.resourcepack.ResourcePackManager;
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
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcePackListPage extends ListPageBase<ResourcePackListPage.ResourcePackInfoObject> implements VersionPage.VersionLoadable {
    private static @Nullable String getWarningKey(ResourcePackFile.Compatibility compatibility) {
        return switch (compatibility) {
            case TOO_NEW -> "resourcepack.warning.too_new";
            case TOO_OLD -> "resourcepack.warning.too_old";
            case INVALID -> "resourcepack.warning.invalid";
            case MISSING_PACK_META -> "resourcepack.warning.missing_pack_meta";
            case MISSING_GAME_META -> "resourcepack.warning.missing_game_meta";
            default -> null;
        };
    }

    private Profile profile;
    private String instanceId;

    private Path resourcePackDirectory;
    private ResourcePackManager resourcePackManager;

    private boolean warningShown = false;

    public ResourcePackListPage() {
        FXUtils.applyDragListener(this, file -> file.getFileName().toString().endsWith(".zip"), this::addFiles);
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
        this.resourcePackDirectory = this.resourcePackManager.getResourcePackDirectory();

        try {
            if (!Files.exists(resourcePackDirectory)) {
                Files.createDirectories(resourcePackDirectory);
            }
        } catch (IOException e) {
            LOG.warning("Failed to create resource pack directory" + resourcePackDirectory, e);
        }
        refresh();
    }

    public void refresh() {
        if (resourcePackManager == null || !Files.isDirectory(resourcePackDirectory)) return;
        setDisable(false);
        if (resourcePackManager.getMinecraftVersion().compareTo(ResourcePackManager.LEAST_MC_VERSION) < 0) {
            getItems().clear();
            setDisable(true);
            return;
        }
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            resourcePackManager.refreshResourcePacks();
            return resourcePackManager.getResourcePacks()
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

        try {
            for (Path file : files) {
                resourcePackManager.importResourcePack(file);
            }
        } catch (IOException e) {
            LOG.warning("Failed to add resource packs", e);
            Controllers.dialog(i18n("resourcepack.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
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
        Controllers.getDownloadPage().showResourcepackDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (resourcePackDirectory != null) {
            FXUtils.openFolder(resourcePackDirectory);
        }
    }

    private void setSelectedEnabled(List<ResourcePackInfoObject> selectedItems, boolean enabled) {
        if (!warningShown) {
            Controllers.confirm(i18n("resourcepack.warning.manipulate"), i18n("message.warning"),
                    () -> {
                        warningShown = true;
                        setSelectedEnabled(selectedItems, enabled);
                    }, null);
        } else {
            for (ResourcePackInfoObject item : selectedItems) {
                item.enabledProperty().set(enabled);
            }
        }
    }

    private void removeSelected(List<ResourcePackInfoObject> selectedItems) {
        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                () -> {
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
                }, null);
    }

    private static final class ResourcePackListPageSkin extends SkinBase<ResourcePackListPage> {
        private final JFXListView<ResourcePackInfoObject> listView;
        private final JFXTextField searchField = new JFXTextField();

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        private boolean isSearching;

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final ChangeListener<Boolean> holder;

        private ResourcePackListPageSkin(ResourcePackListPage control) {
            super(control);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            listView = new JFXListView<>();

            this.holder = FXUtils.onWeakChange(control.loadingProperty(), loading -> {
                if (!loading) {
                    listView.scrollTo(0);
                }
            });

            {

                // Toolbar Selecting
                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                control.removeSelected(listView.getSelectionModel().getSelectedItems());
                            }, null);
                        }),
                        createToolbarButton2(i18n("button.enable"), SVG.CHECK, () ->
                                control.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false)),
                        createToolbarButton2(i18n("button.disable"), SVG.CLOSE, () ->
                                control.setSelectedEnabled(listView.getSelectionModel().getSelectedItems(), false)),
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
                toolbarNormal.setAlignment(Pos.CENTER_LEFT);
                toolbarNormal.setPickOnBounds(false);
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, control::refresh),
                        createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, control::onAddFiles),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, control::onOpenFolder),
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
                center.getStyleClass().add("large-spinner-pane");
                center.loadingProperty().bind(control.loadingProperty());

                Holder<Object> lastCell = new Holder<>();
                listView.setCellFactory(x -> new ResourcePackListCell(listView, lastCell, control));
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
                for (ResourcePackInfoObject item : getSkinnable().getItems()) {
                    ResourcePackFile resourcePack = item.getFile();
                    if (predicate.test(resourcePack.getFileName())
                            || predicate.test(resourcePack.getName())
                            || predicate.test(resourcePack.getFileName())) {
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
            this.enabled.addListener(__ -> file.setEnabled(enabled.get()));
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
                    LOG.warning("Failed to load resource pack icon " + file.getPath(), e);
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
        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnInfo = new JFXButton();

        private Tooltip warningTooltip = null;

        private BooleanProperty booleanProperty = null;

        public ResourcePackListCell(JFXListView<ResourcePackInfoObject> listView, Holder<Object> lastCell, ResourcePackListPage page) {
            super(listView, lastCell);
            this.page = page;

            getStyleClass().add("resource-pack-list-cell");

            HBox root = new HBox(8);
            root.setPickOnBounds(false);
            root.setAlignment(Pos.CENTER_LEFT);

            checkBox = new JFXCheckBox() {
                @Override
                public void fire() {
                    if (!page.warningShown) {
                        Controllers.confirm(i18n("resourcepack.warning.manipulate"), i18n("message.warning"),
                                () -> {
                                    super.fire();
                                    page.warningShown = true;
                                }, null);
                    } else {
                        super.fire();
                    }
                }
            };

            imageView.setFitWidth(24);
            imageView.setFitHeight(24);
            imageView.setPreserveRatio(true);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(FXUtils.limitingSize(SVG.FOLDER.createIcon(24), 24, 24));

            btnInfo.getStyleClass().add("toggle-icon4");
            btnInfo.setGraphic(FXUtils.limitingSize(SVG.INFO.createIcon(24), 24, 24));

            root.getChildren().setAll(checkBox, imageView, content, btnReveal, btnInfo);

            setSelectable();

            StackPane.setMargin(root, new Insets(8));
            getContainer().getChildren().add(root);
        }

        @Override
        protected void updateControl(ResourcePackInfoObject item, boolean empty) {
            pseudoClassStateChanged(WARNING, false);
            if (warningTooltip != null) {
                Tooltip.uninstall(this, warningTooltip);
                warningTooltip = null;
            }

            if (empty || item == null) {
                return;
            }

            ResourcePackFile file = item.getFile();
            imageView.setImage(item.getIcon());

            content.setTitle(file.getName());
            content.setSubtitle(file.getFileName());

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getPath()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ResourcePackInfoDialog(this.page, item)));

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = item.enabledProperty());

            {
                String warningKey = getWarningKey(file.getCompatibility());
                if (warningKey != null) {
                    pseudoClassStateChanged(WARNING, true);
                    FXUtils.installFastTooltip(this, warningTooltip = new Tooltip(i18n(warningKey)));
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

            ImageView imageView = new ImageView();
            imageView.setImage(packInfoObject.getIcon());
            FXUtils.limitSize(imageView, 40, 40);

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(pack.getName());
            title.setSubtitle(pack.getFileName());
            if (pack.getCompatibility() == ResourcePackFile.Compatibility.COMPATIBLE) {
                title.addTag(i18n("resourcepack.compatible"));
            } else {
                title.addTagWarning(i18n(getWarningKey(packInfoObject.file.getCompatibility())));
            }

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(Objects.requireNonNullElse(pack.getDescription(), "").toString());
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

            for (Pair<String, ? extends RemoteModRepository> item : Arrays.asList(
                    pair("mods.curseforge", CurseForgeRemoteModRepository.RESOURCE_PACKS),
                    pair("mods.modrinth", ModrinthRemoteModRepository.RESOURCE_PACKS)
            )) {
                RemoteModRepository repository = item.getValue();
                JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                Task.runAsync(() -> {
                    Optional<RemoteMod.Version> versionOptional = repository.getRemoteVersionByLocalFile(packInfoObject.getFile().getPath());
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
