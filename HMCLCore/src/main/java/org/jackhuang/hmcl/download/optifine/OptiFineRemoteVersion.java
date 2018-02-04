/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.optifine;

import org.jackhuang.hmcl.download.RemoteVersion;

import java.util.function.Supplier;

public class OptiFineRemoteVersion extends RemoteVersion<Void> {
    private final Supplier<String> url;

    public OptiFineRemoteVersion(String gameVersion, String selfVersion, Supplier<String> url, Void tag) {
        super(gameVersion, selfVersion, "", tag);

        this.url = url;
    }

    @Override
    public String getUrl() {
        return url.get();
    }
}
