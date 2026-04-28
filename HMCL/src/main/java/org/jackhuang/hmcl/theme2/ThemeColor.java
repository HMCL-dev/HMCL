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
package org.jackhuang.hmcl.theme2;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// @author Glavo
public sealed interface ThemeColor {

    Preset DEFAULT = new Preset("default", Color.web("#5C6BC0"));

    List<Preset> PRESETS = List.of(
            DEFAULT,
            new Preset("blue", Color.web("#5C6BC0")),
            new Preset("darker_blue", Color.web("#283593")),
            new Preset("green", Color.web("#43A047")),
            new Preset("orange", Color.web("#E67E22")),
            new Preset("purple", Color.web("#9C27B0")),
            new Preset("red", Color.web("#B71C1C"))
    );

    List<ThemeColor> BUILTIN = Lang.merge(PRESETS, List.of(
            FollowSystem.INSTANCE,
            FollowBackground.INSTANCE
    ));

    static @Nullable ThemeColor of(String name) {
        if (name == null)
            return null;
        if (!name.startsWith("#")) {
            for (ThemeColor builtin : BUILTIN) {
                if (name.equalsIgnoreCase(builtin.name()))
                    return builtin;
            }
        }

        try {
            return new Custom(Color.web(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static ThemeColor fromJson(JsonElement json) throws JsonParseException {
        if (json instanceof JsonPrimitive primitive) {
            return ThemeColor.of(primitive.getAsString());
        }

        throw new JsonParseException("Invalid JSON element for ThemeColor: " + json);
    }

    String name();

    record Preset(String name, Color color) implements ThemeColor {
    }

    final class FollowSystem implements ThemeColor {
        public static FollowSystem INSTANCE = new FollowSystem();

        private FollowSystem() {
        }

        @Override
        public String name() {
            return "follow_system";
        }
    }

    final class FollowBackground implements ThemeColor {
        public static FollowBackground INSTANCE = new FollowBackground();

        private FollowBackground() {
        }

        @Override
        public String name() {
            return "follow_background";
        }
    }

    record Custom(Color color) implements ThemeColor {
        @Override
        public String name() {
            return FXUtils.getColorDisplayName(color);
        }
    }

}
