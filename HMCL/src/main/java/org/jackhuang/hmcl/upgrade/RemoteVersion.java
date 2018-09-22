/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.upgrade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.IOException;
import java.util.Optional;

public class RemoteVersion {

    public static RemoteVersion fetch(String url) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(NetworkUtils.toURL(url)), JsonObject.class);
            String version = Optional.ofNullable(response.get("version")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("version is missing"));
            String jarUrl = Optional.ofNullable(response.get("jar")).map(JsonElement::getAsString).orElse(null);
            String jarHash = Optional.ofNullable(response.get("jarsha1")).map(JsonElement::getAsString).orElse(null);
            String packXZUrl = Optional.ofNullable(response.get("packxz")).map(JsonElement::getAsString).orElse(null);
            String packXZHash = Optional.ofNullable(response.get("packxzsha1")).map(JsonElement::getAsString).orElse(null);
            if (packXZUrl != null && packXZHash != null) {
                return new RemoteVersion(version, packXZUrl, Type.PACK_XZ, new IntegrityCheck("SHA-1", packXZHash));
            } else if (jarUrl != null && jarHash != null) {
                return new RemoteVersion(version, jarUrl, Type.JAR, new IntegrityCheck("SHA-1", jarHash));
            } else {
                throw new IOException("No download url is available");
            }
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private String version;
    private String url;
    private Type type;
    private IntegrityCheck integrityCheck;

    public RemoteVersion(String version, String url, Type type, IntegrityCheck integrityCheck) {
        this.version = version;
        this.url = url;
        this.type = type;
        this.integrityCheck = integrityCheck;
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

    @Override
    public String toString() {
        return "[" + version + " from " + url + "]";
    }

    public enum Type {
        PACK_XZ,
        JAR
    }
}
