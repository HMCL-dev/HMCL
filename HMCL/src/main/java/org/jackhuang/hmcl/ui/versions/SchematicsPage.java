/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.schematic.LitematicFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class SchematicsPage extends ListPageBase<SchematicsPage.Item> implements VersionPage.VersionLoadable {

    private static String translateAuthorName(String author) {
        if (I18n.isUseChinese() && "hsds".equals(author)) {
            return "黑山大叔";
        }
        return author;
    }

    private Path schematicsDirectory;
    private DirItem currentDirectory;

    public SchematicsPage() {
        FXUtils.applyDragListener(this,
                file -> currentDirectory != null && Files.isRegularFile(file) && FileUtils.getName(file).endsWith(".litematic"),
                this::addFiles
        );
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SchematicsPageSkin();
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.schematicsDirectory = profile.getRepository().getSchematicsDirectory(version);

        refresh();
    }

    public void refresh() {
        Path schematicsDirectory = this.schematicsDirectory;
        if (schematicsDirectory == null) return;

        setLoading(true);
        Task.supplyAsync(() -> loadAll(schematicsDirectory, null))
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    setLoading(false);
                    if (exception == null) {
                        DirItem target = result;
                        if (currentDirectory != null) {
                            loop:
                            for (int i = 0; i < currentDirectory.relativePath.size(); i++) {
                                String dirName = currentDirectory.relativePath.get(i);

                                for (Item child : target.children) {
                                    if (child instanceof DirItem && child.getName().equals(dirName)) {
                                        target = (DirItem) child;
                                        continue loop;
                                    }
                                }
                                break;
                            }
                        }

                        navigateTo(target);
                    } else {
                        LOG.warning("Failed to load schematics", exception);
                    }
                }).start();
    }

    public void addFiles(List<Path> files) {
        if (currentDirectory == null)
            return;

        Path dir = currentDirectory.path;
        try {
            // Can be executed in the background, but be careful that users can call loadVersion during this time
            Files.createDirectories(dir);
            for (Path file : files) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
            refresh();
        } catch (FileAlreadyExistsException ignored) {
        } catch (IOException e) {
            Controllers.dialog(i18n("schematics.add.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to add schematics to " + dir, e);
        }
    }

    public void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("schematics.add.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                i18n("extension.schematic"), "*.litematic"));
        List<Path> files = FileUtils.toPaths(fileChooser.showOpenMultipleDialog(Controllers.getStage()));
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    public void onCreateDirectory() {
        if (currentDirectory == null)
            return;

        Path parent = currentDirectory.path;
        Controllers.dialog(new InputDialogPane(
                i18n("schematics.create_directory.prompt"),
                "",
                (result, handler) -> {
                    if (StringUtils.isBlank(result)) {
                        handler.reject(i18n("schematics.create_directory.failed.empty_name"));
                        return;
                    }

                    if (result.contains("/") || result.contains("\\") || !FileUtils.isNameValid(result)) {
                        handler.reject(i18n("schematics.create_directory.failed.invalid_name"));
                        return;
                    }

                    Path targetDir = parent.resolve(result);
                    if (Files.exists(targetDir)) {
                        handler.reject(i18n("schematics.create_directory.failed.already_exists"));
                        return;
                    }

                    try {
                        Files.createDirectories(targetDir);
                        handler.resolve();
                        refresh();
                    } catch (IOException e) {
                        LOG.warning("Failed to create directory: " + targetDir, e);
                        handler.reject(i18n("schematics.create_directory.failed", targetDir));
                    }
                }));
    }

    private DirItem loadAll(Path dir, @Nullable DirItem parent) {
        DirItem item = new DirItem(dir, parent);

        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : Lang.toIterable(stream)) {
                if (Files.isDirectory(path)) {
                    item.children.add(loadAll(path, item));
                } else if (path.getFileName().toString().endsWith(".litematic") && Files.isRegularFile(path)) {
                    try {
                        item.children.add(new LitematicFileItem(LitematicFile.load(path)));
                    } catch (IOException e) {
                        LOG.warning("Failed to load litematic file: " + path, e);
                    }
                }
            }
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            LOG.warning("Failed to load schematics in " + dir, e);
        }

        item.children.sort(Comparator.naturalOrder());
        return item;
    }

    private void navigateTo(DirItem item) {
        currentDirectory = item;
        getItems().clear();
        if (item.parent != null) {
            getItems().add(new BackItem(item.parent));
        }
        getItems().addAll(item.children);
    }

    abstract sealed class Item implements Comparable<Item> {

        boolean isDirectory() {
            return this instanceof DirItem;
        }

        abstract Path getPath();

        abstract String getName();

        abstract String getDescription();

        abstract SVG getIcon();

        Node getIcon(int size) {
            StackPane icon = new StackPane();
            icon.setPrefSize(size, size);
            icon.setMaxSize(size, size);
            icon.getChildren().add(getIcon().createIcon(size));
            return icon;
        }

        abstract int order();

        abstract void onClick();

        abstract void onReveal();

        abstract void onDelete();

        @Override
        public int compareTo(@NotNull SchematicsPage.Item o) {
            if (this.order() != o.order())
                return Integer.compare(this.order(), o.order());

            return this.getName().compareTo(o.getName());
        }
    }

    private final class BackItem extends Item {

        private final DirItem parent;

        BackItem(DirItem parent) {
            this.parent = parent;
        }

        @Override
        int order() {
            return 0;
        }

        @Override
        Path getPath() {
            return null;
        }

        @Override
        String getName() {
            return "..";
        }

        @Override
        String getDescription() {
            return i18n("schematics.back_to", parent.getName());
        }

        @Override
        SVG getIcon() {
            return SVG.FOLDER;
        }

        @Override
        void onClick() {
            navigateTo(parent);
        }

        @Override
        void onReveal() {
            throw new UnsupportedOperationException("Unreachable");
        }

        @Override
        void onDelete() {
            throw new UnsupportedOperationException("Unreachable");
        }
    }

    private final class DirItem extends Item {
        final Path path;
        final @Nullable DirItem parent;
        final List<Item> children = new ArrayList<>();
        final List<String> relativePath;

        DirItem(Path path, @Nullable DirItem parent) {
            this.path = path;
            this.parent = parent;

            if (parent != null) {
                this.relativePath = new ArrayList<>(parent.relativePath);
                relativePath.add(path.getFileName().toString());
            } else {
                this.relativePath = Collections.emptyList();
            }
        }

        @Override
        int order() {
            return 1;
        }

        @Override
        Path getPath() {
            return path;
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        String getDescription() {
            return i18n("schematics.sub_items", children.size());
        }

        @Override
        SVG getIcon() {
            return SVG.FOLDER;
        }

        @Override
        void onClick() {
            navigateTo(this);
        }

        @Override
        void onReveal() {
            FXUtils.openFolder(path);
        }

        @Override
        void onDelete() {
            try {
                FileUtils.cleanDirectory(path);
                Files.deleteIfExists(path);
                refresh();
            } catch (IOException e) {
                LOG.warning("Failed to delete directory: " + path, e);
            }
        }
    }

    private final class LitematicFileItem extends Item {
        final LitematicFile file;
        final String name;
        final Image image;

        private LitematicFileItem(LitematicFile file) {
            this.file = file;

            String name = file.getName();
            if (name != null && !"Unnamed".equals(name)) {
                this.name = name;
            } else {
                this.name = StringUtils.removeSuffix(file.getFile().getFileName().toString(), ".litematic");
            }

            WritableImage image = null;
            int[] previewImageData = file.getPreviewImageData();
            if (previewImageData != null && previewImageData.length > 0) {
                int size = (int) Math.sqrt(previewImageData.length);
                if ((size * size) == previewImageData.length) {
                    image = new WritableImage(size, size);
                    PixelWriter pixelWriter = image.getPixelWriter();

                    for (int y = 0, i = 0; y < size; ++y) {
                        for (int x = 0; x < size; ++x) {
                            pixelWriter.setArgb(x, y, previewImageData[i++]);
                        }
                    }

                }
            }
            this.image = image;
        }

        @Override
        int order() {
            return 2;
        }

        @Override
        Path getPath() {
            return file.getFile();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        String getDescription() {
            return file.getFile().getFileName().toString();
        }

        @Override
        SVG getIcon() {
            return SVG.SCHEMA;
        }

        public @Nullable Image getImage() {
            return image;
        }

        Node getIcon(int size) {
            if (image == null) {
                return super.getIcon(size);
            } else {
                ImageView imageView = new ImageView();
                imageView.setFitHeight(size);
                imageView.setFitWidth(size);
                imageView.setImage(image);
                return imageView;
            }
        }

        @Override
        void onClick() {
            Controllers.dialog(new LitematicInfoDialog());
        }

        @Override
        void onReveal() {
            FXUtils.showFileInExplorer(file.getFile());
        }

        @Override
        void onDelete() {
            try {
                Files.deleteIfExists(file.getFile());
                refresh();
            } catch (IOException e) {
                LOG.warning("Failed to delete litematic file: " + file.getFile(), e);
            }
        }

        private final class LitematicInfoDialog extends JFXDialogLayout {
            private final ComponentList details;

            private void addDetailItem(String key, Object detail) {
                BorderPane borderPane = new BorderPane();
                borderPane.setLeft(new Label(key));
                borderPane.setRight(new Label(detail.toString()));
                details.getContent().add(borderPane);
            }

            private void updateContent(LitematicFile file) {
                details.getContent().clear();
                addDetailItem(i18n("schematics.info.name"), file.getName());
                if (StringUtils.isNotBlank(file.getAuthor()))
                    addDetailItem(i18n("schematics.info.schematic_author"), translateAuthorName(file.getAuthor()));
                if (file.getTimeCreated() != null)
                    addDetailItem(i18n("schematics.info.time_created"), I18n.formatDateTime(file.getTimeCreated()));
                if (file.getTimeModified() != null && !file.getTimeModified().equals(file.getTimeCreated()))
                    addDetailItem(i18n("schematics.info.time_modified"), I18n.formatDateTime(file.getTimeModified()));
                if (file.getRegionCount() > 0)
                    addDetailItem(i18n("schematics.info.region_count"), String.valueOf(file.getRegionCount()));
                if (file.getTotalVolume() > 0)
                    addDetailItem(i18n("schematics.info.total_volume"), file.getTotalVolume());
                if (file.getTotalBlocks() > 0)
                    addDetailItem(i18n("schematics.info.total_blocks"), file.getTotalBlocks());
                if (file.getEnclosingSize() != null)
                    addDetailItem(i18n("schematics.info.enclosing_size"),
                            String.format("%d x %d x %d", (int) file.getEnclosingSize().getX(),
                                    (int) file.getEnclosingSize().getY(),
                                    (int) file.getEnclosingSize().getZ()));

                addDetailItem(i18n("schematics.info.version"), file.getVersion());
            }

            LitematicInfoDialog() {
                HBox titleBox = new HBox(8);
                {
                    Node icon = getIcon(40);

                    TwoLineListItem title = new TwoLineListItem();
                    title.setTitle(getName());
                    title.setSubtitle(file.getFile().getFileName().toString());

                    titleBox.getChildren().setAll(icon, title);
                    setHeading(titleBox);
                }

                {
                    this.details = new ComponentList();
                    StackPane detailsContainer = new StackPane();
                    detailsContainer.setPadding(new Insets(10, 0, 0, 0));
                    detailsContainer.getChildren().add(details);
                    setBody(detailsContainer);
                }

                {
                    JFXButton okButton = new JFXButton();
                    okButton.getStyleClass().add("dialog-accept");
                    okButton.setText(i18n("button.ok"));
                    okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
                    getActions().add(okButton);

                    onEscPressed(this, okButton::fire);
                }

                updateContent(file);
            }
        }
    }

    private static final class Cell extends ListCell<Item> {

        private final RipplerContainer graphics;
        private final BorderPane root;
        private final StackPane left;
        private final TwoLineListItem center;
        private final HBox right;

        private final ImageView iconImageView;
        private final SVGContainer iconSVGView;

        private final Tooltip tooltip = new Tooltip();

        public Cell() {
            this.root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            {
                this.left = new StackPane();
                left.setPadding(new Insets(0, 8, 0, 0));

                this.iconImageView = new ImageView();
                FXUtils.limitSize(iconImageView, 32, 32);

                this.iconSVGView = new SVGContainer(32);

                BorderPane.setAlignment(left, Pos.CENTER);
                root.setLeft(left);
            }

            {
                this.center = new TwoLineListItem();
                root.setCenter(center);
            }

            {
                this.right = new HBox(8);
                right.setAlignment(Pos.CENTER_RIGHT);

                JFXButton btnReveal = new JFXButton();
                FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
                btnReveal.getStyleClass().add("toggle-icon4");
                btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon());
                btnReveal.setOnAction(event -> {
                    Item item = getItem();
                    if (item != null && !(item instanceof BackItem))
                        item.onReveal();
                });

                JFXButton btnDelete = new JFXButton();
                btnDelete.getStyleClass().add("toggle-icon4");
                btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon());
                btnDelete.setOnAction(event -> {
                    Item item = getItem();
                    if (item != null && !(item instanceof BackItem)) {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                                item::onDelete, null);
                    }
                });

                right.getChildren().setAll(btnReveal, btnDelete);
            }

            this.graphics = new RipplerContainer(root);
            FXUtils.onClicked(graphics, () -> {
                Item item = getItem();
                if (item != null)
                    item.onClick();
            });
        }

        @Override
        protected void updateItem(Item item, boolean empty) {
            super.updateItem(item, empty);

            iconImageView.setImage(null);

            if (empty || item == null) {
                setGraphic(null);
                center.setTitle("");
                center.setSubtitle("");
            } else {
                if (item instanceof LitematicFileItem fileItem && fileItem.getImage() != null) {
                    iconImageView.setImage(fileItem.getImage());
                    left.getChildren().setAll(iconImageView);
                } else {
                    iconSVGView.setIcon(item.getIcon());
                    left.getChildren().setAll(iconSVGView);
                }

                center.setTitle(item.getName());
                center.setSubtitle(item.getDescription());

                Path path = item.getPath();
                if (path != null) {
                    tooltip.setText(FileUtils.getAbsolutePath(path));
                    FXUtils.installSlowTooltip(left, tooltip);
                } else {
                    tooltip.setText("");
                    Tooltip.uninstall(left, tooltip);
                }

                root.setRight(item instanceof BackItem ? null : right);

                setGraphic(graphics);
            }
        }
    }

    private final class SchematicsPageSkin extends ToolbarListPageSkin<Item, SchematicsPage> {
        SchematicsPageSkin() {
            super(SchematicsPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(SchematicsPage skinnable) {
            return Arrays.asList(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("schematics.add"), SVG.ADD, skinnable::onAddFiles),
                    createToolbarButton2(i18n("schematics.create_directory"), SVG.CREATE_NEW_FOLDER, skinnable::onCreateDirectory)
            );
        }

        @Override
        protected ListCell<Item> createListCell(JFXListView<Item> listView) {
            return new Cell();
        }
    }
}
