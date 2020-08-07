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
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class AuthlibInjectorDownloader implements AuthlibInjectorArtifactProvider {

    private static final String LATEST_BUILD_URL = "https://authlib-injector.yushi.moe/artifact/latest.json";

    private final Path artifactLocation;
    private final Supplier<DownloadProvider> downloadProvider;

    /**
     * The flag will be reset after application restart.
     */
    private boolean updateChecked = false;

    /**
     * @param artifactsDirectory where to save authlib-injector artifacts
     */
    public AuthlibInjectorDownloader(Path artifactsDirectory, Supplier<DownloadProvider> downloadProvider) {
        this.artifactLocation = artifactsDirectory.resolve("authlib-injector.jar");
        this.downloadProvider = downloadProvider;
    }

    @Override
    public AuthlibInjectorArtifactInfo getArtifactInfo() throws IOException {
        synchronized (artifactLocation) {
            Optional<AuthlibInjectorArtifactInfo> local = getLocalArtifact();

            if (!local.isPresent() || !updateChecked) {
                try {
                    update(local);
                    updateChecked = true;
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to download authlib-injector", e);
                    if (!local.isPresent()) {
                        throw e;
                    }
                    LOG.warning("Fallback to use cached artifact: " + local.get());
                }
            }

            return getLocalArtifact().orElseThrow(() -> new IOException("The updated authlib-inejector cannot be recognized"));
        }
    }

    @Override
    public Optional<AuthlibInjectorArtifactInfo> getArtifactInfoImmediately() {
        return getLocalArtifact();
    }

    private void update(Optional<AuthlibInjectorArtifactInfo> local) throws IOException {
        LOG.info("Checking update of authlib-injector");
        AuthlibInjectorVersionInfo latest = getLatestArtifactInfo();

        if (local.isPresent() && local.get().getBuildNumber() >= latest.buildNumber) {
            return;
        }

        try {
            new FileDownloadTask(new URL(downloadProvider.get().injectURL(latest.downloadUrl)), artifactLocation.toFile(),
                    Optional.ofNullable(latest.checksums.get("sha256"))
                            .map(checksum -> new IntegrityCheck("SHA-256", checksum))
                            .orElse(null))
                                    .run();
        } catch (Exception e) {
            throw new IOException("Failed to download authlib-injector", e);
        }

        LOG.info("Updated authlib-injector to " + latest.version);
    }

    private AuthlibInjectorVersionInfo getLatestArtifactInfo() throws IOException {
        try {
            return JsonUtils.fromNonNullJson(
                    NetworkUtils.doGet(
                            new URL(downloadProvider.get().injectURL(LATEST_BUILD_URL))),
                    AuthlibInjectorVersionInfo.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private Optional<AuthlibInjectorArtifactInfo> getLocalArtifact() {
        if (!Files.isRegularFile(artifactLocation)) {
            return Optional.empty();
        }
        try {
            return Optional.of(AuthlibInjectorArtifactInfo.from(artifactLocation));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Bad authlib-injector artifact", e);
            return Optional.empty();
        }
    }

    private static class AuthlibInjectorVersionInfo {
        @SerializedName("build_number")
        public int buildNumber;

        @SerializedName("version")
        public String version;

        @SerializedName("download_url")
        public String downloadUrl;

        @SerializedName("checksums")
        public Map<String, String> checksums;
    }

}
