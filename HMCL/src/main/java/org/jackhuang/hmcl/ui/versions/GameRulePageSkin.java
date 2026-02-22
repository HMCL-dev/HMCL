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

import com.jfoenix.controls.*;
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

import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox toolBar;
    private final JFXTextField searchField;
    private final JFXListView<GameRuleInfo<?>> listView = new JFXListView<>();
    private final Map<String, HBox> cellMap = new HashMap<>();

    GameRulePageSkin(GameRulePage skinnable) {
        super(skinnable);
        ComponentList root = new ComponentList();
        StackPane pane = new StackPane(root);
        {
            pane.setPadding(new Insets(10));
            pane.getStyleClass().add("notice-pane");
            root.getStyleClass().add("no-padding");

            getChildren().add(pane);
        }

        {
            toolBar = new HBox();
            toolBar.setAlignment(Pos.CENTER);
            toolBar.setPadding(new Insets(0, 5, 0, 5));
            toolBar.setSpacing(5);

            JFXComboBox<GameRulePage.RuleModifiedType> viewFilterComboBox = new JFXComboBox<>(GameRulePage.RuleModifiedType.items);
            viewFilterComboBox.setValue(GameRulePage.RuleModifiedType.ALL);
            // Changes to the modifiedList are only applied at the time a type is manually selected; this is by design.
            viewFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                getSkinnable().changeRuleModifiedType(newValue);
            });
            viewFilterComboBox.setPrefWidth(100);

            searchField = new JFXTextField();
            {
                searchField.setPromptText(i18n("search"));
                PauseTransition searchPause = new PauseTransition(Duration.millis(1000));
                searchPause.setOnFinished(event -> getSkinnable().updateSearchPredicate(searchField.getText()));
                searchField.textProperty().addListener((observable) -> searchPause.playFromStart());
                HBox.setHgrow(searchField, Priority.ALWAYS);
            }

            JFXButton resetAllButton = createToolbarButton2(i18n("gamerule.restore_default_values_all"), SVG.RESTORE,
                    () -> Controllers.dialog(new ResetDefaultValuesLayout(skinnable::resettingAllGameRule, getSkinnable().getModifiedItems(), () -> getSkinnable().changeRuleModifiedType(viewFilterComboBox.getSelectionModel().getSelectedItem()))));

            toolBar.getChildren().addAll(searchField, new Label(i18n("gamerule.filter")), viewFilterComboBox, resetAllButton);
            root.getContent().add(toolBar);
        }

        SpinnerPane center = new SpinnerPane();
        {
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.getStyleClass().add("large-spinner-pane");
            center.setContent(listView);

            listView.setItems(getSkinnable().getDisplayedItems());
            listView.setCellFactory(x -> new GameRuleListCell(listView, cellMap));
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            root.getContent().add(center);
        }
    }

    public void resetCellMap() {
        cellMap.clear();
    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo<?>> {
        private final Map<String, HBox> cellMap;

        public GameRuleListCell(JFXListView<GameRuleInfo<?>> listView, Map<String, HBox> cellMap) {
            super(listView);
            this.cellMap = cellMap;
        }

        @Override
        protected void updateControl(GameRuleInfo<?> item, boolean empty) {
            if (empty) return;

            HBox hBox = cellMap.computeIfAbsent(item.getRuleKey(), key -> {
                if (item instanceof GameRuleInfo.IntGameRuleInfo intInfo) {
                    return buildNodeForIntGameRule(intInfo);
                } else if (item instanceof GameRuleInfo.BooleanGameRuleInfo booleanInfo) {
                    return buildNodeForBooleanGameRule(booleanInfo);
                }
                return null;
            });
            if (hBox != null) {
                getContainer().getChildren().setAll(hBox);
            }
        }

        private HBox buildNodeForIntGameRule(GameRuleInfo.IntGameRuleInfo gameRule) {
            HBox cellBox = new HBox();
            {
                cellBox.setPadding(new Insets(8, 8, 8, 16));
                HBox.setHgrow(cellBox, Priority.ALWAYS);
                cellBox.setAlignment(Pos.CENTER_LEFT);
            }

            VBox displayInfoVBox = new VBox();
            {
                if (StringUtils.isNotBlank(gameRule.getDisplayName())) {
                    Label displayNameLabel = new Label(gameRule.getDisplayName());
                    Label ruleKeyLabel = new Label(gameRule.getRuleKey());
                    ruleKeyLabel.getStyleClass().add("subtitle");

                    displayInfoVBox.getChildren().addAll(displayNameLabel, ruleKeyLabel);
                } else {
                    displayInfoVBox.getChildren().addAll(new Label(gameRule.getRuleKey()));
                }
                displayInfoVBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(displayInfoVBox, Priority.ALWAYS);
            }

            HBox rightHBox = new HBox();
            {
                rightHBox.setSpacing(12);
                rightHBox.setAlignment(Pos.CENTER_LEFT);
            }

            JFXTextField textField = new JFXTextField();
            {
                textField.textProperty().bindBidirectional(gameRule.currentValueProperty());
                FXUtils.setValidateWhileTextChanged(textField, true);
                textField.setValidators(
                        new NumberValidator(i18n("input.integer"), false),
                        new NumberRangeValidator(i18n("input.number_range", gameRule.getMinValue(), gameRule.getMaxValue()), gameRule.getMinValue(), gameRule.getMaxValue()));

                textField.setPrefWidth(150);
            }

            rightHBox.getChildren().addAll(textField, buildResetButton(gameRule));
            cellBox.getChildren().addAll(displayInfoVBox, rightHBox);

            return cellBox;
        }

        private HBox buildNodeForBooleanGameRule(GameRuleInfo.BooleanGameRuleInfo gameRule) {
            HBox cellBox = new HBox();
            {
                HBox.setHgrow(cellBox, Priority.ALWAYS);
                cellBox.setAlignment(Pos.CENTER_LEFT);
                cellBox.setPadding(new Insets(0, 8, 0, 0));
            }

            LineToggleButton toggleButton = new LineToggleButton();
            {
                if (StringUtils.isNotBlank(gameRule.getDisplayName())) {
                    toggleButton.setTitle(gameRule.getDisplayName());
                    toggleButton.setSubtitle(gameRule.getRuleKey());
                } else {
                    toggleButton.setTitle(gameRule.getRuleKey());
                }
                HBox.setHgrow(toggleButton, Priority.ALWAYS);
                toggleButton.selectedProperty().bindBidirectional(gameRule.currentValueProperty());
            }

            cellBox.getChildren().addAll(toggleButton, buildResetButton(gameRule));

            return cellBox;
        }

        private StackPane buildResetButton(GameRuleInfo<?> gameRule) {
            JFXButton resetButton = new JFXButton();
            StackPane wrapperPane = new StackPane(resetButton);
            {
                wrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                resetButton.setFocusTraversable(false);
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                resetButton.getStyleClass().add("toggle-icon4");
                if (StringUtils.isNotBlank(gameRule.getDefaultValueText())) {
                    resetButton.setOnAction(event -> gameRule.resetValue());
                    resetButton.disableProperty().bind(gameRule.modifiedProperty().not());
                    FXUtils.installFastTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", gameRule.getDefaultValueText()));
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.now_is_default_values.tooltip"));
                } else {
                    resetButton.setDisable(true);
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.not_have_default_values.tooltip"));
                }
            }
            return wrapperPane;
        }
    }

    static class ResetDefaultValuesLayout extends JFXDialogLayout {
        public ResetDefaultValuesLayout(Runnable resettingAllGameRule, FilteredList<GameRuleInfo<?>> modifiedItems, Runnable callBack) {

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
                                new Label("", SVG.ARROW_FORWARD.createIcon(12)),
                                new Label(i18n("gamerule.column.default")));

                        for (int i = 0; i < modifiedItems.size(); i++) {
                            GameRuleInfo<?> gameRuleInfo = modifiedItems.get(i);
                            String displayName = StringUtils.isNotBlank(gameRuleInfo.getDisplayName()) ? gameRuleInfo.getDisplayName() : gameRuleInfo.getRuleKey();
                            gridPane.addRow(i + 1,
                                    new Label(displayName),
                                    new Label(gameRuleInfo.getCurrentValueText()),
                                    new Label("", SVG.ARROW_FORWARD.createIcon(12)),
                                    new Label(gameRuleInfo.getDefaultValueText()));
                        }
                    }

                    ScrollPane scrollPane = new ScrollPane(gridPane);
                    {
                        gridPane.setHgap(10);
                        gridPane.setVgap(10);
                        gridPane.setPadding(new Insets(0, 5, 10, 0));

                        scrollPane.visibleProperty().bind(showDetailButton.selectedProperty());
                        scrollPane.managedProperty().bind(showDetailButton.selectedProperty());
                        VBox.setMargin(scrollPane, new Insets(5, 8, 5, 8));
                        FXUtils.smoothScrolling(scrollPane);

                        vBox.getChildren().add(scrollPane);
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
                        callBack.run();
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
