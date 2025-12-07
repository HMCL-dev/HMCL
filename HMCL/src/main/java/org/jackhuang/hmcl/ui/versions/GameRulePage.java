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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.Holder;

import java.util.Map;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameRulePage extends ListPageBase<GameRulePage.GameRuleInfo> {

    WorldManagePage worldManagePage;

    Map<String, GameRule> gameRuleMap = GameRule.getCloneGameRuleMap();

    public GameRulePage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        ObservableList<GameRulePage.GameRuleInfo> gameRuleList = FXCollections.observableArrayList();
        //用于测试，目前还在写UI。
        gameRuleMap.forEach((s, gameRule) -> {
            if (gameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                gameRuleList.add(new GameRuleInfo(s, booleanGameRule.getDisplayName(), booleanGameRule.getValue()));
            } else if (gameRule instanceof GameRule.IntGameRule intGameRule) {
                gameRuleList.add(new GameRuleInfo(s, intGameRule.getDisplayName(), intGameRule.getValue()));
            }
        });

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
}
