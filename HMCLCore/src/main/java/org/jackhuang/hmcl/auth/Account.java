/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
public abstract class Account implements Observable {

    /**
     * @return the name of the account who owns the character
     */
    public abstract String getUsername();

    /**
     * @return the character name
     */
    public abstract String getCharacter();

    /**
     * @return the character UUID
     */
    public abstract UUID getUUID();

    /**
     * Login with stored credentials.
     *
     * @throws CredentialExpiredException when the stored credentials has expired, in which case a password login will be performed
     */
    public abstract AuthInfo logIn() throws AuthenticationException;

    /**
     * Login with specified password.
     */
    public abstract AuthInfo logInWithPassword(String password) throws AuthenticationException;

    /**
     * Play offline.
     * @return the specific offline player's info.
     */
    public abstract Optional<AuthInfo> playOffline();

    public abstract Map<Object, Object> toStorage();

    public void clearCache() {
    }

    private ObservableHelper helper = new ObservableHelper(this);

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    /**
     * Called when the account has changed.
     * This method can be called from any thread.
     */
    protected void invalidate() {
        Platform.runLater(helper::invalidate);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", getUsername())
                .append("character", getCharacter())
                .append("uuid", getUUID())
                .toString();
    }
}
