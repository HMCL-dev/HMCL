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

    private final HBox toolBar;
    private final JFXTextField searchField;
    JFXListView<GameRuleInfo<?>> listView = new JFXListView<>();
    private final FilteredList<GameRuleInfo<?>> displayedItems;
    private final FilteredList<GameRuleInfo<?>> modifiedItems;

    GameRulePageSkin(GameRulePage skinnable) {
        super(skinnable);
        ComponentList root = new ComponentList();
        StackPane pane = new StackPane(root);
        {
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");
            root.getStyleClass().add("no-padding");

            getChildren().add(pane);
        }

        displayedItems = new FilteredList<>(skinnable.getItems());
        modifiedItems = new FilteredList<>(skinnable.getItems(), GameRuleInfo::getModified);

        {
            toolBar = new HBox();
            toolBar.setAlignment(Pos.CENTER);
            toolBar.setPadding(new Insets(0, 5, 0, 5));

            JFXButton resetAllButton = createToolbarButton2(i18n("gamerule.restore_default_values_all"), SVG.RESTORE,
                    () -> Controllers.dialog(new ResetDefaultValuesLayout(skinnable::resettingAllGameRule, modifiedItems)));

            searchField = new JFXTextField();
            {
                searchField.setPromptText(i18n("search"));
                PauseTransition pause = new PauseTransition(Duration.millis(100));
                pause.setOnFinished(event -> displayedItems.setPredicate(skinnable.updateSearchPredicate(searchField.getText())));
                searchField.textProperty().addListener((observable) -> {
                    pause.setRate(1);
                    pause.playFromStart();
                });
                HBox.setHgrow(searchField, Priority.ALWAYS);
            }

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, searchField::clear);
            FXUtils.onEscPressed(searchField, closeSearchBar::fire);

            toolBar.getChildren().addAll(resetAllButton, searchField, closeSearchBar);
            root.getContent().add(toolBar);
        }

        SpinnerPane center = new SpinnerPane();
        {
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.setContent(listView);
            listView.setItems(displayedItems);
            listView.setCellFactory(x -> new GameRuleListCell(listView));
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
            root.getContent().add(center);
        }
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
        public ResetDefaultValuesLayout(Runnable resettingAllGameRule, FilteredList<GameRuleInfo<?>> modifiedItems) {

            {
                Stage stage = Controllers.getStage();
                maxWidthProperty().bind(stage.widthProperty().multiply(0.7));
                maxHeightProperty().bind(stage.heightProperty().multiply(0.7));
            }

            //heading area
            setHeading(new Label(i18n("gamerule.restore_default_values_all")));

            //body area
            VBox vBox = new VBox();
            {
                vBox.setSpacing(10);
                setBody(vBox);
            }
            {
                Label warnLabel = modifiedItems.isEmpty() ? new Label(i18n("gamerule.all_is_default")) : new Label(i18n("gamerule.restore_default_values_all.confirm"));
                vBox.getChildren().add(warnLabel);

                if (!modifiedItems.isEmpty()) {

                    MenuUpDownButton showDetailButton = new MenuUpDownButton();
                    {
                        showDetailButton.setText(i18n("gamerule.show_modified_details.button"));
                        showDetailButton.setMaxWidth(USE_PREF_SIZE);

                        vBox.getChildren().add(showDetailButton);
                    }

                    GridPane gridPane = new GridPane();
                    {
                        gridPane.addRow(0,
                                new Label(i18n("gamerule.column.name")),
                                new Label(i18n("gamerule.column.current")),
                                new Label("->"),
                                new Label(i18n("gamerule.column.default")));

                        for (int i = 0; i < modifiedItems.size(); i++) {
                            GameRuleInfo<?> gameRuleInfo = modifiedItems.get(i);
                            String displayName = StringUtils.isNotBlank(gameRuleInfo.getDisplayName()) ? gameRuleInfo.getDisplayName() : gameRuleInfo.getRuleKey();
                            gridPane.addRow(i + 1,
                                    new Label(displayName),
                                    new Label(gameRuleInfo.getCurrentValueText()),
                                    new Label("->"),
                                    new Label(gameRuleInfo.getDefaultValueText()));
                        }
                    }

                    ScrollPane scrollPane = new ScrollPane(gridPane);
                    {
                        gridPane.setHgap(10);
                        gridPane.setVgap(10);

                        scrollPane.visibleProperty().bind(showDetailButton.selectedProperty());
                        scrollPane.managedProperty().bind(showDetailButton.selectedProperty());
                        VBox.setMargin(scrollPane, new Insets(5, 10, 5, 10));
                        FXUtils.smoothScrolling(scrollPane);

                        vBox.getChildren().addAll(scrollPane);
                    }
                }
            }

            //action area
            JFXButton accept = new JFXButton(i18n("button.ok"));
            {
                accept.getStyleClass().add("dialog-accept");
                if (!modifiedItems.isEmpty()) {
                    accept.setOnAction(event -> {
                        resettingAllGameRule.run();
                        fireEvent(new DialogCloseEvent());
                    });
                } else {
                    accept.setOnAction(event -> fireEvent(new DialogCloseEvent()));
                }
            }
            JFXButton reject = new JFXButton(i18n("button.cancel"));
            {
                reject.getStyleClass().add("dialog-cancel");
                reject.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            }
            setActions(accept, reject);

            FXUtils.onEscPressed(this, reject::fire);
        }
    }
}
