package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
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
import org.jackhuang.hmcl.resourcepack.ResourcepackFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.image.ImageUtils;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackListPage extends ListPageBase<ResourcepackListPage.ResourcepackInfoObject> implements VersionPage.VersionLoadable {
    private Path resourcepackDirectory;

    public ResourcepackListPage() {
        FXUtils.applyDragListener(this, file -> file.getFileName().toString().endsWith(".zip"), this::addFiles);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ResourcepackListPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.resourcepackDirectory = profile.getRepository().getResourcepacksDirectory(version);

        try {
            if (!Files.exists(resourcepackDirectory)) {
                Files.createDirectories(resourcepackDirectory);
            }
        } catch (IOException e) {
            LOG.warning("Failed to create resourcepack directory" + resourcepackDirectory, e);
        }
        refresh();
    }

    public void refresh() {
        if (resourcepackDirectory == null || !Files.isDirectory(resourcepackDirectory)) return;
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            try (Stream<Path> stream = Files.list(resourcepackDirectory)) {
                return stream.sorted(Comparator.comparing(FileUtils::getName))
                        .flatMap(item -> {
                            try {
                                return Stream.of(ResourcepackFile.parse(item)).filter(Objects::nonNull).map(ResourcepackInfoObject::new);
                            } catch (IOException e) {
                                LOG.warning("Failed to load resourcepack " + item, e);
                                return Stream.empty();
                            }
                        })
                        .toList();
            }
        }).whenComplete(Schedulers.javafx(), ((result, exception) -> {
            if (exception == null) {
                itemsProperty().setAll(result);
            } else {
                LOG.warning("Failed to load resourcepacks", exception);
            }
            setLoading(false);
        })).start();
    }

    public void addFiles(List<Path> files) {
        if (resourcepackDirectory == null) return;

        try {
            for (Path file : files) {
                Path target = resourcepackDirectory.resolve(file.getFileName());
                if (!Files.exists(target)) {
                    Files.copy(file, target);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to add resourcepacks", e);
            Controllers.dialog(i18n("resourcepack.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
        }

        refresh();
    }

    public void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("resourcepack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("resourcepack"), "*.zip"));
        List<File> files = fileChooser.showOpenMultipleDialog(Controllers.getStage());
        if (files != null && !files.isEmpty()) {
            addFiles(FileUtils.toPaths(files));
        }
    }

    private void onDownload() {
        Controllers.getDownloadPage().showResourcepackDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private static final class ResourcepackListPageSkin extends SkinBase<ResourcepackListPage> {
        private final JFXListView<ResourcepackInfoObject> listView;

        private ResourcepackListPageSkin(ResourcepackListPage control) {
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
            listView.setCellFactory(x -> new ResourcepackListCell(listView, lastCell, control));
            Bindings.bindContent(listView.getItems(), control.getItems());

            center.setContent(listView);
            root.getContent().add(center);

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
        }
    }

    public static class ResourcepackInfoObject {
        private final ResourcepackFile file;
        private SoftReference<CompletableFuture<Image>> iconCache;

        public ResourcepackInfoObject(ResourcepackFile file) {
            this.file = file;
        }

        public ResourcepackFile getFile() {
            return file;
        }

        Image loadIcon() {
            Image image = null;
            byte[] icon = file.getIcon();
            if (icon != null) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(icon)) {
                    image = ImageUtils.DEFAULT.load(inputStream, 64, 64, true, true);
                } catch (Exception e) {
                    LOG.warning("Failed to load resourcepack icon " + file.getPath(), e);
                }
            }

            if (image != null && !image.isError() && image.getWidth() > 0 && image.getHeight() > 0 &&
                    Math.abs(image.getWidth() - image.getHeight()) < 1) {
                return image;
            } else {
                return FXUtils.newBuiltinImage("/assets/img/unknown_pack.png");
            }
        }

        public void loadIcon(ImageView imageView, @Nullable WeakReference<ObjectProperty<ResourcepackInfoObject>> current) {
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

            imageFuture.thenAcceptAsync(image -> {
                if (current != null) {
                    ObjectProperty<ResourcepackInfoObject> infoObjectProperty = current.get();
                    if (infoObjectProperty == null || infoObjectProperty.get() != this) {
                        // The current ListCell has already switched to another object
                        return;
                    }
                }

                imageView.setImage(image);
            }, Schedulers.javafx());
        }
    }

    private static final class ResourcepackListCell extends MDListCell<ResourcepackInfoObject> {
        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnDelete = new JFXButton();
        private final ResourcepackListPage page;

        public ResourcepackListCell(JFXListView<ResourcepackInfoObject> listView, Holder<Object> lastCell, ResourcepackListPage page) {
            super(listView, lastCell);

            this.page = page;

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            HBox left = new HBox(8);
            left.setAlignment(Pos.CENTER);
            FXUtils.limitSize(imageView, 32, 32);
            left.getChildren().add(imageView);
            left.setPadding(new Insets(0, 8, 0, 0));
            FXUtils.setLimitWidth(left, 48);
            root.setLeft(left);

            HBox.setHgrow(content, Priority.ALWAYS);
            root.setCenter(content);

            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));

            btnDelete.getStyleClass().add("toggle-icon4");
            btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon(Theme.blackFill(), -1));

            HBox right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.getChildren().setAll(btnReveal, btnDelete);
            root.setRight(right);

            getContainer().getChildren().add(new RipplerContainer(root));
        }

        @Override
        protected void updateControl(ResourcepackListPage.ResourcepackInfoObject item, boolean empty) {
            if (empty || item == null) {
                return;
            }

            ResourcepackFile file = item.getFile();

            item.loadIcon(imageView, new WeakReference<>(this.itemProperty()));

            content.setTitle(file.getName());
            LocalModFile.Description description = file.getDescription();
            content.setSubtitle(description != null ? description.toString() : "");

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(file.getPath()));

            btnDelete.setOnAction(event ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            () -> onDelete(file), null));
        }

        private void onDelete(ResourcepackFile file) {
            try {
                if (Files.isDirectory(file.getPath())) {
                    FileUtils.deleteDirectory(file.getPath());
                } else {
                    Files.delete(file.getPath());
                }
                page.refresh();
            } catch (IOException e) {
                Controllers.dialog(i18n("resourcepack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                LOG.warning("Failed to delete resourcepack", e);
            }
        }
    }
}
