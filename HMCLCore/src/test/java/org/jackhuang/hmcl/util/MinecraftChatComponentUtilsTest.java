// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
