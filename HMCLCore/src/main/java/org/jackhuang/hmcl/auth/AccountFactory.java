// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import com.google.gson.JsonObject;

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

    public interface ProgressCallback {
        void onProgressChanged(String stageName);
    }

    /**
     * Informs how this account factory verifies user's credential.
     *
     * @see AccountLoginType
     */
    public abstract AccountLoginType getLoginType();

    /**
     * Create a new(to be verified via network) account, and log in.
     *
     * @param selector         for character selection if multiple characters belong to single account. Pick out which character to act as.
     * @param username         username of the account if needed.
     * @param password         password of the account if needed.
     * @param progressCallback notify login progress.
     * @param additionalData   extra data for specific account factory.
     * @return logged-in account.
     * @throws AuthenticationException if an error occurs when logging in.
     */
    public abstract T create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException;

    /**
     * Create a existing(stored in local files) account.
     *
     * @param metadata serialized public account metadata.
     * @param privateData serialized private account data.
     * @return account stored in local storage. Credentials may expired, and you should refresh account state later.
     */
    public abstract T fromStorage(JsonObject metadata, JsonObject privateData);
}
