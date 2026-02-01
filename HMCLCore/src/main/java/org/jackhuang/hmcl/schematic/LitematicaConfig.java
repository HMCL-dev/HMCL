package org.jackhuang.hmcl.schematic;

import com.google.gson.annotations.SerializedName;

public record LitematicaConfig(@SerializedName("Generic") Generic generic) {
    public record Generic(boolean customSchematicBaseDirectoryEnabled, String customSchematicBaseDirectory) {
    }
}
