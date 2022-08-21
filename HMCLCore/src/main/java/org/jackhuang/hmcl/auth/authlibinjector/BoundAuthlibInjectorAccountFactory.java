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
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;

import java.util.Map;
import java.util.Objects;

public class BoundAuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    private final AuthlibInjectorArtifactProvider downloader;
    private final AuthlibInjectorServer server;

    /**
     * @param server Authlib-Injector Server
     */
    public BoundAuthlibInjectorAccountFactory(AuthlibInjectorArtifactProvider downloader, AuthlibInjectorServer server) {
        this.downloader = downloader;
        this.server = server;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    public AuthlibInjectorServer getServer() {
        return server;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        return new AuthlibInjectorAccount(server, downloader, username, password, selector);
    }

    @Override
    public AuthlibInjectorAccount fromStorage(Map<Object, Object> storage) {
        return AuthlibInjectorAccountFactory.fromStorage(storage, downloader, server);
    }
}
