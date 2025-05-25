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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.schematic.LitematicFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

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

    private Profile profile;
    private String version;
    private Path schematicsDirectory;
    private List<String> currentRelativePath = Collections.emptyList();

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SchematicsPageSkin();
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        this.profile = profile;
        this.version = version;
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
                        DirItem current = result;
                        loop:
                        for (int i = 0; i < currentRelativePath.size(); i++) {
                            String dirName = currentRelativePath.get(i);

                            for (Item child : current.children) {
                                if (child instanceof DirItem && child.getName().equals(dirName)) {
                                    current = (DirItem) child;
                                    continue loop;
                                }
                            }

                            currentRelativePath = currentRelativePath.subList(0, i);
                            break;
                        }
                        navigateTo(current);
                    } else {
                        LOG.warning("Failed to load schematics", exception);
                    }
                }).start();
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
        } catch (IOException e) {
            LOG.warning("Failed to load schematics in " + dir, e);
        }

        item.children.sort(Comparator.naturalOrder());
        return item;
    }

    private void navigateTo(DirItem item) {
        getItems().clear();
        if (item.parent != null) {
            getItems().add(new BackItem(item.parent));
        }
        getItems().addAll(item.children);
        currentRelativePath = item.relativePath;
    }

    abstract class Item extends Control implements Comparable<Item> {

        boolean isDirectory() {
            return this instanceof DirItem;
        }

        abstract Path getPath();

        abstract String getName();

        abstract String getDescription();

        abstract SVG getIcon();

        abstract int order();

        abstract void onClick();

        abstract void onReveal();

        @Override
        public int compareTo(@NotNull SchematicsPage.Item o) {
            if (this.order() != o.order())
                return Integer.compare(this.order(), o.order());

            return this.getName().compareTo(o.getName());
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new ItemSkin();
        }

        private final class ItemSkin extends SkinBase<Item> {
            public ItemSkin() {
                super(Item.this);

                BorderPane root = new BorderPane();
                root.getStyleClass().add("md-list-cell");
                root.setPadding(new Insets(8));

                {
                    StackPane left = new StackPane();
                    left.setMaxSize(32, 32);
                    left.setPrefSize(32, 32);
                    left.getChildren().add(getIcon().createIcon(Theme.blackFill(), 24));
                    left.setPadding(new Insets(0, 8, 0, 0));

                    if (Item.this instanceof DirItem || Item.this instanceof LitematicFileItem) {
                        FXUtils.installSlowTooltip(left, getPath().toAbsolutePath().normalize().toString());
                    }

                    BorderPane.setAlignment(left, Pos.CENTER);
                    root.setLeft(left);
                }

                {
                    TwoLineListItem center = new TwoLineListItem();
                    center.setTitle(getName());
                    center.setSubtitle(getDescription());

                    root.setCenter(center);
                }

                if (!(Item.this instanceof BackItem)) {
                    HBox right = new HBox(8);
                    right.setAlignment(Pos.CENTER_RIGHT);

                    JFXButton btnReveal = new JFXButton();
                    right.getChildren().add(btnReveal);
                    FXUtils.installFastTooltip(btnReveal, i18n("world.reveal"));
                    btnReveal.getStyleClass().add("toggle-icon4");
                    btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));
                    btnReveal.setOnAction(event -> Item.this.onReveal());

                    root.setRight(right);
                }

                RipplerContainer container = new RipplerContainer(root);
                FXUtils.onClicked(container, Item.this::onClick);
                this.getChildren().add(container);
            }
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
            return "返回至 " + parent.getName();
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
            parent.onReveal();
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
            return children.size() + " 个子项";
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
            FXUtils.openFolder(path.toFile());
        }
    }

    private final class LitematicFileItem extends Item {
        final LitematicFile file;
        final String name;

        private LitematicFileItem(LitematicFile file) {
            this.file = file;
            this.name = file.getName() != null ? file.getName() : file.getFile().getFileName().toString();
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

        @Override
        void onClick() {
            JFXDialogLayout dialog = new JFXDialogLayout();

            HBox titleBox = new HBox(8);
            {
                StackPane icon = new StackPane();
                icon.setMaxSize(40, 40);
                icon.setPrefSize(40, 40);
                icon.getChildren().add(getIcon().createIcon(Theme.blackFill(), 40));

                TwoLineListItem title = new TwoLineListItem();
                title.setTitle(getName());
                title.setSubtitle(file.getFile().getFileName().toString());

                titleBox.getChildren().setAll(icon, title);
                dialog.setHeading(titleBox);
            }

            {
                List<String> details = new ArrayList<>();

                details.add(i18n("schematics.info.name") + ": " + file.getName());
                if (StringUtils.isNotBlank(file.getAuthor()))
                    details.add(i18n("schematics.info.schematic_author", translateAuthorName(file.getAuthor())));
                if (file.getTimeCreated() != null)
                    details.add(i18n("schematics.info.time_created", I18n.formatDateTime(file.getTimeCreated())));
                if (file.getTimeModified() != null && !file.getTimeModified().equals(file.getTimeCreated()))
                    details.add(i18n("schematics.info.time_modified", I18n.formatDateTime(file.getTimeModified())));
                if (file.getRegionCount() > 0)
                    details.add(i18n("schematics.info.region_count", file.getRegionCount()));
                if (file.getTotalVolume() > 0)
                    details.add(i18n("schematics.info.total_volume", file.getTotalVolume()));
                if (file.getTotalBlocks() > 0)
                    details.add(i18n("schematics.info.total_blocks", file.getTotalBlocks()));
                if (file.getEnclosingSize() != null)
                    details.add(i18n("schematics.info.enclosing_size",
                            (int) file.getEnclosingSize().getX(),
                            (int) file.getEnclosingSize().getY(),
                            (int) file.getEnclosingSize().getZ()));

                Label label = new Label(String.join("\n", details));
                StackPane.setAlignment(label, Pos.CENTER_LEFT);
                StackPane.setMargin(label, new Insets(0, 20, 0, 20));
                dialog.setBody(label);
            }

            {
                JFXButton okButton = new JFXButton();
                okButton.getStyleClass().add("dialog-accept");
                okButton.setText(i18n("button.ok"));
                okButton.setOnAction(e -> dialog.fireEvent(new DialogCloseEvent()));
                dialog.getActions().add(okButton);
            }

            Controllers.dialog(dialog);
        }

        @Override
        void onReveal() {
            FXUtils.showFileInExplorer(file.getFile());
        }
    }

    private final class SchematicsPageSkin extends ToolbarListPageSkin<SchematicsPage> {
        SchematicsPageSkin() {
            super(SchematicsPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(SchematicsPage skinnable) {
            return Arrays.asList(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }
    }
}
