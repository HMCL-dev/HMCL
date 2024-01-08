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
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.Lang.wrap;

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
    public CompletableFuture<?> refreshAsync(String gameVersion) {
        return CompletableFuture.completedFuture((Void) null)
                .thenApplyAsync(wrap(unused -> HttpRequest.GET(apiRoot + "/neoforge/list/" + gameVersion).<List<NeoForgeVersion>>getJson(new TypeToken<List<NeoForgeVersion>>() {
                }.getType())))
                .thenAcceptAsync(neoForgeVersions -> {
                    lock.writeLock().lock();

                    try {
                        versions.clear(gameVersion);
                        for (NeoForgeVersion neoForgeVersion : neoForgeVersions) {
                            String nf = StringUtils.removePrefix(
                                    neoForgeVersion.version,
                                    "1.20.1".equals(gameVersion) ? "1.20.1-forge-" : "neoforge-" // Som of the version numbers for 1.20.1 are like forge.
                            );
                            versions.put(gameVersion, new NeoForgeRemoteVersion(
                                    neoForgeVersion.mcVersion,
                                    nf,
                                    Lang.immutableListOf(
                                            apiRoot + "/neoforge/version/" + neoForgeVersion.version + "/download/installer.jar"
                                    )
                            ));
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Override
    public Optional<NeoForgeRemoteVersion> getVersion(String gameVersion, String remoteVersion) {
        remoteVersion = StringUtils.substringAfter(remoteVersion, "-", remoteVersion);
        return super.getVersion(gameVersion, remoteVersion);
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

        public String getRawVersion() {
            return this.rawVersion;
        }

        public String getVersion() {
            return this.version;
        }

        public String getMcVersion() {
            return this.mcVersion;
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
