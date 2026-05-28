/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Stores account storage maps in a detached JSON file.
///
/// The JSON representation is saved as `game-accounts.json` and stores account entries in the `accounts` list.
@JsonAdapter(AccountStorages.Adapter.class)
@NotNullByDefault
@JsonSerializable
final class AccountStorages extends ObservableSetting implements FormattedJsonSetting {
    /// The file format supported by this account storage list.
    static final JsonFileFormat CURRENT_FORMAT =
            new JsonFileFormat("hmcl.game-accounts", new JsonFileFormat.Version(1, 0));

    /// Creates an empty account storage list.
    AccountStorages() {
        tracker.markDirty(format);
        tracker.markDirty(accounts);
        register();
    }

    /// Creates an account storage list from already serialized account entries.
    ///
    /// @param accounts the serialized account entries
    /// @return an account storage list containing the given entries
    static AccountStorages fromAccounts(List<Map<Object, Object>> accounts) {
        AccountStorages result = new AccountStorages();
        result.getAccounts().setAll(accounts);
        return result;
    }

    /// The format used by this account storage list file.
    @SerializedName(JsonFileFormat.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonFileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<JsonFileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this account storage list file.
    @Override
    public JsonFileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this account storage list file.
    @Override
    public void setFormat(JsonFileFormat format) {
        this.format.set(Objects.requireNonNull(format));
    }

    /// Serialized account entries.
    @SerializedName("accounts")
    private final ObservableList<Map<Object, Object>> accounts = FXCollections.observableArrayList();

    /// Returns serialized account entries.
    public ObservableList<Map<Object, Object>> getAccounts() {
        return accounts;
    }

    /// JSON adapter for [AccountStorages].
    static final class Adapter extends ObservableSetting.Adapter<AccountStorages> {
        /// Creates an empty account storage list for deserialization.
        @Override
        protected AccountStorages createInstance() {
            return new AccountStorages();
        }
    }
}
