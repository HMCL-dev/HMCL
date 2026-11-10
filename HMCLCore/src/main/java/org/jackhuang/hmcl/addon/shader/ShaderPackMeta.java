// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.shader;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.Nullable;

@JsonSerializable
public record ShaderPackMeta(@Nullable String name, @Nullable String version,
                             @SerializedName(value = "shaderDescription") @Nullable String description,
                             @SerializedName(value = "api") @Nullable String apertureApiVersion
) {
    public static final ShaderPackMeta EMPTY = new ShaderPackMeta(null, null, null, null);

}
