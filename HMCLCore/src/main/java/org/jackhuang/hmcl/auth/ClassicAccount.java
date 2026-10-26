// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth;

public abstract class ClassicAccount extends Account {
    /// Creates a classic account.
    ///
    /// @param accountID the stable account entry ID
    protected ClassicAccount(AccountID accountID) {
        super(accountID);
    }

    /**
     * @return the login name used by the remote authentication service
     */
    public abstract String getLoginName();

    /**
     * Login with specified password.
     *
     * When credentials expired, the auth server will ask you to login with password to refresh
     * credentials.
     */
    public abstract AuthInfo logInWithPassword(String password) throws AuthenticationException;
}
