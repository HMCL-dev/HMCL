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
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.JsonFileFormat;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Stores per-workspace authlib-injector authentication servers.
///
/// The JSON representation is saved as `authlib-injector-servers.json` under the current HMCL directory.
///
/// @author Glavo
@JsonAdapter(AuthlibInjectorServerList.Adapter.class)
@NotNullByDefault
@JsonSerializable
public final class AuthlibInjectorServerList extends ObservableSetting implements FormattedJsonSetting {
    /// The LittleSkin Yggdrasil API endpoint bundled into newly created server lists.
    private static final String LITTLE_SKIN_URL = "https://littleskin.cn/api/yggdrasil/";

    /// The file format supported by this authlib-injector server list.
    public static final JsonFileFormat CURRENT_FORMAT =
            new JsonFileFormat("hmcl.authlib-injector-servers", new JsonFileFormat.Version(1, 0));

    /// Creates an empty authlib-injector server list.
    public AuthlibInjectorServerList() {
        tracker.markDirty(format);
        register();
    }

    /// Creates the default authlib-injector server list for a newly created file.
    public static AuthlibInjectorServerList createDefault() {
        AuthlibInjectorServerList result = new AuthlibInjectorServerList();
        result.getServers().add(new AuthlibInjectorServer(LITTLE_SKIN_URL));
        return result;
    }

    /// The format used by this authlib-injector server list file.
    @SerializedName(JsonFileFormat.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonFileFormat> format = new SimpleObjectProperty<>(CURRENT_FORMAT);

    /// Returns the format property.
    public ObjectProperty<JsonFileFormat> formatProperty() {
        return format;
    }

    /// Returns the format used by this authlib-injector server list file.
    @Override
    public JsonFileFormat getFormat() {
        return format.get();
    }

    /// Sets the format used by this authlib-injector server list file.
    @Override
    public void setFormat(JsonFileFormat format) {
        this.format.set(Objects.requireNonNull(format));
    }

    /// Authlib-injector authentication servers available for account login.
    @SerializedName("servers")
    private final ObservableList<AuthlibInjectorServer> servers =
            FXCollections.observableArrayList(server -> new Observable[]{server});

    /// Returns authlib-injector authentication servers available for account login.
    public ObservableList<AuthlibInjectorServer> getServers() {
        return servers;
    }

    /// JSON adapter for [AuthlibInjectorServerList].
    public static final class Adapter extends ObservableSetting.Adapter<AuthlibInjectorServerList> {
        /// Creates an empty authlib-injector server list for deserialization.
        @Override
        protected AuthlibInjectorServerList createInstance() {
            return new AuthlibInjectorServerList();
        }
    }
}
