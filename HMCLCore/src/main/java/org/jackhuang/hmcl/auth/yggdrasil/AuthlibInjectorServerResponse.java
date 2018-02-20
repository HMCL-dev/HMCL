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

public class AuthlibInjectorServerResponse {

    private final Meta meta;

    public AuthlibInjectorServerResponse() {
        this(new Meta());
    }

    public AuthlibInjectorServerResponse(Meta meta) {
        this.meta = meta;
    }

    public Meta getMeta() {
        return meta;
    }

    public static class Meta {
        private final String serverName;

        public Meta() {
            this("");
        }

        public Meta(String serverName) {
            this.serverName = serverName;
        }

        public String getServerName() {
            return serverName;
        }
    }
}
