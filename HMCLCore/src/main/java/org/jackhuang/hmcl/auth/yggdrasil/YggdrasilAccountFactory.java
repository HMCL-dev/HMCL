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

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.net.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.jackhuang.hmcl.util.Lang.tryCast;

/**
 *
 * @author huangyuhui
 */
public class YggdrasilAccountFactory extends AccountFactory<YggdrasilAccount> {

    private final YggdrasilProvider provider;

    public YggdrasilAccountFactory(YggdrasilProvider provider) {
        this.provider = provider;
    }

    @Override
    public YggdrasilAccount create(CharacterSelector selector, String username, String password, Object additionalData, Proxy proxy) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(proxy);

        YggdrasilAccount account = new YggdrasilAccount(new YggdrasilService(provider, proxy), username, UUIDTypeAdapter.fromUUID(UUID.randomUUID()), null, null);
        account.logInWithPassword(password, selector);
        return account;
    }

    @Override
    public YggdrasilAccount fromStorage(Map<Object, Object> storage, Proxy proxy) {
        Objects.requireNonNull(storage);
        Objects.requireNonNull(proxy);

        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have username"));
        String clientToken = tryCast(storage.get("clientToken"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have client token."));
        String character = tryCast(storage.get("clientToken"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have selected character name."));

        return new YggdrasilAccount(new YggdrasilService(provider, proxy), username, clientToken, character, YggdrasilSession.fromStorage(storage));
    }

    public static String randomToken() {
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID());
    }
}
