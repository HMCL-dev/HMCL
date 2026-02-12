package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.resourcepack.ResourcepackFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
                getItems().setAll(result);
            } else {
                LOG.warning("Failed to load resourcepacks", exception);
                getItems().clear();
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
        fileChooser.setTitle(i18n("resourcepack.add.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.resourcepack"), "*.zip"));
        List<Path> files = FileUtils.toPaths(fileChooser.showOpenMultipleDialog(Controllers.getStage()));
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void onDownload() {
        Controllers.getDownloadPage().showResourcepackDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private static final class ResourcepackListPageSkin extends ToolbarListPageSkin<ResourcepackInfoObject, ResourcepackListPage> {

        public ResourcepackListPageSkin(ResourcepackListPage control) {
            super(control);
        }

        @Override
        protected List<Node> initializeToolbar(ResourcepackListPage skinnable) {
            return List.of(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, skinnable::onAddFiles),
                    createToolbarButton2(i18n("resourcepack.download"), SVG.DOWNLOAD, skinnable::onDownload)
            );
        }

        @Override
        protected ListCell<ResourcepackInfoObject> createListCell(JFXListView<ResourcepackInfoObject> listView) {
            return new ResourcepackListCell(listView, getSkinnable());
        }
    }

    public static class ResourcepackInfoObject {
        private final ResourcepackFile file;
        private WeakReference<Image> iconCache;

        public ResourcepackInfoObject(ResourcepackFile file) {
            this.file = file;
        }

        public ResourcepackFile getFile() {
            return file;
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
                    LOG.warning("Failed to load resourcepack icon " + file.getPath(), e);
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

    private static final class ResourcepackListCell extends MDListCell<ResourcepackInfoObject> {
        private final ImageView imageView = new ImageView();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = new JFXButton();
        private final JFXButton btnDelete = new JFXButton();
        private final ResourcepackListPage page;

        public ResourcepackListCell(JFXListView<ResourcepackInfoObject> listView, ResourcepackListPage page) {
            super(listView);

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
        protected void updateControl(ResourcepackListPage.ResourcepackInfoObject item, boolean empty) {
            if (empty || item == null) {
                return;
            }

            ResourcepackFile file = item.getFile();
            imageView.setImage(item.getIcon());

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
