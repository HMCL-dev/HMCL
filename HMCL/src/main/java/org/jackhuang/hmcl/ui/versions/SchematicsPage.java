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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.schematic.LitematicFile;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class SchematicsPage extends ListPageBase<SchematicsPage.Item> implements VersionPage.VersionLoadable {

    private Profile profile;
    private String version;
    private Path schematicsDirectory;

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
                        navigateTo(result);
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
        if (item.parent != null)
            getItems().add(new BackItem(item.parent));
        getItems().addAll(item.children);
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

                    root.setLeft(left);
                }

                {
                    TwoLineListItem center = new TwoLineListItem();
                    center.setTitle(getName());
                    center.setSubtitle(getDescription());

                    root.setCenter(center);
                }

                {
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
        final DirItem parent;
        final List<Item> children = new ArrayList<>();

        DirItem(Path path, DirItem parent) {
            this.path = path;
            this.parent = parent;
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
            List<String> details = new ArrayList<>();
            if (StringUtils.isNotBlank(file.getAuthor()))
                details.add("作者: " + file.getAuthor());

            if (file.getTimeCreated() != null)
                details.add("创建时间: " + I18n.formatDateTime(file.getTimeCreated()));

            return String.join(" | ", details);
        }

        @Override
        SVG getIcon() {
            return SVG.SCHEMA;
        }

        @Override
        void onClick() {
            // TODO
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
