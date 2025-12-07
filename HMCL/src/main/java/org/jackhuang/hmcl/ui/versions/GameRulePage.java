package org.jackhuang.hmcl.ui.versions;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.Holder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class GameRulePage extends ListPageBase<GameRulePage.GameRuleInfo> {

    private WorldManagePage worldManagePage;
    private World world;
    private CompoundTag levelDat;

    Map<String, GameRule> gameRuleMap = GameRule.getCloneGameRuleMap();
    Map<String, GameRule> gameRuleFinalMap = new HashMap<>();

    ObservableList<GameRulePage.GameRuleInfo> gameRuleList;

    public GameRulePage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        this.world = worldManagePage.getWorld();

        gameRuleList = FXCollections.observableArrayList();
        setItems(gameRuleList);

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

    public void updateControls() {
        boolean isNewGameRuleFormat;

        CompoundTag dataTag = levelDat.get("Data");
        CompoundTag gameRuleCompoundTag;
        gameRuleCompoundTag = dataTag.get("game_rules");
        if (gameRuleCompoundTag != null) {
            isNewGameRuleFormat = true;
        } else {
            gameRuleCompoundTag = dataTag.get("GameRules");
            isNewGameRuleFormat = false;
        }
        if (isNewGameRuleFormat) {
            gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
                LOG.trace(gameRuleTag.toString());
                if (gameRuleTag instanceof IntTag intTag) {
                    GameRule finalGameRule = GameRule.createSimpleGameRule(intTag.getName(), intTag.getValue());
                    GameRule gr = gameRuleMap.getOrDefault(intTag.getName(), null);
                    if (gr instanceof GameRule.IntGameRule intGR) {
                        gameRuleFinalMap.put(intTag.getName(), GameRule.mixGameRule(finalGameRule, intGR));
                        LOG.trace("find one: " + finalGameRule.getRuleKey() + ", intTag is " + intTag.getValue());
                    }
                } else if (gameRuleTag instanceof ByteTag byteTag) {
                    GameRule finalGameRule = GameRule.createSimpleGameRule(byteTag.getName(), byteTag.getValue() == 1);
                    GameRule gr = gameRuleMap.getOrDefault(byteTag.getName(), null);
                    if (gr instanceof GameRule.BooleanGameRule booleanGR) {
                        gameRuleFinalMap.put(byteTag.getName(), GameRule.mixGameRule(finalGameRule, booleanGR));
                        LOG.trace("find one: " + finalGameRule.getRuleKey() + ", byteTag is " + byteTag.getValue());
                    }
                }
            });
        }

        LOG.trace("gameRuleFinalMap: size" + gameRuleFinalMap.size() + ", detail: " + gameRuleFinalMap.toString());
        gameRuleFinalMap.forEach((s, gameRule) -> {
            String displayText;
            try {
                displayText = i18n(gameRule.getDisplayI18nKey());
            } catch (Exception e) {
                displayText = gameRule.getDisplayI18nKey();
            }
            if (gameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                gameRuleList.add(new GameRuleInfo(s, displayText, booleanGameRule.getValue()));
            } else if (gameRule instanceof GameRule.IntGameRule intGameRule) {
                gameRuleList.add(new GameRuleInfo(s, displayText, intGameRule.getValue()));
            }
        });

    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameRulePageSkin(this);
    }

    static class GameRuleInfo {

        String ruleKey;
        String displayName;
        BooleanProperty onValue;
        IntegerProperty currentValue;
        GameRuleType gameRuleType;

        BorderPane container = new BorderPane();

        public GameRuleInfo(String ruleKey, String displayName, Boolean onValue) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.onValue = new SimpleBooleanProperty(onValue);
            gameRuleType = GameRuleType.BOOLEAN;

            OptionToggleButton toggleButton = new OptionToggleButton();
            toggleButton.setTitle(displayName);
            toggleButton.setSubtitle(ruleKey);
            toggleButton.setSelected(onValue);

            HBox.setHgrow(container, Priority.ALWAYS);
            container.setCenter(toggleButton);
        }

        public GameRuleInfo(String ruleKey, String displayName, Integer currentValue) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.currentValue = new SimpleIntegerProperty(currentValue);
            gameRuleType = GameRuleType.INT;

            VBox vbox = new VBox();
            vbox.getChildren().addAll(new Label(displayName), new Label(ruleKey));
            vbox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(vbox, Priority.ALWAYS);

            container.setPadding(new Insets(8, 8, 8, 16));
            JFXTextField textField = new JFXTextField();
            textField.maxWidth(10);
            textField.minWidth(10);
            textField.textProperty().set(currentValue.toString());

            HBox.setHgrow(container, Priority.ALWAYS);
            container.setCenter(vbox);
            container.setRight(textField);
        }

    }

    static class GameRulePageSkin extends SkinBase<GameRulePage> {

        private final HBox searchBar;
        private final JFXTextField searchField;
        JFXListView<GameRuleInfo> listView = new JFXListView<>();

        protected GameRulePageSkin(GameRulePage control) {
            super(control);
            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            {
                searchBar = new HBox();
                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(new Insets(0, 5, 0, 5));
                searchField = new JFXTextField();
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                        searchField::clear);
                FXUtils.onEscPressed(searchField, closeSearchBar::fire);
                searchBar.getChildren().addAll(searchField, closeSearchBar);
                root.getContent().add(searchBar);
            }

            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.setContent(listView);
            Holder<Object> lastCell = new Holder<>();
            listView.setItems(getSkinnable().getItems());
            listView.setCellFactory(x -> new GameRuleListCell(listView, lastCell));
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
            root.getContent().add(center);

            pane.getChildren().add(root);
            getChildren().add(pane);

        }
    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo> {

        public GameRuleListCell(JFXListView<GameRuleInfo> listView, Holder<Object> lastCell) {
            super(listView, lastCell);
        }

        @Override
        protected void updateControl(GameRuleInfo item, boolean empty) {
            if (empty) return;

            getContainer().getChildren().setAll(item.container);
        }
    }

    enum GameRuleType {
        INT, BOOLEAN
    }

    private CompoundTag loadWorldInfo() throws IOException {
        if (!Files.isDirectory(world.getFile()))
            throw new IOException("Not a valid world directory");

        return world.readLevelDat();
    }
}
