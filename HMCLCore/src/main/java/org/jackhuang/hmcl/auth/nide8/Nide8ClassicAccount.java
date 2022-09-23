/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.nide8;

import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthenticationException;

public abstract class Nide8ClassicAccount extends Account {

    protected final String serverID;

    public Nide8ClassicAccount(String serverID) {
        this.serverID = serverID;
    }

    public String getServerID() {
        return serverID;
    }

    /**
     * Login with specified password.
     * <p>
     * When credentials expired, the auth server will ask you to login with password to refresh
     * credentials.
     */
    public abstract Nide8AuthInfo logInWithPassword(String password) throws AuthenticationException;
}
