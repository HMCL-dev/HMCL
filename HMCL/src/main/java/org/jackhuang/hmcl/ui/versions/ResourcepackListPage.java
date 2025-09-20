package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ResourcepackListPage extends ListPageBase<ResourcepackListPage.ResourcepackItem> implements VersionPage.VersionLoadable {
    private Path resourcepackDirectory;

    public ResourcepackListPage() {
        FXUtils.applyDragListener(this, file -> file.isFile() && file.getName().endsWith(".zip"), files -> addFiles(files.stream().map(File::toPath).collect(Collectors.toList())));
    }

    private static Node createIcon(Path img) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(32);
        imageView.setFitHeight(32);

        if (Files.exists(img)) {
            try (InputStream is = Files.newInputStream(img)) {
                Image image = new Image(is);
                imageView.setImage(image);
            } catch (IOException ignored) {
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
            LOG.error("Failed to create resourcepack directory", e);
        }
        refresh();
    }

    public void refresh() {
        Task.runAsync(Schedulers.javafx(), this::load).whenComplete(Schedulers.javafx(), (result, exception) -> setLoading(false)).start();
        setLoading(true);
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
            Controllers.dialog(i18n("resourcepack.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to add resourcepacks", e);
        }
    }

    public void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("resourcepack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("resourcepack"), "*.zip"));
        List<File> files = fileChooser.showOpenMultipleDialog(Controllers.getStage());
        if (files != null && !files.isEmpty()) {
            addFiles(files.stream().map(File::toPath).collect(Collectors.toList()));
        }
    }

    private void load() {
        itemsProperty().clear();
        if (resourcepackDirectory == null || !Files.exists(resourcepackDirectory)) return;

        try (Stream<Path> stream = Files.list(resourcepackDirectory)) {
            stream.forEach(path -> {
                try {
                    itemsProperty().add(new ResourcepackItem(ResourcepackFile.parse(path)));
                } catch (Exception e) {
                    LOG.warning("Failed to load resourcepacks " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            LOG.warning("Failed to list resourcepacks directory", e);
        }

        itemsProperty().sort(Comparator.comparing(item -> item.getFile().getName()));
    }

    private void onDownload() {
        runInFX(() -> {
            Controllers.getDownloadPage().showResourcepackDownloads();
            Controllers.navigate(Controllers.getDownloadPage());
        });
    }

    private static class ResourcepackListPageSkin extends ToolbarListPageSkin<ResourcepackListPage> {
        protected ResourcepackListPageSkin(ResourcepackListPage control) {
            super(control);
        }

        @Override
        protected List<Node> initializeToolbar(ResourcepackListPage skinnable) {
            return Arrays.asList(createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh), createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, skinnable::onAddFiles), createToolbarButton2(i18n("resourcepack.download"), SVG.DOWNLOAD, skinnable::onDownload));
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
                if (file.getFile().isDirectory()) {
                    FileUtils.deleteDirectory(file.getFile());
                } else {
                    Files.delete(file.getFile().toPath());
                }
                ResourcepackListPage.this.refresh();
            } catch (IOException e) {
                Controllers.dialog(i18n("resourcepack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                LOG.warning("Failed to delete resourcepack", e);
            }
        }

        public void onReveal() {
            FXUtils.showFileInExplorer(file.getFile().toPath());
        }

        public ResourcepackFile getFile() {
            return file;
        }
    }

    private class ResourcepackItemSkin extends SkinBase<ResourcepackItem> {
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
            center.setSubtitle(item.getFile().getDescription());
            root.setCenter(center);

            HBox right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);
            JFXButton btnReveal = new JFXButton();
            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.getStyleClass().add("toggle-icon4");
            btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));
            btnReveal.setOnAction(event -> item.onReveal());

            JFXButton btnDelete = new JFXButton();
            btnDelete.getStyleClass().add("toggle-icon4");
            btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon(Theme.blackFill(), -1));
            btnDelete.setOnAction(event -> Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), item::onDelete, null));
            right.getChildren().setAll(btnReveal, btnDelete);
            root.setRight(right);

            this.getChildren().add(new RipplerContainer(root));
        }
    }
}
