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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonSerializable
@JsonAdapter(GameRule.GameRuleDeserializer.class)
public sealed abstract class GameRule permits GameRule.BooleanGameRule, GameRule.IntGameRule {

    List<String> ruleKey;
    String displayName = "";

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, boolean value) {
        return new BooleanGameRule(ruleKey, displayName, value);
    }

    public static GameRule createGameRule(ArrayList<String> ruleKey, String displayName, int value) {
        return new IntGameRule(ruleKey, displayName, value);
    }

    public abstract GameRule clone() ;

    static final class BooleanGameRule extends GameRule {
        BooleanProperty value = new SimpleBooleanProperty(false);
        BooleanProperty defaultValue = new SimpleBooleanProperty(false);

        private BooleanGameRule(List<String> ruleKey, String displayName, boolean value) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.value.set(value);
        }

        private BooleanGameRule() {

        }

        @Override
        public GameRule clone() {
            BooleanGameRule booleanGameRule = new BooleanGameRule(ruleKey, displayName, value.getValue());
            booleanGameRule.defaultValue.setValue(defaultValue.getValue());
            return booleanGameRule;
        }
    }

    static final class IntGameRule extends GameRule {
        IntegerProperty value = new SimpleIntegerProperty(0);
        IntegerProperty defaultValue = new SimpleIntegerProperty(0);

        private IntGameRule(List<String> ruleKey, String displayName, int value) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.value.set(value);
        }

        private IntGameRule() {

        }

        @Override
        public GameRule clone() {
            IntGameRule intGameRule = new IntGameRule(ruleKey, displayName, value.getValue());
            intGameRule.defaultValue.setValue(defaultValue.getValue());
            return intGameRule;
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

            gameRule.displayName = jsonObject.get("displayName").getAsString();

            JsonElement ruleKeyElement = jsonObject.get("ruleKey");
            Type listType = JsonUtils.listTypeOf(String.class).getType();
            gameRule.ruleKey = jsonDeserializationContext.deserialize(ruleKeyElement, listType);
            return gameRule;
        }
    }

    public static class GameRuleHolder {
        public static Map<String, GameRule> gameRuleMap = new HashMap<>();

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

            System.out.println("GameRuleMap: " + gameRuleMap);
        }

        public static Map<String, GameRule> cloneGameRuleMap() {
            Map<String, GameRule> newGameRuleMap = new HashMap<>();
            gameRuleMap.forEach((key, gameRule) -> newGameRuleMap.put(key, gameRule.clone()));
            return newGameRuleMap;
        }

    }
}
