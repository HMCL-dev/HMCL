// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;

import java.time.Instant;

/**
 *
 * @author huangyuhui
 */
@JsonSerializable
public record GameRemoteVersionInfo(
        @SerializedName("id") String gameVersion,
        @SerializedName("time") Instant time,
        @SerializedName("releaseTime") Instant releaseTime,
        @SerializedName("type") ReleaseType type,
        @SerializedName("url") String url) implements Validation {

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(gameVersion))
            throw new JsonParseException("GameRemoteVersion id cannot be blank");
        if (StringUtils.isBlank(url))
            throw new JsonParseException("GameRemoteVersion url cannot be blank");
    }
}
