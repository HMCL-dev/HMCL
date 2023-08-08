package org.jackhuang.hmcl.ui.versions;

import com.github.steveice10.opennbt.tag.builtin.*;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DoubleValidator;
import org.jackhuang.hmcl.ui.construct.NumberValidator;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.i18n.Locales;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class WorldInfoPage extends StackPane implements DecoratorPage {
    private final World world;
    private final CompoundTag levelDat;
    private final CompoundTag dataTag;

    private final ObjectProperty<State> stateProperty = new SimpleObjectProperty<>();

    public WorldInfoPage(World world) throws IOException {
        this.world = world;
        this.levelDat = world.readLevelDat();
        this.dataTag = levelDat.get("Data");

        CompoundTag worldGenSettings = dataTag.get("WorldGenSettings");

        stateProperty.set(State.fromTitle(i18n("world.info.title", world.getWorldName())));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        getChildren().setAll(scrollPane);

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

                Label worldNameLabel = new Label();
                worldNameLabel.setText(world.getWorldName());
                BorderPane.setAlignment(worldNameLabel, Pos.CENTER_RIGHT);
                worldNamePane.setRight(worldNameLabel);
            }

            BorderPane gameVersionPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.game_version"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                gameVersionPane.setLeft(label);

                Label gameVersionLabel = new Label();
                gameVersionLabel.setText(world.getGameVersion());
                BorderPane.setAlignment(gameVersionLabel, Pos.CENTER_RIGHT);
                gameVersionPane.setRight(gameVersionLabel);
            }

            BorderPane randomSeedPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.random_seed"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                randomSeedPane.setLeft(label);

                Label randomSeedLabel = new Label();
                BorderPane.setAlignment(randomSeedLabel, Pos.CENTER_RIGHT);
                randomSeedPane.setRight(randomSeedLabel);

                Tag tag = worldGenSettings != null ? worldGenSettings.get("seed") : dataTag.get("RandomSeed");
                if (tag instanceof LongTag) {
                    randomSeedLabel.setText(tag.getValue().toString());
                }
            }

            BorderPane lastPlayedPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.last_played"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                lastPlayedPane.setLeft(label);

                Label lastPlayedLabel = new Label();
                lastPlayedLabel.setText(Locales.SIMPLE_DATE_FORMAT.get().format(new Date(world.getLastPlayed())));
                BorderPane.setAlignment(lastPlayedLabel, Pos.CENTER_RIGHT);
                lastPlayedPane.setRight(lastPlayedLabel);
            }

            BorderPane timePane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.time"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                timePane.setLeft(label);

                Label timeLabel = new Label();
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
                Tag tag = dataTag.get("allowCommands");

                if (tag instanceof ByteTag) {
                    ByteTag byteTag = (ByteTag) tag;
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
                Tag tag = worldGenSettings != null ? worldGenSettings.get("generate_features") : dataTag.get("MapFeatures");

                if (tag instanceof ByteTag) {
                    ByteTag byteTag = (ByteTag) tag;
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
                BorderPane.setAlignment(difficultyBox, Pos.CENTER_RIGHT);
                difficultyPane.setRight(difficultyBox);

                Tag tag = dataTag.get("Difficulty");
                if (tag instanceof ByteTag) {
                    ByteTag byteTag = (ByteTag) tag;
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

            basicInfo.getContent().setAll(
                    worldNamePane, gameVersionPane, randomSeedPane, lastPlayedPane, timePane,
                    allowCheatsButton, generateFeaturesButton, difficultyPane);

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.info.basic")), basicInfo);
        }

        Tag playerTag = dataTag.get("Player");
        if (playerTag instanceof CompoundTag) {
            CompoundTag player = (CompoundTag) playerTag;
            ComponentList playerInfo = new ComponentList();

            BorderPane locationPane = new BorderPane();
            {
                Label label = new Label(i18n("world.info.player.location"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                locationPane.setLeft(label);

                Label locationLabel = new Label();
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
                BorderPane.setAlignment(gameTypeBox, Pos.CENTER_RIGHT);
                playerGameTypePane.setRight(gameTypeBox);

                Tag tag = player.get("playerGameType");
                if (tag instanceof IntTag) {
                    IntTag intTag = (IntTag) tag;
                    GameType gameType = GameType.of(intTag.getValue());
                    if (gameType != null) {
                        gameTypeBox.setValue(gameType);
                        gameTypeBox.valueProperty().addListener((o, oldValue, newValue) -> {
                            if (newValue != null) {
                                intTag.setValue(newValue.ordinal());
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
                healthField.setPrefWidth(50);
                healthField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(healthField, Pos.CENTER_RIGHT);
                healthPane.setRight(healthField);

                Tag tag = player.get("Health");
                if (tag instanceof FloatTag) {
                    FloatTag floatTag = (FloatTag) tag;
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
                foodLevelField.setPrefWidth(50);
                foodLevelField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(foodLevelField, Pos.CENTER_RIGHT);
                foodLevelPane.setRight(foodLevelField);

                Tag tag = player.get("foodLevel");
                if (tag instanceof IntTag) {
                    IntTag intTag = (IntTag) tag;
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
                xpLevelField.setPrefWidth(50);
                xpLevelField.setAlignment(Pos.CENTER_RIGHT);
                BorderPane.setAlignment(xpLevelField, Pos.CENTER_RIGHT);
                xpLevelPane.setRight(xpLevelField);

                Tag tag = player.get("XpLevel");
                if (tag instanceof IntTag) {
                    IntTag intTag = (IntTag) tag;
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
            LOG.log(Level.WARNING, "Failed to save level.dat of world " + world.getWorldName(), e);
        }
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return stateProperty;
    }

    private static final class Dimension {
        static final Dimension OVERWORLD = new Dimension(null);
        static final Dimension THE_NETHER = new Dimension(i18n("world.info.dimension.the_nether"));
        static final Dimension THE_END = new Dimension(i18n("world.info.dimension.the_end"));

        final String name;

        static Dimension of(Tag tag) {
            if (tag instanceof IntTag) {
                switch (((IntTag) tag).getValue()) {
                    case 0:
                        return OVERWORLD;
                    case 1:
                        return THE_NETHER;
                    case 2:
                        return THE_END;
                    default:
                        return null;
                }
            } else if (tag instanceof StringTag) {
                String id = ((StringTag) tag).getValue();
                switch (id) {
                    case "overworld":
                    case "minecraft:overworld":
                        return OVERWORLD;
                    case "the_nether":
                    case "minecraft:the_nether":
                        return THE_NETHER;
                    case "the_end":
                    case "minecraft:the_end":
                        return THE_END;
                    default:
                        return new Dimension(id);
                }
            } else {
                return null;
            }
        }

        private Dimension(String name) {
            this.name = name;
        }

        String formatPosition(Tag tag) {
            if (tag instanceof ListTag) {
                ListTag listTag = (ListTag) tag;
                if (listTag.size() != 3)
                    return null;

                Tag x = listTag.get(0);
                Tag y = listTag.get(1);
                Tag z = listTag.get(2);

                if (x instanceof DoubleTag && y instanceof DoubleTag && z instanceof DoubleTag) {
                    //noinspection MalformedFormatString
                    return this == OVERWORLD
                            ? String.format("(%.2f, %.2f, %.2f)", x.getValue(), y.getValue(), z.getValue())
                            : String.format("%s (%.2f, %.2f, %.2f)", name, x.getValue(), y.getValue(), z.getValue());
                }

                return null;
            }

            if (tag instanceof IntArrayTag) {
                IntArrayTag intArrayTag = (IntArrayTag) tag;

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
        SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR;

        static final ObservableList<GameType> items = FXCollections.observableList(Arrays.asList(values()));

        static GameType of(int d) {
            return d >= 0 && d <= items.size() ? items.get(d) : null;
        }

        @Override
        public String toString() {
            return i18n("world.info.player.game_type." + name().toLowerCase(Locale.ROOT));
        }
    }
}
