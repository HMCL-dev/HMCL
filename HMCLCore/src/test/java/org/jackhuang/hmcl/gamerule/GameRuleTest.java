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

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameRuleTest {

    private String getMataDataJson() throws IOException {
        InputStream is = GameRule.class.getResourceAsStream("/assets/gamerule/gamerule.json");
        return IOUtils.readFullyAsString(is);
    }

    @Test
    public void testParseMataData() throws IOException {
        Map<String, GameRule> metaDataGameRuleMap = new HashMap<>();

        String jsonContent = getMataDataJson();
        List<GameRule> gameRules = JsonUtils.fromNonNullJson(jsonContent, JsonUtils.listTypeOf(GameRule.class));

        for (GameRule gameRule : gameRules) {
            for (String s : gameRule.getRuleKey()) {
                metaDataGameRuleMap.put(s, gameRule);
            }
        }

        assertFalse(gameRules.isEmpty());

    }

    public void assertParseSingleIntGameRule(String jsonContent, List<String> gameRuleKeys, Map<String, Integer> defaultValueMap, Map<String, Integer> minValueMap, Map<String, Integer> maxValueMap) {
        GameRule gameRules = JsonUtils.fromNonNullJson(jsonContent, GameRule.class);
        GameRule.IntGameRule intGameRule = assertInstanceOf(GameRule.IntGameRule.class, gameRules);
        assertEquals(intGameRule.getRuleKey(), gameRuleKeys);
        defaultValueMap.forEach((key, value) -> {
            assertEquals(value, intGameRule.getDefaultValue(GameVersionNumber.asGameVersion(key)).orElseThrow(() -> new AssertionError("cannot get default value for defaultValue")));
        });
        minValueMap.forEach((key, value) -> {
            assertEquals(value, intGameRule.getMinValue(GameVersionNumber.asGameVersion(key)));
        });
        maxValueMap.forEach((key, value) -> {
            assertEquals(value, intGameRule.getMaxValue(GameVersionNumber.asGameVersion(key)));
        });
    }

    public void assertParseSingleBooleanRule(String jsonContent, List<String> gameRuleKeys, Map<String, Boolean> defaultValueMap) {
        GameRule gameRules = JsonUtils.fromNonNullJson(jsonContent, GameRule.class);
        GameRule.BooleanGameRule booleanGameRule = assertInstanceOf(GameRule.BooleanGameRule.class, gameRules);
        defaultValueMap.forEach((key, value) -> {
            assertEquals(value, booleanGameRule.getDefaultValue(GameVersionNumber.asGameVersion(key)).orElseThrow(() -> new AssertionError("cannot get default value for defaultValue")));
        });
    }

    @Test
    public void testParseGameRule() {
        assertParseSingleIntGameRule(
                """
                        {
                          "ruleKey": [
                            "minecraft:test",
                            "test"
                          ],
                          "type": "int",
                          "displayI18nKey": "gamerule.rule.test",
                          "defaultValue": 1,
                          "minValue": {
                            "22w44a": "INT_MIN",
                            "25w44a": 0
                          },
                          "maxValue": {
                            "22w44a": "INT_MAX",
                            "25w44a": 8
                          }
                        }
                        """,
                List.of("minecraft:test", "test"),
                Map.of("25w45a", 1),
                Map.of("23w44a", Integer.MIN_VALUE, "25w45a", 0),
                Map.of("23w44a", Integer.MAX_VALUE, "25w45a", 8)
        );
        assertParseSingleBooleanRule(
                """
                          {
                            "ruleKey": [
                              "minecraft:test",
                              "test"
                            ],
                            "type": "boolean",
                            "displayI18nKey": "gamerule.rule.test",
                            "defaultValue": {
                              "22w44a": true,
                              "25w44a": false
                            }
                          }
                        """,
                List.of("minecraft:test", "test"),
                Map.of("25w45a", false, "23w44a", true)
        );
    }
}
