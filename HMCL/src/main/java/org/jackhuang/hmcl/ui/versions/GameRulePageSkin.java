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
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.Holder;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox searchBar;
    private final JFXTextField searchField;
    JFXListView<GameRuleInfo> listView = new JFXListView<>();
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
            JFXButton resetAllButton = createToolbarButton2(i18n("gamerule.restore_default_values_all"), SVG.RESTORE, () -> {
                Controllers.confirm(i18n("gamerule.restore_default_values_all.confirm"), null, skinnable::resettingAllGameRule, null);
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
        Holder<Object> lastCell = new Holder<>();
        listView.setItems(filteredList);
        listView.setCellFactory(x -> new GameRuleListCell(listView, lastCell));
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
        root.getContent().add(center);

        pane.getChildren().add(root);
        getChildren().add(pane);

    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo> {

        public GameRuleListCell(JFXListView<GameRuleInfo> listView, Holder<Object> lastCell) {
            super(listView, lastCell);
        }

        @Override
        protected void updateControl(GameRuleInfo item, boolean empty) {
            if (empty) return;
            getContainer().getChildren().setAll(item.getContainer());
        }
    }
}
