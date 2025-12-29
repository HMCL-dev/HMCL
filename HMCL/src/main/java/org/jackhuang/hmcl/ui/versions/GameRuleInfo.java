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
import javafx.beans.binding.Bindings;
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

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public sealed abstract class GameRuleInfo<T> permits GameRuleInfo.BooleanGameRuleInfo, GameRuleInfo.IntGameRuleInfo {
    private final String ruleKey;
    private final String displayName;
    private final GameRuleNBT<T, ? extends Tag> gameRuleNBT;
    private final BooleanProperty modified = new SimpleBooleanProperty(this, "modified", false);

    private final Runnable onSave;
    private Runnable resetValue = () -> {
    };

    //Due to the significant difference in skin between BooleanGameRuleInfo and IntGameRuleInfo, which are essentially two completely different styles, it is not suitable to update each other in Cell#updateControl. Therefore, they are directly integrated into the info.
    private final HBox container = new HBox();

    private GameRuleInfo(GameRule gameRule, GameRuleNBT<T, ? extends Tag> gameRuleNBT, Runnable onSave) {
        ruleKey = gameRule.getRuleKey().get(0);
        String displayName = "";
        try {
            if (StringUtils.isNotBlank(gameRule.getDisplayI18nKey())) {
                displayName = i18n(gameRule.getDisplayI18nKey());
            }
        } catch (Exception e) {
            LOG.warning("Failed to get i18n text for key: " + gameRule.getDisplayI18nKey(), e);
        }
        this.displayName = displayName;
        this.gameRuleNBT = gameRuleNBT;
        this.onSave = onSave;
    }

    public abstract String getCurrentValueText();

    public abstract String getDefaultValueText();

    public void resetValue() {
        resetValue.run();
    }

    public void save() {
        onSave.run();
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GameRuleNBT<T, ? extends Tag> getGameRuleNBT() {
        return gameRuleNBT;
    }

    public Runnable getOnSave() {
        return onSave;
    }

    public HBox getContainer() {
        return container;
    }

    public Runnable getResetValue() {
        return resetValue;
    }

    public void setResetValue(Runnable resetValue) {
        this.resetValue = resetValue;
    }

    public BooleanProperty modifiedProperty() {
        return modified;
    }

    public boolean getModified() {
        return modified.get();
    }

    static final class BooleanGameRuleInfo extends GameRuleInfo<Boolean> {
        private final BooleanProperty currentValue;
        private final Boolean defaultValue;

        public BooleanGameRuleInfo(GameRule.BooleanGameRule booleanGameRule, GameRuleNBT<Boolean, Tag> gameRuleNBT, Runnable onSave, GameVersionNumber gameVersionNumber) {
            super(booleanGameRule, gameRuleNBT, onSave);
            this.currentValue = new SimpleBooleanProperty(booleanGameRule.getValue());
            this.defaultValue = booleanGameRule.getDefaultValue(gameVersionNumber).orElse(null);

            buildNodes();
        }

        @Override
        public String getCurrentValueText() {
            return currentValue.getValue().toString();
        }

        @Override
        public String getDefaultValueText() {
            return defaultValue == null ? "" : defaultValue.toString();
        }

        public BooleanProperty currentValueProperty() {
            return currentValue;
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
                HBox.setHgrow(toggleButton, Priority.ALWAYS);
                toggleButton.selectedProperty().bindBidirectional(currentValue);
                currentValue.addListener((observable, oldValue, newValue) -> {
                    getGameRuleNBT().changeValue(newValue);
                    save();
                });
            }

            JFXButton resetButton = new JFXButton();
            StackPane wrapperPane = new StackPane(resetButton);
            {
                wrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                resetButton.setFocusTraversable(false);
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setResetValue(() -> currentValue.set(defaultValue));
                    resetButton.setOnAction(event -> resetValue());
                    modifiedProperty().bind(Bindings.createBooleanBinding(() -> currentValue.getValue() != defaultValue, currentValue));
                    resetButton.disableProperty().bind(modifiedProperty().not());
                    FXUtils.installFastTooltip(resetButton, i18n("gamerule.restore_default_values.tooltip", defaultValue));
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.now_is_default_values.tooltip"));
                } else {
                    resetButton.setDisable(true);
                    FXUtils.installFastTooltip(wrapperPane, i18n("gamerule.not_have_default_values.tooltip"));
                }
            }

            getContainer().getChildren().addAll(toggleButton, wrapperPane);
        }
    }

    static final class IntGameRuleInfo extends GameRuleInfo<String> {
        private final StringProperty currentValue;
        private final Integer defaultValue;
        private final int minValue;
        private final int maxValue;

        public IntGameRuleInfo(GameRule.IntGameRule intGameRule, GameRuleNBT<String, Tag> gameRuleNBT, Runnable onSave, GameVersionNumber gameVersionNumber) {
            super(intGameRule, gameRuleNBT, onSave);
            currentValue = new SimpleStringProperty(String.valueOf(intGameRule.getValue()));
            defaultValue = intGameRule.getDefaultValue(gameVersionNumber).orElse(null);
            minValue = intGameRule.getMinValue(gameVersionNumber);
            maxValue = intGameRule.getMaxValue(gameVersionNumber);

            buildNodes();
        }

        @Override
        public String getCurrentValueText() {
            return currentValue.getValue();
        }

        @Override
        public String getDefaultValueText() {
            return defaultValue == null ? null : defaultValue.toString();
        }

        public StringProperty currentValueProperty() {
            return currentValue;
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

            JFXTextField textField = new JFXTextField();
            {
                textField.textProperty().bindBidirectional(currentValue);
                FXUtils.setValidateWhileTextChanged(textField, true);
                textField.setValidators(
                        new NumberValidator(i18n("input.integer"), false),
                        new NumberRangeValidator(i18n("input.number_range", minValue, maxValue), minValue, maxValue));
                currentValue.addListener((observable, oldValue, newValue) -> {
                    Integer value = Lang.toIntOrNull(newValue);
                    if (value != null && value >= minValue && value <= maxValue) {
                        getGameRuleNBT().changeValue(newValue);
                        save();
                    }
                });

                textField.maxWidth(10);
                textField.minWidth(10);
            }

            JFXButton resetButton = new JFXButton();
            StackPane wrapperPane = new StackPane(resetButton);
            {
                wrapperPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                resetButton.setFocusTraversable(false);
                resetButton.setGraphic(SVG.RESTORE.createIcon(24));
                if (defaultValue != null) {
                    setResetValue(() -> currentValue.set(String.valueOf(defaultValue)));
                    resetButton.setOnAction(event -> resetValue());
                    modifiedProperty().bind(Bindings.createBooleanBinding(() -> !Objects.equals(Lang.toIntOrNull(currentValue.getValue()), defaultValue), currentValue));
                    resetButton.disableProperty().bind(modifiedProperty().not());
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
    }
}
