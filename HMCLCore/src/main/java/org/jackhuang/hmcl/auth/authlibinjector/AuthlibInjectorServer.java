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
package org.jackhuang.hmcl.auth.authlibinjector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.IOException;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class AuthlibInjectorServer {

    public static AuthlibInjectorServer fetchServerInfo(String url) throws IOException {
        try {
            JsonObject response = JsonUtils.fromNonNullJson(NetworkUtils.doGet(NetworkUtils.toURL(url)), JsonObject.class);
            String name = extractServerName(response).orElse(url);
            return new AuthlibInjectorServer(url, name);
        } catch (JsonParseException e) {
            throw new IOException("Malformed response", e);
        }
    }

    private static Optional<String> extractServerName(JsonObject response){
        return tryCast(response.get("meta"), JsonObject.class)
                .flatMap(meta -> tryCast(meta.get("serverName"), JsonPrimitive.class).map(JsonPrimitive::getAsString));
    }

    private String url;
    private String name;

    public AuthlibInjectorServer(String url, String name) {
        this.url = url;
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return url + " (" + name + ")";
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof AuthlibInjectorServer))
            return false;
        AuthlibInjectorServer another = (AuthlibInjectorServer) obj;
        return this.url.equals(another.url);
    }
}
