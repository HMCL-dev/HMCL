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
import java.util.*;

@JsonSerializable
@JsonAdapter(GameRule.GameRuleDeserializer.class)
public sealed abstract class GameRule permits GameRule.BooleanGameRule, GameRule.IntGameRule {

    private List<String> ruleKey;
    private String displayI18nKey = "";

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, boolean value) {
        return new BooleanGameRule(ruleKey, displayName, value);
    }

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, int value) {
        return new IntGameRule(ruleKey, displayName, value);
    }

    public static GameRule createSimpleGameRule(String ruleKey, boolean value) {
        return new BooleanGameRule(Collections.singletonList(ruleKey), "", value);
    }

    public static GameRule createSimpleGameRule(String ruleKey, int value) {
        return new IntGameRule(Collections.singletonList(ruleKey), "", value);
    }

    public static GameRule mixGameRule(GameRule simpleGameRule, GameRule mapGameRule) {
        if (simpleGameRule instanceof BooleanGameRule simplpBooleanGameRule && mapGameRule instanceof BooleanGameRule mixBooleanGameRule) {
            simplpBooleanGameRule.setDisplayI18nKey(mixBooleanGameRule.getDisplayI18nKey());
            simplpBooleanGameRule.setDefaultValue(mixBooleanGameRule.getDefaultValue());
            return simplpBooleanGameRule;
        } else if (simpleGameRule instanceof IntGameRule simplpIntGameRule && simpleGameRule instanceof IntGameRule mixIntGameRule) {
            simplpIntGameRule.setDisplayI18nKey(mixIntGameRule.getDisplayI18nKey());
            simplpIntGameRule.setDefaultValue(mixIntGameRule.getDefaultValue());
            return simplpIntGameRule;
        } else {
            return simpleGameRule;
        }
    }

    public static Map<String, GameRule> addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, String displayName, boolean value) {
        ArrayList<String> ruleKeyList = new ArrayList<>();
        ruleKeyList.add(ruleKey);
        gameRuleMap.put(ruleKey, new BooleanGameRule(ruleKeyList, displayName, value));
        return gameRuleMap;
    }

    public static Map<String, GameRule> addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, String displayName, int value) {
        ArrayList<String> ruleKeyList = new ArrayList<>();
        ruleKeyList.add(ruleKey);
        gameRuleMap.put(ruleKey, new IntGameRule(ruleKeyList, displayName, value));
        return gameRuleMap;
    }

    public static Map<String, GameRule> addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, boolean value) {
        return addSimpleGameRule(gameRuleMap, ruleKey, "", value);
    }

    public static Map<String, GameRule> addSimpleGameRule(Map<String, GameRule> gameRuleMap, String ruleKey, int value) {
        return addSimpleGameRule(gameRuleMap, ruleKey, "", value);
    }

    public static Map<String, GameRule> getCloneGameRuleMap() {
        return GameRule.GameRuleHolder.cloneGameRuleMap();
    }

    public abstract GameRule clone();

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
            this.setRuleKey(ruleKey);
            this.setDisplayI18nKey(displayI18nKey);
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

        private IntGameRule(List<String> ruleKey, String displayI18nKey, int value) {
            this.setRuleKey(ruleKey);
            this.setDisplayI18nKey(displayI18nKey);
            this.value.set(value);
        }

        private IntGameRule() {

        }

        @Override
        public GameRule clone() {
            IntGameRule intGameRule = new IntGameRule(getRuleKey(), getDisplayI18nKey(), value.getValue());
            intGameRule.defaultValue.setValue(defaultValue.getValue());
            return intGameRule;
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
    }

    static class GameRuleDeserializer implements JsonDeserializer<GameRule> {

        @Override
        public GameRule deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            GameRule gameRule;
            if (jsonObject.get("defaultValue") instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isNumber()) {
                gameRule = new IntGameRule();
            } else {
                gameRule = new BooleanGameRule();
            }

            if (gameRule instanceof IntGameRule intGameRule) {
                intGameRule.defaultValue.setValue(jsonObject.get("defaultValue").getAsInt());
            } else {
                BooleanGameRule booleanGameRule = (BooleanGameRule) gameRule;
                booleanGameRule.defaultValue.setValue(jsonObject.get("defaultValue").getAsBoolean());
            }

            gameRule.displayI18nKey = jsonObject.get("displayI18nKey").getAsString();

            JsonElement ruleKeyElement = jsonObject.get("ruleKey");
            Type listType = JsonUtils.listTypeOf(String.class).getType();
            gameRule.ruleKey = jsonDeserializationContext.deserialize(ruleKeyElement, listType);
            return gameRule;
        }
    }

    public static class GameRuleHolder {
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
                throw new RuntimeException(e);
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
