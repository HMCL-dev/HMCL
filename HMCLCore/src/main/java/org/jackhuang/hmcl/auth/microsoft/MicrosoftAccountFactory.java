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
package org.jackhuang.hmcl.auth.microsoft;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.OAuth;

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
        return new MicrosoftAccount(accountID, service, session);
    }
}
