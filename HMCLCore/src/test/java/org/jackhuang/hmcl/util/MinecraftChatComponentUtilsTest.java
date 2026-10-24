/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class MinecraftChatComponentUtilsTest {

    @Test
    public void testPrimitiveAndExtra() {
        assertEquals("", MinecraftChatComponentUtils.toPlainStringFromChatComponent(null));

        assertEquals("Hello world",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(JsonParser.parseString("\"Hello world\"")));

        assertEquals("Hello world",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(JsonParser.parseString("{\"text\":\"Hello \",\"extra\":[{\"text\":\"world\"}]}")));

        assertEquals("Hello world",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(JsonParser.parseString("{\"text\":\"§aHello \",\"extra\":[{\"text\":\"§lworld\"}]}")));
    }

    @Test
    public void testTranslateAndScore() {
        assertEquals("Server restart in 10 seconds",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(
                        JsonParser.parseString("{\"translate\":\"multiplayer.player.joined.renamed\",\"fallback\":\"Server restart in 10 seconds\"}")));

        assertEquals("multiplayer.player.joined.renamedAliceBob",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(
                        JsonParser.parseString("{\"translate\":\"multiplayer.player.joined.renamed\",\"with\":[\"Alice\",\"Bob\"]}")));

        assertEquals("Alice:test",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(
                        JsonParser.parseString("{\"score\":{\"name\":\"Alice\",\"objective\":\"test\"}}")));
    }

    @Test
    public void testRawText() {
        assertEquals("FirstSecond",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(JsonParser.parseString("{\"rawtext\":[{\"text\":\"First\"},{\"text\":\"Second\"}]}")));

        assertEquals("Selected item: @p diamond",
                MinecraftChatComponentUtils.toPlainStringFromChatComponent(JsonParser.parseString("{\"text\":\"Selected item: \",\"extra\":[{\"selector\":\"@p\"},{\"text\":\" diamond\"}]}")));
    }
}
