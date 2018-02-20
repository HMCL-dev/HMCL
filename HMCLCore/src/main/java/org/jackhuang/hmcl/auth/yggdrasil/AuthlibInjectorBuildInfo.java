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
package org.jackhuang.hmcl.auth.yggdrasil;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.IOException;

@Immutable
public final class AuthlibInjectorBuildInfo {

    private final int buildNumber;
    private final String url;

    public AuthlibInjectorBuildInfo() {
        this(0, "");
    }

    public AuthlibInjectorBuildInfo(int buildNumber, String url) {
        this.buildNumber = buildNumber;
        this.url = url;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getUrl() {
        return url;
    }

    public static AuthlibInjectorBuildInfo requestBuildInfo() throws IOException, JsonParseException {
        return requestBuildInfo(UPDATE_URL);
    }

    public static AuthlibInjectorBuildInfo requestBuildInfo(String updateUrl) throws IOException, JsonParseException {
        return JsonUtils.fromNonNullJson(NetworkUtils.doGet(NetworkUtils.toURL(updateUrl)), AuthlibInjectorBuildInfo.class);
    }

    public static final String UPDATE_URL = "https://authlib-injector.to2mbn.org/api/buildInfo";
}
