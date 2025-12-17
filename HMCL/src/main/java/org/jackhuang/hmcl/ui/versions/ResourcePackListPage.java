package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import org.jackhuang.hmcl.resourcepack.ResourcePackFile;
import org.jackhuang.hmcl.resourcepack.ResourcePackManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
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

    private static final class ResourcePackListPageSkin extends SkinBase<ResourcePackListPage> {
        private final JFXListView<ResourcePackInfoObject> listView;

        private ResourcePackListPageSkin(ResourcePackListPage control) {
            super(control);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            listView = new JFXListView<>();

            root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (listView.getSelectionModel().getSelectedItem() != null) {
                        listView.getSelectionModel().clearSelection();
                        e.consume();
                    }
                }
            });
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            HBox toolbar = new HBox();
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.setPickOnBounds(false);
            toolbar.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, control::refresh),
                    createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, control::onAddFiles),
                    createToolbarButton2(i18n("resourcepack.download"), SVG.DOWNLOAD, control::onDownload)
            );
            root.getContent().add(toolbar);

            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.loadingProperty().bind(control.loadingProperty());

            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory(x -> new ResourcePackListCell(listView, lastCell, control));
            Bindings.bindContent(listView.getItems(), control.getItems());

            center.setContent(listView);
            root.getContent().add(center);

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
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

        private final JFXCheckBox checkBox;
        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnDelete = new JFXButton();
        private final JFXButton btnInfo = new JFXButton();
        private final ResourcePackListPage page;

        private Tooltip warningTooltip = null;

        private BooleanProperty booleanProperty = null;

        public ResourcePackListCell(JFXListView<ResourcePackInfoObject> listView, Holder<Object> lastCell, ResourcePackListPage page) {
            super(listView, lastCell);

            getStyleClass().add("resource-pack-list-cell");

            this.page = page;

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

            btnDelete.getStyleClass().add("toggle-icon4");
            btnDelete.setGraphic(FXUtils.limitingSize(SVG.DELETE_FOREVER.createIcon(24), 24, 24));

            btnInfo.getStyleClass().add("toggle-icon4");
            btnInfo.setGraphic(FXUtils.limitingSize(SVG.INFO.createIcon(24), 24, 24));

            root.getChildren().setAll(checkBox, imageView, content, btnReveal, btnDelete, btnInfo);

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

            btnInfo.setOnAction(e -> Controllers.dialog(new ResourcePackInfoDialog(item)));

            btnDelete.setOnAction(event ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            () -> onDelete(file), null));

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

        private void onDelete(ResourcePackFile file) {
            try {
                if (page.resourcePackManager != null) {
                    page.resourcePackManager.removeResourcePacks(file);
                    page.refresh();
                }
            } catch (IOException e) {
                Controllers.dialog(i18n("resourcepack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                LOG.warning("Failed to delete resource pack", e);
            }
        }
    }

    private static final class ResourcePackInfoDialog extends JFXDialogLayout {

        ResourcePackInfoDialog(ResourcePackInfoObject packInfoObject) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            ImageView imageView = new ImageView();
            imageView.setImage(packInfoObject.getIcon());
            FXUtils.limitSize(imageView, 40, 40);

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(packInfoObject.file.getName());
            title.setSubtitle(packInfoObject.file.getFileName());
            if (packInfoObject.file.getCompatibility() == ResourcePackFile.Compatibility.COMPATIBLE) {
                title.addTag(i18n("resourcepack.compatible"));
            } else {
                title.addTagWarning(i18n(getWarningKey(packInfoObject.file.getCompatibility())));
            }

            titleContainer.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), title);
            setHeading(titleContainer);

            Label description = new Label(Objects.requireNonNullElse(packInfoObject.file.getDescription(), "").toString());
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

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }
}
