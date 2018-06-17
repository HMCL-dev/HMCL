/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.setting;

import java.util.Map;
import java.util.TreeMap;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;

import com.google.gson.annotations.SerializedName;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

public final class Config implements Cloneable {

    @SerializedName("last")
    public final StringProperty selectedProfile = new SimpleStringProperty("");

    @SerializedName("backgroundType")
    public final IntegerProperty backgroundImageType = new SimpleIntegerProperty(0);

    @SerializedName("bgpath")
    public final StringProperty backgroundImage = new SimpleStringProperty();

    @SerializedName("commonpath")
    public final StringProperty commonDirectory = new SimpleStringProperty(Launcher.MINECRAFT_DIRECTORY.getAbsolutePath());

    @SerializedName("hasProxy")
    public final BooleanProperty hasProxy = new SimpleBooleanProperty();

    @SerializedName("hasProxyAuth")
    public final BooleanProperty hasProxyAuth = new SimpleBooleanProperty();

    @SerializedName("proxyType")
    public final IntegerProperty proxyType = new SimpleIntegerProperty();

    @SerializedName("proxyHost")
    public final StringProperty proxyHost = new SimpleStringProperty();

    @SerializedName("proxyPort")
    public final StringProperty proxyPort = new SimpleStringProperty();

    @SerializedName("proxyUserName")
    public final StringProperty proxyUser = new SimpleStringProperty();

    @SerializedName("proxyPassword")
    public final StringProperty proxyPass = new SimpleStringProperty();

    @SerializedName("theme")
    public final StringProperty theme = new SimpleStringProperty();

    @SerializedName("localization")
    public final StringProperty localization = new SimpleStringProperty();

    @SerializedName("downloadtype")
    public final IntegerProperty downloadType = new SimpleIntegerProperty();

    @SerializedName("configurations")
    public final ObservableMap<String, Profile> configurations = FXCollections.observableMap(new TreeMap<>());

    @SerializedName("accounts")
    public final ObservableList<Map<Object, Object>> accounts = FXCollections.observableArrayList();

    @SerializedName("selectedAccount")
    public final StringProperty selectedAccount = new SimpleStringProperty("");

    @SerializedName("fontFamily")
    public final StringProperty fontFamily = new SimpleStringProperty("Consolas");

    @SerializedName("fontSize")
    public final DoubleProperty fontSize = new SimpleDoubleProperty(12);

    @SerializedName("logLines")
    public final IntegerProperty logLines = new SimpleIntegerProperty(100);

    public final ObservableList<AuthlibInjectorServer> authlibInjectorServers = FXCollections.observableArrayList();

    @Override
    public Config clone() {
        return Settings.GSON.fromJson(Settings.GSON.toJson(this), Config.class);
    }
}
