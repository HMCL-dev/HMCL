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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.glavo.png.javafx.PNGJavaFXUtils;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

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

        return world.readLevelDat();
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
                Label label = new Label(i18n("world.name"));
                worldNamePane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                JFXTextField worldNameField = new JFXTextField();
                worldNameField.setDisable(worldManagePage.isDisable());
                worldNameField.setPrefWidth(200);
                BorderPane.setAlignment(worldNameField, Pos.CENTER_RIGHT);
                worldNamePane.setRight(worldNameField);

                Tag tag = dataTag.get("LevelName");
                if (tag instanceof StringTag stringTag) {
                    worldNameField.setText(stringTag.getValue());

                    worldNameField.textProperty().addListener((observable, oldValue, newValue) -> {
                        if (newValue != null) {
                            try {
                                stringTag.setValue(newValue);
                                world.setWorldName(newValue);
                                saveLevelDat();
                            } catch (Throwable ignored) {

                            }
                        }
                    });
                } else {
                    worldNameField.setDisable(true);
                }
            }

            BorderPane gameVersionPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.game_version"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                gameVersionPane.setLeft(label);

                Label gameVersionLabel = new Label();
                FXUtils.copyOnDoubleClick(gameVersionLabel);
                gameVersionLabel.setText(world.getGameVersion());
                BorderPane.setAlignment(gameVersionLabel, Pos.CENTER_RIGHT);
                gameVersionPane.setRight(gameVersionLabel);
            }

            BorderPane iconPane = new BorderPane();
            {
                Label label = new Label("图标");
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                iconPane.setLeft(label);


                FXUtils.limitSize(iconImageView, 32, 32);
                iconImageView.setImage(world.getIcon() == null ? FXUtils.newBuiltinImage("/assets/img/unknown_server.png") : world.getIcon());
                FXUtils.onClicked(iconImageView, this::replaceWorldIcon);
                iconPane.setRight(iconImageView);
            }

            BorderPane randomSeedPane = new BorderPane();
            {

                HBox left = new HBox(8);
                BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                left.setAlignment(Pos.CENTER_LEFT);
                randomSeedPane.setLeft(left);

                Label label = new Label(i18n("world.info.random_seed"));

                SimpleBooleanProperty visibility = new SimpleBooleanProperty();
                StackPane visibilityButton = new StackPane();
                visibilityButton.setCursor(Cursor.HAND);
                FXUtils.setLimitWidth(visibilityButton, 12);
                FXUtils.setLimitHeight(visibilityButton, 12);
                FXUtils.onClicked(visibilityButton, () -> visibility.set(!visibility.get()));

                left.getChildren().setAll(label, visibilityButton);

                Label randomSeedLabel = new Label();
                FXUtils.copyOnDoubleClick(randomSeedLabel);
                BorderPane.setAlignment(randomSeedLabel, Pos.CENTER_RIGHT);
                randomSeedPane.setRight(randomSeedLabel);

                Tag tag = worldGenSettings != null ? worldGenSettings.get("seed") : dataTag.get("RandomSeed");
                if (tag instanceof LongTag) {
                    randomSeedLabel.setText(tag.getValue().toString());
                }

                BoxBlur blur = new BoxBlur();
                blur.setIterations(3);
                FXUtils.onChangeAndOperate(visibility, isVisibility -> {
                    SVG icon = isVisibility ? SVG.VISIBILITY : SVG.VISIBILITY_OFF;
                    visibilityButton.getChildren().setAll(icon.createIcon(Theme.blackFill(), 12));
                    randomSeedLabel.setEffect(isVisibility ? null : blur);
                });
            }

            BorderPane lastPlayedPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.last_played"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                lastPlayedPane.setLeft(label);

                Label lastPlayedLabel = new Label();
                FXUtils.copyOnDoubleClick(lastPlayedLabel);
                lastPlayedLabel.setText(formatDateTime(Instant.ofEpochMilli(world.getLastPlayed())));
                BorderPane.setAlignment(lastPlayedLabel, Pos.CENTER_RIGHT);
                lastPlayedPane.setRight(lastPlayedLabel);
            }

            BorderPane timePane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.time"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                timePane.setLeft(label);

                Label timeLabel = new Label();
                FXUtils.copyOnDoubleClick(timeLabel);
                BorderPane.setAlignment(timeLabel, Pos.CENTER_RIGHT);
                timePane.setRight(timeLabel);

                Tag tag = dataTag.get("Time");
                if (tag instanceof LongTag) {
                    long days = ((LongTag) tag).getValue() / 24000;
                    timeLabel.setText(i18n("world.info.time.format", days));
                }
            }

            OptionToggleButton allowCheatsButton = new OptionToggleButton();
            {
                allowCheatsButton.setTitle(i18n("world.info.allow_cheats"));
                allowCheatsButton.setDisable(worldManagePage.isReadOnly());
                Tag tag = dataTag.get("allowCommands");

                if (tag instanceof ByteTag byteTag) {
                    byte value = byteTag.getValue();
                    if (value == 0 || value == 1) {
                        allowCheatsButton.setSelected(value == 1);
                        allowCheatsButton.selectedProperty().addListener((o, oldValue, newValue) -> {
                            byteTag.setValue(newValue ? (byte) 1 : (byte) 0);
                            saveLevelDat();
                        });
                    } else {
                        allowCheatsButton.setDisable(true);
                    }
                } else {
                    allowCheatsButton.setDisable(true);
                }
            }

            OptionToggleButton generateFeaturesButton = new OptionToggleButton();
            {
                generateFeaturesButton.setTitle(i18n("world.info.generate_features"));
                generateFeaturesButton.setDisable(worldManagePage.isReadOnly());
                Tag tag = worldGenSettings != null ? worldGenSettings.get("generate_features") : dataTag.get("MapFeatures");

                if (tag instanceof ByteTag byteTag) {
                    byte value = byteTag.getValue();
                    if (value == 0 || value == 1) {
                        generateFeaturesButton.setSelected(value == 1);
                        generateFeaturesButton.selectedProperty().addListener((o, oldValue, newValue) -> {
                            byteTag.setValue(newValue ? (byte) 1 : (byte) 0);
                            saveLevelDat();
                        });
                    } else {
                        generateFeaturesButton.setDisable(true);
                    }
                } else {
                    generateFeaturesButton.setDisable(true);
                }
            }

            BorderPane difficultyPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.difficulty"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                difficultyPane.setLeft(label);

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

            OptionToggleButton difficultyLockPane = new OptionToggleButton();
            {
                difficultyLockPane.setTitle("难度锁定");
                difficultyLockPane.setDisable(worldManagePage.isReadOnly());

                Tag tag = dataTag.get("DifficultyLocked");

                if (tag instanceof ByteTag byteTag) {
                    byte value = byteTag.getValue();
                    if (value == 0 || value == 1) {
                        difficultyLockPane.setSelected(value == 1);
                        difficultyLockPane.selectedProperty().addListener((o, oldValue, newValue) -> {
                            byteTag.setValue(newValue ? (byte) 1 : (byte) 0);
                            saveLevelDat();
                        });
                    } else {
                        difficultyLockPane.setDisable(true);
                    }
                } else {
                    difficultyLockPane.setDisable(true);
                }
            }

            basicInfo.getContent().setAll(
                    worldNamePane, gameVersionPane, iconPane, randomSeedPane, lastPlayedPane, timePane,
                    allowCheatsButton, generateFeaturesButton, difficultyPane, difficultyLockPane);

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info.basic")), basicInfo);
        }

        Tag playerTag = dataTag.get("Player");
        if (playerTag instanceof CompoundTag player) {
            ComponentList playerInfo = new ComponentList();

            BorderPane locationPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.location"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                locationPane.setLeft(label);

                Label locationLabel = new Label();
                FXUtils.copyOnDoubleClick(locationLabel);
                BorderPane.setAlignment(locationLabel, Pos.CENTER_RIGHT);
                locationPane.setRight(locationLabel);

                Dimension dim = Dimension.of(player.get("Dimension"));
                if (dim != null) {
                    String posString = dim.formatPosition(player.get("Pos"));
                    if (posString != null)
                        locationLabel.setText(posString);
                }
            }

            BorderPane lastDeathLocationPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.last_death_location"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                lastDeathLocationPane.setLeft(label);

                Label lastDeathLocationLabel = new Label();
                FXUtils.copyOnDoubleClick(lastDeathLocationLabel);
                BorderPane.setAlignment(lastDeathLocationLabel, Pos.CENTER_RIGHT);
                lastDeathLocationPane.setRight(lastDeathLocationLabel);

                Tag tag = player.get("LastDeathLocation");
                if (tag instanceof CompoundTag) {
                    Dimension dim = Dimension.of(((CompoundTag) tag).get("dimension"));
                    if (dim != null) {
                        String posString = dim.formatPosition(((CompoundTag) tag).get("pos"));
                        if (posString != null)
                            lastDeathLocationLabel.setText(posString);
                    }
                }
            }

            BorderPane spawnPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.spawn"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                spawnPane.setLeft(label);

                Label spawnLabel = new Label();
                FXUtils.copyOnDoubleClick(spawnLabel);
                BorderPane.setAlignment(spawnLabel, Pos.CENTER_RIGHT);
                spawnPane.setRight(spawnLabel);

                Dimension dim = Dimension.of(player.get("SpawnDimension"));
                if (dim != null) {
                    Tag x = player.get("SpawnX");
                    Tag y = player.get("SpawnY");
                    Tag z = player.get("SpawnZ");

                    if (x instanceof IntTag && y instanceof IntTag && z instanceof IntTag)
                        spawnLabel.setText(dim.formatPosition(((IntTag) x).getValue(), ((IntTag) y).getValue(), ((IntTag) z).getValue()));
                }
            }

            BorderPane playerGameTypePane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.game_type"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                playerGameTypePane.setLeft(label);

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
                Label label = new Label(i18n("world.info.player.health"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                healthPane.setLeft(label);

                JFXTextField healthField = new JFXTextField();
                healthField.setDisable(worldManagePage.isReadOnly());
                healthField.setPrefWidth(50);
                healthField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(healthField, Pos.CENTER_RIGHT);
                healthPane.setRight(healthField);

                Tag tag = player.get("Health");
                if (tag instanceof FloatTag floatTag) {
                    healthField.setText(new DecimalFormat("#").format(floatTag.getValue().floatValue()));

                    healthField.textProperty().addListener((o, oldValue, newValue) -> {
                        if (newValue != null) {
                            try {
                                floatTag.setValue(Float.parseFloat(newValue));
                                saveLevelDat();
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    FXUtils.setValidateWhileTextChanged(healthField, true);
                    healthField.setValidators(new DoubleValidator(i18n("input.number"), true));
                } else {
                    healthField.setDisable(true);
                }
            }

            BorderPane foodLevelPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.food_level"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                foodLevelPane.setLeft(label);

                JFXTextField foodLevelField = new JFXTextField();
                foodLevelField.setDisable(worldManagePage.isReadOnly());
                foodLevelField.setPrefWidth(50);
                foodLevelField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(foodLevelField, Pos.CENTER_RIGHT);
                foodLevelPane.setRight(foodLevelField);

                Tag tag = player.get("foodLevel");
                if (tag instanceof IntTag intTag) {
                    foodLevelField.setText(String.valueOf(intTag.getValue()));

                    foodLevelField.textProperty().addListener((o, oldValue, newValue) -> {
                        if (newValue != null) {
                            try {
                                intTag.setValue(Integer.parseInt(newValue));
                                saveLevelDat();
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    FXUtils.setValidateWhileTextChanged(foodLevelField, true);
                    foodLevelField.setValidators(new NumberValidator(i18n("input.number"), true));
                } else {
                    foodLevelField.setDisable(true);
                }
            }

            BorderPane xpLevelPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.xp_level"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                xpLevelPane.setLeft(label);

                JFXTextField xpLevelField = new JFXTextField();
                xpLevelField.setDisable(worldManagePage.isReadOnly());
                xpLevelField.setPrefWidth(50);
                xpLevelField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(xpLevelField, Pos.CENTER_RIGHT);
                xpLevelPane.setRight(xpLevelField);

                Tag tag = player.get("XpLevel");
                if (tag instanceof IntTag intTag) {
                    xpLevelField.setText(String.valueOf(intTag.getValue()));

                    xpLevelField.textProperty().addListener((o, oldValue, newValue) -> {
                        if (newValue != null) {
                            try {
                                intTag.setValue(Integer.parseInt(newValue));
                                saveLevelDat();
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    FXUtils.setValidateWhileTextChanged(xpLevelField, true);
                    xpLevelField.setValidators(new NumberValidator(i18n("input.number"), true));
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

    private void saveLevelDat() {
        LOG.info("Saving level.dat of world " + world.getWorldName());
        try {
            this.world.writeLevelDat(levelDat);
        } catch (IOException e) {
            LOG.warning("Failed to save level.dat of world " + world.getWorldName(), e);
        }
    }

    private static final class Dimension {
        static final Dimension OVERWORLD = new Dimension(null);
        static final Dimension THE_NETHER = new Dimension(i18n("world.info.dimension.the_nether"));
        static final Dimension THE_END = new Dimension(i18n("world.info.dimension.the_end"));

        final String name;

        static Dimension of(Tag tag) {
            if (tag instanceof IntTag intTag) {
                switch (intTag.getValue()) {
                    case 0:
                        return OVERWORLD;
                    case 1:
                        return THE_NETHER;
                    case 2:
                        return THE_END;
                    default:
                        return null;
                }
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

        private Dimension(String name) {
            this.name = name;
        }

        String formatPosition(Tag tag) {
            if (tag instanceof ListTag listTag) {
                if (listTag.size() != 3)
                    return null;

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
            return d >= 0 && d <= items.size() ? items.get(d) : null;
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

    private void replaceWorldIcon() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择图像");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png"));
        fileChooser.setInitialFileName("icon");

        File file = fileChooser.showOpenDialog(Controllers.getStage());
        if (file == null) return;

        Image original = new Image(file.toURI().toString());

        Image square = cropCenterSquare(original);

        Image finalImage;
        if ((int) square.getWidth() == 64 && (int) square.getHeight() == 64) {
            finalImage = square;
        } else {
            finalImage = resizeImage(square, 64, 64);
        }

        Path output = world.getFile().resolve("icon.png");
        saveImage(finalImage, output);
    }

    private Image cropCenterSquare(Image img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;

        PixelReader reader = img.getPixelReader();
        WritableImage newImg = new WritableImage(reader, x, y, size, size);
        return newImg;
    }

    private Image resizeImage(Image img, int width, int height) {
        ImageView view = new ImageView(img);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);

        SnapshotParameters params = new SnapshotParameters();
        return view.snapshot(params, null);
    }

    private void saveImage(Image image, Path path) {
        try {
            PNGJavaFXUtils.writeImage(image, path);
            iconImageView.setImage(image);
        } catch (IOException e) {
            LOG.warning(e.getMessage());
        }
    }
}
