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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldBackupsPage extends ListPageBase<WorldBackupsPage.BackupInfo> implements DecoratorPage {
    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final World world;
    private final Path backupsDir;
    private final Pattern backupFileNamePattern;
    private final ObjectProperty<State> state = new SimpleObjectProperty<>();

    public WorldBackupsPage(World world, Path backupsDir) {
        this.backupsDir = backupsDir;
        this.world = world;
        this.backupFileNamePattern = Pattern.compile(
                "(?<datetime>[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2})_"
                        + Pattern.quote(world.getFileName())
                        + "( (?<count>[0-9]+))?\\.zip");
        this.state.set(State.fromTitle(i18n("world.backup.title", world.getWorldName())));
        loadBackups();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @Override
    public void refresh() {
        loadBackups();
    }

    private void loadBackups() {
        setLoading(true);
        Task.supplyAsync(() -> {
            if (Files.isDirectory(backupsDir)) {
                try (Stream<Path> paths = Files.list(backupsDir)) {
                    ArrayList<BackupInfo> result = new ArrayList<>();

                    paths.forEach(path -> {
                        if (Files.isRegularFile(path)) {
                            try {
                                Matcher matcher = backupFileNamePattern.matcher(path.getFileName().toString());
                                if (matcher.matches()) {
                                    LocalDateTime time = LocalDateTime.parse(matcher.group("datetime"), TIME_FORMATTER);
                                    int count = 0;

                                    if (matcher.group("count") != null) {
                                        count = Integer.parseInt(matcher.group("count"));
                                    }

                                    result.add(new BackupInfo(path, world, new World(path), time, count));
                                }
                            } catch (Throwable e) {
                                LOG.warning("Failed to load backup file " + path, e);
                            }
                        }
                    });

                    result.sort(Comparator.naturalOrder());
                    return result;
                }
            } else {
                return Collections.<BackupInfo>emptyList();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            this.setLoading(false);
            if (exception == null) {
                this.setItems(FXCollections.observableList(result));
            } else {
                LOG.warning("Failed to load backups", exception);
            }
        }).start();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new WorldBackupsPageSkin();
    }

    private final class WorldBackupsPageSkin extends ToolbarListPageSkin<WorldBackupsPage> {

        WorldBackupsPageSkin() {
            super(WorldBackupsPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(WorldBackupsPage skinnable) {
            return Arrays.asList(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }
    }

    public final class BackupInfo extends Control implements Comparable<BackupInfo> {
        private final Path file;
        private final World sourceWorld;
        private final World backupWorld;
        private final LocalDateTime backupTime;
        private final int count;

        public BackupInfo(Path file, World sourceWorld, World backupWorld, LocalDateTime backupTime, int count) {
            this.file = file;
            this.sourceWorld = sourceWorld;
            this.backupWorld = backupWorld;
            this.backupTime = backupTime;
            this.count = count;
        }

        public WorldBackupsPage getBackupsPage() {
            return WorldBackupsPage.this;
        }

        public World getSourceWorld() {
            return sourceWorld;
        }

        public World getBackupWorld() {
            return backupWorld;
        }

        public LocalDateTime getBackupTime() {
            return backupTime;
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new BackupInfoSkin(this);
        }

        void onReveal() {
            FXUtils.showFileInExplorer(file);
        }

        void onDelete() {
            WorldBackupsPage.this.getItems().remove(this);
            Task.runAsync(() -> Files.delete(file)).start();
        }

        @Override
        public int compareTo(@NotNull WorldBackupsPage.BackupInfo that) {
            assert this.sourceWorld == that.sourceWorld;

            int c = this.backupTime.compareTo(that.backupTime);
            return c != 0 ? c : Integer.compare(this.count, that.count);
        }
    }

    public static final class BackupInfoSkin extends SkinBase<BackupInfo> {

        public BackupInfoSkin(BackupInfo skinnable) {
            super(skinnable);

            World world = skinnable.getBackupWorld();

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            {
                StackPane left = new StackPane();
                root.setLeft(left);
                left.setPadding(new Insets(0, 8, 0, 0));

                ImageView imageView = new ImageView();
                left.getChildren().add(imageView);
                FXUtils.limitSize(imageView, 32, 32);
                imageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
            }

            {
                TwoLineListItem item = new TwoLineListItem();
                root.setCenter(item);

                if (skinnable.getBackupWorld().getWorldName() != null)
                    item.setTitle(parseColorEscapes(skinnable.getBackupWorld().getWorldName()));
                item.setSubtitle(formatDateTime(skinnable.getBackupTime()) + (skinnable.count == 0 ? "" : " (" + skinnable.count + ")"));

                if (world.getGameVersion() != null)
                    item.getTags().add(world.getGameVersion());
            }

            {
                HBox right = new HBox(8);
                root.setRight(right);
                right.setAlignment(Pos.CENTER_RIGHT);

                JFXButton btnReveal = new JFXButton();
                right.getChildren().add(btnReveal);
                btnReveal.getStyleClass().add("toggle-icon4");
                btnReveal.setGraphic(SVG.FOLDER_OPEN.createIcon(Theme.blackFill(), -1));
                btnReveal.setOnAction(event -> skinnable.onReveal());

                JFXButton btnDelete = new JFXButton();
                right.getChildren().add(btnDelete);
                btnDelete.getStyleClass().add("toggle-icon4");
                btnDelete.setGraphic(SVG.DELETE.createIcon(Theme.blackFill(), -1));
                btnDelete.setOnAction(event ->
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), skinnable::onDelete, null));
            }

            getChildren().setAll(new RipplerContainer(root));
        }
    }
}
