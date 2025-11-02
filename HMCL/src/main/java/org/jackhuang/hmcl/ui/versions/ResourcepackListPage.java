package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.resourcepack.ResourcepackFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackListPage extends ListPageBase<ResourcepackListPage.ResourcepackItem> implements VersionPage.VersionLoadable {
    private Path resourcepackDirectory;

    public ResourcepackListPage() {
        FXUtils.applyDragListener(this, file -> file.getFileName().toString().endsWith(".zip"), this::addFiles);
    }

    private static Node createIcon(Path img) {
        ImageView imageView = new ImageView();
        FXUtils.limitSize(imageView, 32, 32);

        if (img != null && Files.exists(img)) {
            try {
                imageView.setImage(FXUtils.loadImage(img, 64, 64, true, true));
            } catch (Exception e) {
                LOG.warning("Failed to load image " + img, e);
            }
        }

        if (imageView.getImage() == null) {
            imageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_pack.png"));
        }

        return imageView;
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
                                return Stream.of(ResourcepackFile.parse(item)).filter(Objects::nonNull).map(ResourcepackItem::new);
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

    private static final class ResourcepackListPageSkin extends ToolbarListPageSkin<ResourcepackListPage> {
        private ResourcepackListPageSkin(ResourcepackListPage control) {
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
    }

    public class ResourcepackItem extends Control {
        private final ResourcepackFile file;
//        final JFXCheckBox checkBox = new JFXCheckBox();

        public ResourcepackItem(ResourcepackFile file) {
            this.file = file;
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new ResourcepackItemSkin(this);
        }

        public void onDelete() {
            try {
                if (Files.isDirectory(file.getPath())) {
                    FileUtils.deleteDirectory(file.getPath());
                } else {
                    Files.delete(file.getPath());
                }
                ResourcepackListPage.this.refresh();
            } catch (IOException e) {
                Controllers.dialog(i18n("resourcepack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                LOG.warning("Failed to delete resourcepack", e);
            }
        }

        public void onReveal() {
            FXUtils.showFileInExplorer(file.getPath());
        }

        public ResourcepackFile getFile() {
            return file;
        }
    }

    private final class ResourcepackItemSkin extends SkinBase<ResourcepackItem> {
        public ResourcepackItemSkin(ResourcepackItem item) {
            super(item);
            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            HBox left = new HBox(8);
            left.setAlignment(Pos.CENTER);
            left.getChildren().addAll(createIcon(item.getFile().getIcon()));
//            left.getChildren().addAll(item.checkBox, createIcon(item.getFile().getIcon()));
            left.setPadding(new Insets(0, 8, 0, 0));
//            FXUtils.setLimitWidth(left, 64);
            FXUtils.setLimitWidth(left, 48);
            root.setLeft(left);

            TwoLineListItem center = new TwoLineListItem();
//            center.setPadding(new Insets(0, 0, 0, 8));
            center.setTitle(item.getFile().getName());
            LocalModFile.Description description = item.getFile().getDescription();
            center.setSubtitle(description != null ? description.toString() : "");
            root.setCenter(center);

            JFXButton btnReveal = new JFXButton();
            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));
            btnReveal.setOnAction(event -> item.onReveal());

            JFXButton btnDelete = new JFXButton();
            btnDelete.getStyleClass().add("toggle-icon4");
            btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon(Theme.blackFill(), -1));
            btnDelete.setOnAction(event ->
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            item::onDelete, null));

            HBox right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.getChildren().setAll(btnReveal, btnDelete);
            root.setRight(right);

            this.getChildren().add(new RipplerContainer(root));
        }
    }
}
