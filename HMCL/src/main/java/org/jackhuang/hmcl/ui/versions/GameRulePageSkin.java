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

import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lang;

import java.util.Optional;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox searchBar;
    private final JFXTextField searchField;
    JFXListView<GameRulePageSkin.GameRuleInfo> listView = new JFXListView<>();
    private final FilteredList<GameRuleInfo> filteredList;

    GameRulePageSkin(GameRulePage skinnable) {
        super(skinnable);
        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        filteredList = new FilteredList<>(skinnable.getItems());

        {
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
            searchBar.getChildren().addAll(searchField, closeSearchBar);
            root.getContent().add(searchBar);
        }

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.getStyleClass().add("large-spinner-pane");
        center.setContent(listView);
        Holder<Object> lastCell = new Holder<>();
        listView.setItems(filteredList);
        listView.setCellFactory(x -> new GameRuleListCell(listView, lastCell));
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
        root.getContent().add(center);

        pane.getChildren().add(root);
        getChildren().add(pane);

    }

    static class GameRuleInfo {

        String ruleKey;
        String displayName;
        GameRuleNBT gameRuleNbt;

        HBox container = new HBox();

        public GameRuleInfo(String ruleKey, String displayName, Boolean onValue, Optional<Boolean> defaultValue, GameRuleNBT<Boolean, Tag> gameRuleNbt, Runnable onSave) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.gameRuleNbt = gameRuleNbt;

            OptionToggleButton toggleButton = new OptionToggleButton();
            toggleButton.setTitle(displayName);
            toggleButton.setSubtitle(ruleKey);
            toggleButton.setSelected(onValue);
            toggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                gameRuleNbt.changeValue(newValue);
                onSave.run();
            });

            HBox.setHgrow(toggleButton, Priority.ALWAYS);
            HBox.setHgrow(container, Priority.ALWAYS);
            container.getChildren().add(toggleButton);

            JFXButton resetButton = new JFXButton();
            resetButton.setGraphic(SVG.ARROW_BACK.createIcon(24));
            defaultValue.ifPresentOrElse(value -> {
                resetButton.setOnAction(event -> {
                    toggleButton.selectedProperty().set(value);
                });
                FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", value));
            }, () -> {
                resetButton.setDisable(true);
            });

            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(0, 8, 0, 0));
            container.getChildren().add(resetButton);
        }

        public GameRuleInfo(String ruleKey, String displayName, Integer currentValue, int minValue, int maxValue, Optional<Integer> defaultValue, GameRuleNBT<String, Tag> gameRuleNbt, Runnable onSave) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.gameRuleNbt = gameRuleNbt;

            VBox vbox = new VBox();
            vbox.getChildren().addAll(new Label(displayName), new Label(ruleKey));

            vbox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(vbox, Priority.ALWAYS);
            container.setPadding(new Insets(8, 8, 8, 16));

            HBox hBox = new HBox();
            JFXTextField textField = new JFXTextField();
            textField.textProperty().set(currentValue.toString());
            FXUtils.setValidateWhileTextChanged(textField, true);
            textField.setValidators(new NumberRangeValidator(i18n("input.integer"), i18n("input.number_range", minValue, maxValue), minValue, maxValue, false));
            textField.textProperty().addListener((observable, oldValue, newValue) -> {
                Integer value = Lang.toIntOrNull(newValue);
                if (value == null) {
                    return;
                } else if (value > maxValue || value < minValue) {
                    return;
                } else {
                    gameRuleNbt.changeValue(newValue);
                    onSave.run();
                }
            });

            textField.maxWidth(10);
            textField.minWidth(10);
            hBox.getChildren().add(textField);

            JFXButton resetButton = new JFXButton();
            resetButton.setGraphic(SVG.ARROW_BACK.createIcon(24));
            defaultValue.ifPresentOrElse(value -> {
                resetButton.setOnAction(event -> {
                    textField.textProperty().set(String.valueOf(value));
                });
                FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", value));
            }, () -> {
                resetButton.setDisable(true);
            });

            resetButton.setAlignment(Pos.BOTTOM_CENTER);
            hBox.setSpacing(12);
            hBox.getChildren().add(resetButton);
            hBox.setAlignment(Pos.CENTER_LEFT);

            HBox.setHgrow(container, Priority.ALWAYS);
            container.getChildren().add(vbox);
            container.getChildren().add(hBox);
            container.setAlignment(Pos.CENTER_LEFT);
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
}
