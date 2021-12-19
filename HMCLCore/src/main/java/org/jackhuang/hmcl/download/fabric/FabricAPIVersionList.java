/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download.fabric;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.util.Lang;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.wrap;

public class FabricAPIVersionList extends VersionList<FabricAPIRemoteVersion> {

    private final DownloadProvider downloadProvider;

    public FabricAPIVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        return CompletableFuture.runAsync(wrap(() -> {
            for (RemoteMod.Version modVersion : Lang.toIterable(CurseForgeRemoteModRepository.MODS.getRemoteVersionsById("306612"))) {
                for (String gameVersion : modVersion.getGameVersions()) {
                    versions.put(gameVersion, new FabricAPIRemoteVersion(gameVersion, modVersion.getName(), modVersion.getName(), modVersion.getDatePublished(),
                            Collections.singletonList(modVersion.getFile().getUrl())));
                }
            }
        }));
    }

    @Override
    protected Collection<FabricAPIRemoteVersion> getVersionsImpl(String gameVersion) {
        Matcher matcher = GAME_VERSION_PATTERN.matcher(gameVersion);
        if (matcher.find()) {
            return super.getVersionsImpl(String.format("%s.%s", matcher.group("major"), matcher.group("minor")));
        }
        return super.getVersionsImpl(gameVersion);
    }

    private static final Pattern FABRIC_VERSION_PATTERN = Pattern.compile("^(?<version>[0-9.]+)\\+(build\\.(?<build>\\d+)-)?(?<mcversion>[0-9.]+)$");
    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("^(?<major>[0-9]+)\\.(?<minor>[0-9]+)");
}
