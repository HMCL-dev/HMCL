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
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.paint.Paint;
import org.hildan.fxgson.creators.ObservableListCreator;
import org.hildan.fxgson.creators.ObservableMapCreator;
import org.hildan.fxgson.creators.ObservableSetCreator;
import org.hildan.fxgson.factories.JavaFxPropertyTypeAdapterFactory;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.EnumOrdinalDeserializer;
import org.jackhuang.hmcl.util.gson.FileTypeAdapter;
import org.jackhuang.hmcl.util.gson.ObservableField;
import org.jackhuang.hmcl.util.gson.PaintAdapter;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;
import org.jackhuang.hmcl.util.javafx.DirtyTracker;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.net.Proxy;
import java.util.*;

@JsonAdapter(value = Config.Adapter.class)
public final class Config implements Observable {

    public static final int CURRENT_UI_VERSION = 0;

    public static final Gson CONFIG_GSON = new GsonBuilder()
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .registerTypeAdapter(ObservableList.class, new ObservableListCreator())
            .registerTypeAdapter(ObservableSet.class, new ObservableSetCreator())
            .registerTypeAdapter(ObservableMap.class, new ObservableMapCreator())
            .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
            .registerTypeAdapter(EnumBackgroundImage.class, new EnumOrdinalDeserializer<>(EnumBackgroundImage.class)) // backward compatibility for backgroundType
            .registerTypeAdapter(Proxy.Type.class, new EnumOrdinalDeserializer<>(Proxy.Type.class)) // backward compatibility for hasProxy
            .registerTypeAdapter(Paint.class, new PaintAdapter())
            .setPrettyPrinting()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private static final List<ObservableField<Config>> FIELDS;

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        Field[] fields = Config.class.getDeclaredFields();

        var configFields = new ArrayList<ObservableField<Config>>(fields.length);
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers))
                continue;

            configFields.add(ObservableField.of(lookup, field));
        }
        FIELDS = List.copyOf(configFields);
    }

    @Nullable
    public static Config fromJson(String json) throws JsonParseException {
        return CONFIG_GSON.fromJson(json, Config.class);
    }

    private transient final ObservableHelper helper = new ObservableHelper(this);
    private transient final DirtyTracker tracker = new DirtyTracker();
    private transient final Map<String, JsonElement> unknownFields = new HashMap<>();

    public Config() {
        var shouldBeWrite = Collections.<Observable>newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(shouldBeWrite, configVersion, uiVersion);

        for (var field : FIELDS) {
            Observable observable = field.get(this);
            if (shouldBeWrite.contains(observable))
                tracker.markDirty(observable);
            else
                tracker.track(observable);
            observable.addListener(helper);
        }
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

    // Properties

    @SerializedName("_version")
    private final IntegerProperty configVersion = new SimpleIntegerProperty(0);

    public IntegerProperty configVersionProperty() {
        return configVersion;
    }

    public int getConfigVersion() {
        return configVersion.get();
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion.set(configVersion);
    }

    /**
     * The version of UI that the user have last used.
     * If there is a major change in UI, {@link Config#CURRENT_UI_VERSION} should be increased.
     * When {@link #CURRENT_UI_VERSION} is higher than the property, the user guide should be shown,
     * then this property is set to the same value as {@link #CURRENT_UI_VERSION}.
     * In particular, the property is default to 0, so that whoever open the application for the first time will see the guide.
     */
    @SerializedName("uiVersion")
    private final IntegerProperty uiVersion = new SimpleIntegerProperty(CURRENT_UI_VERSION);

    public IntegerProperty uiVersionProperty() {
        return uiVersion;
    }

    public int getUiVersion() {
        return uiVersion.get();
    }

    public void setUiVersion(int uiVersion) {
        this.uiVersion.set(uiVersion);
    }

    @SerializedName("x")
    private final DoubleProperty x = new SimpleDoubleProperty();

    public DoubleProperty xProperty() {
        return x;
    }

    public double getX() {
        return x.get();
    }

    public void setX(double x) {
        this.x.set(x);
    }

    @SerializedName("y")
    private final DoubleProperty y = new SimpleDoubleProperty();

    public DoubleProperty yProperty() {
        return y;
    }

    public double getY() {
        return y.get();
    }

    public void setY(double y) {
        this.y.set(y);
    }

    @SerializedName("width")
    private final DoubleProperty width = new SimpleDoubleProperty();

    public DoubleProperty widthProperty() {
        return width;
    }

    public double getWidth() {
        return width.get();
    }

    public void setWidth(double width) {
        this.width.set(width);
    }

    @SerializedName("height")
    private final DoubleProperty height = new SimpleDoubleProperty();

    public DoubleProperty heightProperty() {
        return height;
    }

    public double getHeight() {
        return height.get();
    }

    public void setHeight(double height) {
        this.height.set(height);
    }

    @SerializedName("localization")
    private final ObjectProperty<SupportedLocale> localization = new SimpleObjectProperty<>(Locales.DEFAULT);

    public ObjectProperty<SupportedLocale> localizationProperty() {
        return localization;
    }

    public SupportedLocale getLocalization() {
        return localization.get();
    }

    public void setLocalization(SupportedLocale localization) {
        this.localization.set(localization);
    }

    @SerializedName("promptedVersion")
    private final StringProperty promptedVersion = new SimpleStringProperty();

    public String getPromptedVersion() {
        return promptedVersion.get();
    }

    public StringProperty promptedVersionProperty() {
        return promptedVersion;
    }

    public void setPromptedVersion(String promptedVersion) {
        this.promptedVersion.set(promptedVersion);
    }

    @SerializedName("shownTips")
    private final ObservableMap<String, Object> shownTips = FXCollections.observableHashMap();

    public ObservableMap<String, Object> getShownTips() {
        return shownTips;
    }

    @SerializedName("commonDirType")
    private final ObjectProperty<EnumCommonDirectory> commonDirType = new SimpleObjectProperty<>(EnumCommonDirectory.DEFAULT);

    public ObjectProperty<EnumCommonDirectory> commonDirTypeProperty() {
        return commonDirType;
    }

    public EnumCommonDirectory getCommonDirType() {
        return commonDirType.get();
    }

    public void setCommonDirType(EnumCommonDirectory commonDirType) {
        this.commonDirType.set(commonDirType);
    }

    @SerializedName("commonpath")
    private final StringProperty commonDirectory = new SimpleStringProperty(Metadata.MINECRAFT_DIRECTORY.toString());

    public StringProperty commonDirectoryProperty() {
        return commonDirectory;
    }

    public String getCommonDirectory() {
        return commonDirectory.get();
    }

    public void setCommonDirectory(String commonDirectory) {
        this.commonDirectory.set(commonDirectory);
    }

    @SerializedName("logLines")
    private final ObjectProperty<Integer> logLines = new SimpleObjectProperty<>();

    public ObjectProperty<Integer> logLinesProperty() {
        return logLines;
    }

    public Integer getLogLines() {
        return logLines.get();
    }

    public void setLogLines(Integer logLines) {
        this.logLines.set(logLines);
    }

    // UI

    @SerializedName("theme")
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>();

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme theme) {
        this.theme.set(theme);
    }

    @SerializedName("fontFamily")
    private final StringProperty fontFamily = new SimpleStringProperty();

    public StringProperty fontFamilyProperty() {
        return fontFamily;
    }

    public String getFontFamily() {
        return fontFamily.get();
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily.set(fontFamily);
    }

    @SerializedName("fontSize")
    private final DoubleProperty fontSize = new SimpleDoubleProperty(12);

    public DoubleProperty fontSizeProperty() {
        return fontSize;
    }

    public double getFontSize() {
        return fontSize.get();
    }

    public void setFontSize(double fontSize) {
        this.fontSize.set(fontSize);
    }

    @SerializedName("launcherFontFamily")
    private final StringProperty launcherFontFamily = new SimpleStringProperty();

    public StringProperty launcherFontFamilyProperty() {
        return launcherFontFamily;
    }

    public String getLauncherFontFamily() {
        return launcherFontFamily.get();
    }

    public void setLauncherFontFamily(String launcherFontFamily) {
        this.launcherFontFamily.set(launcherFontFamily);
    }

    @SerializedName("animationDisabled")
    private final BooleanProperty animationDisabled = new SimpleBooleanProperty();

    public BooleanProperty animationDisabledProperty() {
        return animationDisabled;
    }

    public boolean isAnimationDisabled() {
        return animationDisabled.get();
    }

    public void setAnimationDisabled(boolean animationDisabled) {
        this.animationDisabled.set(animationDisabled);
    }

    @SerializedName("titleTransparent")
    private final BooleanProperty titleTransparent = new SimpleBooleanProperty(false);

    public BooleanProperty titleTransparentProperty() {
        return titleTransparent;
    }

    public boolean isTitleTransparent() {
        return titleTransparent.get();
    }

    public void setTitleTransparent(boolean titleTransparent) {
        this.titleTransparent.set(titleTransparent);
    }

    @SerializedName("backgroundType")
    private final ObjectProperty<EnumBackgroundImage> backgroundImageType = new SimpleObjectProperty<>(EnumBackgroundImage.DEFAULT);

    public ObjectProperty<EnumBackgroundImage> backgroundImageTypeProperty() {
        return backgroundImageType;
    }

    public EnumBackgroundImage getBackgroundImageType() {
        return backgroundImageType.get();
    }

    public void setBackgroundImageType(EnumBackgroundImage backgroundImageType) {
        this.backgroundImageType.set(backgroundImageType);
    }

    @SerializedName("bgpath")
    private final StringProperty backgroundImage = new SimpleStringProperty();

    public StringProperty backgroundImageProperty() {
        return backgroundImage;
    }

    public String getBackgroundImage() {
        return backgroundImage.get();
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    @SerializedName("bgurl")
    private final StringProperty backgroundImageUrl = new SimpleStringProperty();

    public StringProperty backgroundImageUrlProperty() {
        return backgroundImageUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl.get();
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl.set(backgroundImageUrl);
    }

    @SerializedName("bgpaint")
    private final ObjectProperty<Paint> backgroundPaint = new SimpleObjectProperty<>();

    public Paint getBackgroundPaint() {
        return backgroundPaint.get();
    }

    public ObjectProperty<Paint> backgroundPaintProperty() {
        return backgroundPaint;
    }

    public void setBackgroundPaint(Paint backgroundPaint) {
        this.backgroundPaint.set(backgroundPaint);
    }

    @SerializedName("bgImageOpacity")
    private final IntegerProperty backgroundImageOpacity = new SimpleIntegerProperty(100);

    public IntegerProperty backgroundImageOpacityProperty() {
        return backgroundImageOpacity;
    }

    public int getBackgroundImageOpacity() {
        return backgroundImageOpacity.get();
    }

    public void setBackgroundImageOpacity(int backgroundImageOpacity) {
        this.backgroundImageOpacity.set(backgroundImageOpacity);
    }

    // Networks

    @SerializedName("autoDownloadThreads")
    private final BooleanProperty autoDownloadThreads = new SimpleBooleanProperty(true);

    public BooleanProperty autoDownloadThreadsProperty() {
        return autoDownloadThreads;
    }

    public boolean getAutoDownloadThreads() {
        return autoDownloadThreads.get();
    }

    public void setAutoDownloadThreads(boolean autoDownloadThreads) {
        this.autoDownloadThreads.set(autoDownloadThreads);
    }

    @SerializedName("downloadThreads")
    private final IntegerProperty downloadThreads = new SimpleIntegerProperty(64);

    public IntegerProperty downloadThreadsProperty() {
        return downloadThreads;
    }

    public int getDownloadThreads() {
        return downloadThreads.get();
    }

    public void setDownloadThreads(int downloadThreads) {
        this.downloadThreads.set(downloadThreads);
    }

    @SerializedName("downloadType")
    private final StringProperty downloadType = new SimpleStringProperty(DownloadProviders.DEFAULT_RAW_PROVIDER_ID);

    public StringProperty downloadTypeProperty() {
        return downloadType;
    }

    public String getDownloadType() {
        return downloadType.get();
    }

    public void setDownloadType(String downloadType) {
        this.downloadType.set(downloadType);
    }

    @SerializedName("autoChooseDownloadType")
    private final BooleanProperty autoChooseDownloadType = new SimpleBooleanProperty(true);

    public BooleanProperty autoChooseDownloadTypeProperty() {
        return autoChooseDownloadType;
    }

    public boolean isAutoChooseDownloadType() {
        return autoChooseDownloadType.get();
    }

    public void setAutoChooseDownloadType(boolean autoChooseDownloadType) {
        this.autoChooseDownloadType.set(autoChooseDownloadType);
    }

    @SerializedName("versionListSource")
    private final StringProperty versionListSource = new SimpleStringProperty("balanced");

    public StringProperty versionListSourceProperty() {
        return versionListSource;
    }

    public String getVersionListSource() {
        return versionListSource.get();
    }

    public void setVersionListSource(String versionListSource) {
        this.versionListSource.set(versionListSource);
    }

    @SerializedName("hasProxy")
    private final BooleanProperty hasProxy = new SimpleBooleanProperty();

    public BooleanProperty hasProxyProperty() {
        return hasProxy;
    }

    public boolean hasProxy() {
        return hasProxy.get();
    }

    public void setHasProxy(boolean hasProxy) {
        this.hasProxy.set(hasProxy);
    }

    @SerializedName("hasProxyAuth")
    private final BooleanProperty hasProxyAuth = new SimpleBooleanProperty();

    public BooleanProperty hasProxyAuthProperty() {
        return hasProxyAuth;
    }

    public boolean hasProxyAuth() {
        return hasProxyAuth.get();
    }

    public void setHasProxyAuth(boolean hasProxyAuth) {
        this.hasProxyAuth.set(hasProxyAuth);
    }

    @SerializedName("proxyType")
    private final ObjectProperty<Proxy.Type> proxyType = new SimpleObjectProperty<>(Proxy.Type.HTTP);

    public ObjectProperty<Proxy.Type> proxyTypeProperty() {
        return proxyType;
    }

    public Proxy.Type getProxyType() {
        return proxyType.get();
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType.set(proxyType);
    }

    @SerializedName("proxyHost")
    private final StringProperty proxyHost = new SimpleStringProperty();

    public StringProperty proxyHostProperty() {
        return proxyHost;
    }

    public String getProxyHost() {
        return proxyHost.get();
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost.set(proxyHost);
    }

    @SerializedName("proxyPort")
    private final IntegerProperty proxyPort = new SimpleIntegerProperty();

    public IntegerProperty proxyPortProperty() {
        return proxyPort;
    }

    public int getProxyPort() {
        return proxyPort.get();
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort.set(proxyPort);
    }

    @SerializedName("proxyUserName")
    private final StringProperty proxyUser = new SimpleStringProperty();

    public StringProperty proxyUserProperty() {
        return proxyUser;
    }

    public String getProxyUser() {
        return proxyUser.get();
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser.set(proxyUser);
    }

    @SerializedName("proxyPassword")
    private final StringProperty proxyPass = new SimpleStringProperty();

    public StringProperty proxyPassProperty() {
        return proxyPass;
    }

    public String getProxyPass() {
        return proxyPass.get();
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass.set(proxyPass);
    }

    // Game

    @SerializedName("disableAutoGameOptions")
    private final BooleanProperty disableAutoGameOptions = new SimpleBooleanProperty(false);

    public BooleanProperty disableAutoGameOptionsProperty() {
        return disableAutoGameOptions;
    }

    public boolean isDisableAutoGameOptions() {
        return disableAutoGameOptions.get();
    }

    public void setDisableAutoGameOptions(boolean disableAutoGameOptions) {
        this.disableAutoGameOptions.set(disableAutoGameOptions);
    }

    // Accounts

    @SerializedName("authlibInjectorServers")
    private final ObservableList<AuthlibInjectorServer> authlibInjectorServers = FXCollections.observableArrayList(server -> new Observable[]{server});

    public ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers;
    }

    @SerializedName("addedLittleSkin")
    private final BooleanProperty addedLittleSkin = new SimpleBooleanProperty(false);

    public BooleanProperty addedLittleSkinProperty() {
        return addedLittleSkin;
    }

    public boolean isAddedLittleSkin() {
        return addedLittleSkin.get();
    }

    public void setAddedLittleSkin(boolean addedLittleSkin) {
        this.addedLittleSkin.set(addedLittleSkin);
    }

    /**
     * The preferred login type to use when the user wants to add an account.
     */
    @SerializedName("preferredLoginType")
    private final StringProperty preferredLoginType = new SimpleStringProperty();

    public StringProperty preferredLoginTypeProperty() {
        return preferredLoginType;
    }

    public String getPreferredLoginType() {
        return preferredLoginType.get();
    }

    public void setPreferredLoginType(String preferredLoginType) {
        this.preferredLoginType.set(preferredLoginType);
    }

    @SerializedName("selectedAccount")
    private final StringProperty selectedAccount = new SimpleStringProperty();

    public StringProperty selectedAccountProperty() {
        return selectedAccount;
    }

    public String getSelectedAccount() {
        return selectedAccount.get();
    }

    public void setSelectedAccount(String selectedAccount) {
        this.selectedAccount.set(selectedAccount);
    }

    @SerializedName("accounts")
    private final ObservableList<Map<Object, Object>> accountStorages = FXCollections.observableArrayList();

    public ObservableList<Map<Object, Object>> getAccountStorages() {
        return accountStorages;
    }

    // Configurations

    @SerializedName("last")
    private final StringProperty selectedProfile = new SimpleStringProperty("");

    public StringProperty selectedProfileProperty() {
        return selectedProfile;
    }

    public String getSelectedProfile() {
        return selectedProfile.get();
    }

    public void setSelectedProfile(String selectedProfile) {
        this.selectedProfile.set(selectedProfile);
    }

    @SerializedName("configurations")
    private final SimpleMapProperty<String, Profile> configurations = new SimpleMapProperty<>(FXCollections.observableMap(new TreeMap<>()));

    public MapProperty<String, Profile> getConfigurations() {
        return configurations;
    }

    public static final class Adapter implements JsonSerializer<Config>, JsonDeserializer<Config> {

        @Override
        public JsonElement serialize(Config config, Type typeOfSrc, JsonSerializationContext context) {
            if (config == null)
                return JsonNull.INSTANCE;

            JsonObject result = new JsonObject();
            for (var field : FIELDS) {
                Observable observable = field.get(config);
                if (config.tracker.isDirty(observable)) {
                    JsonElement serialized = field.serialize(config, context);
                    if (serialized != null && !serialized.isJsonNull())
                        result.add(field.getSerializedName(), serialized);
                }
            }
            config.unknownFields.forEach(result::add);
            return result;
        }

        @Override
        public Config deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull())
                return null;

            if (!json.isJsonObject())
                throw new JsonParseException("Config is not an object: " + json);

            Config config = new Config();

            var values = new LinkedHashMap<>(json.getAsJsonObject().asMap());
            for (ObservableField<Config> field : FIELDS) {
                JsonElement value = values.remove(field.getSerializedName());
                if (value != null) {
                    config.tracker.markDirty(field.get(config));
                    field.deserialize(config, value, context);
                }
            }

            config.unknownFields.putAll(values);
            return config;
        }
    }
}
