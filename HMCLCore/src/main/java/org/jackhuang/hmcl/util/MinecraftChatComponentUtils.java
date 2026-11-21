// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import com.google.gson.*;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class MinecraftChatComponentUtils {

    private static final Pattern FORMATTING_CODE = Pattern.compile("§.?");
    private static final int MAX_DEPTH = 64;

    private MinecraftChatComponentUtils() {
    }

    public static String toPlainText(@NotNull JsonElement chat) {
        return toPlainText(chat, MAX_DEPTH);
    }

    public static String toPlainText(@NotNull JsonElement chat, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        append(chat, sb, maxDepth);
        return FORMATTING_CODE.matcher(sb).replaceAll("");
    }

    private static void append(@NotNull JsonElement element, @NotNull StringBuilder sb, int depth) {
        if (element instanceof JsonNull || depth < 0) {
            return;
        }

        if (element instanceof JsonPrimitive primitive) {
            sb.append(primitive.getAsString());
        } else if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                append(child, sb, depth - 1);
            }
        } else if (element instanceof JsonObject object) {
            appendObject(object, sb, depth);
        }
    }

    private static void appendObject(@NotNull JsonObject object, @NotNull StringBuilder sb, int depth) {
        if (object.get("text") instanceof JsonPrimitive p) {
            sb.append(p.getAsString());
        } else if (object.get("translate") instanceof JsonPrimitive) {
            appendTranslate(object, sb, depth);
        } else if (object.get("score") instanceof JsonObject score) {
            appendScore(score, sb);
        } else if (object.get("selector") instanceof JsonPrimitive p) {
            // impossible.
            sb.append(p.getAsString());
        } else if (object.get("keybind") instanceof JsonPrimitive p) {
            // impossible.
            sb.append(p.getAsString());
        } else if (object.get("nbt") instanceof JsonPrimitive p) {
            // impossible.
            sb.append(p.getAsString());
        }

        if (object.get("extra") instanceof JsonArray extra) {
            append(extra, sb, depth - 1);
        }
        if (object.get("rawtext") instanceof JsonArray rawtext) {
            append(rawtext, sb, depth - 1);
        }
    }

    private static void appendTranslate(@NotNull JsonObject object, @NotNull StringBuilder sb, int depth) {
        JsonElement fallback = object.get("fallback");
        if (fallback != null && !fallback.isJsonNull()) {
            append(fallback, sb, depth - 1);
            return;
        }

        if (object.get("translate") instanceof JsonPrimitive key) {
            sb.append(key.getAsString());
        }
        if (object.get("with") instanceof JsonArray with) {
            append(with, sb, depth - 1);
        }
    }

    private static void appendScore(@NotNull JsonObject score, @NotNull StringBuilder sb) {
        JsonElement value = score.get("value");
        if (value instanceof JsonPrimitive primitive) {
            sb.append(primitive.getAsString());
            return;
        }
        if (value instanceof JsonObject valueObject
                && valueObject.get("value") instanceof JsonPrimitive inner) {
            sb.append(inner.getAsString());
            return;
        }

        if (score.get("name") instanceof JsonPrimitive name) {
            sb.append(name.getAsString());
        }
        if (score.get("objective") instanceof JsonPrimitive objective) {
            sb.append(':').append(objective.getAsString());
        }
    }
}
