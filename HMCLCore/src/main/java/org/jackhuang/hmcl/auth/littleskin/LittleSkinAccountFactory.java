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
package org.jackhuang.hmcl.auth.littleskin;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;

import java.util.Map;
import java.util.Objects;

public class LittleSkinAccountFactory extends AccountFactory<LittleSkinAccount> {

    private final LittleSkinService service;

    public LittleSkinAccountFactory(LittleSkinService service) {
        this.service = service;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.NONE;
    }

    @Override
    public LittleSkinAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        return new LittleSkinAccount(service);
    }

    @Override
    public LittleSkinAccount fromStorage(Map<Object, Object> storage) {
        Objects.requireNonNull(storage);
        LittleSkinSession session = LittleSkinSession.fromStorage(storage);
        return new LittleSkinAccount(service, session);
    }

    @Override
    public boolean isAvailable() {
        return service.isAvailable();
    }
}
