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
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.NumberRangeValidator;
import org.jackhuang.hmcl.ui.construct.NumberValidator;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed abstract class GameRuleInfo<T> permits GameRuleInfo.BooleanGameRuleInfo, GameRuleInfo.IntGameRuleInfo {
    private String ruleKey;
    private String displayName;
    private GameRuleNBT<T, ? extends Tag> gameRuleNBT;
    private Runnable onSave;

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

    public void setOnSave(Runnable onSave) {
        this.onSave = onSave;
    }

    public Runnable getOnSave() {
        return onSave;
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
        boolean currentValue;
        Boolean defaultValue;

        public BooleanGameRuleInfo(GameRule.BooleanGameRule booleanGameRule, GameRuleNBT<Boolean, Tag> gameRuleNBT, Runnable onSave) {
            this.setRuleKey(booleanGameRule.getRuleKey().get(0));
            String displayName = "";
            try {
                if (StringUtils.isNotBlank(booleanGameRule.getDisplayI18nKey())) {
                    displayName = i18n(booleanGameRule.getDisplayI18nKey());
                }
            } catch (Exception e) {
                LOG.warning("Failed to get i18n text for key: " + booleanGameRule.getDisplayI18nKey(), e);
            }
            this.setDisplayName(displayName);
            this.currentValue = booleanGameRule.getValue();
            this.defaultValue = booleanGameRule.getDefaultValue().orElse(null);
            this.setGameRuleNBT(gameRuleNBT);
            this.setOnSave(onSave);

            buildNodes();
        }

        public void buildNodes() {
            {
                HBox.setHgrow(getContainer(), Priority.ALWAYS);
                getContainer().setAlignment(Pos.CENTER_LEFT);
                getContainer().setPadding(new Insets(0, 8, 0, 0));
            }

            OptionToggleButton toggleButton = new OptionToggleButton();
            {
                toggleButton.setTitle(getDisplayName());
                toggleButton.setSubtitle(getRuleKey());
                toggleButton.setSelected(currentValue);
                toggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    getGameRuleNBT().changeValue(newValue);
                    getOnSave().run();
                });
                HBox.setHgrow(toggleButton, Priority.ALWAYS);
            }

            JFXButton resetButton = new JFXButton();
            {
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setSetToDefault(() -> toggleButton.selectedProperty().set(defaultValue));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", defaultValue));
                } else {
                    resetButton.setDisable(true);
                }
            }

            getContainer().getChildren().addAll(toggleButton, resetButton);
        }

    }

    static final class IntGameRuleInfo extends GameRuleInfo<String> {
        int currentValue;
        int minValue;
        int maxValue;
        Integer defaultValue;

        public IntGameRuleInfo(GameRule.IntGameRule intGameRule, GameRuleNBT<String, Tag> gameRuleNBT, Runnable onSave) {
            this.setRuleKey(intGameRule.getRuleKey().get(0));
            String displayName = "";
            try {
                if (StringUtils.isNotBlank(intGameRule.getDisplayI18nKey())) {
                    displayName = i18n(intGameRule.getDisplayI18nKey());
                }
            } catch (Exception e) {
                LOG.warning("Failed to get i18n text for key: " + intGameRule.getDisplayI18nKey(), e);
            }
            this.setDisplayName(displayName);
            currentValue = intGameRule.getValue();
            minValue = intGameRule.getMinValue();
            maxValue = intGameRule.getMaxValue();
            defaultValue = intGameRule.getDefaultValue().orElse(null);
            this.setGameRuleNBT(gameRuleNBT);
            this.setOnSave(onSave);

            buildNodes();
        }

        public void buildNodes() {
            {
                getContainer().setPadding(new Insets(8, 8, 8, 16));
                HBox.setHgrow(getContainer(), Priority.ALWAYS);
                getContainer().setAlignment(Pos.CENTER_LEFT);
            }

            VBox displayInfoVBox = new VBox();
            {
                displayInfoVBox.getChildren().addAll(new Label(getDisplayName()), new Label(getRuleKey()));
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
                textField.textProperty().set(Integer.toString(currentValue));
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
                        getGameRuleNBT().changeValue(newValue);
                        getOnSave().run();
                    }
                });

                textField.maxWidth(10);
                textField.minWidth(10);
            }

            JFXButton resetButton = new JFXButton();
            {
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setSetToDefault(() -> textField.textProperty().set(String.valueOf(defaultValue)));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    FXUtils.installSlowTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", defaultValue));
                } else {
                    resetButton.setDisable(true);
                }

                resetButton.setAlignment(Pos.BOTTOM_CENTER);
            }

            hBox.getChildren().addAll(textField, resetButton);
            getContainer().getChildren().addAll(displayInfoVBox, hBox);
        }
    }
}
