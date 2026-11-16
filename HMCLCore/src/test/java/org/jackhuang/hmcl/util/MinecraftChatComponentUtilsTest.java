// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftChatComponentUtilsTest {

    private static JsonElement toChat(String s) {
        return JsonParser.parseString(s);
    }

    private static String toPlain(String s) {
        return MinecraftChatComponentUtils.toPlainText(toChat(s));
    }

    @Test
    void testPrimitivesAndEmpty() {
        assertEquals("Hello", toPlain("\"Hello\""));
        assertEquals("42", toPlain("42"));
        assertEquals("true", toPlain("true"));
        assertEquals("", toPlain("null"));
        assertEquals("", toPlain("{}"));
        assertEquals("", toPlain("[]"));
    }

    @Test
    void testArrays() {
        assertEquals("abc", toPlain("[\"a\",\"b\",\"c\"]"));
        assertEquals("Hello world", toPlain("[{\"text\":\"Hello\"},{\"text\":\" \"},{\"text\":\"world\"}]"));
        assertEquals("abc", toPlain("[[\"a\",[\"b\"]],\"c\"]"));
    }

    @Test
    void testTextAndFormatting() {
        assertEquals("Hi", toPlain("{\"text\":\"Hi\"}"));
        assertEquals("", toPlain("{\"text\":\"\"}"));
        assertEquals("Hi", toPlain("{\"text\":\"Hi\",\"color\":\"red\",\"bold\":true,\"italic\":true,"
                + "\"underlined\":true,\"strikethrough\":true,\"obfuscated\":true,"
                + "\"font\":\"x\",\"insertion\":\"y\","
                + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://x\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"tip\"}}}"));
        assertEquals("Hello", toPlain("{\"text\":\"§cHello\"}"));
        assertEquals("RedBold", toPlain("{\"text\":\"§cRed§lBold§r\"}"));
        assertEquals("Hello", toPlain("{\"text\":\"Hello§\"}"));
    }

    @Test
    void testExtraAndRawtext() {
        assertEquals("Hello world", toPlain("{\"text\":\"Hello\",\"extra\":[{\"text\":\" \"},{\"text\":\"world\"}]}"));
        assertEquals("ab", toPlain("{\"extra\":[{\"text\":\"a\"},{\"text\":\"b\"}]}"));
        assertEquals("foobar", toPlain("{\"rawtext\":[{\"text\":\"foo\"},{\"text\":\"bar\"}]}"));
        assertEquals("abcd", toPlain("{\"text\":\"a\",\"extra\":[{\"text\":\"b\",\"extra\":[{\"text\":\"c\",\"extra\":[{\"text\":\"d\"}]}]}]}"));
    }

    @Test
    void testTranslate() {
        assertEquals("chat.type.text", toPlain("{\"translate\":\"chat.type.text\"}"));
        assertEquals("chat.type.textAliceHi", toPlain("{\"translate\":\"chat.type.text\",\"with\":[{\"text\":\"Alice\"},{\"text\":\"Hi\"}]}"));
        assertEquals("Alice joined", toPlain("{\"translate\":\"chat.type.text\",\"with\":[{\"text\":\"Alice\"}],\"fallback\":\"Alice joined\"}"));
        assertEquals("Fallback!", toPlain("{\"translate\":\"key\",\"fallback\":{\"text\":\"Fallback\",\"extra\":[{\"text\":\"!\"}]}}"));
        assertEquals("key", toPlain("{\"translate\":\"key\",\"fallback\":null}"));
        assertEquals("key!", toPlain("{\"translate\":\"key\",\"extra\":[{\"text\":\"!\"}]}"));
    }

    @Test
    void testScoreAndServerSideComponents() {
        assertEquals("5", toPlain("{\"score\":{\"name\":\"Alice\",\"objective\":\"kills\",\"value\":5}}"));
        assertEquals("100", toPlain("{\"score\":{\"name\":\"Alice\",\"objective\":\"kills\",\"value\":{\"type\":\"fixed\",\"value\":100}}}"));
        assertEquals("Alice:kills", toPlain("{\"score\":{\"name\":\"Alice\",\"objective\":\"kills\"}}"));
        assertEquals("", toPlain("{\"score\":{}}"));
        assertEquals("Alice", toPlain("{\"score\":{\"name\":\"Alice\"}}"));
        assertEquals(":kills", toPlain("{\"score\":{\"objective\":\"kills\"}}"));
        assertEquals("@p", toPlain("{\"selector\":\"@p\"}"));
        assertEquals("key.inventory", toPlain("{\"keybind\":\"key.inventory\"}"));
        assertEquals("CustomName", toPlain("{\"nbt\":\"CustomName\"}"));
    }

    @Test
    void testDepthAndPriority() {
        String deep = "{\"text\":\"a\",\"extra\":[{\"text\":\"b\",\"extra\":[{\"text\":\"c\"}]}]}";
        assertEquals("abc", MinecraftChatComponentUtils.toPlainText(toChat(deep), 64));
        assertEquals("", MinecraftChatComponentUtils.toPlainText(toChat(deep), -1));
        assertEquals("a", MinecraftChatComponentUtils.toPlainText(toChat(deep), 0));

        assertEquals("T", toPlain("{\"text\":\"T\",\"translate\":\"K\"}"));
        assertEquals("K", toPlain("{\"translate\":\"K\",\"score\":{\"name\":\"N\",\"objective\":\"O\"}}"));
        assertEquals("N:O", toPlain("{\"score\":{\"name\":\"N\",\"objective\":\"O\"},\"selector\":\"@p\"}"));
        assertEquals("@a", toPlain("{\"selector\":\"@a\",\"keybind\":\"key.inventory\"}"));
        assertEquals("key.jump", toPlain("{\"keybind\":\"key.jump\",\"nbt\":\"CustomName\"}"));
    }

    @Test
    void testIntegration() {
        assertEquals("<Alice> Hello world", toPlain("{\"translate\":\"chat.type.text\",\"with\":["
                + "{\"text\":\"Alice\",\"color\":\"yellow\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/msg Alice \"}},"
                + "{\"text\":\"Hello world\",\"color\":\"white\"}],"
                + "\"fallback\":\"<Alice> Hello world\"}"));
        assertEquals("3 - Kills", toPlain("{\"score\":{\"name\":\"§aSteve\",\"objective\":\"§bkills\",\"value\":3},"
                + "\"extra\":[{\"text\":\" §7- \"},{\"text\":\"§cKills\"}]}"));
    }
}
