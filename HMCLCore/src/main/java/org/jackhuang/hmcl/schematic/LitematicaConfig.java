// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.schematic;

import com.google.gson.annotations.SerializedName;

public record LitematicaConfig(@SerializedName("Generic") Generic generic) {
    public record Generic(boolean customSchematicBaseDirectoryEnabled, String customSchematicBaseDirectory) {
    }
}
