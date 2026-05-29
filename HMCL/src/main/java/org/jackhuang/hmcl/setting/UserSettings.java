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
package org.jackhuang.hmcl.setting;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// Stores launcher settings shared by all workspaces for the current user.
@NotNullByDefault
@JsonAdapter(UserSettings.Adapter.class)
public final class UserSettings extends ObservableSetting {

    /// Deserializes user settings from JSON.
    ///
    /// @param json the JSON content to parse
    /// @return the parsed settings, or {@code null} when the JSON value is {@code null}
    public static @Nullable UserSettings fromJson(String json) throws JsonParseException {
        return JsonUtils.fromJson(LauncherSettings.SETTINGS_GSON, json, UserSettings.class);
    }

    /// Creates empty user settings with default values.
    public UserSettings() {
        register();
    }

    /// Serializes these settings to JSON.
    public String toJson() {
        return LauncherSettings.SETTINGS_GSON.toJson(this);
    }

    /// The accepted launcher agreement version.
    @SerializedName("agreementVersion")
    private final IntegerProperty agreementVersion = new SimpleIntegerProperty();

    /// Returns the accepted launcher agreement version property.
    public IntegerProperty agreementVersionProperty() {
        return agreementVersion;
    }

    /// Returns the accepted launcher agreement version.
    public int getAgreementVersion() {
        return agreementVersion.get();
    }

    /// Sets the accepted launcher agreement version.
    public void setAgreementVersion(int agreementVersion) {
        this.agreementVersion.set(agreementVersion);
    }

    /// The accepted Terracotta agreement version.
    @SerializedName("terracottaAgreementVersion")
    private final IntegerProperty terracottaAgreementVersion = new SimpleIntegerProperty();

    /// Returns the accepted Terracotta agreement version property.
    public IntegerProperty terracottaAgreementVersionProperty() {
        return terracottaAgreementVersion;
    }

    /// Returns the accepted Terracotta agreement version.
    public int getTerracottaAgreementVersion() {
        return terracottaAgreementVersion.get();
    }

    /// Sets the accepted Terracotta agreement version.
    public void setTerracottaAgreementVersion(int terracottaAgreementVersion) {
        this.terracottaAgreementVersion.set(terracottaAgreementVersion);
    }

    /// The platform prompt version shown to the user.
    @SerializedName("platformPromptVersion")
    private final IntegerProperty platformPromptVersion = new SimpleIntegerProperty();

    /// Returns the platform prompt version property.
    public IntegerProperty platformPromptVersionProperty() {
        return platformPromptVersion;
    }

    /// Returns the platform prompt version shown to the user.
    public int getPlatformPromptVersion() {
        return platformPromptVersion.get();
    }

    /// Sets the platform prompt version shown to the user.
    public void setPlatformPromptVersion(int platformPromptVersion) {
        this.platformPromptVersion.set(platformPromptVersion);
    }

    /// The number of launcher log files to retain.
    @SerializedName("logRetention")
    private final IntegerProperty logRetention = new SimpleIntegerProperty(20);

    /// Returns the log retention property.
    public IntegerProperty logRetentionProperty() {
        return logRetention;
    }

    /// Returns the number of launcher log files to retain.
    public int getLogRetention() {
        return logRetention.get();
    }

    /// Sets the number of launcher log files to retain.
    public void setLogRetention(int logRetention) {
        this.logRetention.set(logRetention);
    }

    /// Whether offline accounts are enabled for this user.
    @SerializedName("enableOfflineAccount")
    private final BooleanProperty enableOfflineAccount = new SimpleBooleanProperty(false);

    /// Returns the offline account enablement property.
    public BooleanProperty enableOfflineAccountProperty() {
        return enableOfflineAccount;
    }

    /// Returns whether offline accounts are enabled for this user.
    public boolean isEnableOfflineAccount() {
        return enableOfflineAccount.get();
    }

    /// Sets whether offline accounts are enabled for this user.
    public void setEnableOfflineAccount(boolean value) {
        enableOfflineAccount.set(value);
    }

    /// The JavaFX font antialiasing mode override.
    @SerializedName("fontAntiAliasing")
    private final StringProperty fontAntiAliasing = new SimpleStringProperty();

    /// Returns the JavaFX font antialiasing mode override property.
    public StringProperty fontAntiAliasingProperty() {
        return fontAntiAliasing;
    }

    /// Returns the JavaFX font antialiasing mode override.
    public @Nullable String getFontAntiAliasing() {
        return fontAntiAliasing.get();
    }

    /// Sets the JavaFX font antialiasing mode override.
    public void setFontAntiAliasing(@Nullable String value) {
        this.fontAntiAliasing.set(value);
    }

    /// User-added Java executable paths.
    @SerializedName("userJava")
    private final ObservableSet<String> userJava = FXCollections.observableSet(new LinkedHashSet<>());

    /// Returns user-added Java executable paths.
    public ObservableSet<String> getUserJava() {
        return userJava;
    }

    /// Disabled Java executable paths.
    @SerializedName("disabledJava")
    private final ObservableSet<String> disabledJava = FXCollections.observableSet(new LinkedHashSet<>());

    /// Returns disabled Java executable paths.
    public ObservableSet<String> getDisabledJava() {
        return disabledJava;
    }

    /// Gson adapter for observable user settings.
    static final class Adapter extends ObservableSetting.Adapter<UserSettings> {
        /// Creates an empty user settings instance during deserialization.
        @Override
        protected UserSettings createInstance() {
            return new UserSettings();
        }
    }
}
