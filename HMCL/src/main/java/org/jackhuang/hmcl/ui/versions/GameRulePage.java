package org.jackhuang.hmcl.ui.versions;

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
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.Holder;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameRulePage extends ListPageBase<GameRulePage.GameRuleInfo> {

    WorldManagePage worldManagePage;

    public GameRulePage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        ObservableList<GameRulePage.GameRuleInfo> gameRuleList = FXCollections.observableArrayList();
        //用于测试，目前还在写UI。
        gameRuleList.add(new GameRuleInfo("sneak_speed", "潜行前进速度", 2));
        gameRuleList.add(new GameRuleInfo("spawn_immediately", "死亡立即重生", false));
        gameRuleList.add(new GameRuleInfo("time_advance", "时间自然流逝", true));
        gameRuleList.add(new GameRuleInfo("max_health", "最大生命值", 30));
        gameRuleList.add(new GameRuleInfo("pvp_enabled", "启用玩家间对抗", false));
        gameRuleList.add(new GameRuleInfo("keep_inventory_on_death", "死亡后保留物品栏", true));
        gameRuleList.add(new GameRuleInfo("mob_spawning_enabled", "启用怪物生成", true));
        gameRuleList.add(new GameRuleInfo("day_night_cycle_speed", "昼夜交替速度", 1));
        gameRuleList.add(new GameRuleInfo("fall_damage_multiplier", "掉落伤害倍率", 1));
        gameRuleList.add(new GameRuleInfo("friendly_fire_enabled", "启用友军伤害", false));
        gameRuleList.add(new GameRuleInfo("health_regeneration", "启用生命值自然恢复", true));
        gameRuleList.add(new GameRuleInfo("explosions_destroy_blocks", "爆炸破坏方块", true));
        gameRuleList.add(new GameRuleInfo("show_coordinates", "显示玩家坐标", true));
        gameRuleList.add(new GameRuleInfo("crafting_enabled", "启用制作系统", true));
        gameRuleList.add(new GameRuleInfo("hunger_system_enabled", "启用饥饿系统", true));
        gameRuleList.add(new GameRuleInfo("xp_multiplier", "经验值获取倍率", 1));
        gameRuleList.add(new GameRuleInfo("max_players", "服务器最大玩家数", 10));
        gameRuleList.add(new GameRuleInfo("gravity_level", "重力等级", 10));
        gameRuleList.add(new GameRuleInfo("keep_xp_on_death", "死亡后保留经验值", false));
        gameRuleList.add(new GameRuleInfo("weather_cycle_enabled", "启用天气变化", true));
        gameRuleList.add(new GameRuleInfo("loot_drop_multiplier", "战利品掉落倍率", 1));
        gameRuleList.add(new GameRuleInfo("build_height_limit", "建筑高度限制", 256));
        gameRuleList.add(new GameRuleInfo("enable_flight", "允许玩家飞行", false));
        gameRuleList.add(new GameRuleInfo("mob_griefing", "怪物破坏环境", true));
        gameRuleList.add(new GameRuleInfo("resource_respawn_rate", "资源刷新速率", 100));
        gameRuleList.add(new GameRuleInfo("chat_enabled", "启用游戏内聊天", true));
        gameRuleList.add(new GameRuleInfo("max_mana", "最大法力值", 100));
        gameRuleList.add(new GameRuleInfo("mana_regeneration_rate", "法力恢复速度", 5));
        gameRuleList.add(new GameRuleInfo("stamina_consumption_rate", "耐力消耗速率", 1));
        gameRuleList.add(new GameRuleInfo("enable_portals", "启用传送门", true));
        gameRuleList.add(new GameRuleInfo("difficulty_level", "游戏难度等级", 2));
        gameRuleList.add(new GameRuleInfo("command_blocks_enabled", "启用命令方块", false));
        gameRuleList.add(new GameRuleInfo("structure_generation", "生成世界结构", true));
        gameRuleList.add(new GameRuleInfo("item_durability", "启用物品耐久度", true));

        setItems(gameRuleList);
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
            root.getContent().add(center);

            pane.getChildren().add(root);
            getChildren().add(pane);

        }
    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo> {

        public GameRuleListCell(JFXListView<GameRuleInfo> listView, Holder<Object> lastCell) {
            super(listView, lastCell);
            //setSelectable();

            //getContainer().getChildren().setAll(hBox);
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
}
