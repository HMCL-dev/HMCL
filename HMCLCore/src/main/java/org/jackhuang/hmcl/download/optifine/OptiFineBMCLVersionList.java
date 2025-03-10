/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.optifine;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

/**
 * @author huangyuhui
 */
public final class OptiFineBMCLVersionList extends VersionList<OptiFineRemoteVersion> {
    private final String apiRoot;

    /**
     * @param apiRoot API Root of BMCLAPI implementations
     */
    public OptiFineBMCLVersionList(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    private String fromLookupVersion(String version) {
        switch (version) {
            case "1.8.0":
                return "1.8";
            case "1.9.0":
                return "1.9";
            default:
                return version;
        }
    }

    private String toLookupVersion(String version) {
        switch (version) {
            case "1.8":
                return "1.8.0";
            case "1.9":
                return "1.9.0";
            default:
                return version;
        }
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        return HttpRequest.GET(apiRoot + "/optifine/versionlist").getJsonAsync(listTypeOf(OptiFineVersion.class)).thenAcceptAsync(root -> {
            lock.writeLock().lock();

            try {
                versions.clear();
                Set<String> duplicates = new HashSet<>();
                for (OptiFineVersion element : root) {
                    String version = element.type + "_" + element.patch;
                    String mirror = apiRoot + "/optifine/" + toLookupVersion(element.gameVersion) + "/" + element.type + "/" + element.patch;
                    if (!duplicates.add(mirror))
                        continue;

                    boolean isPre = element.patch != null && (element.patch.startsWith("pre") || element.patch.startsWith("alpha"));

                    if (StringUtils.isBlank(element.gameVersion))
                        continue;

                    String gameVersion = VersionNumber.normalize(fromLookupVersion(element.gameVersion));
                    versions.put(gameVersion, new OptiFineRemoteVersion(gameVersion, version, Collections.singletonList(mirror), isPre));
                }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * @author huangyuhui
     */
    private static final class OptiFineVersion {
        @SerializedName("type")
        private final String type;

        @SerializedName("patch")
        private final String patch;

        @SerializedName("mcversion")
        private final String gameVersion;

        public OptiFineVersion(String type, String patch, String gameVersion) {
            this.type = type;
            this.patch = patch;
            this.gameVersion = gameVersion;
        }
    }
}
