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
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.schematic.LitematicFile;
import org.jackhuang.hmcl.schematic.Schematic;
import org.jackhuang.hmcl.schematic.SchematicType;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.*;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createTip;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class SchematicsPage extends ListPageBase<SchematicsPage.Item> implements VersionPage.VersionLoadable {

    private static volatile RemoteMod litematica;
    private static volatile RemoteMod forgematica;

    private static String translateAuthorName(String author) {
        if (I18n.isUseChinese() && "hsds".equals(author)) {
            return "黑山大叔";
        }
        return author;
    }

    private static String translateType(SchematicType type) {
        return i18n("schematics.info.type." + type.name().toLowerCase(Locale.ROOT));
    }

    private Profile profile;
    private String instanceId;
    private Path schematicsDirectory;
    private final ObjectProperty<DirItem> currentDirectory = new SimpleObjectProperty<>(this, "currentDirectory", null);
    private final ObjectProperty<Pair<String, Runnable>> warningTip = new SimpleObjectProperty<>(this, "tip", pair(null, null));

    private final BooleanProperty isRootProperty = new SimpleBooleanProperty(this, "isRoot", true);

    public SchematicsPage() {
        FXUtils.applyDragListener(this,
                file -> currentDirectoryProperty().get() != null && Schematic.isFileSchematic(file),
                this::addFiles
        );
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SchematicsPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.profile = profile;
        this.instanceId = version;
        this.schematicsDirectory = profile.getRepository().getSchematicsDirectory(version);

        refresh();
    }

    private ObjectProperty<DirItem> currentDirectoryProperty() {
        return currentDirectory;
    }

    public BooleanProperty isRootProperty() {
        return isRootProperty;
    }

    public void navigateBack() {
        var d = currentDirectoryProperty().get();
        if (d == null) return;
        if (d.parent != null) navigateTo(d.parent);
    }

    public void refresh() {
        if (schematicsDirectory == null) return;

        setLoading(true);
        Task.supplyAsync(() -> {
            var litematicaState = LitematicaState.NOT_INSTALLED;
            var modManager = profile.getRepository().getModManager(instanceId);
            try {
                modManager.refreshMods();
                var mods = modManager.getMods();
                for (var localModFile : mods) {
                    if ("litematica".equals(localModFile.getId()) || "forgematica".equals(localModFile.getId())) {
                        if (localModFile.isActive()) {
                            litematicaState = LitematicaState.OK;
                            break;
                        } else {
                            litematicaState = LitematicaState.DISABLED;
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warning("Failed to load mods, unable to check litematica", e);
            }
            boolean shouldUseForgematica = false;
            if (litematicaState == LitematicaState.NOT_INSTALLED) {
                shouldUseForgematica =
                        (modManager.getSupportedLoaders().contains(ModLoaderType.FORGE)
                                || modManager.getSupportedLoaders().contains(ModLoaderType.NEO_FORGED))
                                && GameVersionNumber.asGameVersion(Optional.ofNullable(modManager.getGameVersion())).isAtLeast("1.16.4", "20w45a");
                if (litematica == null && !shouldUseForgematica) {
                    try {
                        litematica = ModrinthRemoteModRepository.MODS.getModById("litematica");
                    } catch (IOException ignored) {
                    }
                } else if (forgematica == null && shouldUseForgematica) {
                    try {
                        forgematica = ModrinthRemoteModRepository.MODS.getModById("forgematica");
                    } catch (IOException ignored) {
                    }
                }
            }
            return pair(pair(litematicaState, shouldUseForgematica), loadRoot(schematicsDirectory));
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                switch (result.key().key()) {
                    case NOT_INSTALLED -> {
                        boolean useForgematica = forgematica != null && result.key().value();
                        boolean useLitematica = litematica != null && !result.key().value();
                        if (useForgematica || useLitematica) {
                            warningTip.set(pair(i18n("schematics.warning.no_litematica_install"), () -> {
                                var modDownloads = Controllers.getDownloadPage().showModDownloads();
                                modDownloads.selectVersion(instanceId);
                                Controllers.navigate(new DownloadPage(
                                        modDownloads,
                                        useForgematica ? forgematica : litematica,
                                        modDownloads.getProfileVersion(),
                                        modDownloads.getCallback())
                                );
                            }));
                        } else {
                            warningTip.set(pair(i18n("schematics.warning.no_litematica"), null));
                        }
                    }
                    case DISABLED -> warningTip.set(pair(i18n("schematics.warning.litematica_disabled"), null));
                    default -> warningTip.set(pair(null, null));
                }
                DirItem target = result.value();
                if (currentDirectoryProperty().get() != null) {
                    loop:
                    for (String dirName : currentDirectoryProperty().get().relativePath) {
                        target.preLoad();
                        for (var dirChild : target.dirChildren) {
                            if (dirChild.getName().equals(dirName)) {
                                target = dirChild;
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
            setLoading(false);
        }).start();
    }

    public void addFiles(List<Path> files) {
        if (currentDirectoryProperty().get() == null)
            return;

        Path dir = currentDirectoryProperty().get().getPath();
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
        if (currentDirectoryProperty().get() == null) return;

        Path parent = currentDirectoryProperty().get().getPath();
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

    public void onRevealSchematicsFolder() {
        var d = currentDirectoryProperty().get();
        var p = d != null ? d.getPath() : schematicsDirectory;
        if (p != null) FXUtils.openFolder(p);
    }

    private DirItem loadRoot(Path dir) {
        var item = new DirItem(dir, null);
        item.load();
        return item;
    }

    private void navigateTo(DirItem item) {
        if (currentDirectoryProperty().get() == item) return;
        currentDirectoryProperty().set(item);
        isRootProperty().set(item.parent == null);
        setLoading(true);
        Task.runAsync(item::load).whenComplete(Schedulers.javafx(), exception -> {
            if (currentDirectoryProperty().get() == item) {
                getItems().setAll(item.children);
                setLoading(false);
            }
        }).start();
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

    private final class DirItem extends Item {
        final Path path;
        final @Nullable DirItem parent;
        final List<Item> children = new ArrayList<>();
        final List<DirItem> dirChildren = new ArrayList<>();
        final List<String> relativePath;
        int size = 0;
        boolean preLoaded = false;
        boolean loaded = false;

        DirItem(Path path, @Nullable DirItem parent) {
            this.path = path;
            this.parent = parent;

            if (parent != null) {
                this.relativePath = new ArrayList<>(parent.relativePath);
                relativePath.add(FileUtils.getName(path));
            } else {
                this.relativePath = Collections.emptyList();
            }
        }

        @Override
        int order() {
            return 0;
        }

        @Override
        Path getPath() {
            return path;
        }

        @Override
        public String getName() {
            return FileUtils.getName(path);
        }

        @Override
        String getDescription() {
            return i18n("schematics.sub_items", size);
        }

        @Override
        SVG getIcon() {
            return SVG.FOLDER;
        }

        void preLoad() throws IOException {
            if (this.preLoaded) return;
            try (Stream<Path> stream = Files.list(path)) {
                stream.forEach(p -> {
                    boolean b1 = Files.isDirectory(p);
                    boolean b2 = Schematic.isFileSchematic(p);
                    if (b1 || b2) this.size++;
                    if (b1) {
                        var child = new DirItem(p, this);
                        this.dirChildren.add(child);
                    }
                });
            }
            this.preLoaded = true;
        }

        void load() {
            if (this.loaded) return;

            try {
                preLoad();
                try (Stream<Path> stream = Files.list(path)) {
                    for (var dir : dirChildren) {
                        dir.preLoad();
                        this.children.add(dir);
                    }
                    stream.filter(Schematic::isFileSchematic)
                            .forEach(p -> {
                                try {
                                    this.children.add(new SchematicItem(p));
                                } catch (IOException e) {
                                    LOG.warning("Failed to load schematic file: " + path, e);
                                }
                            });
                }
            } catch (NoSuchFileException ignored) {
            } catch (IOException e) {
                LOG.warning("Failed to load schematics in " + path, e);
            }

            this.children.sort(Comparator.naturalOrder());
            this.loaded = true;
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
                FileUtils.deleteDirectory(path);
                refresh();
            } catch (IOException e) {
                LOG.warning("Failed to delete directory: " + path, e);
            }
        }
    }

    private final class SchematicItem extends Item {
        final Path path;
        final Schematic file;
        final String name;
        final Image image;

        private SchematicItem(Path path) throws IOException {
            this.path = path;
            this.file = Schematic.load(path);

            if (file == null) throw new AssertionError(); // Should be impossible

            if (this.file instanceof LitematicFile lFile) {
                String name = lFile.getName();
                if (StringUtils.isNotBlank(name) && !"Unnamed".equals(name)) {
                    this.name = name;
                } else {
                    this.name = FileUtils.getNameWithoutExtension(path);
                }
            } else {
                this.name = FileUtils.getNameWithoutExtension(path);
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
            return 1;
        }

        @Override
        Path getPath() {
            return path;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        String getDescription() {
            return path.getFileName().toString();
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
            Controllers.dialog(new SchematicInfoDialog());
        }

        @Override
        void onReveal() {
            FXUtils.showFileInExplorer(path);
        }

        @Override
        void onDelete() {
            try {
                Files.deleteIfExists(path);
                refresh();
            } catch (IOException e) {
                LOG.warning("Failed to delete schematic file: " + path, e);
            }
        }

        private final class SchematicInfoDialog extends JFXDialogLayout {
            private final ComponentList details;

            private void addDetailItem(String key, Object detail) {
                BorderPane borderPane = new BorderPane();
                borderPane.setLeft(new Label(key));
                borderPane.setRight(new Label(detail.toString()));
                details.getContent().add(borderPane);
            }

            private void updateContent(Schematic file) {
                details.getContent().clear();
                addDetailItem(i18n("schematics.info.name"), file.getName());
                addDetailItem(i18n("schematics.info.type"), translateType(file.getType()));
                if (StringUtils.isNotBlank(file.getAuthor()))
                    addDetailItem(i18n("schematics.info.schematic_author"), translateAuthorName(file.getAuthor()));
                if (file.getTimeCreated() != null)
                    addDetailItem(i18n("schematics.info.time_created"), I18n.formatDateTime(file.getTimeCreated()));
                if (file.getTimeModified() != null && !file.getTimeModified().equals(file.getTimeCreated()))
                    addDetailItem(i18n("schematics.info.time_modified"), I18n.formatDateTime(file.getTimeModified()));
                if (file.getRegionCount().isPresent())
                    addDetailItem(i18n("schematics.info.region_count"), String.valueOf(file.getRegionCount().getAsInt()));
                if (file.getTotalVolume().isPresent())
                    addDetailItem(i18n("schematics.info.total_volume"), file.getTotalVolume().getAsInt());
                if (file.getTotalBlocks().isPresent())
                    addDetailItem(i18n("schematics.info.total_blocks"), file.getTotalBlocks().getAsInt());
                if (file.getEnclosingSize() != null)
                    addDetailItem(i18n("schematics.info.enclosing_size"),
                            String.format("%d x %d x %d", file.getEnclosingSize().x(),
                                    file.getEnclosingSize().y(),
                                    file.getEnclosingSize().z()));
                if (StringUtils.isNotBlank(file.getMinecraftVersion()))
                    addDetailItem(i18n("schematics.info.mc_data_version"), file.getMinecraftVersion());
                if (file.getVersion().isPresent())
                    addDetailItem(i18n("schematics.info.version"),
                            file.getSubVersion().isPresent()
                                    ? "%d.%d".formatted(file.getVersion().getAsInt(), file.getSubVersion().getAsInt())
                                    : file.getVersion().getAsInt()
                    );
            }

            SchematicInfoDialog() {
                HBox titleBox = new HBox(8);
                {
                    Node icon = getIcon(40);

                    TwoLineListItem title = new TwoLineListItem();
                    title.setTitle(getName());
                    title.setSubtitle(path.getFileName().toString());

                    titleBox.getChildren().setAll(icon, title);
                    setHeading(titleBox);
                }

                {
                    this.details = new ComponentList();
                    details.setStyle("-fx-effect: null;");
                    StackPane detailsContainer = new StackPane();
                    detailsContainer.getChildren().add(details);
                    ScrollPane scrollPane = new ScrollPane(detailsContainer);
                    scrollPane.setStyle("-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.26), 5, 0.06, -0.5, 1);");
                    scrollPane.setFitToWidth(true);
                    FXUtils.smoothScrolling(scrollPane);
                    StackPane.setMargin(scrollPane, new Insets(10, 0, 0, 0));
                    setBody(scrollPane);
                }

                {
                    JFXButton okButton = new JFXButton();
                    okButton.getStyleClass().add("dialog-accept");
                    okButton.setText(i18n("button.ok"));
                    okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
                    getActions().add(okButton);

                    onEscPressed(this, okButton::fire);
                }

                this.prefWidthProperty().bind(Controllers.getStage().widthProperty().multiply(0.6));
                this.maxHeightProperty().bind(Controllers.getStage().heightProperty().multiply(0.8));

                updateContent(file);
            }
        }
    }

    private static final class Cell extends MDListCell<Item> {

        private final StackPane left;
        private final TwoLineListItem center;
        private final HBox right;

        private final ImageView iconImageView;
        private final SVGPath iconSVG;
        private final StackPane iconSVGWrapper;

        private final BooleanProperty isFileProperty = new SimpleBooleanProperty(this, "isFile", false);
        private final BooleanProperty isDirectoryProperty = new SimpleBooleanProperty(this, "isDirectory", false);

        private final Tooltip tooltip = new Tooltip();

        public Cell(JFXListView<Item> listView) {
            super(listView);

            var box = new HBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPickOnBounds(false);

            {
                this.left = new StackPane();
                left.setPadding(new Insets(0, 8, 0, 0));

                this.iconImageView = new ImageView();
                FXUtils.limitSize(iconImageView, 32, 32);

                this.iconSVG = new SVGPath();
                iconSVG.getStyleClass().add("svg");
                iconSVG.setScaleX(32.0 / SVG.DEFAULT_SIZE);
                iconSVG.setScaleY(32.0 / SVG.DEFAULT_SIZE);

                this.iconSVGWrapper = new StackPane(new Group(iconSVG));
                iconSVGWrapper.setAlignment(Pos.CENTER);
                FXUtils.setLimitWidth(iconSVGWrapper, 32);
                FXUtils.setLimitHeight(iconSVGWrapper, 32);
            }
            {
                this.center = new TwoLineListItem();
                HBox.setHgrow(center, Priority.ALWAYS);
            }
            {
                this.right = new HBox(8);
                right.setAlignment(Pos.CENTER_RIGHT);

                JFXButton btnReveal = new JFXButton();
                FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
                btnReveal.getStyleClass().add("toggle-icon4");
                {
                    var fo = SVG.FOLDER_OPEN.createIcon();
                    var f = SVG.FOLDER.createIcon();
                    btnReveal.graphicProperty().bind(isDirectoryProperty.map(b -> b ? fo : f));
                }
                btnReveal.setOnAction(event -> {
                    Item item = getItem();
                    if (item != null) item.onReveal();
                });

                JFXButton btnExplore = new JFXButton();
                btnExplore.getStyleClass().add("toggle-icon4");
                btnExplore.setGraphic(SVG.EXPLORE.createIcon()); // Change the icon if allows editing
                btnExplore.setOnAction(event -> {
                    Item item = getItem();
                    if (item instanceof SchematicItem) {
                        try {
                            Controllers.navigate(new NBTEditorPage(item.getPath()));
                        } catch (IOException ignored) { // Should be impossible
                        }
                    }
                });
                btnExplore.visibleProperty().bind(isFileProperty);

                JFXButton btnDelete = new JFXButton();
                btnDelete.getStyleClass().add("toggle-icon4");
                btnDelete.setGraphic(SVG.DELETE_FOREVER.createIcon());
                btnDelete.setOnAction(event -> {
                    Item item = getItem();
                    if (item != null) {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                                item::onDelete, null);
                    }
                });

                right.getChildren().setAll(btnExplore, btnReveal, btnDelete);
            }

            box.getChildren().setAll(left, center, right);
            StackPane.setMargin(box, new Insets(8));
            getContainer().getChildren().setAll(box);
            onClicked(() -> {
                var item = getItem();
                if (item != null) item.onClick();
            });
        }

        @Override
        protected void updateControl(Item item, boolean empty) {
            if (empty || item == null) return;

            iconImageView.setImage(null);

            isFileProperty.set(item instanceof SchematicItem);
            isDirectoryProperty.set(item.isDirectory());

            if (item instanceof SchematicItem fileItem && fileItem.getImage() != null) {
                iconImageView.setImage(fileItem.getImage());
                left.getChildren().setAll(iconImageView);
            } else {
                iconSVG.setContent(item.getIcon().getPath());
                left.getChildren().setAll(iconSVGWrapper);
            }

            center.setTitle(item.getName());
            center.setSubtitle(item.getDescription());

            Path path = item.getPath();
            if (path != null) {
                tooltip.setText(FileUtils.getAbsolutePath(path));
                FXUtils.installSlowTooltip(left, tooltip);
            } else {
                Tooltip.uninstall(left, tooltip);
            }
        }
    }

    private static final class SchematicsPageSkin extends SkinBase<SchematicsPage> {

        private final JFXListView<Item> listView;

        SchematicsPageSkin(SchematicsPage skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");
            listView = new JFXListView<>();
            listView.setSelectionModel(new NoneMultipleSelectionModel<>());

            {
                var toolbar = new HBox();
                JFXButton btnGoBack = createToolbarButton2("", SVG.ARROW_BACK, skinnable::navigateBack);
                btnGoBack.disableProperty().bind(skinnable.isRootProperty());
                toolbar.getChildren().setAll(
                        btnGoBack,
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("schematics.add"), SVG.ADD, skinnable::onAddFiles),
                        createToolbarButton2(i18n("schematics.create_directory"), SVG.CREATE_NEW_FOLDER, skinnable::onCreateDirectory),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::onRevealSchematicsFolder)
                );
                root.getContent().add(toolbar);
            }

            {
                var tip = createTip();
                HBox.setMargin(tip, new Insets(5));
                var tipPane = new HBox(tip);
                tipPane.setAlignment(Pos.CENTER_LEFT);
                FXUtils.onChangeAndOperate(skinnable.warningTip, pair -> {
                    root.getContent().remove(tipPane);
                    if (pair != null && !StringUtils.isBlank(pair.key())) {
                        var txt = new Text(pair.key());
                        if (pair.value() != null) FXUtils.onClicked(txt, pair.value());
                        tip.getChildren().setAll(txt);
                        root.getContent().add(1, tipPane);
                    }
                });
            }

            {
                SpinnerPane center = new SpinnerPane();
                ComponentList.setVgrow(center, Priority.ALWAYS);
                center.loadingProperty().bind(skinnable.loadingProperty());

                listView.setCellFactory(x -> new Cell(listView));
                listView.setSelectionModel(new NoneMultipleSelectionModel<>());
                Bindings.bindContent(listView.getItems(), skinnable.getItems());

                // ListViewBehavior would consume ESC pressed event, preventing us from handling it
                // So we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                center.setContent(listView);
                root.getContent().add(center);
            }

            {
                var relPath = createTip();
                HBox.setMargin(relPath, new Insets(5));
                FXUtils.onChangeAndOperate(skinnable.currentDirectoryProperty(), currentDir -> {
                    relPath.getChildren().clear();
                    var d = currentDir;
                    while (d != null) {
                        relPath.getChildren().add(0, new Text("/"));
                        var txt = new Text(d.getName());
                        var finalD = d;
                        FXUtils.onClicked(txt, () -> skinnable.navigateTo(finalD));
                        relPath.getChildren().add(0, txt);
                        d = d.parent;
                    }
                });
                var relPathPane = new HBox(relPath);
                relPathPane.setAlignment(Pos.CENTER_LEFT);
                root.getContent().add(relPathPane);
            }

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
        }
    }

    private enum LitematicaState {
        DISABLED,
        NOT_INSTALLED,
        OK
    }

}
