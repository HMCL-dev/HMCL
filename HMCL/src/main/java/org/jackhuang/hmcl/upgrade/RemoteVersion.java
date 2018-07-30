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

import java.io.IOException;
import java.util.Optional;

import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class RemoteVersion {

    public static RemoteVersion fetch(String url) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(NetworkUtils.toURL(url)), JsonObject.class);
            String version = Optional.ofNullable(response.get("version")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("version is missing"));
            String downloadUrl = Optional.ofNullable(response.get("jar")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("jar is missing"));
            String sha1 = Optional.ofNullable(response.get("jarsha1")).map(JsonElement::getAsString).orElseThrow(() -> new IOException("jarsha1 is missing"));
            return new RemoteVersion(version, downloadUrl, new IntegrityCheck("SHA-1", sha1));
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private String version;
    private String url;
    private IntegrityCheck integrityCheck;

    public RemoteVersion(String version, String url, IntegrityCheck integrityCheck) {
        this.version = version;
        this.url = url;
        this.integrityCheck = integrityCheck;
    }

    public String getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public IntegrityCheck getIntegrityCheck() {
        return integrityCheck;
    }

    @Override
    public String toString() {
        return "[" + version + " from " + url + "]";
    }
}
