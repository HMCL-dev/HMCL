// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.util.Map;
import java.util.Objects;

public class MicrosoftAccountFactory extends AccountFactory<MicrosoftAccount> {

    private final MicrosoftService service;

    public MicrosoftAccountFactory(MicrosoftService service) {
        this.service = service;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.NONE;
    }

    @Override
    public MicrosoftAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        return new MicrosoftAccount(service, (OAuth.GrantFlow) additionalData);
    }

    @Override
    public MicrosoftAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);
        AccountID accountID = Account.readAccountID(metadata);
        MicrosoftSession session = MicrosoftSession.fromStorage(metadata, privateData);

        if (privateData.get("profileProperties") instanceof JsonObject profilePropertiesObject) {
            Map<String, String> properties = JsonUtils.GSON.fromJson(
                    profilePropertiesObject,
                    JsonUtils.mapTypeOf(String.class, String.class));
            MicrosoftSession.GameProfile profile = session.profile();
            var profileRepository = service.getProfileRepository();
            profileRepository.put(profile.id(), new CompleteGameProfile(new GameProfile(profile.id(), profile.name()), properties));
            profileRepository.invalidate(profile.id());
        }

        return new MicrosoftAccount(accountID, service, session);
    }
}
