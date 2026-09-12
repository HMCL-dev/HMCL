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
package org.jackhuang.hmcl.schematic;

import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public enum SchematicType {
    LITEMATIC("litematic"),
    NBT_STRUCTURE("nbt"),
    SCHEM("schematic", "schem");

    public static SchematicType getType(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        String ext = FileUtils.getExtension(file);
        for (SchematicType type : values()) {
            if (type.extensions.contains(ext)) return type;
        }
        return null;
    }

    public final List<String> extensions;

    SchematicType(String... ext) {
        this.extensions = List.of(ext);
    }
}
