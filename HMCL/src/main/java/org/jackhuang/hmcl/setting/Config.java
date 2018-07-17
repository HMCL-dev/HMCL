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

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.hildan.fxgson.creators.ObservableListCreator;
import org.hildan.fxgson.creators.ObservableMapCreator;
import org.hildan.fxgson.creators.ObservableSetCreator;
import org.hildan.fxgson.factories.JavaFxPropertyTypeAdapterFactory;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.EnumOrdinalDeserializer;
import org.jackhuang.hmcl.util.FileTypeAdapter;
import org.jackhuang.hmcl.util.ObservableHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

public final class Config implements Cloneable, Observable {

    private static final Gson CONFIG_GSON = new GsonBuilder()
            .registerTypeAdapter(VersionSetting.class, VersionSetting.Serializer.INSTANCE)
            .registerTypeAdapter(Profile.class, Profile.Serializer.INSTANCE)
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .registerTypeAdapter(ObservableList.class, new ObservableListCreator())
            .registerTypeAdapter(ObservableSet.class, new ObservableSetCreator())
            .registerTypeAdapter(ObservableMap.class, new ObservableMapCreator())
            .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
            .registerTypeAdapter(EnumBackgroundImage.class, new EnumOrdinalDeserializer<>(EnumBackgroundImage.class)) // backward compatibility for backgroundType
            .setPrettyPrinting()
            .create();

    public static Config fromJson(String json) throws JsonParseException {
        Config instance = CONFIG_GSON.fromJson(json, Config.class);
        // Gson will replace the property fields (even they are final!)
        // So we have to add the listeners again after deserialization
        instance.addListenerToProperties();
        return instance;
    }

    @SerializedName("last")
    public final StringProperty selectedProfile = new SimpleStringProperty("");

    @SerializedName("backgroundType")
    public final ObjectProperty<EnumBackgroundImage> backgroundImageType = new SimpleObjectProperty<>(EnumBackgroundImage.DEFAULT);

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
    public final IntegerProperty downloadType = new SimpleIntegerProperty(1);

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

    @SerializedName("firstLaunch")
    public final BooleanProperty firstLaunch = new SimpleBooleanProperty(true);

    public final ObservableList<AuthlibInjectorServer> authlibInjectorServers = FXCollections.observableArrayList();

    private transient ObservableHelper helper = new ObservableHelper(this);

    public Config() {
        addListenerToProperties();
    }

    private void addListenerToProperties() {
        Stream.of(getClass().getFields())
                .filter(it -> {
                    int modifiers = it.getModifiers();
                    return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers);
                })
                .filter(it -> Observable.class.isAssignableFrom(it.getType()))
                .map(it -> {
                    try {
                        return (Observable) it.get(this);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Failed to get my own properties");
                    }
                })
                .forEach(helper::receiveUpdatesFrom);
    }

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    public String toJson() {
        return CONFIG_GSON.toJson(this);
    }

    @Override
    public Config clone() {
        return fromJson(this.toJson());
    }
}
