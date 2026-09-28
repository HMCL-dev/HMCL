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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class MinecraftChatComponentUtils {
    private MinecraftChatComponentUtils() {

    }

    public static String toPlainStringFromChatComponent(JsonElement chat) {
        if (chat == null || chat.isJsonNull()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        extractText(chat, sb);
        return sb.toString().replaceAll("§.?", "");
    }

    private static void extractText(JsonElement chat, StringBuilder sb) {
        if (chat == null || chat.isJsonNull()) {
            return;
        }

        if (chat.isJsonPrimitive()) {
            JsonPrimitive prim = chat.getAsJsonPrimitive();
            if (prim.isString()) {
                sb.append(prim.getAsString());
            } else if (prim.isBoolean()) {
                sb.append(prim.getAsBoolean());
            } else {
                sb.append(prim.getAsNumber().toString());
            }
            return;
        }

        if (chat.isJsonArray()) {
            for (JsonElement child : chat.getAsJsonArray()) {
                extractText(child, sb);
            }
            return;
        }

        if (chat.isJsonObject()) {
            JsonObject obj = chat.getAsJsonObject();

            if (obj.has("text")) {
                JsonElement textEl = obj.get("text");
                if (textEl.isJsonPrimitive()) {
                    sb.append(textEl.getAsString());
                }
            }

            if (obj.has("translate")) {
                sb.append(obj.get("translate").getAsString());
                if (obj.has("with")) {
                    JsonArray withArr = obj.getAsJsonArray("with");
                    for (JsonElement w : withArr) {
                        extractText(w, sb);
                    }
                }
            }

            if (obj.has("score")) {
                JsonObject scoreObj = obj.getAsJsonObject("score");
                if (scoreObj.has("name")) sb.append(scoreObj.get("name").getAsString());
                if (scoreObj.has("objective")) sb.append(":").append(scoreObj.get("objective").getAsString());
            }

            if (obj.has("selector")) {
                sb.append(obj.get("selector").getAsString());
            }

            if (obj.has("keybind")) {
                sb.append(obj.get("keybind").getAsString());
            }

            if (obj.has("nbt")) {
                sb.append(obj.get("nbt").getAsString());
            }

            if (obj.has("rawtext")) {
                JsonArray rawArr = obj.getAsJsonArray("rawtext");
                for (JsonElement child : rawArr) {
                    extractText(child, sb);
                }
            }

            if (obj.has("extra")) {
                JsonArray extraArr = obj.getAsJsonArray("extra");
                for (JsonElement child : extraArr) {
                    extractText(child, sb);
                }
            }
        }
    }
}
