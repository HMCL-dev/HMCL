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
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
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

    /// The serialized cached skin URL property name.
    private static final String PROPERTY_CACHED_SKIN_URL = "cachedSkinUrl";

    /// The serialized cached skin model property name.
    private static final String PROPERTY_CACHED_SKIN_MODEL = "cachedSkinModel";

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

    /// Cached skin texture URL of this account, or `null` if no skin has been cached.
    private @Nullable String cachedSkinUrl;

    /// Cached skin model name (`"slim"` or `"default"`) of this account, or `null` if unknown.
    private @Nullable String cachedSkinModel;

    /// Returns the cached skin texture URL of this account.
    ///
    /// @return the cached skin texture URL, or `null` if no skin has been cached for this account
    public @Nullable String getCachedSkinUrl() {
        return cachedSkinUrl;
    }

    /// Returns the cached skin model name of this account.
    ///
    /// @return the cached skin model name (`"slim"` or `"default"`), or `null` if unknown
    public @Nullable String getCachedSkinModel() {
        return cachedSkinModel;
    }

    /// Updates the cached skin of this account, persisting it into the account private data.
    ///
    /// This method does nothing if the cache is already up to date. It is safe to call from any thread.
    ///
    /// @param url the skin texture URL, or `null` to clear the cache
    /// @param model the skin model name (`"slim"` or `"default"`), or `null` if unknown
    public void setCachedSkin(@Nullable String url, @Nullable String model) {
        if (Objects.equals(url, cachedSkinUrl) && Objects.equals(model, cachedSkinModel)) {
            return;
        }
        this.cachedSkinUrl = url;
        this.cachedSkinModel = model;
        invalidate();
    }

    /// Restores the cached skin of an account from its serialized account private data.
    ///
    /// The restored value does not invalidate the account, since it is identical to the persisted state.
    ///
    /// @param account the account whose cached skin should be restored
    /// @param storage the serialized account private data
    public static void restoreCachedSkin(Account account, JsonObject storage) {
        @Nullable String url = JsonUtils.getString(storage, PROPERTY_CACHED_SKIN_URL);
        if (url == null) {
            return;
        }
        account.cachedSkinUrl = url;
        account.cachedSkinModel = JsonUtils.getString(storage, PROPERTY_CACHED_SKIN_MODEL);
    }

    /// Writes private account data into the target JSON object.
    ///
    /// Private data is stored outside `accounts.json` and may contain credentials or cached profile data.
    ///
    /// The target object is owned by the caller. Implementations may only mutate it during this method call and must
    /// not retain a reference to it.
    @MustBeInvokedByOverriders
    public void writePrivateData(JsonObject privateData) {
        if (cachedSkinUrl != null) {
            privateData.addProperty(PROPERTY_CACHED_SKIN_URL, cachedSkinUrl);
            if (cachedSkinModel != null) {
                privateData.addProperty(PROPERTY_CACHED_SKIN_MODEL, cachedSkinModel);
            }
        }
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
