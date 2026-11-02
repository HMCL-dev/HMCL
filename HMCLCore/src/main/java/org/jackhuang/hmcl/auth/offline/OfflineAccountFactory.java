// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.JsonObject;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

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
        return new OfflineAccount(AccountID.generate(), downloader, username, uuid, null);
    }

    @Override
    public OfflineAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) {
        AdditionalData data;
        UUID uuid;
        Skin skin;
        if (additionalData != null) {
            data = (AdditionalData) additionalData;
            uuid = data.uuid == null ? getUUIDFromUserName(username) : data.uuid;
            skin = data.skin;
        } else {
            uuid = getUUIDFromUserName(username);
            skin = null;
        }
        return new OfflineAccount(AccountID.generate(), downloader, username, uuid, skin);
    }

    @Override
    public OfflineAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        AccountID accountID = Account.readAccountID(metadata);
        String profileName = JsonUtils.getString(metadata, "profileName");
        if (profileName == null) {
            throw new IllegalStateException("Offline account configuration malformed.");
        }
        String profileIDText = JsonUtils.getString(metadata, "profileID");
        UUID profileID = profileIDText != null
                ? UUIDs.parse(profileIDText)
                : getUUIDFromUserName(profileName);
        Skin skin = Skin.fromStorage(metadata.get("skin") instanceof JsonObject skinObject ? skinObject : null);

        return new OfflineAccount(accountID, downloader, profileName, profileID, skin);
    }

    public static UUID getUUIDFromUserName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8));
    }

    public static class AdditionalData {
        private final UUID uuid;
        private final Skin skin;

        public AdditionalData(UUID uuid, Skin skin) {
            this.uuid = uuid;
            this.skin = skin;
        }
    }

}
