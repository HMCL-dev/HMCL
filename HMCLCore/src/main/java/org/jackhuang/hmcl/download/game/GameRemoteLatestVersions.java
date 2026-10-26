// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonSerializable;

/**
 * @author huangyuhui
 */
@Immutable
@JsonSerializable
public record GameRemoteLatestVersions(
        @SerializedName("snapshot") String snapshot,
        @SerializedName("release") String release) {
}
