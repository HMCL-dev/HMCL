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
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.List;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox searchBar;
    private final JFXTextField searchField;
    JFXListView<GameRuleInfo<?>> listView = new JFXListView<>();
    private final FilteredList<GameRuleInfo<?>> filteredList;

    GameRulePageSkin(GameRulePage skinnable) {
        super(skinnable);
        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        filteredList = new FilteredList<>(skinnable.getItems());

        {
            JFXButton resetAllButton = createToolbarButton2(i18n("gamerule.restore_default_values_all"), SVG.RESTORE, () -> {
                //Controllers.confirm(i18n("gamerule.restore_default_values_all.confirm"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING, skinnable::resettingAllGameRule, null);
                Controllers.dialog(new ResetDefaultValuesLayout(skinnable::resettingAllGameRule, skinnable.getItems()));
            });

            searchBar = new HBox();
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(event -> filteredList.setPredicate(skinnable.updateSearchPredicate(searchField.getText())));
            searchField.textProperty().addListener((observable) -> {
                pause.setRate(1);
                pause.playFromStart();
            });
            HBox.setHgrow(searchField, Priority.ALWAYS);

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    searchField::clear);
            FXUtils.onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().addAll(resetAllButton, searchField, closeSearchBar);
            root.getContent().add(searchBar);
        }

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.getStyleClass().add("large-spinner-pane");
        center.setContent(listView);
        listView.setItems(filteredList);
        listView.setCellFactory(x -> new GameRuleListCell(listView));
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
        root.getContent().add(center);

        pane.getChildren().add(root);
        getChildren().add(pane);

    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo<?>> {

        public GameRuleListCell(JFXListView<GameRuleInfo<?>> listView) {
            super(listView);
        }

        @Override
        protected void updateControl(GameRuleInfo<?> item, boolean empty) {
            if (empty) return;
            getContainer().getChildren().setAll(item.getContainer());
        }
    }

    static class ResetDefaultValuesLayout extends JFXDialogLayout {
        public ResetDefaultValuesLayout(Runnable resettingAllGameRule, List<GameRuleInfo<?>> gameRules) {
            setHeading(new Label(i18n("message.warning")));
            Label warnLabel = new Label(i18n("gamerule.restore_default_values_all.confirm"));
            MenuUpDownButton menuUpDownButton = new MenuUpDownButton();
            {
                menuUpDownButton.setText("查看具体变更");
                menuUpDownButton.setMaxWidth(USE_PREF_SIZE);
            }
            ScrollPane scrollPane = new ScrollPane();
            GridPane gridPane = new GridPane();
            {
                gridPane.setHgap(10);
                gridPane.setVgap(10);
                scrollPane.setContent(gridPane);
                scrollPane.visibleProperty().bind(menuUpDownButton.selectedProperty());
                scrollPane.managedProperty().bind(menuUpDownButton.selectedProperty());
                int index = 1;
                gridPane.addRow(0, new Label("名称"), new Label("当前值"), new Label("->"), new Label("默认值"));
                for (GameRuleInfo<?> gameRule : gameRules) {
                    if (!gameRule.getIsDefault().getAsBoolean()) {
                        String oldValue = "";
                        String newValue = "";
                        if (gameRule instanceof GameRuleInfo.BooleanGameRuleInfo booleanGameRuleInfo) {
                            oldValue = String.valueOf(booleanGameRuleInfo.getValue());
                            newValue = String.valueOf(booleanGameRuleInfo.getDefaultValue());
                        } else if (gameRule instanceof GameRuleInfo.IntGameRuleInfo intGameRuleInfo) {
                            oldValue = intGameRuleInfo.getValue();
                            newValue = intGameRuleInfo.getDefaultValue();
                        }
                        gridPane.addRow(index, new Label(StringUtils.isNotBlank(gameRule.getDisplayName()) ? gameRule.getDisplayName() : gameRule.getRuleKey()), new Label(oldValue), new Label("->"), new Label(newValue));
                        index++;
                    }
                }
                if (index == 1) {
                    gridPane.addRow(1, new Label("无变更"));
                }
            }
            VBox vBox = new VBox();
            vBox.setAlignment(Pos.TOP_LEFT);
            vBox.getChildren().addAll(warnLabel, menuUpDownButton, scrollPane);
            setBody(vBox);
            JFXButton accept = new JFXButton(i18n("button.ok"));
            JFXButton reject = new JFXButton(i18n("button.cancel"));
            accept.setOnAction(event -> {
                resettingAllGameRule.run();
                fireEvent(new DialogCloseEvent());
            });
            reject.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            setActions(accept, reject);
        }
    }
}
