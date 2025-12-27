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
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox searchBar;
    private final JFXTextField searchField;
    JFXListView<GameRuleInfo<?>> listView = new JFXListView<>();
    private final FilteredList<GameRuleInfo<?>> filteredList;
    private final FilteredList<GameRuleInfo<?>> notInDefaultValueList;

    GameRulePageSkin(GameRulePage skinnable) {
        super(skinnable);
        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        filteredList = new FilteredList<>(skinnable.getItems());
        notInDefaultValueList = new FilteredList<>(skinnable.getItems());
        notInDefaultValueList.setPredicate(gameRuleInfo -> !gameRuleInfo.getIsDefault().getAsBoolean());

        {
            JFXButton resetAllButton = createToolbarButton2(i18n("gamerule.restore_default_values_all"), SVG.RESTORE, () -> {
                //Controllers.confirm(i18n("gamerule.restore_default_values_all.confirm"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING, skinnable::resettingAllGameRule, null);
                Controllers.dialog(new ResetDefaultValuesLayout(skinnable::resettingAllGameRule, notInDefaultValueList));
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
        public ResetDefaultValuesLayout(Runnable resettingAllGameRule, FilteredList<GameRuleInfo<?>> notInDefaultValueList) {

            {
                Stage stage = Controllers.getStage();
                maxWidthProperty().bind(stage.widthProperty().multiply(0.7));
                maxHeightProperty().bind(stage.heightProperty().multiply(0.7));
            }

            setHeading(new Label(i18n("gamerule.restore_default_values_all")));

            VBox vBox = new VBox();
            {
                vBox.setSpacing(10);
                setBody(vBox);
            }
            {
                Label warnLabel = notInDefaultValueList.isEmpty() ? new Label(i18n("gamerule.all_is_default")) : new Label(i18n("gamerule.restore_default_values_all.confirm"));
                vBox.getChildren().add(warnLabel);

                if (!notInDefaultValueList.isEmpty()) {

                    MenuUpDownButton showDetailButton = new MenuUpDownButton();
                    {
                        showDetailButton.setText(i18n("gamerule.show_modified_details.button"));
                        showDetailButton.setMaxWidth(USE_PREF_SIZE);
                    }

                    ScrollPane scrollPane = new ScrollPane();
                    GridPane gridPane = new GridPane();
                    {
                        gridPane.setHgap(10);
                        gridPane.setVgap(10);
                        scrollPane.setContent(gridPane);
                        scrollPane.visibleProperty().bind(showDetailButton.selectedProperty());
                        scrollPane.managedProperty().bind(showDetailButton.selectedProperty());
                        FXUtils.smoothScrolling(scrollPane);

                        VBox.setMargin(scrollPane, new Insets(5, 10, 5, 10));
                        vBox.getChildren().addAll(showDetailButton, scrollPane);
                    }
                    {
                        gridPane.addRow(0,
                                new Label(i18n("gamerule.column.name")),
                                new Label(i18n("gamerule.column.current")),
                                new Label("->"),
                                new Label(i18n("gamerule.column.default")));

                        for (int i = 0; i < notInDefaultValueList.size(); i++) {
                            GameRuleInfo<?> gameRuleInfo = notInDefaultValueList.get(i);
                            String oldValue = gameRuleInfo.getCurrentValueText();
                            String newValue = gameRuleInfo.getDefaultValueText();
                            gridPane.addRow(i + 1,
                                    new Label(StringUtils.isNotBlank(gameRuleInfo.getDisplayName()) ? gameRuleInfo.getDisplayName() : gameRuleInfo.getRuleKey()),
                                    new Label(oldValue),
                                    new Label("->"),
                                    new Label(newValue));
                        }

                    }
                }
            }

            JFXButton accept = new JFXButton(i18n("button.ok"));
            {
                accept.getStyleClass().add("dialog-accept");
                if (!notInDefaultValueList.isEmpty()) {
                    accept.setOnAction(event -> {
                        resettingAllGameRule.run();
                        fireEvent(new DialogCloseEvent());
                    });
                } else {
                    accept.setOnAction(event -> {
                        fireEvent(new DialogCloseEvent());
                    });
                }
            }
            JFXButton reject = new JFXButton(i18n("button.cancel"));
            {
                reject.setOnAction(event -> fireEvent(new DialogCloseEvent()));
                reject.getStyleClass().add("dialog-cancel");
            }
            setActions(accept, reject);

            FXUtils.onEscPressed(this, reject::fire);
        }
    }
}
