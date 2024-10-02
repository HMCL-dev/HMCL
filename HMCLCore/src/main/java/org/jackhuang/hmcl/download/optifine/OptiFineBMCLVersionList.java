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
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
        return HttpRequest.GET(apiRoot + "/optifine/versionlist").<List<OptiFineVersion>>getJsonAsync(new TypeToken<List<OptiFineVersion>>() {
        }.getType()).thenAcceptAsync(root -> {
            lock.writeLock().lock();

            try {
                versions.clear();
                Set<String> duplicates = new HashSet<>();
                for (OptiFineVersion element : root) {
                    String version = element.getType() + "_" + element.getPatch();
                    String mirror = "https://bmclapi2.bangbang93.com/optifine/" + toLookupVersion(element.getGameVersion()) + "/" + element.getType() + "/" + element.getPatch();
                    if (!duplicates.add(mirror))
                        continue;

                    boolean isPre = element.getPatch() != null && (element.getPatch().startsWith("pre") || element.getPatch().startsWith("alpha"));

                    if (StringUtils.isBlank(element.getGameVersion()))
                        continue;

                    String gameVersion = VersionNumber.normalize(fromLookupVersion(element.getGameVersion()));
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

        @SerializedName("dl")
        private final String downloadLink;

        @SerializedName("ver")
        private final String version;

        @SerializedName("date")
        private final String date;

        @SerializedName("type")
        private final String type;

        @SerializedName("patch")
        private final String patch;

        @SerializedName("mirror")
        private final String mirror;

        @SerializedName("mcversion")
        private final String gameVersion;

        public OptiFineVersion() {
            this(null, null, null, null, null, null, null);
        }

        public OptiFineVersion(String downloadLink, String version, String date, String type, String patch, String mirror, String gameVersion) {
            this.downloadLink = downloadLink;
            this.version = version;
            this.date = date;
            this.type = type;
            this.patch = patch;
            this.mirror = mirror;
            this.gameVersion = gameVersion;
        }

        public String getDownloadLink() {
            return downloadLink;
        }

        public String getVersion() {
            return version;
        }

        public String getDate() {
            return date;
        }

        public String getType() {
            return type;
        }

        public String getPatch() {
            return patch;
        }

        public String getMirror() {
            return mirror;
        }

        public String getGameVersion() {
            return gameVersion;
        }
    }
}
