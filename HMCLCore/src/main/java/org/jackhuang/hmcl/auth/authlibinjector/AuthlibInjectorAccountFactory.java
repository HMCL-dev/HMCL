/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class AuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    private AuthlibInjectorArtifactProvider downloader;
    private Function<String, AuthlibInjectorServer> serverLookup;

    /**
     * @param serverLookup a function that looks up {@link AuthlibInjectorServer} by url
     */
    public AuthlibInjectorAccountFactory(AuthlibInjectorArtifactProvider downloader, Function<String, AuthlibInjectorServer> serverLookup) {
        this.downloader = downloader;
        this.serverLookup = serverLookup;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        AuthlibInjectorServer server = (AuthlibInjectorServer) additionalData;

        AuthlibInjectorAccount account = new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider(server.getUrl())),
                server, downloader, username, null, null);
        account.logInWithPassword(password, selector);
        return account;
    }

    @Override
    public AuthlibInjectorAccount fromStorage(Map<Object, Object> storage) {
        Objects.requireNonNull(storage);

        YggdrasilSession session = YggdrasilSession.fromStorage(storage);

        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have username"));
        String apiRoot = tryCast(storage.get("serverBaseURL"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have API root."));

        AuthlibInjectorServer server = serverLookup.apply(apiRoot);

        return new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider(server.getUrl())),
                server, downloader, username, session.getSelectedProfile().getId(), session);
    }
}
