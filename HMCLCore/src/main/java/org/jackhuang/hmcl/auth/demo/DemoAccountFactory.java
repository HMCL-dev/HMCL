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
package org.jackhuang.hmcl.auth.demo;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.tryCast;

/**
 *
 * @author huangyuhui
 */
public final class DemoAccountFactory extends AccountFactory<DemoAccount> {

    public DemoAccountFactory() {}

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.NONE;
    }

    public DemoAccount create(String username, UUID uuid) {
        return new DemoAccount(username, uuid);
    }

    @Override
    public DemoAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) {
        username = "Player";
        UUID uuid;
        uuid = getUUIDFromUserName(username);
        return new DemoAccount(username, uuid);
    }

    @Override
    public DemoAccount fromStorage(Map<Object, Object> storage) {
        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalStateException("Demo account configuration malformed."));
        UUID uuid = tryCast(storage.get("uuid"), String.class)
                .map(UUIDTypeAdapter::fromString)
                .orElse(getUUIDFromUserName(username));

        return new DemoAccount(username, uuid);
    }

    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

}
