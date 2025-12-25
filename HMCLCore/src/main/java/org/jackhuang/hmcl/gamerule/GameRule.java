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

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;

/// Represents an abstract game rule in Minecraft (e.g., `doDaylightCycle`, `randomTickSpeed`).
///
/// This class handles the logic for:
/// * Defining rule types (Boolean or Integer).
/// * Parsing rules from NBT tags (read from `level.dat`).
/// * Serializing/Deserializing rules via GSON.
///
/// It is a sealed class permitting only [BooleanGameRule] and [IntGameRule].
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
        return new BooleanGameRule(Collections.singletonList(ruleKey), value);
    }

    public static GameRule createSimpleGameRule(String ruleKey, int value) {
        IntGameRule intGameRule = new IntGameRule(Collections.singletonList(ruleKey), value);
        intGameRule.maxValue = Integer.MAX_VALUE;
        intGameRule.minValue = Integer.MIN_VALUE;
        return intGameRule;
    }

    /// Parses an NBT Tag to create a corresponding [GameRule].
    ///
    /// This method handles type coercion:
    /// * [IntTag] -> [IntGameRule]
    /// * [ByteTag] -> [BooleanGameRule]
    /// * [StringTag] -> Tries to parse as [BooleanGameRule] ("true"/"false") or [IntGameRule].
    ///
    /// @param tag The NBT tag to parse.
    /// @return An Optional containing the GameRule if parsing was successful.
    private static Optional<GameRule> createSimpleRuleFromTag(Tag tag) {
        String name = tag.getName();

        if (tag instanceof IntTag intTag) {
            return Optional.of(createSimpleGameRule(name, intTag.getValue()));
        }
        if (tag instanceof ByteTag byteTag) {
            return Optional.of(createSimpleGameRule(name, byteTag.getValue() == 1));
        }
        if (tag instanceof StringTag stringTag) {
            String value = stringTag.getValue();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Optional.of(createSimpleGameRule(name, Boolean.parseBoolean(value)));
            }
            Integer intValue = Lang.toIntOrNull(value);
            if (intValue != null) {
                return Optional.of(createSimpleGameRule(name, intValue));
            }
        }

        return Optional.empty();
    }

    /// Retrieves a fully populated GameRule based on an NBT tag.
    ///
    /// This combines parsing the tag [#createGameRuleNBT(Tag)] and applying known metadata
    /// from the provided `gameRuleMap`.
    public static Optional<GameRule> getFullGameRule(Tag tag) {
        return createSimpleRuleFromTag(tag).map(simpleGameRule -> {
            Optional.ofNullable(GameRuleHolder.metaDataGameRuleMap.get(tag.getName()))
                    .ifPresent(simpleGameRule::applyMetadata);
            return simpleGameRule;
        });
    }

    /// Creates a [GameRuleNBT] wrapper around an NBT Tag.
    /// Used for unified changing operations back to NBT format.
    ///
    /// @see GameRuleNBT
    public static Optional<GameRuleNBT<?, ? extends Tag>> createGameRuleNBT(Tag tag) {
        if (tag instanceof StringTag stringTag && (tag.getValue().equals("true") || tag.getValue().equals("false"))) {
            return Optional.of(new GameRuleNBT.StringByteGameRuleNBT(stringTag));
        } else if (tag instanceof StringTag stringTag && Lang.toIntOrNull(stringTag.getValue()) != null) {
            return Optional.of(new GameRuleNBT.StringIntGameRuleNBT(stringTag));
        } else if (tag instanceof IntTag intTag) {
            return Optional.of(new GameRuleNBT.IntGameRuleNBT(intTag));
        } else if (tag instanceof ByteTag byteTag) {
            return Optional.of(new GameRuleNBT.ByteGameRuleNBT(byteTag));
        }
        return Optional.empty();
    }

    /// Copies metadata (e.g., descriptions, ranges) from the source rule to this instance.
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

    /// Implementation of a boolean-based GameRule.
    public static final class BooleanGameRule extends GameRule {
        private Boolean value = false;
        private Boolean defaultValue;

        private BooleanGameRule(List<String> ruleKey, String displayI18nKey, Boolean value, Boolean defaultValue) {
            super(ruleKey, displayI18nKey);
            this.value = value;
            this.defaultValue = defaultValue;
        }

        private BooleanGameRule(List<String> ruleKey, boolean value) {
            super(ruleKey, "");
            this.value = value;
        }

        private BooleanGameRule() {

        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof BooleanGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                source.getDefaultValue().ifPresent(this::setDefaultValue);
            }
        }

        public Optional<Boolean> getDefaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        private void setDefaultValue(boolean value) {
            defaultValue = value;
        }

        public boolean getValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }

    /// Implementation of an integer-based GameRule.
    /// supports min/max value validation.
    public static final class IntGameRule extends GameRule {
        private Integer value = 0;
        private Integer defaultValue;
        private int maxValue = 0;
        private int minValue = 0;

        private IntGameRule(List<String> ruleKey, String displayI18nKey, Integer value, Integer defaultValue, int maxValue, int minValue) {
            super(ruleKey, displayI18nKey);
            this.value = value;
            this.defaultValue = defaultValue;
            this.maxValue = maxValue;
            this.minValue = minValue;
        }

        private IntGameRule(List<String> ruleKey, int value) {
            super(ruleKey, "");
            this.value = value;
        }

        private IntGameRule() {

        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof IntGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                source.getDefaultValue().ifPresent(this::setDefaultValue);
                this.maxValue = source.getMaxValue();
                this.minValue = source.getMinValue();
            }
        }

        public Optional<Integer> getDefaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        private void setDefaultValue(int value) {
            defaultValue = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
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

    /// Custom GSON deserializer for [GameRule].
    /// Determines whether to create an [IntGameRule] or [BooleanGameRule] based on the JSON content.
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
        private static final Map<String, GameRule> metaDataGameRuleMap = new HashMap<>();

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
                    metaDataGameRuleMap.put(s, gameRule);
                }
            }
        }

    }
}
