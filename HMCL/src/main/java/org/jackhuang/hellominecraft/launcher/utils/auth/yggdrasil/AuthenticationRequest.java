/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import java.util.HashMap;

public class AuthenticationRequest {

    public HashMap<String, Object> agent;
    public String username, password, clientToken;
    public boolean requestUser = true;

    public AuthenticationRequest(String username, String password, String clientToken) {
        agent = new HashMap<>();
        agent.put("name", "Minecraft");
        agent.put("version", 1);

        this.username = username;
        this.password = password;
        this.clientToken = clientToken;
    }
}
