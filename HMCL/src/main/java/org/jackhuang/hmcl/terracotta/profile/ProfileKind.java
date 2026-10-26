// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.terracotta.profile;

import com.google.gson.annotations.SerializedName;

public enum ProfileKind {
    @SerializedName("HOST")
    HOST,
    @SerializedName("LOCAL")
    LOCAL,
    @SerializedName("GUEST")
    GUEST
}
