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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.NumberRangeValidator;
import org.jackhuang.hmcl.ui.construct.NumberValidator;
import org.jackhuang.hmcl.ui.construct.OptionToggleButton;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.Objects;
import java.util.function.BooleanSupplier;

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
    private BooleanSupplier isDefault = () -> true;

    private GameRuleInfo(GameRule gameRule, GameRuleNBT<T, ? extends Tag> gameRuleNBT, Runnable onSave) {
        setRuleKey(gameRule.getRuleKey().get(0));
        String displayName = "";
        try {
            if (StringUtils.isNotBlank(gameRule.getDisplayI18nKey())) {
                displayName = i18n(gameRule.getDisplayI18nKey());
            }
        } catch (Exception e) {
            LOG.warning("Failed to get i18n text for key: " + gameRule.getDisplayI18nKey(), e);
        }
        setDisplayName(displayName);
        setDisplayName(displayName);
        setGameRuleNBT(gameRuleNBT);
        setOnSave(onSave);
    }

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

    public BooleanSupplier getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(BooleanSupplier isDefault) {
        this.isDefault = isDefault;
    }

    static final class BooleanGameRuleInfo extends GameRuleInfo<Boolean> {
        //boolean currentValue;
        BooleanProperty currentValue;
        Boolean defaultValue;

        public BooleanGameRuleInfo(GameRule.BooleanGameRule booleanGameRule, GameRuleNBT<Boolean, Tag> gameRuleNBT, Runnable onSave, GameVersionNumber gameVersionNumber) {
            super(booleanGameRule, gameRuleNBT, onSave);
            this.currentValue = new SimpleBooleanProperty(booleanGameRule.getValue());
            this.defaultValue = booleanGameRule.getDefaultValue(gameVersionNumber).orElse(null);

            buildNodes();
        }

        public boolean getValue() {
            return currentValue.get();
        }

        public boolean getDefaultValue() {
            return defaultValue;
        }

        public void buildNodes() {
            {
                HBox.setHgrow(getContainer(), Priority.ALWAYS);
                getContainer().setAlignment(Pos.CENTER_LEFT);
                getContainer().setPadding(new Insets(0, 8, 0, 0));
            }

            JFXButton resetButton = new JFXButton();
            StackPane wrapperPane = new StackPane(resetButton);
            OptionToggleButton toggleButton = new OptionToggleButton();
            {
                toggleButton.setTitle(getDisplayName());
                toggleButton.setSubtitle(getRuleKey());
                toggleButton.selectedProperty().bindBidirectional(currentValue);
                toggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    getGameRuleNBT().changeValue(newValue);
                    getOnSave().run();
                    resetButton.setDisable(shouldResetButtonDisabled(newValue));
                });
                HBox.setHgrow(toggleButton, Priority.ALWAYS);
            }
            {
                wrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                resetButton.setFocusTraversable(false);
                resetButton.setDisable(shouldResetButtonDisabled(currentValue.getValue()));
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setSetToDefault(() -> toggleButton.selectedProperty().set(defaultValue));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    setIsDefault(() -> toggleButton.isSelected() == defaultValue);
                    FXUtils.installFastTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", defaultValue));
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.now_is_default_values.tooltip"));
                } else {
                    resetButton.setDisable(true);
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.not_have_default_values.tooltip"));
                }
            }

            getContainer().getChildren().addAll(toggleButton, wrapperPane);
        }

        public boolean shouldResetButtonDisabled(boolean newValue) {
            return defaultValue == null || newValue == defaultValue;
        }

    }

    static final class IntGameRuleInfo extends GameRuleInfo<String> {
        StringProperty currentValue;
        int minValue;
        int maxValue;
        Integer defaultValue;

        public IntGameRuleInfo(GameRule.IntGameRule intGameRule, GameRuleNBT<String, Tag> gameRuleNBT, Runnable onSave, GameVersionNumber gameVersionNumber) {
            super(intGameRule, gameRuleNBT, onSave);
            currentValue = new SimpleStringProperty(String.valueOf(intGameRule.getValue()));
            minValue = intGameRule.getMinValue(gameVersionNumber);
            maxValue = intGameRule.getMaxValue(gameVersionNumber);
            defaultValue = intGameRule.getDefaultValue(gameVersionNumber).orElse(null);

            buildNodes();
        }

        public String getValue() {
            return currentValue.get();
        }

        public String getDefaultValue() {
            return defaultValue == null ? null : defaultValue.toString();
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

            HBox rightHBox = new HBox();
            {
                rightHBox.setSpacing(12);
                rightHBox.setAlignment(Pos.CENTER_LEFT);
            }

            JFXButton resetButton = new JFXButton();
            StackPane wrapperPane = new StackPane(resetButton);
            JFXTextField textField = new JFXTextField();
            {
                textField.textProperty().bindBidirectional(currentValue);
                FXUtils.setValidateWhileTextChanged(textField, true);
                textField.setValidators(
                        new NumberValidator(i18n("input.integer"), false),
                        new NumberRangeValidator(i18n("input.number_range", minValue, maxValue), minValue, maxValue));
                textField.textProperty().addListener((observable, oldValue, newValue) -> {
                    Integer value = Lang.toIntOrNull(newValue);
                    if (value != null && value >= minValue && value <= maxValue) {
                        getGameRuleNBT().changeValue(newValue);
                        getOnSave().run();
                        resetButton.setDisable(shouldResetButtonDisabled(value.toString()));
                    }
                });

                textField.maxWidth(10);
                textField.minWidth(10);
            }
            {
                wrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                resetButton.setFocusTraversable(false);
                resetButton.setDisable(shouldResetButtonDisabled(currentValue.getValue()));
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setSetToDefault(() -> textField.textProperty().set(String.valueOf(defaultValue)));
                    resetButton.setOnAction(event -> getSetToDefault().run());
                    setIsDefault(() -> textField.textProperty().get().equals(String.valueOf(defaultValue)));
                    FXUtils.installFastTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", defaultValue));
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.now_is_default_values.tooltip"));
                } else {
                    resetButton.setDisable(true);
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.not_have_default_values.tooltip"));
                }

                resetButton.setAlignment(Pos.BOTTOM_CENTER);
            }

            rightHBox.getChildren().addAll(textField, wrapperPane);
            getContainer().getChildren().addAll(displayInfoVBox, rightHBox);
        }

        public boolean shouldResetButtonDisabled(String newValue) {
            return defaultValue == null || Objects.equals(Lang.toIntOrNull(newValue), defaultValue);
        }
    }
}
