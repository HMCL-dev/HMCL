/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.schematic.NBTStructureFile;
import org.jackhuang.hmcl.schematic.SchematicType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.util.Identifier;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Calboot
/// @see <a href="https://minecraft.wiki/w/Structure_file">Minecraft Wiki - Structure file</a>
/// @see <a href="https://minecraft.wiki/w/Structure_Block">Minecraft Wiki - Structure Block</a>
public class StructureListPage extends ListPageBase<StructureListPage.NBTStructureInfoObject> implements WorldManagePage.WorldRefreshable {

    private final World world;
    private final @Nullable Path directory;
    private final int dataVersion;
    private final boolean flattened;

    public StructureListPage(WorldManagePage worldManagePage) {
        this.world = worldManagePage.getWorld();
        this.dataVersion = world.getDataVersion();
        if (dataVersion <= 500) { // lower than 1.10 16w20a, unsupported
            directory = null;
            flattened = false;
        } else if (dataVersion >= 1451) { // 1.13 17w47a
            directory = world.getFile().resolve("generated");
            flattened = true;
        } else {
            directory = world.getFile().resolve("structures");
            flattened = false;
        }

        FXUtils.applyDragListener(this, p -> SchematicType.getType(p) == SchematicType.NBT_STRUCTURE, this::addFiles);

        refresh();
    }

    private static List<NBTStructureInfoObject> listStructureFiles(Path dir, @Nullable String namespace) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> SchematicType.getType(p) == SchematicType.NBT_STRUCTURE)
                    .map(p -> {
                        try {
                            return NBTStructureFile.load(p);
                        } catch (IOException e) {
                            LOG.warning("Failed to load nbt structure file at " + p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(structureFile ->
                            new NBTStructureInfoObject(namespace, structureFile))
                    .toList();
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new StructureListPageSkin(this);
    }

    @Override
    public void refresh() {
        setDisabled(false);
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            if (directory == null)
                return null;
            if (!Files.isDirectory(directory))
                return List.<NBTStructureInfoObject>of();

            if (flattened) {
                String structureDirName = dataVersion >= 3946 // 1.21 24w21a
                        ? "structure"
                        : "structures";

                try (var namespaceStream = Files.list(directory)) {
                    return namespaceStream
                            .filter(Files::isDirectory)
                            .flatMap(namespaceDir -> {
                                var structuresDir = namespaceDir.resolve(structureDirName);
                                if (!Files.isDirectory(structuresDir)) return Stream.of();
                                try {
                                    return listStructureFiles(structuresDir, namespaceDir.getFileName().toString()).stream();
                                } catch (IOException e) {
                                    LOG.warning("Failed to load nbt structure files in " + namespaceDir, e);
                                    return Stream.of();
                                }
                            }).toList();
                }
            } else {
                return listStructureFiles(directory, null);
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                if (result != null) {
                    getItems().setAll(result);
                } else { // unsupported world game version
                    getItems().clear();
                    setDisabled(true);
                }
            } else {
                LOG.warning("Failed to load nbt structure files", exception);
                getItems().clear();
            }
            setLoading(false);
        }).start();
    }

    private void reveal() {
        if (directory != null) FXUtils.openFolder(directory);
    }

    private void rename(NBTStructureInfoObject object) {
        if (directory == null) return;
        String initial = object.getIdString();
        Controllers.prompt(
                i18n("world.structure.rename"),
                (result, handler) -> {
                    Path file = object.structureFile.getFile();
                    try {
                        Files.move(file, getPathFor(result), StandardCopyOption.REPLACE_EXISTING);
                        refresh();
                        handler.resolve();
                    } catch (Exception e) {
                        LOG.warning("Failed to rename nbt structure file " + file, e);
                        handler.reject(i18n("message.failed"));
                    }
                    },
                initial,
                new Validator(i18n("world.structure.rename.invalid_id"), flattened ? Identifier::isValid : FileUtils::isNameValid),
                new Validator(i18n("world.structure.rename.already_exists"), id -> initial.equals(id) || !Files.exists(getPathFor(id)))
        );
    }

    private Path getPathFor(String identifier) {
        if (directory == null) throw new IllegalStateException("Unsupported world game version");
        if (flattened) {
            Identifier id = Identifier.of(identifier);
            var structureDirName = dataVersion >= 3946
                    ? "structure"
                    : "structures";
            return directory.resolve(id.namespace()).resolve(structureDirName).resolve(id.path() + ".nbt");
        } else {
            return directory.resolve(identifier + ".nbt");
        }
    }

    private void chooseAndAdd() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("schematics.info.type.nbt_structure"), "*.nbt"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);

        if (res != null) addFiles(res);
    }

    private void addFiles(Collection<Path> files) {
        if (directory == null || files.isEmpty()) return;

        List<Path> failures = new ArrayList<>();
        Task.runAsync(Schedulers.io(), () -> {
            for (Path file : files) {
                try {
                    var target = getPathFor(FileUtils.getNameWithoutExtension(file));
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                } catch (IOException e) {
                    failures.add(file);
                    LOG.warning("Failed to add structure file " + file, e);
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            if (!failures.isEmpty()) {
                StringBuilder failure = new StringBuilder(i18n("world.structure.add.failed"));
                for (Path file: failures) {
                    failure.append("\n").append(file.toString());
                }
                Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
            refresh();
        }).start();
    }

    public static final class NBTStructureInfoObject {

        private final @Nullable String namespace;
        private final NBTStructureFile structureFile;

        private NBTStructureInfoObject(@Nullable String namespace, NBTStructureFile structureFile) {
            this.namespace = namespace;
            this.structureFile = structureFile;
        }

        public String getName() {
            return structureFile.getName();
        }

        public String getIdString() {
            if (namespace == null) return getName();
            return namespace + ":" + getName();
        }
    }

    private static final class StructureListPageSkin extends ToolbarListPageSkin<NBTStructureInfoObject, StructureListPage> {

        public StructureListPageSkin(StructureListPage skinnable) {
            super(skinnable);

            listView.setCellFactory(x -> new StructureCell(skinnable, listView));
        }

        @Override
        protected List<Node> initializeToolbar(StructureListPage skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::reveal),
                    ToolbarListPageSkin.createToolbarButton2(i18n("world.structure.add"), SVG.ADD, skinnable::chooseAndAdd)
            );
        }
    }

    private static final class StructureCell extends MDListCell<NBTStructureInfoObject> {

        private final int dataVersion;
        private final TwoLineListItem content = new TwoLineListItem();

        public StructureCell(StructureListPage page, JFXListView<NBTStructureInfoObject> listView) {
            super(listView);

            this.dataVersion = page.dataVersion;

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            JFXButton renameButton = FXUtils.newToggleButton4(SVG.EDIT);
            renameButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty()) page.rename(getItem());
            });

            JFXButton exploreButton = FXUtils.newToggleButton4(SVG.EXPLORE); // Change the icon if allows editing
            exploreButton.setOnAction(e -> {
                if (getItem() == null || isEmpty()) return;
                try {
                    Controllers.navigate(new NBTEditorPage(getItem().structureFile.getFile(), getItem().getIdString()));
                } catch (IOException ignored) { // should never happen
                }
            });

            JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER);
            revealButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty()) FXUtils.showFileInExplorer(getItem().structureFile.getFile());
            });

            JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE_FOREVER);
            deleteButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty()) {
                    var item = getItem();
                    var path = item.structureFile.getFile();
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                        try {
                            Files.deleteIfExists(path);
                            page.refresh();
                        } catch (IOException ex) {
                            LOG.warning("Failed to delete nbt structure file: " + path, ex);
                        }
                    }, null);
                }
            });

            container.getChildren().setAll(content, renameButton, exploreButton, revealButton, deleteButton);

            StackPane.setMargin(container, new Insets(8, 8, 8, 16));
            getContainer().getChildren().setAll(container);

            onClicked(() -> {
                if (getItem() != null && !isEmpty()) Controllers.dialog(new StructureInfoDialog(getItem()));
            });
        }

        @Override
        protected void updateControl(NBTStructureInfoObject item, boolean empty) {
            if (item == null || empty) return;

            content.setTitle(item.structureFile.getName());
            content.setSubtitle(item.namespace + ":" + item.structureFile.getName());

            int nbtDataVersion = item.structureFile.getMinecraftDataVersion().orElse(500);
            if (nbtDataVersion > dataVersion)
                content.addTagWarning(i18n("schematics.info.mc_data_version") + ": %d > %d".formatted(nbtDataVersion, dataVersion));
        }
    }

    private static final class StructureInfoDialog extends JFXDialogLayout {

        private final ComponentList details;

        private StructureInfoDialog(NBTStructureInfoObject object) {

            HBox titleBox = new HBox(8);
            {
                TwoLineListItem title = new TwoLineListItem();
                title.setTitle(object.getName());
                title.setSubtitle(object.getIdString());

                titleBox.getChildren().setAll(title);
                setHeading(titleBox);
            }

            {
                this.details = new ComponentList();
                details.setStyle("-fx-effect: null;");
                StackPane detailsContainer = new StackPane();
                detailsContainer.getChildren().add(details);
                ScrollPane scrollPane = new ScrollPane(detailsContainer);
                scrollPane.setFitToWidth(true);
                FXUtils.setOverflowHidden(scrollPane, 8);
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

            this.prefWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.6));
            this.maxHeightProperty().bind(Controllers.getDecorator().contentHeightProperty().multiply(0.8));

            updateContent(object.structureFile);
        }

        private void addDetailItem(String key, Object detail) {
            BorderPane borderPane = new BorderPane();
            borderPane.setLeft(new Label(key));
            borderPane.setRight(new Label(detail.toString()));
            details.getContent().add(borderPane);
        }

        private void updateContent(NBTStructureFile file) {
            details.getContent().clear();
            addDetailItem(i18n("schematics.info.name"), file.getName());
            addDetailItem(i18n("schematics.info.type"), SchematicsPage.translateType(file.getType()));
            if (StringUtils.isNotBlank(file.getAuthor()))
                addDetailItem(i18n("schematics.info.schematic_author"), SchematicsPage.translateAuthorName(file.getAuthor()));
            if (file.getTotalVolume().isPresent())
                addDetailItem(i18n("schematics.info.total_volume"), file.getTotalVolume().getAsLong());
            if (file.getEnclosingSize() != null)
                addDetailItem(i18n("schematics.info.enclosing_size"),
                        String.format("%d x %d x %d", file.getEnclosingSize().x(),
                                file.getEnclosingSize().y(),
                                file.getEnclosingSize().z()));
            if (StringUtils.isNotBlank(file.getMinecraftVersion()))
                addDetailItem(i18n("schematics.info.mc_data_version"), file.getMinecraftVersion());
        }
    }
}
