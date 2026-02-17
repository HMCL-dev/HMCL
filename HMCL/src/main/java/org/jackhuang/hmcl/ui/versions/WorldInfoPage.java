/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.github.steveice10.opennbt.tag.builtin.*;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldInfoPage extends SpinnerPane implements WorldManagePage.WorldRefreshable {
    private final WorldManagePage worldManagePage;
    private boolean isReadOnly;
    private final World world;
    private CompoundTag levelDat;

    ImageView iconImageView = new ImageView();

    public WorldInfoPage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        this.world = worldManagePage.getWorld();
        refresh();
    }

    private CompoundTag loadWorldInfo() throws IOException {
        if (!Files.isDirectory(world.getFile()))
            throw new IOException("Not a valid world directory");

        return world.getLevelData();
    }

    private void updateControls() {
        CompoundTag dataTag = levelDat.get("Data");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        setContent(scrollPane);

        VBox rootPane = new VBox();
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");

        ComponentList worldInfo = new ComponentList();
        {
            var worldNamePane = new LinePane();
            {
                worldNamePane.setTitle(i18n("world.name"));
                JFXTextField worldNameField = new JFXTextField();
                setRightTextField(worldNamePane, worldNameField, 200);

                if (dataTag.get("LevelName") instanceof StringTag worldNameTag) {
                    var worldName = new SimpleStringProperty(worldNameTag.getValue());
                    FXUtils.bindString(worldNameField, worldName);
                    worldNameField.getProperties().put(WorldInfoPage.class.getName() + ".worldNameProperty", worldName);
                    worldName.addListener((observable, oldValue, newValue) -> {
                        if (StringUtils.isNotBlank(newValue)) {
                            try {
                                world.setWorldName(newValue);
                                worldManagePage.setTitle(i18n("world.manage.title", StringUtils.parseColorEscapes(world.getWorldName())));
                            } catch (Exception e) {
                                LOG.warning("Failed to set world name", e);
                            }
                        }
                    });
                } else {
                    worldNameField.setDisable(true);
                }
            }

            var gameVersionPane = new LineTextPane();
            {
                gameVersionPane.setTitle(i18n("world.info.game_version"));
                gameVersionPane.setText(world.getGameVersion() == null ? "" : world.getGameVersion().toNormalizedString());
            }

            var iconPane = new LinePane();
            {
                iconPane.setTitle(i18n("world.icon"));

                {
                    FXUtils.limitSize(iconImageView, 32, 32);
                    iconImageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
                }

                JFXButton editIconButton = new JFXButton();
                JFXButton resetIconButton = new JFXButton();
                {
                    editIconButton.setGraphic(SVG.EDIT.createIcon(20));
                    editIconButton.setDisable(isReadOnly);
                    editIconButton.setOnAction(event -> Controllers.confirm(
                            I18n.i18n("world.icon.change.tip"),
                            I18n.i18n("world.icon.change"),
                            MessageDialogPane.MessageType.INFO,
                            this::changeWorldIcon,
                            null
                    ));
                    FXUtils.installFastTooltip(editIconButton, i18n("button.edit"));
                    editIconButton.getStyleClass().add("toggle-icon4");

                    resetIconButton.setGraphic(SVG.RESTORE.createIcon(20));
                    resetIconButton.setDisable(isReadOnly);
                    resetIconButton.setOnAction(event -> this.clearWorldIcon());
                    FXUtils.installFastTooltip(resetIconButton, i18n("button.reset"));
                    resetIconButton.getStyleClass().add("toggle-icon4");
                }

                HBox hBox = new HBox(8);
                hBox.setAlignment(Pos.CENTER_LEFT);
                hBox.getChildren().addAll(iconImageView, editIconButton, resetIconButton);

                iconPane.setRight(hBox);
            }

            var seedPane = new LinePane();
            {
                seedPane.setTitle(i18n("world.info.random_seed"));

                SimpleBooleanProperty visibility = new SimpleBooleanProperty();
                StackPane visibilityButton = new StackPane();
                {
                    visibilityButton.setCursor(Cursor.HAND);
                    visibilityButton.setAlignment(Pos.CENTER_RIGHT);
                    FXUtils.onClicked(visibilityButton, () -> visibility.set(!visibility.get()));
                }

                Label seedLabel = new Label();
                {
                    FXUtils.copyOnDoubleClick(seedLabel);
                    seedLabel.setAlignment(Pos.CENTER_RIGHT);

                    seedLabel.setText(world.getSeed() != null ? world.getSeed().toString() : "");

                    BoxBlur blur = new BoxBlur();
                    blur.setIterations(3);
                    FXUtils.onChangeAndOperate(visibility, isVisibility -> {
                        SVG icon = isVisibility ? SVG.VISIBILITY : SVG.VISIBILITY_OFF;
                        visibilityButton.getChildren().setAll(icon.createIcon(12));
                        seedLabel.setEffect(isVisibility ? null : blur);
                    });
                }

                HBox right = new HBox(8);
                {
                    right.setAlignment(Pos.CENTER_RIGHT);
                    right.getChildren().setAll(visibilityButton, seedLabel);
                    seedPane.setRight(right);
                }
            }

            var worldSpawnPoint = new LineTextPane();
            {
                worldSpawnPoint.setTitle(i18n("world.info.spawn"));

                String value;
                if (dataTag.get("spawn") instanceof CompoundTag spawnTag && spawnTag.get("pos") instanceof IntArrayTag posTag) {
                    value = Dimension.of(spawnTag.get("dimension") instanceof StringTag dimensionTag
                                    ? dimensionTag
                                    : new StringTag("SpawnDimension", "minecraft:overworld"))
                            .formatPosition(posTag);
                } else if (dataTag.get("SpawnX") instanceof IntTag intX
                        && dataTag.get("SpawnY") instanceof IntTag intY
                        && dataTag.get("SpawnZ") instanceof IntTag intZ) {
                    value = Dimension.OVERWORLD.formatPosition(intX.getValue(), intY.getValue(), intZ.getValue());
                } else {
                    value = null;
                }

                worldSpawnPoint.setText(value);
            }

            var lastPlayedPane = new LineTextPane();
            {
                lastPlayedPane.setTitle(i18n("world.info.last_played"));
                lastPlayedPane.setText(formatDateTime(Instant.ofEpochMilli(world.getLastPlayed())));
            }

            var timePane = new LineTextPane();
            {
                timePane.setTitle(i18n("world.info.time"));
                if (dataTag.get("Time") instanceof LongTag timeTag) {
                    Duration duration = Duration.ofSeconds(timeTag.getValue() / 20);
                    timePane.setText(i18n("world.info.time.format", duration.toDays(), duration.toHoursPart(), duration.toMinutesPart()));
                }
            }

            var allowCheatsButton = new LineToggleButton();
            {
                allowCheatsButton.setTitle(i18n("world.info.allow_cheats"));
                allowCheatsButton.setDisable(isReadOnly);

                bindTagAndToggleButton(dataTag.get("allowCommands"), allowCheatsButton);
            }

            var generateFeaturesButton = new LineToggleButton();
            {
                generateFeaturesButton.setTitle(i18n("world.info.generate_features"));
                generateFeaturesButton.setDisable(isReadOnly);

                // generate_features was valid after 20w20a and MapFeatures was before that
                if (dataTag.get("WorldGenSettings") instanceof CompoundTag worldGenSettings) {
                    bindTagAndToggleButton(worldGenSettings.get("generate_features"), generateFeaturesButton);
                } else {
                    bindTagAndToggleButton(dataTag.get("MapFeatures"), generateFeaturesButton);
                }
            }

            var difficultyButton = new LineSelectButton<Difficulty>();
            {
                difficultyButton.setTitle(i18n("world.info.difficulty"));
                difficultyButton.setDisable(worldManagePage.isReadOnly());
                difficultyButton.setItems(Difficulty.items);

                if (dataTag.get("Difficulty") instanceof ByteTag difficultyTag) {
                    Difficulty difficulty = Difficulty.of(difficultyTag.getValue());
                    if (difficulty != null) {
                        difficultyButton.setValue(difficulty);
                        difficultyButton.valueProperty().addListener((o, oldValue, newValue) -> {
                            if (newValue != null) {
                                difficultyTag.setValue((byte) newValue.ordinal());
                                saveLevelDat();
                            }
                        });
                    } else {
                        difficultyButton.setDisable(true);
                    }
                } else {
                    difficultyButton.setDisable(true);
                }
            }

            var difficultyLockPane = new LineToggleButton();
            {
                difficultyLockPane.setTitle(i18n("world.info.difficulty_lock"));
                difficultyLockPane.setDisable(isReadOnly);

                bindTagAndToggleButton(dataTag.get("DifficultyLocked"), difficultyLockPane);
            }

            worldInfo.getContent().setAll(
                    worldNamePane, gameVersionPane, iconPane, seedPane, worldSpawnPoint, lastPlayedPane, timePane,
                    allowCheatsButton, generateFeaturesButton, difficultyButton, difficultyLockPane);

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info")), worldInfo);
        }

        if (dataTag.get("Player") instanceof CompoundTag playerTag) {
            ComponentList playerInfo = new ComponentList();

            var locationPane = new LineTextPane();
            {
                locationPane.setTitle(i18n("world.info.player.location"));
                Dimension dimension = Dimension.of(playerTag.get("Dimension"));
                if (dimension != null && playerTag.get("Pos") instanceof ListTag posTag) {
                    locationPane.setText(dimension.formatPosition(posTag));
                }
            }

            var lastDeathLocationPane = new LineTextPane();
            {
                lastDeathLocationPane.setTitle(i18n("world.info.player.last_death_location"));
                // Valid after 22w14a; prior to this version, the game did not record the last death location data.
                if (playerTag.get("LastDeathLocation") instanceof CompoundTag LastDeathLocationTag) {
                    Dimension dimension = Dimension.of(LastDeathLocationTag.get("dimension"));
                    if (dimension != null && LastDeathLocationTag.get("pos") instanceof IntArrayTag posTag) {
                        lastDeathLocationPane.setText(dimension.formatPosition(posTag));
                    }
                }
            }

            var spawnPane = new LineTextPane();
            {
                spawnPane.setTitle(i18n("world.info.player.spawn"));
                if (playerTag.get("respawn") instanceof CompoundTag respawnTag
                        && respawnTag.get("dimension") instanceof StringTag dimensionTag
                        && respawnTag.get("pos") instanceof IntArrayTag intArrayTag
                        && intArrayTag.length() >= 3) { // Valid after 25w07a
                    spawnPane.setText(Dimension.of(dimensionTag).formatPosition(intArrayTag));
                } else if (playerTag.get("SpawnX") instanceof IntTag intX
                        && playerTag.get("SpawnY") instanceof IntTag intY
                        && playerTag.get("SpawnZ") instanceof IntTag intZ) { // Valid before 25w07a
                    // SpawnDimension tag is valid after 20w12a. Prior to this version, the game did not record the respawn point dimension and respawned in the Overworld.
                    spawnPane.setText((playerTag.get("SpawnDimension") instanceof StringTag dimensionTag ? Dimension.of(dimensionTag) : Dimension.OVERWORLD)
                            .formatPosition(intX.getValue(), intY.getValue(), intZ.getValue()));
                }
            }

            var playerGameTypePane = new LineSelectButton<GameType>();
            {
                playerGameTypePane.setTitle(i18n("world.info.player.game_type"));
                playerGameTypePane.setDisable(worldManagePage.isReadOnly());
                playerGameTypePane.setItems(GameType.items);

                if (playerTag.get("playerGameType") instanceof IntTag playerGameTypeTag
                        && dataTag.get("hardcore") instanceof ByteTag hardcoreTag) {
                    boolean isHardcore = hardcoreTag.getValue() == 1;
                    GameType gameType = GameType.of(playerGameTypeTag.getValue(), isHardcore);
                    if (gameType != null) {
                        playerGameTypePane.setValue(gameType);
                        playerGameTypePane.valueProperty().addListener((o, oldValue, newValue) -> {
                            if (newValue != null) {
                                if (newValue == GameType.HARDCORE) {
                                    playerGameTypeTag.setValue(0); // survival (hardcore worlds are survival+hardcore flag)
                                    hardcoreTag.setValue((byte) 1);
                                } else {
                                    playerGameTypeTag.setValue(newValue.ordinal());
                                    hardcoreTag.setValue((byte) 0);
                                }
                                saveLevelDat();
                            }
                        });
                    } else {
                        playerGameTypePane.setDisable(true);
                    }
                } else {
                    playerGameTypePane.setDisable(true);
                }
            }

            var healthPane = new LinePane();
            {
                healthPane.setTitle(i18n("world.info.player.health"));
                setRightTextField(healthPane, 50, playerTag.get("Health"));
            }

            var foodLevelPane = new LinePane();
            {
                foodLevelPane.setTitle(i18n("world.info.player.food_level"));
                setRightTextField(foodLevelPane, 50, playerTag.get("foodLevel"));
            }

            var foodSaturationPane = new LinePane();
            {
                foodSaturationPane.setTitle(i18n("world.info.player.food_saturation_level"));
                setRightTextField(foodSaturationPane, 50, playerTag.get("foodSaturationLevel"));
            }

            var xpLevelPane = new LinePane();
            {
                xpLevelPane.setTitle(i18n("world.info.player.xp_level"));
                setRightTextField(xpLevelPane, 50, playerTag.get("XpLevel"));
            }

            playerInfo.getContent().setAll(
                    locationPane, lastDeathLocationPane, spawnPane, playerGameTypePane,
                    healthPane, foodLevelPane, foodSaturationPane, xpLevelPane
            );

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info.player")), playerInfo);
        }
    }

    private void setRightTextField(LinePane linePane, int perfWidth, Tag tag) {
        JFXTextField textField = new JFXTextField();
        setRightTextField(linePane, textField, perfWidth);
        if (tag instanceof IntTag intTag) {
            bindTagAndTextField(intTag, textField);
        } else if (tag instanceof FloatTag floatTag) {
            bindTagAndTextField(floatTag, textField);
        } else {
            textField.setDisable(true);
        }
    }

    private void setRightTextField(LinePane linePane, JFXTextField textField, int perfWidth) {
        textField.setDisable(isReadOnly);
        textField.setPrefWidth(perfWidth);
        linePane.setRight(textField);
    }

    private void bindTagAndToggleButton(Tag tag, LineToggleButton toggleButton) {
        if (tag instanceof ByteTag byteTag) {
            byte value = byteTag.getValue();
            if (value == 0 || value == 1) {
                toggleButton.setSelected(value == 1);
                toggleButton.selectedProperty().addListener((o, oldValue, newValue) -> {
                    try {
                        byteTag.setValue((byte) (newValue ? 1 : 0));
                        saveLevelDat();
                    } catch (Exception e) {
                        toggleButton.setSelected(oldValue);
                        LOG.warning("Exception happened when saving level.dat", e);
                    }
                });
            } else {
                toggleButton.setDisable(true);
            }
        } else {
            toggleButton.setDisable(true);
        }
    }

    private void bindTagAndTextField(IntTag intTag, JFXTextField jfxTextField) {
        jfxTextField.setText(intTag.getValue().toString());

        jfxTextField.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    Integer integer = Lang.toIntOrNull(newValue);
                    if (integer != null) {
                        intTag.setValue(integer);
                        saveLevelDat();
                    }
                } catch (Exception e) {
                    jfxTextField.setText(oldValue);
                    LOG.warning("Exception happened when saving level.dat", e);
                }
            }
        });
        FXUtils.setValidateWhileTextChanged(jfxTextField, true);
        jfxTextField.setValidators(new NumberValidator(i18n("input.number"), true));
    }

    private void bindTagAndTextField(FloatTag floatTag, JFXTextField jfxTextField) {
        jfxTextField.setText(new DecimalFormat("0.#").format(floatTag.getValue()));

        jfxTextField.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    Float floatValue = Lang.toFloatOrNull(newValue);
                    if (floatValue != null) {
                        floatTag.setValue(floatValue);
                        saveLevelDat();
                    }
                } catch (Exception e) {
                    jfxTextField.setText(oldValue);
                    LOG.warning("Exception happened when saving level.dat", e);
                }
            }
        });
        FXUtils.setValidateWhileTextChanged(jfxTextField, true);
        jfxTextField.setValidators(new DoubleValidator(i18n("input.number"), true));
    }

    private void saveLevelDat() {
        LOG.info("Saving level.dat of world " + world.getWorldName());
        try {
            this.world.writeLevelDat(levelDat);
        } catch (IOException e) {
            LOG.warning("Failed to save level.dat of world " + world.getWorldName(), e);
        }
    }

    @Override
    public void refresh() {
        this.isReadOnly = worldManagePage.isReadOnly();
        this.setLoading(true);
        Task.supplyAsync(this::loadWorldInfo)
                .whenComplete(Schedulers.javafx(), ((result, exception) -> {
                    if (exception == null) {
                        this.levelDat = result;
                        updateControls();
                        setLoading(false);
                    } else {
                        LOG.warning("Failed to load level.dat", exception);
                        setFailedReason(i18n("world.info.failed"));
                    }
                })).start();
    }

    private record Dimension(String name) {
        static final Dimension OVERWORLD = new Dimension(null);
        static final Dimension THE_NETHER = new Dimension(i18n("world.info.dimension.the_nether"));
        static final Dimension THE_END = new Dimension(i18n("world.info.dimension.the_end"));

        static Dimension of(Tag tag) {
            if (tag instanceof IntTag intTag) {
                return switch (intTag.getValue()) {
                    case 0 -> OVERWORLD;
                    case -1 -> THE_NETHER;
                    case 1 -> THE_END;
                    default -> null;
                };
            } else if (tag instanceof StringTag stringTag) {
                String id = stringTag.getValue();
                return switch (id) {
                    case "overworld", "minecraft:overworld" -> OVERWORLD;
                    case "the_nether", "minecraft:the_nether" -> THE_NETHER;
                    case "the_end", "minecraft:the_end" -> THE_END;
                    default -> new Dimension(id);
                };
            } else {
                return null;
            }
        }

        String formatPosition(Tag tag) {
            if (tag instanceof ListTag listTag && listTag.size() == 3) {

                Tag x = listTag.get(0);
                Tag y = listTag.get(1);
                Tag z = listTag.get(2);

                if (x instanceof DoubleTag && y instanceof DoubleTag && z instanceof DoubleTag) {
                    return this == OVERWORLD
                            ? String.format("(%.2f, %.2f, %.2f)", x.getValue(), y.getValue(), z.getValue())
                            : String.format("%s (%.2f, %.2f, %.2f)", name, x.getValue(), y.getValue(), z.getValue());
                }

                return null;
            }

            if (tag instanceof IntArrayTag intArrayTag) {

                int x = intArrayTag.getValue(0);
                int y = intArrayTag.getValue(1);
                int z = intArrayTag.getValue(2);

                return this == OVERWORLD
                        ? String.format("(%d, %d, %d)", x, y, z)
                        : String.format("%s (%d, %d, %d)", name, x, y, z);
            }

            return null;
        }

        String formatPosition(int x, int y, int z) {
            return this == OVERWORLD
                    ? String.format("(%d, %d, %d)", x, y, z)
                    : String.format("%s (%d, %d, %d)", name, x, y, z);
        }

        String formatPosition(double x, double y, double z) {
            return this == OVERWORLD
                    ? String.format("(%.2f, %.2f, %.2f)", x, y, z)
                    : String.format("%s (%.2f, %.2f, %.2f)", name, x, y, z);
        }
    }

    private enum Difficulty {
        PEACEFUL, EASY, NORMAL, HARD;

        static final ObservableList<Difficulty> items = FXCollections.observableList(Arrays.asList(values()));

        static Difficulty of(int d) {
            return (d >= 0 && d < items.size()) ? items.get(d) : null;
        }

        @Override
        public String toString() {
            return i18n("world.info.difficulty." + name().toLowerCase(Locale.ROOT));
        }
    }

    private enum GameType {
        SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR, HARDCORE;

        static final ObservableList<GameType> items = FXCollections.observableList(Arrays.asList(values()));

        static GameType of(int d, boolean hardcore) {
            if (hardcore && d == 0) return HARDCORE; // hardcore + survival
            return d >= 0 && d < 4 ? items.get(d) : null;
        }

        @Override
        public String toString() {
            return i18n("world.info.player.game_type." + name().toLowerCase(Locale.ROOT));
        }
    }

    private void changeWorldIcon() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.icon.choose.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.png"), "*.png"));
        fileChooser.setInitialFileName("icon.png");

        Path iconPath = FileUtils.toPath(fileChooser.showOpenDialog(Controllers.getStage()));
        if (iconPath == null) return;

        Image image;
        try {
            image = FXUtils.loadImage(iconPath);
        } catch (Exception e) {
            LOG.warning("Failed to load image", e);
            Controllers.dialog(i18n("world.icon.change.fail.load.text"), i18n("world.icon.change.fail.load.title"), MessageDialogPane.MessageType.ERROR);
            return;
        }
        if ((int) image.getWidth() == 64 && (int) image.getHeight() == 64) {
            Path output = world.getFile().resolve("icon.png");
            saveWorldIcon(iconPath, image, output);
        } else {
            Controllers.dialog(i18n("world.icon.change.fail.not_64x64.text", (int) image.getWidth(), (int) image.getHeight()), i18n("world.icon.change.fail.not_64x64.title"), MessageDialogPane.MessageType.ERROR);
        }
    }

    private void saveWorldIcon(Path sourcePath, Image image, Path targetPath) {
        Image oldImage = iconImageView.getImage();
        try {
            FileUtils.copyFile(sourcePath, targetPath);
            iconImageView.setImage(image);
            Controllers.showToast(i18n("world.icon.change.succeed.toast"));
        } catch (IOException e) {
            LOG.warning("Failed to save world icon " + e.getMessage());
            iconImageView.setImage(oldImage);
        }
    }

    private void clearWorldIcon() {
        Path output = world.getFile().resolve("icon.png");
        try {
            Files.deleteIfExists(output);
            iconImageView.setImage(FXUtils.newBuiltinImage("/assets/img/unknown_server.png"));
        } catch (IOException e) {
            LOG.warning("Failed to delete world icon " + e.getMessage());
        }
    }
}
