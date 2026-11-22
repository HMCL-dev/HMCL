// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
@JsonSerializable
public record GameRemoteVersions(
        @SerializedName("versions") List<GameRemoteVersionInfo> versions,
        @SerializedName("latest") GameRemoteLatestVersions latest) implements Validation {

    @Override
    public void validate() throws JsonParseException {
        if (versions == null)
            throw new JsonParseException("GameRemoteVersions.versions cannot be null");
    }
}
