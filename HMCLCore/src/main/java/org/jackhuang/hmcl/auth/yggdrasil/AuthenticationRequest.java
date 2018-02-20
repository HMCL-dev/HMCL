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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class AuthenticationRequest {

    /**
     * The user name of Minecraft account.
     */
    private final String username;

    /**
     * The password of Minecraft account.
     */
    private final String password;

    /**
     * The client token of this game.
     */
    private final String clientToken;

    private final Map<String, Object> agent = Lang.mapOf(
            new Pair<>("name", "minecraft"),
            new Pair<>("version", 1));

    private final boolean requestUser = true;

    public AuthenticationRequest(String username, String password, String clientToken) {
        this.username = username;
        this.password = password;
        this.clientToken = clientToken;
    }

    public String getUsername() {
        return username;
    }

    public String getClientToken() {
        return clientToken;
    }

    public Map<String, Object> getAgent() {
        return agent;
    }

    public boolean isRequestUser() {
        return requestUser;
    }

}
