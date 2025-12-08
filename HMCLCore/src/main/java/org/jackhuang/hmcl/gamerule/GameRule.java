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
package org.jackhuang.hmcl.gamerule;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonSerializable
@JsonAdapter(GameRule.GameRuleDeserializer.class)
public sealed abstract class GameRule permits GameRule.BooleanGameRule, GameRule.IntGameRule {

    private List<String> ruleKey;
    private String displayI18nKey = "";

    protected GameRule() {
    }

    protected GameRule(List<String> ruleKey, String displayI18nKey) {
        this.ruleKey = ruleKey;
        this.displayI18nKey = displayI18nKey;
    }

    public static GameRule createSimpleGameRule(String ruleKey, boolean value) {
        return new BooleanGameRule(Collections.singletonList(ruleKey), "", value);
    }

    public static GameRule createSimpleGameRule(String ruleKey, int value) {
        return new IntGameRule(Collections.singletonList(ruleKey), "", value);
    }

    public static void mixGameRule(GameRule simpleGameRule, GameRule gameRule) {
        simpleGameRule.applyMetadata(gameRule);
    }

    public static void addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, String displayName, boolean value) {
        gameRuleMap.put(ruleKey, new BooleanGameRule(Collections.singletonList(ruleKey), displayName, value));
    }

    public static void addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, String displayName, int value) {
        gameRuleMap.put(ruleKey, new IntGameRule(Collections.singletonList(ruleKey), displayName, value));
    }

    public static Map<String, GameRule> getCloneGameRuleMap() {
        return GameRule.GameRuleHolder.cloneGameRuleMap();
    }

    public abstract GameRule clone();

    public abstract void applyMetadata(GameRule metadataSource);

    public void setRuleKey(List<String> ruleKey) {
        this.ruleKey = ruleKey;
    }

    public List<String> getRuleKey() {
        return ruleKey;
    }

    public void setDisplayI18nKey(String displayI18nKey) {
        this.displayI18nKey = displayI18nKey;
    }

    public String getDisplayI18nKey() {
        return displayI18nKey;
    }

    public static final class BooleanGameRule extends GameRule {
        private final BooleanProperty value = new SimpleBooleanProperty(false);
        private final BooleanProperty defaultValue = new SimpleBooleanProperty(false);

        private BooleanGameRule(List<String> ruleKey, String displayI18nKey, boolean value) {
            super(ruleKey, displayI18nKey);
            this.value.set(value);
        }

        private BooleanGameRule() {

        }

        @Override
        public GameRule clone() {
            BooleanGameRule booleanGameRule = new BooleanGameRule(getRuleKey(), getDisplayI18nKey(), value.getValue());
            booleanGameRule.defaultValue.setValue(defaultValue.getValue());
            return booleanGameRule;
        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof BooleanGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                this.setDefaultValue(source.getDefaultValue());
            }
        }

        public boolean getDefaultValue() {
            return defaultValue.get();
        }

        public BooleanProperty defaultValueProperty() {
            return defaultValue;
        }

        private void setDefaultValue(boolean value) {
            this.defaultValue.setValue(value);
        }

        public boolean getValue() {
            return value.get();
        }

        public BooleanProperty valueProperty() {
            return value;
        }

        public void setValue(boolean value) {
            this.value.setValue(value);
        }
    }

    public static final class IntGameRule extends GameRule {
        private final IntegerProperty value = new SimpleIntegerProperty(0);
        private final IntegerProperty defaultValue = new SimpleIntegerProperty(0);
        private int maxValue = 0;
        private int minValue = 0;

        private IntGameRule(List<String> ruleKey, String displayI18nKey, int value) {
            super(ruleKey, displayI18nKey);
            this.value.set(value);
        }

        private IntGameRule() {

        }

        @Override
        public GameRule clone() {
            IntGameRule intGameRule = new IntGameRule(getRuleKey(), getDisplayI18nKey(), value.getValue());
            intGameRule.defaultValue.setValue(defaultValue.getValue());
            intGameRule.minValue = minValue;
            intGameRule.maxValue = maxValue;
            return intGameRule;
        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof IntGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                this.setDefaultValue(source.getDefaultValue());
                this.maxValue = source.getMaxValue();
                this.minValue = source.getMinValue();
            }
        }

        public int getDefaultValue() {
            return defaultValue.get();
        }

        public IntegerProperty defaultValueProperty() {
            return defaultValue;
        }

        private void setDefaultValue(int value) {
            this.defaultValue.setValue(value);
        }

        public int getValue() {
            return value.get();
        }

        public IntegerProperty valueProperty() {
            return value;
        }

        public void setValue(int value) {
            this.value.setValue(value);
        }

        public int getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(int maxValue) {
            this.maxValue = maxValue;
        }

        public int getMinValue() {
            return minValue;
        }

        public void setMinValue(int minValue) {
            this.minValue = minValue;
        }
    }

    static class GameRuleDeserializer implements JsonDeserializer<GameRule> {

        @Override
        public GameRule deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            GameRule gameRule = createRuleFromDefaultValue(jsonObject.get("defaultValue"));
            gameRule.displayI18nKey = jsonObject.get("displayI18nKey").getAsString();

            Type listType = JsonUtils.listTypeOf(String.class).getType();
            gameRule.ruleKey = context.deserialize(jsonObject.get("ruleKey"), listType);

            if (gameRule instanceof IntGameRule intGameRule) {
                if (jsonObject.has("maxValue") && jsonObject.get("maxValue") instanceof JsonPrimitive jsonPrimitive) {
                    intGameRule.maxValue = jsonPrimitive.getAsInt();
                } else {
                    intGameRule.maxValue = Integer.MAX_VALUE;
                }

                if (jsonObject.has("minValue") && jsonObject.get("minValue") instanceof JsonPrimitive jsonPrimitive) {
                    intGameRule.minValue = jsonPrimitive.getAsInt();
                } else {
                    intGameRule.minValue = Integer.MIN_VALUE;
                }
            }

            return gameRule;
        }

        private GameRule createRuleFromDefaultValue(JsonElement defaultValueElement) {
            if (defaultValueElement instanceof JsonPrimitive p && p.isNumber()) {
                IntGameRule rule = new IntGameRule();
                rule.setDefaultValue(defaultValueElement.getAsInt());
                return rule;
            } else {
                BooleanGameRule rule = new BooleanGameRule();
                rule.setDefaultValue(defaultValueElement.getAsBoolean());
                return rule;
            }
        }
    }

    static final class GameRuleHolder {
        private static final Map<String, GameRule> gameRuleMap = new HashMap<>();

        static {
            List<GameRule> gameRules;
            try (InputStream is = GameRule.class.getResourceAsStream("/assets/gamerule/gamerule.json")) {
                if (is == null) {
                    throw new IOException("Resource not found: /assets/gamerule/gamerule.json");
                }
                String jsonContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                gameRules = JsonUtils.fromNonNullJson(jsonContent, JsonUtils.listTypeOf(GameRule.class));
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize GameRuleHolder", e);
            }
            for (GameRule gameRule : gameRules) {
                for (String s : gameRule.ruleKey) {
                    gameRuleMap.put(s, gameRule);
                }
            }
        }

        private static Map<String, GameRule> cloneGameRuleMap() {
            Map<String, GameRule> newGameRuleMap = new HashMap<>();
            gameRuleMap.forEach((key, gameRule) -> newGameRuleMap.put(key, gameRule.clone()));
            return newGameRuleMap;
        }

    }
}
