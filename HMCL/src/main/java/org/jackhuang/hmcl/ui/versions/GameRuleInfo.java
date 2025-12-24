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
import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.NumberRangeValidator;
import org.jackhuang.hmcl.ui.construct.NumberValidator;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.util.Lang;

import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public sealed abstract class GameRuleInfo<T> permits GameRuleInfo.BooleanGameRuleInfo, GameRuleInfo.IntGameRuleInfo {
    private String ruleKey;
    private String displayName;
    private GameRuleNBT<T, ? extends Tag> gameRuleNBT;

    //Due to the significant difference in skin between BooleanGameRuleInfo and IntGameRuleInfo, which are essentially two completely different styles, it is not suitable to update each other in Cell#updateControl. Therefore, they are directly integrated into the info.
    private HBox container = new HBox();
    private Runnable setToDefault = () -> {
    };

    public void resetValue() {
        setToDefault.run();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public GameRuleNBT<T, ? extends Tag> getGameRuleNBT() {
        return gameRuleNBT;
    }

    public void setGameRuleNBT(GameRuleNBT<T, ? extends Tag> gameRuleNBT) {
        this.gameRuleNBT = gameRuleNBT;
    }

    public HBox getContainer() {
        return container;
    }

    public void setContainer(HBox container) {
        this.container = container;
    }

    public Runnable getSetToDefault() {
        return setToDefault;
    }

    public void setSetToDefault(Runnable setToDefault) {
        this.setToDefault = setToDefault;
    }

    static final class BooleanGameRuleInfo extends GameRuleInfo<Boolean> {

        public BooleanGameRuleInfo(String ruleKey, String displayName, Boolean onValue, Optional<Boolean> defaultValue, GameRuleNBT<Boolean, Tag> gameRuleNBT, Runnable onSave) {
            this.setRuleKey(ruleKey);
            this.setDisplayName(displayName);
            this.setGameRuleNBT(gameRuleNBT);

            {
                HBox.setHgrow(getContainer(), Priority.ALWAYS);
                getContainer().setAlignment(Pos.CENTER_LEFT);
                getContainer().setPadding(new Insets(0, 8, 0, 0));
            }

            OptionToggleButton toggleButton = new OptionToggleButton();
            {
                toggleButton.setTitle(displayName);
                toggleButton.setSubtitle(ruleKey);
                toggleButton.setSelected(onValue);
                toggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    gameRuleNBT.changeValue(newValue);
                    onSave.run();
                });
                HBox.setHgrow(toggleButton, Priority.ALWAYS);
            }

            JFXButton resetButton = new JFXButton();
            {
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                defaultValue.ifPresentOrElse(value -> {
                    setSetToDefault(() -> toggleButton.selectedProperty().set(value));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", value));
                }, () -> resetButton.setDisable(true));
            }

            getContainer().getChildren().addAll(toggleButton, resetButton);
        }

    }

    static final class IntGameRuleInfo extends GameRuleInfo<String> {

        public IntGameRuleInfo(String ruleKey, String displayName, Integer currentValue, int minValue, int maxValue, Optional<Integer> defaultValue, GameRuleNBT<String, Tag> gameRuleNBT, Runnable onSave) {
            this.setRuleKey(ruleKey);
            this.setDisplayName(displayName);
            this.setGameRuleNBT(gameRuleNBT);

            {
                getContainer().setPadding(new Insets(8, 8, 8, 16));
                HBox.setHgrow(getContainer(), Priority.ALWAYS);
                getContainer().setAlignment(Pos.CENTER_LEFT);
            }

            VBox displayInfoVBox = new VBox();
            {
                displayInfoVBox.getChildren().addAll(new Label(displayName), new Label(ruleKey));
                displayInfoVBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(displayInfoVBox, Priority.ALWAYS);
            }

            HBox hBox = new HBox();
            {
                hBox.setSpacing(12);
                hBox.setAlignment(Pos.CENTER_LEFT);
            }

            JFXTextField textField = new JFXTextField();
            {
                textField.textProperty().set(currentValue.toString());
                FXUtils.setValidateWhileTextChanged(textField, true);
                textField.setValidators(
                        new NumberValidator(i18n("input.integer"), false),
                        new NumberRangeValidator(i18n("input.number_range", minValue, maxValue), minValue, maxValue));
                textField.textProperty().addListener((observable, oldValue, newValue) -> {
                    Integer value = Lang.toIntOrNull(newValue);
                    if (value == null) {
                        return;
                    } else if (value > maxValue || value < minValue) {
                        return;
                    } else {
                        gameRuleNBT.changeValue(newValue);
                        onSave.run();
                    }
                });

                textField.maxWidth(10);
                textField.minWidth(10);
            }

            JFXButton resetButton = new JFXButton();
            {
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                defaultValue.ifPresentOrElse(value -> {
                    setSetToDefault(() -> textField.textProperty().set(String.valueOf(value)));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", value));
                }, () -> resetButton.setDisable(true));

                resetButton.setAlignment(Pos.BOTTOM_CENTER);
            }

            hBox.getChildren().addAll(textField, resetButton);
            getContainer().getChildren().addAll(displayInfoVBox, hBox);
        }
    }
}
