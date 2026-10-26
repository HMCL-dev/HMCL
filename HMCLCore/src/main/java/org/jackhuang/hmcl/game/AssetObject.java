// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;

import java.io.IOException;
import java.nio.file.Path;

/// @author huangyuhui
@JsonSerializable
public record AssetObject(String hash, long size) {
    public AssetObject {
        if (StringUtils.isBlank(hash) || hash.length() < 2) {
            throw new IllegalArgumentException("Invalid asset hash: " + hash);
        }
    }

    public String getLocation() {
        return hash.substring(0, 2) + "/" + hash;
    }

    public boolean validateChecksum(Path file, boolean defaultValue) throws IOException {
        if (hash == null) return defaultValue;
        return DigestUtils.digestToString("SHA-1", file).equalsIgnoreCase(hash);
    }
}
