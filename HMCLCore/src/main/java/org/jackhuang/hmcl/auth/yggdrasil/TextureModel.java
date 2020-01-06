/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.yggdrasil;

import java.util.Map;
import java.util.UUID;

public enum TextureModel {
    STEVE("default"), ALEX("slim");

    public final String modelName;

    TextureModel(String modelName) {
        this.modelName = modelName;
    }

    public static TextureModel detectModelName(Map<String, String> metadata) {
        if (metadata != null && "slim".equals(metadata.get("model"))) {
            return ALEX;
        } else {
            return STEVE;
        }
    }

    public static TextureModel detectUUID(UUID uuid) {
        return (uuid.hashCode() & 1) == 1 ? ALEX : STEVE;
    }
}
