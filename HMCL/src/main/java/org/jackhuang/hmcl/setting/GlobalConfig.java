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
import org.jackhuang.hmcl.util.gson.ObservableSetting;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@JsonAdapter(GlobalConfig.Adapter.class)
public final class GlobalConfig extends ObservableSetting {

    @Nullable
    public static GlobalConfig fromJson(String json) throws JsonParseException {
        return Config.CONFIG_GSON.fromJson(json, GlobalConfig.class);
    }

    public GlobalConfig() {
        register();
    }

    public String toJson() {
        return Config.CONFIG_GSON.toJson(this);
    }

    @SerializedName("agreementVersion")
    private final IntegerProperty agreementVersion = new SimpleIntegerProperty();

    public IntegerProperty agreementVersionProperty() {
        return agreementVersion;
    }

    public int getAgreementVersion() {
        return agreementVersion.get();
    }

    public void setAgreementVersion(int agreementVersion) {
        this.agreementVersion.set(agreementVersion);
    }

    @SerializedName("terracottaAgreementVersion")
    private final IntegerProperty terracottaAgreementVersion = new SimpleIntegerProperty();

    public IntegerProperty terracottaAgreementVersionProperty() {
        return terracottaAgreementVersion;
    }

    public int getTerracottaAgreementVersion() {
        return terracottaAgreementVersion.get();
    }

    public void setTerracottaAgreementVersion(int terracottaAgreementVersion) {
        this.terracottaAgreementVersion.set(terracottaAgreementVersion);
    }

    @SerializedName("platformPromptVersion")
    private final IntegerProperty platformPromptVersion = new SimpleIntegerProperty();

    public IntegerProperty platformPromptVersionProperty() {
        return platformPromptVersion;
    }

    public int getPlatformPromptVersion() {
        return platformPromptVersion.get();
    }

    public void setPlatformPromptVersion(int platformPromptVersion) {
        this.platformPromptVersion.set(platformPromptVersion);
    }

    @SerializedName("logRetention")
    private final IntegerProperty logRetention = new SimpleIntegerProperty(20);

    public IntegerProperty logRetentionProperty() {
        return logRetention;
    }

    public int getLogRetention() {
        return logRetention.get();
    }

    public void setLogRetention(int logRetention) {
        this.logRetention.set(logRetention);
    }

    @SerializedName("enableOfflineAccount")
    private final BooleanProperty enableOfflineAccount = new SimpleBooleanProperty(false);

    public BooleanProperty enableOfflineAccountProperty() {
        return enableOfflineAccount;
    }

    public boolean isEnableOfflineAccount() {
        return enableOfflineAccount.get();
    }

    public void setEnableOfflineAccount(boolean value) {
        enableOfflineAccount.set(value);
    }

    @SerializedName("fontAntiAliasing")
    private final StringProperty fontAntiAliasing = new SimpleStringProperty();

    public StringProperty fontAntiAliasingProperty() {
        return fontAntiAliasing;
    }

    public String getFontAntiAliasing() {
        return fontAntiAliasing.get();
    }

    public void setFontAntiAliasing(String value) {
        this.fontAntiAliasing.set(value);
    }

    @SerializedName("userJava")
    private final ObservableSet<String> userJava = FXCollections.observableSet(new LinkedHashSet<>());

    public ObservableSet<String> getUserJava() {
        return userJava;
    }

    @SerializedName("disabledJava")
    private final ObservableSet<String> disabledJava = FXCollections.observableSet(new LinkedHashSet<>());

    public ObservableSet<String> getDisabledJava() {
        return disabledJava;
    }

    static final class Adapter extends ObservableSetting.Adapter<GlobalConfig> {
        @Override
        protected GlobalConfig createInstance() {
            return new GlobalConfig();
        }
    }
}
