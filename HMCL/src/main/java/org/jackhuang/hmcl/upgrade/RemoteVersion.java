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

import java.io.IOException;
import java.util.Optional;

public final class RemoteVersion {

    public static RemoteVersion fetch(UpdateChannel channel, boolean preview, String url) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(url), JsonObject.class);
            String version = Optional.ofNullable(response.get("version")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("version is missing"));
            String jarUrl = Optional.ofNullable(response.get("jar")).map(JsonElement::getAsString).orElse(null);
            String jarHash = Optional.ofNullable(response.get("jarsha1")).map(JsonElement::getAsString).orElse(null);
            boolean force = Optional.ofNullable(response.get("force")).map(JsonElement::getAsBoolean).orElse(false);
            if (jarUrl != null && jarHash != null) {
                return new RemoteVersion(channel, version, jarUrl, Type.JAR, new IntegrityCheck("SHA-1", jarHash), preview, force);
            } else {
                throw new IOException("No download url is available");
            }
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private final UpdateChannel channel;
    private final String version;
    private final String url;
    private final Type type;
    private final IntegrityCheck integrityCheck;
    private final boolean preview;
    private final boolean force;

    public RemoteVersion(UpdateChannel channel, String version, String url, Type type, IntegrityCheck integrityCheck, boolean preview, boolean force) {
        this.channel = channel;
        this.version = version;
        this.url = url;
        this.type = type;
        this.integrityCheck = integrityCheck;
        this.preview = preview;
        this.force = force;
    }

    public UpdateChannel getChannel() {
        return channel;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public Type getType() {
        return type;
    }

    public IntegrityCheck getIntegrityCheck() {
        return integrityCheck;
    }

    public boolean isPreview() {
        return preview;
    }

    public boolean isForce() {
        return force;
    }

    @Override
    public String toString() {
        return "[" + version + " from " + url + "]";
    }

    public enum Type {
        JAR
    }
}
