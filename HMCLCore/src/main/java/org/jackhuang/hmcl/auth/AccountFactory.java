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
package org.jackhuang.hmcl.auth;

import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public abstract class AccountFactory<T extends Account> {

    public enum AccountLoginType {
        /**
         * Either username or password should not be provided.
         * AccountFactory will take its own way to check credentials.
         */
        NONE(false, false),

        /**
         * AccountFactory only needs username.
         */
        USERNAME(true, false),

        /**
         * AccountFactory needs both username and password for credential verification.
         */
        USERNAME_PASSWORD(true, true);

        public final boolean requiresUsername, requiresPassword;

        AccountLoginType(boolean requiresUsername, boolean requiresPassword) {
            this.requiresUsername = requiresUsername;
            this.requiresPassword = requiresPassword;
        }
    }

    /**
     * Informs how this account factory verifies user's credential.
     * @see AccountLoginType
     */
    public abstract AccountLoginType getLoginType();

    /**
     * Create a new(to be verified via network) account, and log in.
     * @param selector for character selection if multiple characters belong to single account. Pick out which character to act as.
     * @param username username of the account if needed.
     * @param password password of the account if needed.
     * @param additionalData extra data for specific account factory.
     * @return logged-in account.
     * @throws AuthenticationException if an error occurs when logging in.
     */
    public abstract T create(CharacterSelector selector, String username, String password, Object additionalData) throws AuthenticationException;

    /**
     * Create a existing(stored in local files) account.
     * @param storage serialized account data.
     * @return account stored in local storage. Credentials may expired, and you should refresh account state later.
     */
    public abstract T fromStorage(Map<Object, Object> storage);
}
