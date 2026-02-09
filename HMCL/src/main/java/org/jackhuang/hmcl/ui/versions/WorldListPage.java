/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.ChunkBaseApp;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.StringUtils.parseColorEscapes;
import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldListPage extends ListPageBase<World> implements VersionPage.VersionLoadable {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);

    private Path savesDir;
    private List<World> worlds;
    private Profile profile;
    private String instanceId;
    private final BooleanProperty supportQuickPlay = new SimpleBooleanProperty(this, "supportQuickPlay", false);

    private int refreshCount = 0;

    public WorldListPage() {
        FXUtils.applyDragListener(this, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
            installWorld(modpacks.get(0));
        });

        showAll.addListener(e -> updateWorldList());
    }

    @Override
    protected Skin<WorldListPage> createDefaultSkin() {
        return new WorldListPageSkin();
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.instanceId = id;
        this.savesDir = profile.getRepository().getSavesDirectory(id);
        refresh();
    }

    private void updateWorldList() {
        if (worlds == null) {
            getItems().clear();
        } else if (showAll.get()) {
            getItems().setAll(worlds);
        } else {
            GameVersionNumber gameVersion = profile.getRepository().getGameVersion(instanceId).map(GameVersionNumber::asGameVersion).orElse(null);
            getItems().setAll(worlds.stream()
                    .filter(world -> world.getGameVersion() == null || world.getGameVersion().equals(gameVersion))
                    .toList());
        }
    }

    public void refresh() {
        if (profile == null || instanceId == null)
            return;

        int currentRefresh = ++refreshCount;

        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            // Ensure the game version number is parsed
            profile.getRepository().getGameVersion(instanceId);
            return World.getWorlds(savesDir);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (refreshCount != currentRefresh) {
                // A newer refresh task is running, discard this result
                return;
            }

            Optional<String> gameVersion = profile.getRepository().getGameVersion(instanceId);
            supportQuickPlay.set(World.supportQuickPlay(GameVersionNumber.asGameVersion(gameVersion)));

            worlds = result;
            updateWorldList();

            if (exception != null)
                LOG.warning("Failed to load world list page", exception);

            setLoading(false);
        }).start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("world.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.world"), "*.zip"));
        List<Path> res = FileUtils.toPaths(chooser.showOpenMultipleDialog(Controllers.getStage()));

        if (res == null || res.isEmpty()) return;
        installWorld(res.get(0));
    }

    public void download() {
        Controllers.getDownloadPage().showWorldDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void installWorld(Path zipFile) {
        // Only accept one world file because user is required to confirm the new world name
        // Or too many input dialogs are popped.
        Task.supplyAsync(() -> new World(zipFile))
                .whenComplete(Schedulers.javafx(), world -> {
                    Controllers.prompt(i18n("world.name.enter"), (name, handler) -> {
                        Task.runAsync(() -> world.install(savesDir, name))
                                .whenComplete(Schedulers.javafx(), () -> {
                                    handler.resolve();
                                    refresh();
                                }, e -> {
                                    if (e instanceof FileAlreadyExistsException)
                                        handler.reject(i18n("world.add.failed", i18n("world.add.already_exists")));
                                    else if (e instanceof IOException && e.getCause() instanceof InvalidPathException)
                                        handler.reject(i18n("world.add.failed", i18n("install.new_game.malformed")));
                                    else
                                        handler.reject(i18n("world.add.failed", e.getClass().getName() + ": " + e.getLocalizedMessage()));
                                }).start();
                    }, world.getWorldName(), new Validator(i18n("install.new_game.malformed"), FileUtils::isNameValid));
                }, e -> {
                    LOG.warning("Unable to parse world file " + zipFile, e);
                    Controllers.dialog(i18n("world.add.invalid"));
                }).start();
    }

    private void showManagePage(World world) {
        Controllers.navigate(new WorldManagePage(world, profile, instanceId));
    }

    public void export(World world) {
        WorldManageUIUtils.export(world);
    }

    public void delete(World world) {
        WorldManageUIUtils.delete(world, this::refresh);
    }

    public void copy(World world) {
        WorldManageUIUtils.copyWorld(world, this::refresh);
    }

    public void reveal(World world) {
        FXUtils.openFolder(world.getFile());
    }

    public void launch(World world) {
        Versions.launchAndEnterWorld(profile, instanceId, world.getFileName());
    }

    public void generateLaunchScript(World world) {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, instanceId, world.getFileName());
    }

    public BooleanProperty showAllProperty() {
        return showAll;
    }

    public ReadOnlyBooleanProperty supportQuickPlayProperty() {
        return supportQuickPlay;
    }

    private final class WorldListPageSkin extends ToolbarListPageSkin<World, WorldListPage> {

        WorldListPageSkin() {
            super(WorldListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(WorldListPage skinnable) {
            JFXCheckBox chkShowAll = new JFXCheckBox(i18n("world.show_all"));
            chkShowAll.selectedProperty().bindBidirectional(skinnable.showAllProperty());

            return Arrays.asList(
                    chkShowAll,
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("world.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("world.download"), SVG.DOWNLOAD, skinnable::download)
            );
        }

        @Override
        protected ListCell<World> createListCell(JFXListView<World> listView) {
            return new WorldListCell(getSkinnable());
        }
    }

    private static final class WorldListCell extends ListCell<World> {

        private final WorldListPage page;

        private final RipplerContainer graphic;
        private final ImageView imageView;
        private final Tooltip leftTooltip;
        private final TwoLineListItem content;
        private final JFXButton btnLaunch;

        public WorldListCell(WorldListPage page) {
            this.page = page;

            var root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            {
                StackPane left = new StackPane();
                this.leftTooltip = new Tooltip();
                FXUtils.installSlowTooltip(left, leftTooltip);
                root.setLeft(left);
                left.setPadding(new Insets(0, 8, 0, 0));

                this.imageView = new ImageView();
                left.getChildren().add(imageView);
                FXUtils.limitSize(imageView, 32, 32);
            }

            {
                this.content = new TwoLineListItem();
                root.setCenter(content);
                content.setMouseTransparent(true);
            }

            {
                HBox right = new HBox(8);
                root.setRight(right);
                right.setAlignment(Pos.CENTER_RIGHT);

                btnLaunch = new JFXButton();
                btnLaunch.visibleProperty().bind(page.supportQuickPlayProperty());
                btnLaunch.managedProperty().bind(btnLaunch.visibleProperty());
                right.getChildren().add(btnLaunch);
                btnLaunch.getStyleClass().add("toggle-icon4");
                btnLaunch.setGraphic(SVG.ROCKET_LAUNCH.createIcon());
                FXUtils.installFastTooltip(btnLaunch, i18n("version.launch"));
                btnLaunch.setOnAction(event -> {
                    World world = getItem();
                    if (world != null)
                        page.launch(world);
                });

                JFXButton btnMore = new JFXButton();
                right.getChildren().add(btnMore);
                btnMore.getStyleClass().add("toggle-icon4");
                btnMore.setGraphic(SVG.MORE_VERT.createIcon());
                btnMore.setOnAction(event -> {
                    World world = getItem();
                    if (world != null)
                        showPopupMenu(world, page.supportQuickPlayProperty().get(), JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
                });
            }

            this.graphic = new RipplerContainer(root);
            graphic.setOnMouseClicked(event -> {
                if (event.getClickCount() != 1)
                    return;

                World world = getItem();
                if (world == null)
                    return;

                if (event.getButton() == MouseButton.PRIMARY)
                    page.showManagePage(world);
                else if (event.getButton() == MouseButton.SECONDARY)
                    showPopupMenu(world, page.supportQuickPlayProperty().get(), JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
            });
        }

        @Override
        protected void updateItem(World world, boolean empty) {
            super.updateItem(world, empty);

            this.content.getTags().clear();

            if (empty || world == null) {
                setGraphic(null);
                imageView.setImage(null);
                leftTooltip.setText("");
                content.setTitle("");
                content.setSubtitle("");
            } else {
                imageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
                leftTooltip.setText(world.getFile().toString());
                content.setTitle(world.getWorldName() != null ? parseColorEscapes(world.getWorldName()) : "");

                if (world.getGameVersion() != null)
                    content.addTag(I18n.getDisplayVersion(world.getGameVersion()));
                if (world.isLocked()) {
                    content.addTag(i18n("world.locked"));
                    btnLaunch.setDisable(true);
                } else {
                    btnLaunch.setDisable(false);
                }

                content.setSubtitle(i18n("world.datetime", formatDateTime(Instant.ofEpochMilli(world.getLastPlayed()))));

                setGraphic(graphic);
            }
        }

        // Popup Menu

        public void showPopupMenu(World world, boolean supportQuickPlay, JFXPopup.PopupHPosition hPosition, double initOffsetX, double initOffsetY) {
            boolean worldLocked = world.isLocked();

            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            if (supportQuickPlay) {

                IconedMenuItem launchItem = new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch_and_enter_world"), () -> page.launch(world), popup);
                launchItem.setDisable(worldLocked);

                popupMenu.getContent().addAll(
                        launchItem,
                        new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), () -> page.generateLaunchScript(world), popup),
                        new MenuSeparator()
                );
            }

            popupMenu.getContent().add(new IconedMenuItem(SVG.SETTINGS, i18n("world.manage.button"), () -> page.showManagePage(world), popup));

            if (ChunkBaseApp.isSupported(world)) {
                popupMenu.getContent().addAll(
                        new MenuSeparator(),
                        new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(world), popup),
                        new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(world), popup),
                        new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(world), popup)
                );

                if (ChunkBaseApp.supportEndCity(world)) {
                    popupMenu.getContent().add(new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"), () -> ChunkBaseApp.openEndCityFinder(world), popup));
                }
            }

            IconedMenuItem exportMenuItem = new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), () -> page.export(world), popup);
            exportMenuItem.setDisable(worldLocked);

            IconedMenuItem deleteMenuItem = new IconedMenuItem(SVG.DELETE, i18n("world.delete"), () -> page.delete(world), popup);
            deleteMenuItem.setDisable(worldLocked);

            IconedMenuItem duplicateMenuItem = new IconedMenuItem(SVG.CONTENT_COPY, i18n("world.duplicate"), () -> page.copy(world), popup);
            duplicateMenuItem.setDisable(worldLocked);

            popupMenu.getContent().addAll(
                    new MenuSeparator(),
                    exportMenuItem,
                    deleteMenuItem,
                    duplicateMenuItem
            );

            popupMenu.getContent().addAll(
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.world"), () -> page.reveal(world), popup)
            );

            JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(this, popup);
            popup.show(this, vPosition, hPosition, initOffsetX, vPosition == JFXPopup.PopupVPosition.TOP ? initOffsetY : -initOffsetY);
        }
    }
}
