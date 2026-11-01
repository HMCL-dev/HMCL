// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.regex.Pattern;

public final class MinecraftChatComponentUtils {

    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("§.?");

    private MinecraftChatComponentUtils() {
    }

    public static String toPlainStringFromChatComponent(JsonElement chat) {
        if (chat == null || chat.isJsonNull()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        extractText(chat, sb);
        return FORMATTING_CODE_PATTERN.matcher(sb).replaceAll("");
    }

    private static void extractText(JsonPrimitive prim, StringBuilder sb) {
        if (prim == null || prim.isJsonNull()) {
            return;
        }
        sb.append(prim.getAsString());
    }

    private static void extractText(JsonArray array, StringBuilder sb) {
        if (array == null) {
            return;
        }
        for (JsonElement child : array) {
            extractText(child, sb);
        }
    }

    private static boolean hasJsonArray(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element instanceof JsonArray;
    }

    private static boolean hasJsonObject(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element instanceof JsonObject;
    }

    private static boolean hasJsonPrimitive(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element instanceof JsonPrimitive;
    }

    private static void extractText(JsonElement chat, StringBuilder sb) {
        if (chat == null || chat.isJsonNull()) {
            return;
        }

        if (chat.isJsonPrimitive()) {
            extractText(chat.getAsJsonPrimitive(), sb);
            return;
        }

        if (chat.isJsonArray()) {
            extractText(chat.getAsJsonArray(), sb);
            return;
        }

        if (chat.isJsonObject()) {
            JsonObject obj = chat.getAsJsonObject();

            if (hasJsonPrimitive(obj, "text")) {
                extractText(obj.getAsJsonPrimitive("text"), sb);
            } else if (hasJsonPrimitive(obj, "translate")) {
                extractTranslate(obj, sb);
            } else if (hasJsonObject(obj, "score")) {
                extractScore(obj.getAsJsonObject("score"), sb);
            } else if (hasJsonPrimitive(obj, "selector")) {
                extractText(obj.getAsJsonPrimitive("selector"), sb);
            } else if (hasJsonPrimitive(obj, "keybind")) {
                extractText(obj.getAsJsonPrimitive("keybind"), sb);
            } else if (hasJsonPrimitive(obj, "nbt")) {
                extractText(obj.getAsJsonPrimitive("nbt"), sb);
            }

            if (hasJsonArray(obj, "extra")) {
                extractText(obj.getAsJsonArray("extra"), sb);
            }

            if (hasJsonArray(obj, "rawtext")) {
                extractText(obj.getAsJsonArray("rawtext"), sb);
            }
        }
    }

    private static void extractTranslate(JsonObject obj, StringBuilder sb) {
        if (hasJsonPrimitive(obj, "fallback")) {
            extractText(obj.getAsJsonPrimitive("fallback"), sb);
        } else {
            extractText(obj.getAsJsonPrimitive("translate"), sb);
        }

        if (hasJsonArray(obj, "with")) {
            extractText(obj.getAsJsonArray("with"), sb);
        }
    }

    private static void extractScore(JsonObject scoreObj, StringBuilder sb) {
        if (hasJsonPrimitive(scoreObj, "name")) {
            extractText(scoreObj.getAsJsonPrimitive("name"), sb);
        }

        if (hasJsonPrimitive(scoreObj, "objective")) {
            sb.append(':').append(scoreObj.get("objective").getAsString());
        }
    }
}
