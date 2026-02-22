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
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public sealed abstract class GameRuleInfo<T> permits GameRuleInfo.BooleanGameRuleInfo, GameRuleInfo.IntGameRuleInfo {
    private final String ruleKey;
    private final String displayName;
    private final GameRuleNBT<T, ? extends Tag> gameRuleNBT;
    private final BooleanProperty modified = new SimpleBooleanProperty(this, "modified", false);

    private final Runnable onSave;
    private Runnable resetValue = () -> {
    };

    private GameRuleInfo(GameRule gameRule, GameRuleNBT<T, ? extends Tag> gameRuleNBT, Runnable onSave) {
        ruleKey = gameRule.getRuleKey().get(0);
        String displayName = "";
        if (StringUtils.isNotBlank(gameRule.getDisplayI18nKey()) && I18n.hasKey(gameRule.getDisplayI18nKey())) {
            displayName = i18n(gameRule.getDisplayI18nKey());
        }
        this.displayName = displayName;
        this.gameRuleNBT = gameRuleNBT;
        this.onSave = onSave;
    }

    public static GameRuleInfo<?> createGameRuleInfo(GameRule gameRule, GameRuleNBT<?, ? extends Tag> gameRuleNBT, Runnable onSave, GameVersionNumber gameVersion) {
        if (gameRule instanceof GameRule.IntGameRule intGameRule) {
            @SuppressWarnings("unchecked") var typedGameRuleNBT = (GameRuleNBT<String, Tag>) gameRuleNBT;
            return new GameRuleInfo.IntGameRuleInfo(intGameRule, typedGameRuleNBT, onSave, gameVersion);
        } else if (gameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
            @SuppressWarnings("unchecked") var typedGameRuleNBT = (GameRuleNBT<Boolean, Tag>) gameRuleNBT;
            return new GameRuleInfo.BooleanGameRuleInfo(booleanGameRule, typedGameRuleNBT, onSave, gameVersion);
        }
        return null;
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

            currentValue.addListener((observable, oldValue, newValue) -> {
                getGameRuleNBT().changeValue(newValue);
                save();
            });

            if (defaultValue != null) {
                setResetValue(() -> currentValue.set(defaultValue));
                modifiedProperty().bind(Bindings.createBooleanBinding(() -> currentValue.getValue() != defaultValue, currentValue));
            }

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

        public Boolean getDefaultValue() {
            return defaultValue;
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

            currentValue.addListener((observable, oldValue, newValue) -> {
                Integer value = Lang.toIntOrNull(newValue);
                if (value != null && value >= minValue && value <= maxValue) {
                    getGameRuleNBT().changeValue(newValue);
                    save();
                }
            });

            if (defaultValue != null) {
                setResetValue(() -> currentValue.set(String.valueOf(defaultValue)));
                modifiedProperty().bind(Bindings.createBooleanBinding(() -> !Objects.equals(Lang.toIntOrNull(currentValue.getValue()), defaultValue), currentValue));
            }
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

        public int getMinValue() {
            return minValue;
        }

        public int getMaxValue() {
            return maxValue;
        }

        public Integer getDefaultValue() {
            return defaultValue;
        }
    }
}
