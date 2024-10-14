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
package org.jackhuang.hmcl.download.neoforge;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.Lang.wrap;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

public final class NeoForgeBMCLVersionList extends VersionList<NeoForgeRemoteVersion> {
    private final String apiRoot;

    /**
     * @param apiRoot API Root of BMCLAPI implementations
     */
    public NeoForgeBMCLVersionList(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public CompletableFuture<?> loadAsync() {
        throw new UnsupportedOperationException("NeoForgeBMCLVersionList does not support loading the entire NeoForge remote version list.");
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        throw new UnsupportedOperationException("NeoForgeBMCLVersionList does not support loading the entire NeoForge remote version list.");
    }

    @Override
    public Optional<NeoForgeRemoteVersion> getVersion(String gameVersion, String remoteVersion) {
        if (gameVersion.equals("1.20.1")) {
            remoteVersion = NeoForgeRemoteVersion.normalize(remoteVersion);
        }
        return super.getVersion(gameVersion, remoteVersion);
    }

    @Override
    public CompletableFuture<?> refreshAsync(String gameVersion) {
        return CompletableFuture.completedFuture((Void) null)
                .thenApplyAsync(wrap(unused -> HttpRequest.GET(apiRoot + "/neoforge/list/" + gameVersion).getJson(listTypeOf(NeoForgeVersion.class))))
                .thenAcceptAsync(neoForgeVersions -> {
                    lock.writeLock().lock();

                    try {
                        versions.clear(gameVersion);
                        for (NeoForgeVersion neoForgeVersion : neoForgeVersions) {
                            versions.put(gameVersion, new NeoForgeRemoteVersion(
                                    neoForgeVersion.mcVersion,
                                    NeoForgeRemoteVersion.normalize(neoForgeVersion.version),
                                    Collections.singletonList(apiRoot + "/neoforge/version/" + neoForgeVersion.version + "/download/installer.jar")
                            ));
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Immutable
    private static final class NeoForgeVersion implements Validation {
        private final String rawVersion;

        private final String version;

        @SerializedName("mcversion")
        private final String mcVersion;

        public NeoForgeVersion(String rawVersion, String version, String mcVersion) {
            this.rawVersion = rawVersion;
            this.version = version;
            this.mcVersion = mcVersion;
        }

        @Override
        public void validate() throws JsonParseException {
            if (this.rawVersion == null) {
                throw new JsonParseException("NeoForgeVersion rawVersion cannot be null.");
            }
            if (this.version == null) {
                throw new JsonParseException("NeoForgeVersion version cannot be null.");
            }
            if (this.mcVersion == null) {
                throw new JsonParseException("NeoForgeVersion mcversion cannot be null.");
            }
        }
    }
}
