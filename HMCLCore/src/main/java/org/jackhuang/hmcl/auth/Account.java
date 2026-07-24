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

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.game.skin.TextureType;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author huangyuhui
 */
@NotNullByDefault
public abstract class Account implements Observable {
    /// The serialized account ID property name.
    public static final String PROPERTY_ACCOUNT_ID = "accountID";

    /// The stable ID of this account entry.
    private final AccountID accountID;

    /// Creates an account.
    ///
    /// @param accountID the stable account entry ID
    protected Account(AccountID accountID) {
        this.accountID = Objects.requireNonNull(accountID);
    }

    /// Returns the stable ID of this account entry.
    public AccountID getAccountID() {
        return accountID;
    }

    /**
     * @return the profile name
     */
    public abstract String getProfileName();

    /**
     * @return the profile ID
     */
    public abstract UUID getProfileID();

    /**
     * Login with stored credentials.
     *
     * @throws CredentialExpiredException when the stored credentials has expired, in which case a password login will be performed
     */
    public abstract AuthInfo logIn() throws AuthenticationException;

    /**
     * Play offline.
     *
     * @return the specific offline player's info.
     */
    public abstract AuthInfo playOffline() throws AuthenticationException;

    public boolean canUploadSkin() {
        return false;
    }

    public void uploadSkin(boolean isSlim, Path file) throws AuthenticationException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Unsupported Operation");
    }

    /// Writes public account metadata into the target JSON object.
    ///
    /// Metadata is stored in `accounts.json` and must not contain credentials or cached private profile data.
    ///
    /// The target object is owned by the caller. Implementations may only mutate it during this method call and must
    /// not retain a reference to it.
    @MustBeInvokedByOverriders
    public void writeMetadata(JsonObject metadata) {
        metadata.addProperty(PROPERTY_ACCOUNT_ID, accountID.toString());
    }

    /// Writes private account data into the target JSON object.
    ///
    /// Private data is stored outside `accounts.json` and may contain credentials or cached profile data.
    ///
    /// The target object is owned by the caller. Implementations may only mutate it during this method call and must
    /// not retain a reference to it.
    @MustBeInvokedByOverriders
    public void writePrivateData(JsonObject privateData) {
    }

    public void clearCache() {
    }

    private final BooleanProperty portable = new SimpleBooleanProperty(false);

    public BooleanProperty portableProperty() {
        return portable;
    }

    public boolean isPortable() {
        return portable.get();
    }

    public void setPortable(boolean value) {
        this.portable.set(value);
    }

    /// Returns the stable account ID for the given serialized account record.
    ///
    /// @param storage the serialized account record
    /// @return the stable account ID, or `null` if the account record has no valid account ID
    public static @Nullable AccountID getAccountID(JsonObject storage) {
        @Nullable String accountID = JsonUtils.getString(storage, PROPERTY_ACCOUNT_ID);
        if (accountID == null) {
            return null;
        }

        try {
            return AccountID.parse(accountID);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// Reads an account ID from serialized account storage.
    ///
    /// @param storage the account storage object
    /// @return the parsed account ID
    /// @throws IllegalArgumentException if the storage has no valid account ID
    public static AccountID readAccountID(JsonObject storage) {
        @Nullable String accountID = JsonUtils.getString(storage, PROPERTY_ACCOUNT_ID);
        if (accountID == null) {
            throw new IllegalArgumentException("accountID is missing");
        }

        return AccountID.parse(accountID);
    }

    private final ObservableHelper helper = new ObservableHelper(this);

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

    public ObjectBinding<Optional<Map<TextureType, Texture>>> getTextures() {
        return Bindings.createObjectBinding(Optional::empty);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(portable.get(), accountID);
    }

    @Override
    public final boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Account))
            return false;

        Account another = (Account) obj;
        return isPortable() == another.isPortable() && accountID.equals(another.accountID);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("accountID", accountID)
                .append("profileName", getProfileName())
                .append("profileID", getProfileID())
                .append("portable", isPortable())
                .toString();
    }
}
