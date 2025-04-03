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
package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.tag.builtin.Tag;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Glavo
 */
public enum NBTTagType {
    BYTE, SHORT, INT, LONG, FLOAT, DOUBLE,
    BYTE_ARRAY, INT_ARRAY, LONG_ARRAY,
    STRING,
    LIST, COMPOUND;

    private static final Map<String, NBTTagType> lookupTable = new HashMap<>();

    static {
        for (NBTTagType type : values()) {
            lookupTable.put(type.getTagClassName(), type);
        }
    }

    public static NBTTagType typeOf(Tag tag) {
        NBTTagType type = lookupTable.get(tag.getClass().getSimpleName());
        if (type == null) {
            throw new IllegalArgumentException("Unknown tag: " + type);
        }
        return type;
    }

    private final String iconUrl;
    private final String tagClassName;

    NBTTagType() {
        String tagName;
        String className;

        int idx = name().indexOf('_');
        if (idx < 0) {
            tagName = name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
            className = tagName + "Tag";
        } else {
            tagName = name().charAt(0) + name().substring(1, idx + 1).toLowerCase(Locale.ROOT)
                    + name().charAt(idx + 1) + name().substring(idx + 2).toLowerCase(Locale.ROOT);
            className = tagName.substring(0, idx) + tagName.substring(idx + 1) + "Tag";
        }

        this.iconUrl = "/assets/img/nbt/TAG_" + tagName + ".png";
        this.tagClassName = className;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getTagClassName() {
        return tagClassName;
    }
}
