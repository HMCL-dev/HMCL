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
