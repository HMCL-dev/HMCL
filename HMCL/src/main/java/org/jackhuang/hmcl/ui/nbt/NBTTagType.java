package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.tag.builtin.Tag;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
