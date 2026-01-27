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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.glavo.png.javafx.PNGJavaFXUtils;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jetbrains.annotations.PropertyKey;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Callable;

import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldInfoPage extends SpinnerPane {
    private final WorldManagePage worldManagePage;
    private final World world;
    private CompoundTag levelDat;

    ImageView iconImageView = new ImageView();

    public WorldInfoPage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        this.world = worldManagePage.getWorld();

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

    private CompoundTag loadWorldInfo() throws IOException {
        if (!Files.isDirectory(world.getFile()))
            throw new IOException("Not a valid world directory");

        return world.getLevelData();
    }

    private void updateControls() {
        CompoundTag dataTag = levelDat.get("Data");
        CompoundTag worldGenSettings = dataTag.get("WorldGenSettings");

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

        ComponentList basicInfo = new ComponentList();
        {
            BorderPane worldNamePane = new BorderPane();
            {
                setLeftLabel(worldNamePane, "world.name");
                JFXTextField worldNameField = new JFXTextField();
                setRightTextField(worldNamePane, worldNameField, 200);

                Tag tag = dataTag.get("LevelName");
                if (tag instanceof StringTag stringTag) {
                    worldNameField.setText(stringTag.getValue());

                    worldNameField.textProperty().addListener((observable, oldValue, newValue) -> {
                        if (newValue != null) {
                            try {
                                world.setWorldName(newValue);
                            } catch (Exception e) {
                                LOG.warning("Failed to set world name", e);
                            }
                        }
                    });
                } else {
                    worldNameField.setDisable(true);
                }
            }

            BorderPane gameVersionPane = new BorderPane();
            {
                setLeftLabel(gameVersionPane, "world.info.game_version");
                Label gameVersionLabel = new Label();
                setRightTextLabel(gameVersionPane, gameVersionLabel, () -> world.getGameVersion() == null ? "" : world.getGameVersion().toNormalizedString());
            }

            BorderPane iconPane = new BorderPane();
            {
                setLeftLabel(iconPane, "world.icon");

                Runnable onClickAction = () -> Controllers.confirm(
                        i18n("world.icon.change.tip"), i18n("world.icon.change"), MessageDialogPane.MessageType.INFO,
                        this::changeWorldIcon,
                        null
                );

                FXUtils.limitSize(iconImageView, 32, 32);
                {
                    iconImageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
                }

                JFXButton editIconButton = new JFXButton();
                JFXButton resetIconButton = new JFXButton();
                {
                    editIconButton.setGraphic(SVG.EDIT.createIcon(20));
                    editIconButton.setDisable(worldManagePage.isReadOnly());
                    FXUtils.onClicked(editIconButton, onClickAction);
                    FXUtils.installFastTooltip(editIconButton, i18n("button.edit"));
                    editIconButton.getStyleClass().add("toggle-icon4");

                    resetIconButton.setGraphic(SVG.RESTORE.createIcon(20));
                    resetIconButton.setDisable(worldManagePage.isReadOnly());
                    FXUtils.onClicked(resetIconButton, this::clearWorldIcon);
                    FXUtils.installFastTooltip(resetIconButton, i18n("button.reset"));
                    resetIconButton.getStyleClass().add("toggle-icon4");
                }

                HBox hBox = new HBox(8);
                hBox.setAlignment(Pos.CENTER_LEFT);
                hBox.getChildren().addAll(iconImageView, editIconButton, resetIconButton);

                iconPane.setRight(hBox);
            }

            BorderPane seedPane = new BorderPane();
            {
                setLeftLabel(seedPane, "world.info.random_seed");

                SimpleBooleanProperty visibility = new SimpleBooleanProperty();
                StackPane visibilityButton = new StackPane();
                {
                    visibilityButton.setCursor(Cursor.HAND);
                    visibilityButton.setAlignment(Pos.BOTTOM_RIGHT);
                    FXUtils.setLimitWidth(visibilityButton, 12);
                    FXUtils.setLimitHeight(visibilityButton, 12);
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
                    BorderPane.setAlignment(right, Pos.CENTER_RIGHT);
                    right.getChildren().setAll(visibilityButton, seedLabel);
                    seedPane.setRight(right);
                }
            }

            BorderPane lastPlayedPane = new BorderPane();
            {
                setLeftLabel(lastPlayedPane, "world.info.last_played");
                Label lastPlayedLabel = new Label();
                setRightTextLabel(lastPlayedPane, lastPlayedLabel, () -> formatDateTime(Instant.ofEpochMilli(world.getLastPlayed())));
            }

            BorderPane timePane = new BorderPane();
            {
                setLeftLabel(timePane, "world.info.time");

                Label timeLabel = new Label();
                setRightTextLabel(timePane, timeLabel, () -> {
                    Tag tag = dataTag.get("Time");
                    if (tag instanceof LongTag) {
                        long days = ((LongTag) tag).getValue() / 24000;
                        return i18n("world.info.time.format", days);
                    } else {
                        return "";
                    }
                });
            }

            LineToggleButton allowCheatsButton = new LineToggleButton();
            {
                allowCheatsButton.setTitle(i18n("world.info.allow_cheats"));
                allowCheatsButton.setDisable(worldManagePage.isReadOnly());
                Tag tag = dataTag.get("allowCommands");

                checkTagAndSetListener(tag, allowCheatsButton);
            }

            LineToggleButton generateFeaturesButton = new LineToggleButton();
            {
                generateFeaturesButton.setTitle(i18n("world.info.generate_features"));
                generateFeaturesButton.setDisable(worldManagePage.isReadOnly());
                Tag tag = worldGenSettings != null ? worldGenSettings.get("generate_features") : dataTag.get("MapFeatures");

                checkTagAndSetListener(tag, generateFeaturesButton);
            }

            BorderPane difficultyPane = new BorderPane();
            {
                setLeftLabel(difficultyPane, "world.info.difficulty");

                JFXComboBox<Difficulty> difficultyBox = new JFXComboBox<>(Difficulty.items);
                difficultyBox.setDisable(worldManagePage.isReadOnly());
                BorderPane.setAlignment(difficultyBox, Pos.CENTER_RIGHT);
                difficultyPane.setRight(difficultyBox);

                Tag tag = dataTag.get("Difficulty");
                if (tag instanceof ByteTag byteTag) {
                    Difficulty difficulty = Difficulty.of(byteTag.getValue());
                    if (difficulty != null) {
                        difficultyBox.setValue(difficulty);
                        difficultyBox.valueProperty().addListener((o, oldValue, newValue) -> {
                            if (newValue != null) {
                                byteTag.setValue((byte) newValue.ordinal());
                                saveLevelDat();
                            }
                        });
                    } else {
                        difficultyBox.setDisable(true);
                    }
                } else {
                    difficultyBox.setDisable(true);
                }
            }

            LineToggleButton difficultyLockPane = new LineToggleButton();
            {
                difficultyLockPane.setTitle(i18n("world.info.difficulty_lock"));
                difficultyLockPane.setDisable(worldManagePage.isReadOnly());

                Tag tag = dataTag.get("DifficultyLocked");
                checkTagAndSetListener(tag, difficultyLockPane);
            }

            basicInfo.getContent().setAll(
                    worldNamePane, gameVersionPane, iconPane, seedPane, lastPlayedPane, timePane,
                    allowCheatsButton, generateFeaturesButton, difficultyPane, difficultyLockPane);

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info.basic")), basicInfo);
        }

        Tag playerTag = dataTag.get("Player");
        if (playerTag instanceof CompoundTag player) {
            ComponentList playerInfo = new ComponentList();

            BorderPane locationPane = new BorderPane();
            {
                setLeftLabel(locationPane, "world.info.player.location");
                Label locationLabel = new Label();
                setRightTextLabel(locationPane, locationLabel, () -> {
                    Dimension dim = Dimension.of(player.get("Dimension"));
                    if (dim != null) {
                        String posString = dim.formatPosition(player.get("Pos"));
                        if (posString != null)
                            return posString;
                    }
                    return "";
                });
            }

            BorderPane lastDeathLocationPane = new BorderPane();
            {
                setLeftLabel(lastDeathLocationPane, "world.info.player.last_death_location");
                Label lastDeathLocationLabel = new Label();
                setRightTextLabel(lastDeathLocationPane, lastDeathLocationLabel, () -> {
                    Tag tag = player.get("LastDeathLocation");// Valid after 22w14a; prior to this version, the game did not record the last death location data.
                    if (tag instanceof CompoundTag compoundTag) {
                        Dimension dim = Dimension.of(compoundTag.get("dimension"));
                        if (dim != null) {
                            String posString = dim.formatPosition(compoundTag.get("pos"));
                            if (posString != null)
                                return posString;
                        }
                    }
                    return "";
                });

            }

            BorderPane spawnPane = new BorderPane();
            {
                setLeftLabel(spawnPane, "world.info.player.spawn");
                Label spawnLabel = new Label();
                setRightTextLabel(spawnPane, spawnLabel, () -> {

                    Dimension dimension;
                    if (player.get("respawn") instanceof CompoundTag respawnTag && respawnTag.get("dimension") != null) { // Valid after 25w07a
                        dimension = Dimension.of(respawnTag.get("dimension"));
                        Tag posTag = respawnTag.get("pos");

                        if (posTag instanceof IntArrayTag intArrayTag && intArrayTag.length() >= 3) {
                            return dimension.formatPosition(intArrayTag.getValue(0), intArrayTag.getValue(1), intArrayTag.getValue(2));
                        }
                    } else if (player.get("SpawnX") instanceof IntTag intX
                            && player.get("SpawnY") instanceof IntTag intY
                            && player.get("SpawnZ") instanceof IntTag intZ) { // Valid before 25w07a
                        // SpawnDimension tag is valid after 20w12a. Prior to this version, the game did not record the respawn point dimension and respawned in the Overworld.
                        dimension = Dimension.of(player.get("SpawnDimension") == null ? new IntTag("SpawnDimension", 0) : player.get("SpawnDimension"));
                        if (dimension == null) {
                            return "";
                        }

                        return dimension.formatPosition(intX.getValue(), intY.getValue(), intZ.getValue());
                    }

                    return "";
                });
            }

            BorderPane playerGameTypePane = new BorderPane();
            {
                setLeftLabel(playerGameTypePane, "world.info.player.game_type");

                JFXComboBox<GameType> gameTypeBox = new JFXComboBox<>(GameType.items);
                gameTypeBox.setDisable(worldManagePage.isReadOnly());
                BorderPane.setAlignment(gameTypeBox, Pos.CENTER_RIGHT);
                playerGameTypePane.setRight(gameTypeBox);

                Tag tag = player.get("playerGameType");
                Tag hardcoreTag = dataTag.get("hardcore");
                boolean isHardcore = hardcoreTag instanceof ByteTag && ((ByteTag) hardcoreTag).getValue() == 1;

                if (tag instanceof IntTag intTag) {
                    GameType gameType = GameType.of(intTag.getValue(), isHardcore);
                    if (gameType != null) {
                        gameTypeBox.setValue(gameType);
                        gameTypeBox.valueProperty().addListener((o, oldValue, newValue) -> {
                            if (newValue != null) {
                                if (newValue == GameType.HARDCORE) {
                                    intTag.setValue(0); // survival (hardcore worlds are survival+hardcore flag)
                                    if (hardcoreTag instanceof ByteTag) {
                                        ((ByteTag) hardcoreTag).setValue((byte) 1);
                                    }
                                } else {
                                    intTag.setValue(newValue.ordinal());
                                    if (hardcoreTag instanceof ByteTag) {
                                        ((ByteTag) hardcoreTag).setValue((byte) 0);
                                    }
                                }
                                saveLevelDat();
                            }
                        });
                    } else {
                        gameTypeBox.setDisable(true);
                    }
                } else {
                    gameTypeBox.setDisable(true);
                }
            }

            BorderPane healthPane = new BorderPane();
            {
                setLeftLabel(healthPane, "world.info.player.health");
                JFXTextField healthField = new JFXTextField();
                setRightTextField(healthPane, healthField, 50);

                Tag tag = player.get("Health");
                if (tag instanceof FloatTag floatTag) {
                    setTagAndTextField(floatTag, healthField);
                } else {
                    healthField.setDisable(true);
                }
            }

            BorderPane foodLevelPane = new BorderPane();
            {
                setLeftLabel(foodLevelPane, "world.info.player.food_level");
                JFXTextField foodLevelField = new JFXTextField();
                setRightTextField(foodLevelPane, foodLevelField, 50);

                Tag tag = player.get("foodLevel");
                if (tag instanceof IntTag intTag) {
                    setTagAndTextField(intTag, foodLevelField);
                } else {
                    foodLevelField.setDisable(true);
                }
            }

            BorderPane xpLevelPane = new BorderPane();
            {
                setLeftLabel(xpLevelPane, "world.info.player.xp_level");
                JFXTextField xpLevelField = new JFXTextField();
                setRightTextField(xpLevelPane, xpLevelField, 50);

                Tag tag = player.get("XpLevel");
                if (tag instanceof IntTag intTag) {
                    setTagAndTextField(intTag, xpLevelField);
                } else {
                    xpLevelField.setDisable(true);
                }
            }

            playerInfo.getContent().setAll(
                    locationPane, lastDeathLocationPane, spawnPane,
                    playerGameTypePane, healthPane, foodLevelPane, xpLevelPane
            );

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info.player")), playerInfo);
        }
    }

    private void setLeftLabel(BorderPane borderPane, @PropertyKey(resourceBundle = "assets.lang.I18N") String key) {
        Label label = new Label(i18n(key));
        BorderPane.setAlignment(label, Pos.CENTER_LEFT);
        borderPane.setLeft(label);
    }

    private void setRightTextField(BorderPane borderPane, JFXTextField textField, int perfWidth) {
        textField.setDisable(worldManagePage.isReadOnly());
        textField.setPrefWidth(perfWidth);
        textField.setAlignment(Pos.CENTER_RIGHT);
        borderPane.setRight(textField);
    }

    private void setRightTextLabel(BorderPane borderPane, Label label, Callable<String> setNameCall) {
        FXUtils.copyOnDoubleClick(label);
        BorderPane.setAlignment(label, Pos.CENTER_RIGHT);
        try {
            label.setText(setNameCall.call());
        } catch (Exception e) {
            LOG.warning("Exception happened when setting name", e);
        }
        borderPane.setRight(label);
    }

    private void checkTagAndSetListener(Tag tag, LineToggleButton toggleButton) {
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

    private void setTagAndTextField(IntTag intTag, JFXTextField jfxTextField) {
        jfxTextField.setText(String.valueOf(intTag.getValue()));

        jfxTextField.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    intTag.setValue(Integer.parseInt(newValue));
                    saveLevelDat();
                } catch (Exception e) {
                    jfxTextField.setText(oldValue);
                    LOG.warning("Exception happened when saving level.dat", e);
                }
            }
        });
        FXUtils.setValidateWhileTextChanged(jfxTextField, true);
        jfxTextField.setValidators(new NumberValidator(i18n("input.number"), true));
    }

    private void setTagAndTextField(FloatTag floatTag, JFXTextField jfxTextField) {
        jfxTextField.setText(new DecimalFormat("#").format(floatTag.getValue().floatValue()));

        jfxTextField.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    floatTag.setValue(Float.parseFloat(newValue));
                    saveLevelDat();
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

    private record Dimension(String name) {
        static final Dimension OVERWORLD = new Dimension(null);
        static final Dimension THE_NETHER = new Dimension(i18n("world.info.dimension.the_nether"));
        static final Dimension THE_END = new Dimension(i18n("world.info.dimension.the_end"));

        static Dimension of(Tag tag) {
            if (tag instanceof IntTag intTag) {
                return switch (intTag.getValue()) {
                    case 0 -> OVERWORLD;
                    case 1 -> THE_NETHER;
                    case 2 -> THE_END;
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

        File file = fileChooser.showOpenDialog(Controllers.getStage());
        if (file == null) return;

        Image image;
        try {
            image = FXUtils.loadImage(file.toPath());
        } catch (Exception e) {
            LOG.warning("Failed to load image", e);
            Controllers.dialog(i18n("world.icon.change.fail.load.text"), i18n("world.icon.change.fail.load.title"), MessageDialogPane.MessageType.ERROR);
            return;
        }
        if ((int) image.getWidth() == 64 && (int) image.getHeight() == 64) {
            Path output = world.getFile().resolve("icon.png");
            saveImage(image, output);
        } else {
            Controllers.dialog(i18n("world.icon.change.fail.not_64x64.text", (int) image.getWidth(), (int) image.getHeight()), i18n("world.icon.change.fail.not_64x64.title"), MessageDialogPane.MessageType.ERROR);
        }
    }

    private void saveImage(Image image, Path path) {
        Image oldImage = iconImageView.getImage();
        try {
            PNGJavaFXUtils.writeImage(image, path);
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
