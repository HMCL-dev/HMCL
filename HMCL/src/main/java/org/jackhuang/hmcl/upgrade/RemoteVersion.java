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
package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public record RemoteVersion(UpdateChannel channel, String version, String url, Type type, IntegrityCheck integrityCheck,
                            boolean preview, boolean force, @Nullable Path downloadedFile) {

    public static RemoteVersion fetch(UpdateChannel channel, boolean preview, String url) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(url), JsonObject.class);
            String version = Optional.ofNullable(response.get("version")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("version is missing"));
            String jarUrl = Optional.ofNullable(response.get("jar")).map(JsonElement::getAsString).orElse(null);
            String jarHash = Optional.ofNullable(response.get("jarsha1")).map(JsonElement::getAsString).orElse(null);
            boolean force = Optional.ofNullable(response.get("force")).map(JsonElement::getAsBoolean).orElse(false);
            if (jarUrl != null && jarHash != null) {
                return new RemoteVersion(channel, version, jarUrl, Type.JAR, new IntegrityCheck("SHA-1", jarHash), preview, force, null);
            } else {
                throw new IOException("No download url is available");
            }
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    @Override
    public @NotNull String toString() {
        return "[" + version + " from " + url + "]";
    }

    public RemoteVersion tryDownload() {
        if (downloadedFile() != null) return this;
        Path downloaded;
        try {
            downloaded = Files.createTempFile("hmcl-update-", ".jar");
        } catch (IOException e) {
            LOG.warning("Failed to create temp file", e);
            return this;
        }

        var executor = new HMCLDownloadTask(this, downloaded).executor();
        if (executor.test()) {
            return new RemoteVersion(channel(), version(), url(), type(), integrityCheck(), preview(), force(), downloaded);
        } else {
            LOG.warning("Failed to download update for " + this, executor.getException());
            return this;
        }
    }

    public enum Type {
        JAR
    }
}
