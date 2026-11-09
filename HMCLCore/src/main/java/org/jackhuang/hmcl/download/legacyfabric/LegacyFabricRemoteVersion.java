// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.legacyfabric;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.*;

@NotNullByDefault
public final class LegacyFabricRemoteVersion extends ComponentRemoteVersion {

    public static final WebURL LOADER_META_URL = WebURL.parse("https://meta.legacyfabric.net/v2/versions/loader");
    public static final WebURL GAME_META_URL = WebURL.parse("https://meta.legacyfabric.net/v2/versions/game");

    /// Constructor.
    ///
    /// @param gameVersion the Minecraft version that this remote version suits.
    /// @param selfVersion the version string of the remote version.
    /// @param urls        the installer or universal jar original URL.
    public LegacyFabricRemoteVersion(GameVersionNumber gameVersion, String selfVersion, List<String> urls) {
        super(GameComponentType.LEGACY_FABRIC, gameVersion, selfVersion, null, Type.UNCATEGORIZED, urls);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new LegacyFabricInstallTask(dependencyManager, baseManifest, this);
    }

}
