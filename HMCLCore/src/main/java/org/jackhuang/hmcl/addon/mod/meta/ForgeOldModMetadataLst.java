// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod.meta;

import org.jackhuang.hmcl.util.gson.JsonSerializable;

import java.util.List;

/// @author Glavo
@JsonSerializable
public record ForgeOldModMetadataLst(
        int modListVersion,
        List<ForgeOldModMetadata> modList) {
}
