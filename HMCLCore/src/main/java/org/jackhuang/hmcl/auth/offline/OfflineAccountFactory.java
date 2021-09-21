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
package org.jackhuang.hmcl.auth.offline;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;

import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.tryCast;

/**
 *
 * @author huangyuhui
 */
public final class OfflineAccountFactory extends AccountFactory<OfflineAccount> {
    private final AuthlibInjectorArtifactProvider downloader;

    public OfflineAccountFactory(AuthlibInjectorArtifactProvider downloader) {
        this.downloader = downloader;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME;
    }

    public OfflineAccount create(String username, UUID uuid) {
        return new OfflineAccount(downloader, username, uuid, null, null);
    }

    @Override
    public OfflineAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) {
        AdditionalData data;
        UUID uuid;
        String skin;
        String cape;
        if (additionalData != null) {
            data = (AdditionalData) additionalData;
            uuid = data.uuid == null ? getUUIDFromUserName(username) : data.uuid;
            skin = data.skin;
            cape = data.cape;
        } else {
            uuid = getUUIDFromUserName(username);
            skin = cape = null;
        }
        return new OfflineAccount(downloader, username, uuid, skin, cape);
    }

    @Override
    public OfflineAccount fromStorage(Map<Object, Object> storage) {
        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalStateException("Offline account configuration malformed."));
        UUID uuid = tryCast(storage.get("uuid"), String.class)
                .map(UUIDTypeAdapter::fromString)
                .orElse(getUUIDFromUserName(username));
        String skin = tryCast(storage.get("skin"), String.class).orElse(null);
        String cape = tryCast(storage.get("cape"), String.class).orElse(null);

        return new OfflineAccount(downloader, username, uuid, skin, cape);
    }

    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

    public static class AdditionalData {
        private final UUID uuid;
        private final String skin;
        private final String cape;

        public AdditionalData(UUID uuid, String skin, String cape) {
            this.uuid = uuid;
            this.skin = skin;
            this.cape = cape;
        }
    }

}
