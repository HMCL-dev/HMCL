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

import com.google.gson.JsonElement;
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
import org.jetbrains.annotations.Contract;
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

    public abstract Map<Object, Object> toStorage();

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

    /// Writes stable fields that identify this account into the given JSON object.
    ///
    /// The identifier object must not contain credentials or other secrets. It is used only to find the
    /// same account again after account storages have been reloaded.
    @Contract(mutates = "param1")
    public abstract void toIdentifier(JsonObject json);

    /// Returns whether the given identifier object matches the stable identifier fields of this account.
    ///
    /// Extra members in the given object are ignored by this method so callers can add storage or type metadata.
    @Contract(pure = true)
    public boolean matchIdentifier(JsonObject json) {
        JsonObject identifier = new JsonObject();
        toIdentifier(identifier);

        for (Map.Entry<String, JsonElement> entry : identifier.asMap().entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (!value.equals(json.get(key)))
                return false;
        }

        return true;
    }

    /// Returns the stable account identifier for the given account storage.
    ///
    /// The returned object includes the account type and the same stable fields written by account implementations.
    ///
    /// @param storage the account storage map
    /// @return the stable account identifier, or `null` if the account cannot be identified
    public static @Nullable JsonObject identifier(Map<?, ?> storage) {
        @Nullable String type = JsonUtils.getString(storage, "type");
        if (type == null) {
            return null;
        }

        JsonObject identifier = new JsonObject();
        identifier.addProperty("type", type);
        switch (type) {
            case "offline" -> {
                if (!addIdentifierProperty(identifier, storage, "username")) {
                    return null;
                }
            }
            case "microsoft" -> {
                if (!addIdentifierProperty(identifier, storage, "uuid")) {
                    return null;
                }
            }
            case "yggdrasil" -> {
                if (!addIdentifierProperty(identifier, storage, "username")
                        || !addIdentifierProperty(identifier, storage, "uuid")) {
                    return null;
                }
            }
            case "authlibInjector" -> {
                if (!addIdentifierProperty(identifier, storage, "serverBaseURL")
                        || !addIdentifierProperty(identifier, storage, "username")
                        || !addIdentifierProperty(identifier, storage, "uuid")) {
                    return null;
                }
            }
            default -> {
                return null;
            }
        }
        return identifier;
    }

    /// Adds a string member from an account storage map to an identifier object.
    ///
    /// @param identifier the identifier object to update
    /// @param storage the account storage map
    /// @param key the member key
    /// @return whether the member exists and was added
    private static boolean addIdentifierProperty(JsonObject identifier, Map<?, ?> storage, String key) {
        @Nullable String value = JsonUtils.getString(storage, key);
        if (value == null) {
            return false;
        }
        identifier.addProperty(key, value);
        return true;
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
    public int hashCode() {
        return Objects.hash(portable);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Account))
            return false;

        Account another = (Account) obj;
        return isPortable() == another.isPortable();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("username", getUsername())
                .append("character", getCharacter())
                .append("uuid", getUUID())
                .append("portable", isPortable())
                .toString();
    }
}
