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
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionedValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Represents an abstract game rule in Minecraft (e.g., `doDaylightCycle`, `randomTickSpeed`).
///
/// This class handles the logic for:
/// * Defining rule types (Boolean or Integer).
/// * Parsing rules from NBT tags (read from `level.dat`).
/// * Deserializing rules form `resources/assets/gamerule/gamerule.json`
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
            return Optional.of(new IntGameRule(name, intTag.getValue()));
        } else if (tag instanceof ByteTag byteTag) {
            return Optional.of(new BooleanGameRule(name, byteTag.getValue() == 1));
        } else if (tag instanceof StringTag stringTag) {
            String value = stringTag.getValue();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                return Optional.of(new BooleanGameRule(name, Boolean.parseBoolean(value)));
            }
            Integer intValue = Lang.toIntOrNull(value);
            if (intValue != null) {
                return Optional.of(new IntGameRule(name, intValue));
            }
        }

        return Optional.empty();
    }

    /// Creates a [GameRuleNBT] wrapper around an NBT Tag.
    /// Used for unified changing operations back to NBT format.
    ///
    /// @see GameRuleNBT
    public static Optional<GameRuleNBT<?, ? extends Tag>> createGameRuleNBT(Tag tag) {
        if (tag instanceof IntTag intTag) {
            return Optional.of(new GameRuleNBT.IntGameRuleNBT(intTag));
        } else if (tag instanceof ByteTag byteTag) {
            return Optional.of(new GameRuleNBT.ByteGameRuleNBT(byteTag));
        } else if (tag instanceof StringTag stringTag && (stringTag.getValue().equals("true") || stringTag.getValue().equals("false"))) {
            return Optional.of(new GameRuleNBT.StringByteGameRuleNBT(stringTag));
        } else if (tag instanceof StringTag stringTag && Lang.toIntOrNull(stringTag.getValue()) != null) {
            return Optional.of(new GameRuleNBT.StringIntGameRuleNBT(stringTag));
        }
        return Optional.empty();
    }

    /// Copies metadata (descriptions, default value and ranges) from the source rule to this instance.
    public abstract void applyMetadata(GameRule metadataSource);

    public abstract GameRule deserialize(JsonObject jsonObject, Type type, JsonDeserializationContext context);

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
        private boolean value = false;
        private final VersionedValue<Boolean> defaultValue = new VersionedValue<>();

        private BooleanGameRule() {
        }

        private BooleanGameRule(String ruleKey, boolean value) {
            super(Collections.singletonList(ruleKey), "");
            this.value = value;
        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof BooleanGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                this.defaultValue.putAll(source.defaultValue);
            }
        }

        @Override
        public GameRule deserialize(JsonObject jsonObject, Type type, JsonDeserializationContext context) {
            this.setRuleKey(JsonUtils.fromNonNullJson(jsonObject.get("ruleKey").toString(), JsonUtils.listTypeOf(String.class)));
            this.setDisplayI18nKey(jsonObject.get("displayI18nKey").getAsString());
            JsonElement defaultValue = jsonObject.get("defaultValue");
            if (defaultValue instanceof JsonPrimitive p && p.isBoolean()) {
                this.addDefaultValue(p.getAsBoolean());
            } else if (defaultValue instanceof JsonObject o) {
                o.asMap().forEach((key, value) -> this.addDefaultValue(key, value.getAsBoolean()));
            }

            return this;
        }

        public Optional<Boolean> getDefaultValue(GameVersionNumber gameVersionNumber) {
            return defaultValue.getValue(gameVersionNumber);
        }

        private void addDefaultValue(boolean value) {
            defaultValue.putMinVersion("1.4.2", value);
        }

        private void addDefaultValue(String versionName, boolean value) {
            defaultValue.putMinVersion(versionName, value);
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
        private int value = 0;
        private final VersionedValue<Integer> defaultValue = new VersionedValue<>();
        private final VersionedValue<Integer> minValue = new VersionedValue<>();
        private final VersionedValue<Integer> maxValue = new VersionedValue<>();

        private IntGameRule() {
        }

        private IntGameRule(String ruleKey, int value) {
            super(Collections.singletonList(ruleKey), "");
            this.value = value;
            addMaxValue(Integer.MAX_VALUE);
            addMinValue(Integer.MIN_VALUE);
        }

        @Override
        public void applyMetadata(GameRule metadataSource) {
            if (metadataSource instanceof IntGameRule source) {
                this.setDisplayI18nKey(source.getDisplayI18nKey());
                this.defaultValue.putAll(source.defaultValue);
                this.maxValue.putAll(source.maxValue);
                this.minValue.putAll(source.minValue);
            }
        }

        @Override
        public GameRule deserialize(JsonObject jsonObject, Type type, JsonDeserializationContext context) {
            this.setRuleKey(JsonUtils.fromNonNullJson(jsonObject.get("ruleKey").toString(), JsonUtils.listTypeOf(String.class)));
            this.setDisplayI18nKey(jsonObject.get("displayI18nKey").getAsString());

            if (jsonObject.get("defaultValue") instanceof JsonPrimitive p && p.isNumber()) {
                this.addDefaultValue(p.getAsInt());
            } else if (jsonObject.get("defaultValue") instanceof JsonObject o) {
                o.asMap().forEach((key, value) -> this.addDefaultValue(key, value.getAsInt()));
            }

            if (jsonObject.get("maxValue") instanceof JsonPrimitive jsonPrimitive) {
                this.addMaxValue(jsonPrimitive.getAsInt());
            } else if (jsonObject.get("maxValue") instanceof JsonObject o) {
                o.asMap().forEach((key, value) -> this.addMaxValue(key, parseValue(value)));
            } else {
                this.addMaxValue(Integer.MAX_VALUE);
            }

            if (jsonObject.get("minValue") instanceof JsonPrimitive jsonPrimitive) {
                this.addMinValue(jsonPrimitive.getAsInt());
            } else if (jsonObject.get("minValue") instanceof JsonObject o) {
                o.asMap().forEach((key, value) -> this.addMinValue(key, parseValue(value)));
            } else {
                this.addMinValue(Integer.MIN_VALUE);
            }
            return this;
        }

        private int parseValue(JsonElement jsonElement) {
            JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            } else {
                String str = primitive.getAsString();
                return "INT_MAX".equals(str) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            }
        }

        public Optional<Integer> getDefaultValue(GameVersionNumber gameVersionNumber) {
            return defaultValue.getValue(gameVersionNumber);
        }

        private void addDefaultValue(int value) {
            this.defaultValue.putMinVersion("1.4.2", value);
        }

        private void addDefaultValue(String versionName, int value) {
            this.defaultValue.putMinVersion(versionName, value);
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public int getMaxValue(GameVersionNumber gameVersionNumber) {
            return this.maxValue.getValue(gameVersionNumber, Integer.MAX_VALUE);
        }

        public void addMaxValue(int maxValue) {
            this.maxValue.putMinVersion("1.4.2", maxValue);
        }

        public void addMaxValue(String versionName, int maxValue) {
            this.maxValue.putMinVersion(versionName, maxValue);
        }

        public int getMinValue(GameVersionNumber gameVersionNumber) {
            return minValue.getValue(gameVersionNumber, Integer.MIN_VALUE);
        }

        public void addMinValue(int minValue) {
            this.minValue.putMinVersion("1.4.2", minValue);
        }

        public void addMinValue(String versionName, int minValue) {
            this.minValue.putMinVersion(versionName, minValue);
        }
    }

    /// Custom GSON deserializer for [GameRule].
    /// Determines whether to create an [IntGameRule] or [BooleanGameRule] based on the JSON content.
    static class GameRuleDeserializer implements JsonDeserializer<GameRule> {

        @Override
        public GameRule deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            GameRule gameRule;
            switch (jsonObject.get("type").getAsString()) {
                case "int" -> gameRule = new IntGameRule();
                case "boolean" -> gameRule = new BooleanGameRule();
                default -> throw new JsonParseException("Unknown GameRule type: " + jsonObject.get("type").getAsString());
            }
            return gameRule.deserialize(jsonObject, type, context);
        }
    }

    static final class GameRuleHolder {
        private static final Map<String, GameRule> metaDataGameRuleMap = new HashMap<>();

        static {
            try {
                InputStream is = GameRule.class.getResourceAsStream("/assets/gamerule/gamerule.json");
                String jsonContent = IOUtils.readFullyAsString(is);
                List<GameRule> gameRules = JsonUtils.fromNonNullJson(jsonContent, JsonUtils.listTypeOf(GameRule.class));

                for (GameRule gameRule : gameRules) {
                    for (String s : gameRule.ruleKey) {
                        metaDataGameRuleMap.put(s, gameRule);
                    }
                }
            } catch (IOException e) {
                LOG.warning("Cannot read gamerule.json" + e.getMessage());
            } catch (JsonParseException e) {
                LOG.warning("Cannot parse gamerule.json" + e.getMessage());
            }
        }

    }
}
