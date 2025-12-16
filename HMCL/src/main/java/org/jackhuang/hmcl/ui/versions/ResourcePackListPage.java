package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.LocalModFile;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcePackListPage extends ListPageBase<ResourcePackListPage.ResourcePackInfoObject> implements VersionPage.VersionLoadable {
    private Path resourcePackDirectory;
    private ResourcePackManager resourcePackManager;

    public ResourcePackListPage() {
        FXUtils.applyDragListener(this, file -> file.getFileName().toString().endsWith(".zip"), this::addFiles);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ResourcePackListPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.resourcePackDirectory = profile.getRepository().getResourcePackDirectory(version);
        this.resourcePackManager = new ResourcePackManager(profile.getRepository(), version);

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
        private final JFXCheckBox checkBox = new JFXCheckBox();
        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnDelete = new JFXButton();
        private final ResourcePackListPage page;

        private BooleanProperty booleanProperty = null;

        public ResourcePackListCell(JFXListView<ResourcePackInfoObject> listView, Holder<Object> lastCell, ResourcePackListPage page) {
            super(listView, lastCell);

            this.page = page;

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(8));

            HBox left = new HBox(8);
            left.setAlignment(Pos.CENTER);
            FXUtils.limitSize(imageView, 32, 32);
            left.getChildren().addAll(checkBox, imageView);
            left.setPadding(new Insets(0, 8, 0, 0));
            FXUtils.setLimitWidth(left, 48);
            root.setLeft(left);

            HBox.setHgrow(content, Priority.ALWAYS);
            root.setCenter(content);

            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon());

            btnDelete.getStyleClass().add("toggle-icon4");
            btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon());

            HBox right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.getChildren().setAll(btnReveal, btnDelete);
            root.setRight(right);

            getContainer().getChildren().add(new RipplerContainer(root));
        }

        @Override
        protected void updateControl(ResourcePackInfoObject item, boolean empty) {
            if (empty || item == null) {
                return;
            }

            ResourcePackFile file = item.getFile();
            imageView.setImage(item.getIcon());

            content.setTitle(file.getName());
            LocalModFile.Description description = file.getDescription();
            content.setSubtitle(description != null ? description.toString() : "");

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getPath()));

            btnDelete.setOnAction(event ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            () -> onDelete(file), null));

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = item.enabledProperty());
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
}
