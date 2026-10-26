// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.forge;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// @author huangyuhui
@NotNullByDefault
@JsonSerializable
public record ForgeInstallProfile(@SerializedName("install") ForgeInstall install,
                                  @SerializedName("versionInfo") GameInstanceManifest versionInfo) {

    public ForgeInstallProfile {
        Objects.requireNonNull(install, "install");
        Objects.requireNonNull(versionInfo, "versionInfo");
    }

    @Override
    public ForgeInstall install() {
        return install;
    }

    @Override
    public GameInstanceManifest versionInfo() {
        return versionInfo;
    }
}
