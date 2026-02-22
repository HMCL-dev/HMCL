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
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GameRuleNBTTest {
    @Test
    public void testByteTag() {
        ByteTag tag = new ByteTag("byte_tag", (byte) 1);
        GameRuleNBT<?, ? extends Tag> gameRuleNBT = GameRule.createGameRuleNBT(tag).orElseThrow(() -> new AssertionError("Expected GameRuleNBT to be created for ByteTag"));

        GameRuleNBT.ByteGameRuleNBT byteGameRuleNBT = assertInstanceOf(GameRuleNBT.ByteGameRuleNBT.class, gameRuleNBT);
        byteGameRuleNBT.changeValue(false);
        assertEquals((byte) 0, tag.getValue());
    }

    @Test
    public void testStringByteTag() {
        StringTag tag = new StringTag("string_byte_tag", "true");
        GameRuleNBT<?, ? extends Tag> gameRuleNBT = GameRule.createGameRuleNBT(tag).orElseThrow(() -> new AssertionError("Expected GameRuleNBT to be created for StringedByteTag"));

        GameRuleNBT.StringByteGameRuleNBT stringedByteGameRuleNBT = assertInstanceOf(GameRuleNBT.StringByteGameRuleNBT.class, gameRuleNBT);
        stringedByteGameRuleNBT.changeValue(false);
        assertEquals("false", tag.getValue());
    }

    @Test
    public void testIntTag() {
        IntTag tag = new IntTag("int_tag", 1);
        GameRuleNBT<?, ? extends Tag> gameRuleNBT = GameRule.createGameRuleNBT(tag).orElseThrow(() -> new AssertionError("Expected GameRuleNBT to be created for IntTag"));

        GameRuleNBT.IntGameRuleNBT intGameRuleNBT = assertInstanceOf(GameRuleNBT.IntGameRuleNBT.class, gameRuleNBT);
        intGameRuleNBT.changeValue(2);
        assertEquals(2, tag.getValue());
    }

    @Test
    public void testStringIntTag() {
        StringTag tag = new StringTag("string_int_tag", "1");
        GameRuleNBT<?, ? extends Tag> gameRuleNBT = GameRule.createGameRuleNBT(tag).orElseThrow(() -> new AssertionError("Expected GameRuleNBT to be created for StringedIntTag"));

        GameRuleNBT.StringIntGameRuleNBT stringIntGameRuleNBT = assertInstanceOf(GameRuleNBT.StringIntGameRuleNBT.class, gameRuleNBT);
        stringIntGameRuleNBT.changeValue(2);
        assertEquals("2", tag.getValue());
    }

    @Test
    public void testWrongTag() {
        StringTag tag = new StringTag("wrong_tag", "abc");
        Optional<GameRuleNBT<?, ? extends Tag>> gameRuleNBT = GameRule.createGameRuleNBT(tag);
        assertTrue(gameRuleNBT.isEmpty());
    }
}
