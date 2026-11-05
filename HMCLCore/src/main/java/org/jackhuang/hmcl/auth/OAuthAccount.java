// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

/// Base class for accounts authenticated through OAuth.
@NotNullByDefault
public abstract class OAuthAccount extends Account {
    /// Creates an OAuth account.
    ///
    /// @param accountID the stable account entry ID
    protected OAuthAccount(AccountID accountID) {
        super(accountID);
    }

    /**
     * Fully login.
     *
     * OAuth server may ask us to do fully login because too frequent action to log in, IP changed,
     * or some other vulnerabilities detected.
     *
     * Difference between logIn & logInWhenCredentialsExpired.
     * logIn only update access token by refresh token, and will not ask user to login by opening the authorization
     * page in web browser.
     * logInWhenCredentialsExpired will open the authorization page in web browser, asking user to select an account
     * (and enter password or PIN if necessary).
     */
    public abstract AuthInfo logInWhenCredentialsExpired() throws AuthenticationException;

    /// Exception thrown when refreshed OAuth credentials belong to a different account.
    public static class WrongAccountException extends AuthenticationException {
        /// The expected Minecraft profile ID.
        private final UUID expected;

        /// The actual Minecraft profile ID returned by the OAuth flow.
        private final UUID actual;

        /// Creates a wrong account exception.
        ///
        /// @param expected the expected Minecraft profile ID
        /// @param actual the actual Minecraft profile ID returned by the OAuth flow
        public WrongAccountException(UUID expected, UUID actual) {
            super("Expected account " + expected + ", but found " + actual);
            this.expected = expected;
            this.actual = actual;
        }

        /// Returns the expected Minecraft profile ID.
        public UUID getExpected() {
            return expected;
        }

        /// Returns the actual Minecraft profile ID returned by the OAuth flow.
        public UUID getActual() {
            return actual;
        }
    }
}
