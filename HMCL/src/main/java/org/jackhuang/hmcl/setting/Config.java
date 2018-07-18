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
import java.net.Proxy;
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
            .registerTypeAdapter(Theme.class, new Theme.TypeAdapter())
            .registerTypeAdapter(EnumBackgroundImage.class, new EnumOrdinalDeserializer<>(EnumBackgroundImage.class)) // backward compatibility for backgroundType
            .registerTypeAdapter(Proxy.Type.class, new EnumOrdinalDeserializer<>(Proxy.Type.class)) // backward compatibility for hasProxy
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
    private StringProperty selectedProfile = new SimpleStringProperty("");

    @SerializedName("backgroundType")
    private ObjectProperty<EnumBackgroundImage> backgroundImageType = new SimpleObjectProperty<>(EnumBackgroundImage.DEFAULT);

    @SerializedName("bgpath")
    private StringProperty backgroundImage = new SimpleStringProperty();

    @SerializedName("commonpath")
    private StringProperty commonDirectory = new SimpleStringProperty(Launcher.MINECRAFT_DIRECTORY.getAbsolutePath());

    @SerializedName("hasProxy")
    private BooleanProperty hasProxy = new SimpleBooleanProperty();

    @SerializedName("hasProxyAuth")
    private BooleanProperty hasProxyAuth = new SimpleBooleanProperty();

    @SerializedName("proxyType")
    private ObjectProperty<Proxy.Type> proxyType = new SimpleObjectProperty<>(Proxy.Type.DIRECT);

    @SerializedName("proxyHost")
    private StringProperty proxyHost = new SimpleStringProperty();

    @SerializedName("proxyPort")
    private StringProperty proxyPort = new SimpleStringProperty();

    @SerializedName("proxyUserName")
    private StringProperty proxyUser = new SimpleStringProperty();

    @SerializedName("proxyPassword")
    private StringProperty proxyPass = new SimpleStringProperty();

    @SerializedName("theme")
    private ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.BLUE);

    @SerializedName("localization")
    private StringProperty localization = new SimpleStringProperty();

    @SerializedName("downloadtype")
    private IntegerProperty downloadType = new SimpleIntegerProperty(1);

    @SerializedName("configurations")
    private ObservableMap<String, Profile> configurations = FXCollections.observableMap(new TreeMap<>());

    @SerializedName("accounts")
    private ObservableList<Map<Object, Object>> accounts = FXCollections.observableArrayList();

    @SerializedName("selectedAccount")
    private StringProperty selectedAccount = new SimpleStringProperty("");

    @SerializedName("fontFamily")
    private StringProperty fontFamily = new SimpleStringProperty("Consolas");

    @SerializedName("fontSize")
    private DoubleProperty fontSize = new SimpleDoubleProperty(12);

    @SerializedName("logLines")
    private IntegerProperty logLines = new SimpleIntegerProperty(100);

    @SerializedName("firstLaunch")
    private BooleanProperty firstLaunch = new SimpleBooleanProperty(true);

    @SerializedName("authlibInjectorServers")
    private ObservableList<AuthlibInjectorServer> authlibInjectorServers = FXCollections.observableArrayList();

    private transient ObservableHelper helper = new ObservableHelper(this);

    public Config() {
        addListenerToProperties();
    }

    private void addListenerToProperties() {
        Stream.of(getClass().getDeclaredFields())
                .filter(it -> {
                    int modifiers = it.getModifiers();
                    return !Modifier.isTransient(modifiers) && !Modifier.isStatic(modifiers);
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

    // Getters & Setters & Properties
    public String getSelectedProfile() {
        return selectedProfile.get();
    }

    public void setSelectedProfile(String selectedProfile) {
        this.selectedProfile.set(selectedProfile);
    }

    public StringProperty selectedProfileProperty() {
        return selectedProfile;
    }

    public EnumBackgroundImage getBackgroundImageType() {
        return backgroundImageType.get();
    }

    public void setBackgroundImageType(EnumBackgroundImage backgroundImageType) {
        this.backgroundImageType.set(backgroundImageType);
    }

    public ObjectProperty<EnumBackgroundImage> backgroundImageTypeProperty() {
        return backgroundImageType;
    }

    public String getBackgroundImage() {
        return backgroundImage.get();
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    public StringProperty backgroundImageProperty() {
        return backgroundImage;
    }

    public String getCommonDirectory() {
        return commonDirectory.get();
    }

    public void setCommonDirectory(String commonDirectory) {
        this.commonDirectory.set(commonDirectory);
    }

    public StringProperty commonDirectoryProperty() {
        return commonDirectory;
    }

    public boolean hasProxy() {
        return hasProxy.get();
    }

    public void setHasProxy(boolean hasProxy) {
        this.hasProxy.set(hasProxy);
    }

    public BooleanProperty hasProxyProperty() {
        return hasProxy;
    }

    public boolean hasProxyAuth() {
        return hasProxyAuth.get();
    }

    public void setHasProxyAuth(boolean hasProxyAuth) {
        this.hasProxyAuth.set(hasProxyAuth);
    }

    public BooleanProperty hasProxyAuthProperty() {
        return hasProxyAuth;
    }

    public Proxy.Type getProxyType() {
        return proxyType.get();
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType.set(proxyType);
    }

    public ObjectProperty<Proxy.Type> proxyTypeProperty() {
        return proxyType;
    }

    public String getProxyHost() {
        return proxyHost.get();
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost.set(proxyHost);
    }

    public StringProperty proxyHostProperty() {
        return proxyHost;
    }

    public String getProxyPort() {
        return proxyPort.get();
    }

    public void setProxyPort(String proxyPort) {
        this.proxyPort.set(proxyPort);
    }

    public StringProperty proxyPortProperty() {
        return proxyPort;
    }

    public String getProxyUser() {
        return proxyUser.get();
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser.set(proxyUser);
    }

    public StringProperty proxyUserProperty() {
        return proxyUser;
    }

    public String getProxyPass() {
        return proxyPass.get();
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass.set(proxyPass);
    }

    public StringProperty proxyPassProperty() {
        return proxyPass;
    }

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme theme) {
        this.theme.set(theme);
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public String getLocalization() {
        return localization.get();
    }

    public void setLocalization(String localization) {
        this.localization.set(localization);
    }

    public StringProperty localizationProperty() {
        return localization;
    }

    public int getDownloadType() {
        return downloadType.get();
    }

    public void setDownloadType(int downloadType) {
        this.downloadType.set(downloadType);
    }

    public IntegerProperty downloadTypeProperty() {
        return downloadType;
    }

    public ObservableMap<String, Profile> getConfigurations() {
        return configurations;
    }

    public ObservableList<Map<Object, Object>> getAccounts() {
        return accounts;
    }

    public String getSelectedAccount() {
        return selectedAccount.get();
    }

    public void setSelectedAccount(String selectedAccount) {
        this.selectedAccount.set(selectedAccount);
    }

    public StringProperty selectedAccountProperty() {
        return selectedAccount;
    }

    public String getFontFamily() {
        return fontFamily.get();
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily.set(fontFamily);
    }

    public StringProperty fontFamilyProperty() {
        return fontFamily;
    }

    public double getFontSize() {
        return fontSize.get();
    }

    public void setFontSize(double fontSize) {
        this.fontSize.set(fontSize);
    }

    public DoubleProperty fontSizeProperty() {
        return fontSize;
    }

    public int getLogLines() {
        return logLines.get();
    }

    public void setLogLines(int logLines) {
        this.logLines.set(logLines);
    }

    public IntegerProperty logLinesProperty() {
        return logLines;
    }

    public boolean isFirstLaunch() {
        return firstLaunch.get();
    }

    public void setFirstLaunch(boolean firstLaunch) {
        this.firstLaunch.set(firstLaunch);
    }

    public BooleanProperty firstLaunchProperty() {
        return firstLaunch;
    }

    public ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers;
    }

}
