package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.gson.JsonSerializable;

@JsonSerializable
public record ResourcepackMeta(Pack pack) {

    @JsonSerializable
    public record Pack(String description) {
    }
}
