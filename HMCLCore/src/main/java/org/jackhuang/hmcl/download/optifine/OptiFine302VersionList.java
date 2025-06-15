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
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author huangyuhui
 */
public final class OptiFine302VersionList extends VersionList<OptiFineRemoteVersion> {
    private final String versionListURL;

    public OptiFine302VersionList(String versionListURL) {
        this.versionListURL = versionListURL;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        return HttpRequest.GET(versionListURL).getJsonAsync(TypeToken.get(OptiFine302VersionList.VersionList.class)).thenAcceptAsync(root -> {
            lock.writeLock().lock();

            try {
                versions.clear();
                for (OptiFineVersion element : root.versions) {
                    String gameVersion = VersionNumber.normalize(element.gameVersion);
                    versions.put(gameVersion, new OptiFineRemoteVersion(
                            gameVersion, element.version,
                            root.downloadBases.stream().map(u -> u + element.fileName).collect(Collectors.toList()),
                            element.fileName.startsWith("pre")
                    ));
                }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private static final class VersionList {
        @SerializedName("file")
        private final List<OptiFineVersion> versions;

        @SerializedName("download")
        private final List<String> downloadBases;

        public VersionList(List<OptiFineVersion> versions, List<String> downloadBases) {
            this.versions = versions;
            this.downloadBases = downloadBases;
        }
    }

    private static final class OptiFineVersion {
        @SerializedName("name")
        private final String version;
        @SerializedName("filename")
        private final String fileName;
        @SerializedName("mcversion")
        private final String gameVersion;

        public OptiFineVersion(String version, String fileName, String gameVersion) {
            this.version = version;
            this.fileName = fileName;
            this.gameVersion = gameVersion;
        }
    }
}
