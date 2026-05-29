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

import com.github.f4b6a3.uuid.alt.GUID;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
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
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.*;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.*;

/// Stores the current workspace's main launcher settings.
///
/// This file keeps launcher-level choices such as UI preferences, network settings, selected game directory,
/// selected instances, and account selection. Larger domain-specific stores, such as game directories,
/// game settings presets, accounts, launcher state, and authlib-injector servers, are persisted in detached
/// JSON files managed by [ConfigHolder].
@JsonAdapter(value = Config.Adapter.class)
public final class Config extends ObservableSetting {

    /// The JSON schema supported by this config class.
    public static final JsonSchema CURRENT_SCHEMA = new JsonSchema("settings", new JsonSchema.Version(1, 0, 0));

    /// The JSON member name for the default game setting preset ID.
    static final String DEFAULT_GAME_SETTINGS_PRESET_MEMBER_NAME = "defaultGameSettingsPreset";

    /// The JSON member name for the selected game directory ID.
    static final String SELECTED_GAME_DIRECTORY_MEMBER_NAME = "selectedGameDirectory";

    /// The JSON member name for selected instance IDs keyed by game directory ID.
    static final String SELECTED_INSTANCE_MEMBER_NAME = "selectedInstance";

    /// Gson instance used for main config and related settings objects that depend on JavaFX properties.
    public static final Gson CONFIG_GSON = new GsonBuilder()
            .registerTypeAdapter(Path.class, PathTypeAdapter.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapter(GUID.class, GUIDTypeAdapter.INSTANCE)
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

    /// Deserializes a config object from JSON.
    ///
    /// @param json the JSON object to read
    /// @return the deserialized config, or `null` if the JSON represents `null`
    /// @throws JsonParseException if the JSON cannot be deserialized as a config
    @Nullable
    public static Config fromJson(JsonObject json) throws JsonParseException {
        return CONFIG_GSON.fromJson(json, Config.class);
    }

    /// Creates an empty config using current defaults.
    public Config() {
        tracker.markDirty(schema);
        register();
    }

    /// Serializes this config to formatted JSON.
    public String toJson() {
        return CONFIG_GSON.toJson(this);
    }

    // Properties

    /// The schema used by this config file.
    @SerializedName(JsonSchema.DEFAULT_MEMBER_NAME)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this config file.
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this config file.
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// The launcher UI language.
    @SerializedName("language")
    private final ObjectProperty<SupportedLocale> language = new SimpleObjectProperty<>(SupportedLocale.DEFAULT);

    /// Returns the launcher UI language property.
    public ObjectProperty<SupportedLocale> languageProperty() {
        return language;
    }

    /// Returns the launcher UI language.
    public SupportedLocale getLanguage() {
        return language.get();
    }

    /// Sets the launcher UI language.
    public void setLanguage(SupportedLocale language) {
        this.language.set(language);
    }

    /// Whether preview builds are accepted by update checks.
    @SerializedName("acceptPreviewUpdate")
    private final BooleanProperty acceptPreviewUpdate = new SimpleBooleanProperty(false);

    /// Returns the preview update opt-in property.
    public BooleanProperty acceptPreviewUpdateProperty() {
        return acceptPreviewUpdate;
    }

    /// Returns whether preview builds are accepted by update checks.
    public boolean isAcceptPreviewUpdate() {
        return acceptPreviewUpdate.get();
    }

    /// Sets whether preview builds are accepted by update checks.
    public void setAcceptPreviewUpdate(boolean acceptPreviewUpdate) {
        this.acceptPreviewUpdate.set(acceptPreviewUpdate);
    }

    /// Whether automatic update dialogs are disabled.
    @SerializedName("disableAutoShowUpdateDialog")
    private final BooleanProperty disableAutoShowUpdateDialog = new SimpleBooleanProperty(false);

    /// Returns the automatic update dialog disable property.
    public BooleanProperty disableAutoShowUpdateDialogProperty() {
        return disableAutoShowUpdateDialog;
    }

    /// Returns whether automatic update dialogs are disabled.
    public boolean isDisableAutoShowUpdateDialog() {
        return disableAutoShowUpdateDialog.get();
    }

    /// Sets whether automatic update dialogs are disabled.
    public void setDisableAutoShowUpdateDialog(boolean disableAutoShowUpdateDialog) {
        this.disableAutoShowUpdateDialog.set(disableAutoShowUpdateDialog);
    }

    /// Whether April Fools features are disabled.
    @SerializedName("disableAprilFools")
    private final BooleanProperty disableAprilFools = new SimpleBooleanProperty(false);

    /// Returns the April Fools disable property.
    public BooleanProperty disableAprilFoolsProperty() {
        return disableAprilFools;
    }

    /// Returns whether April Fools features are disabled.
    public boolean isDisableAprilFools() {
        return disableAprilFools.get();
    }

    /// Sets whether April Fools features are disabled.
    public void setDisableAprilFools(boolean disableAprilFools) {
        this.disableAprilFools.set(disableAprilFools);
    }

    /// The common Minecraft directory selection mode.
    @SerializedName("commonDirType")
    private final ObjectProperty<EnumCommonDirectory> commonDirType = new RawPreservingObjectProperty<>(EnumCommonDirectory.DEFAULT);

    /// Returns the common Minecraft directory selection mode property.
    public ObjectProperty<EnumCommonDirectory> commonDirTypeProperty() {
        return commonDirType;
    }

    /// Returns the common Minecraft directory selection mode.
    public EnumCommonDirectory getCommonDirType() {
        return commonDirType.get();
    }

    /// Sets the common Minecraft directory selection mode.
    public void setCommonDirType(EnumCommonDirectory commonDirType) {
        this.commonDirType.set(commonDirType);
    }

    /// The custom common Minecraft directory path.
    @SerializedName("commonpath")
    private final StringProperty commonDirectory = new SimpleStringProperty(Metadata.MINECRAFT_DIRECTORY.toString());

    /// Returns the custom common Minecraft directory property.
    public StringProperty commonDirectoryProperty() {
        return commonDirectory;
    }

    /// Returns the custom common Minecraft directory path.
    public String getCommonDirectory() {
        return commonDirectory.get();
    }

    /// Sets the custom common Minecraft directory path.
    public void setCommonDirectory(String commonDirectory) {
        this.commonDirectory.set(commonDirectory);
    }

    /// The maximum number of log lines kept in log views.
    @SerializedName("logLines")
    private final ObjectProperty<@Nullable Integer> logLines = new SimpleObjectProperty<>();

    /// Returns the log line limit property.
    public ObjectProperty<@Nullable Integer> logLinesProperty() {
        return logLines;
    }

    /// Returns the maximum number of log lines kept in log views.
    public @Nullable Integer getLogLines() {
        return logLines.get();
    }

    /// Sets the maximum number of log lines kept in log views.
    public void setLogLines(@Nullable Integer logLines) {
        this.logLines.set(logLines);
    }

    // UI

    /// The configured theme brightness identifier.
    @SerializedName("themeBrightness")
    private final StringProperty themeBrightness = new SimpleStringProperty("light");

    /// Returns the theme brightness property.
    public StringProperty themeBrightnessProperty() {
        return themeBrightness;
    }

    /// Returns the configured theme brightness identifier.
    public String getThemeBrightness() {
        return themeBrightness.get();
    }

    /// Sets the configured theme brightness identifier.
    public void setThemeBrightness(String themeBrightness) {
        this.themeBrightness.set(themeBrightness);
    }

    /// The selected launcher theme color.
    @SerializedName("theme")
    private final ObjectProperty<ThemeColor> themeColor = new SimpleObjectProperty<>(ThemeColor.DEFAULT);

    /// Returns the launcher theme color property.
    public ObjectProperty<ThemeColor> themeColorProperty() {
        return themeColor;
    }

    /// Returns the selected launcher theme color.
    public ThemeColor getThemeColor() {
        return themeColor.get();
    }

    /// Sets the selected launcher theme color.
    public void setThemeColor(ThemeColor themeColor) {
        this.themeColor.set(themeColor);
    }

    /// The font family used by launcher content.
    @SerializedName("fontFamily")
    private final StringProperty fontFamily = new SimpleStringProperty();

    /// Returns the launcher content font family property.
    public StringProperty fontFamilyProperty() {
        return fontFamily;
    }

    /// Returns the font family used by launcher content.
    public String getFontFamily() {
        return fontFamily.get();
    }

    /// Sets the font family used by launcher content.
    public void setFontFamily(String fontFamily) {
        this.fontFamily.set(fontFamily);
    }

    /// The launcher UI font size.
    @SerializedName("fontSize")
    private final DoubleProperty fontSize = new SimpleDoubleProperty(12);

    /// Returns the launcher UI font size property.
    public DoubleProperty fontSizeProperty() {
        return fontSize;
    }

    /// Returns the launcher UI font size.
    public double getFontSize() {
        return fontSize.get();
    }

    /// Sets the launcher UI font size.
    public void setFontSize(double fontSize) {
        this.fontSize.set(fontSize);
    }

    /// The font family used by launcher chrome.
    @SerializedName("launcherFontFamily")
    private final StringProperty launcherFontFamily = new SimpleStringProperty();

    /// Returns the launcher chrome font family property.
    public StringProperty launcherFontFamilyProperty() {
        return launcherFontFamily;
    }

    /// Returns the font family used by launcher chrome.
    public String getLauncherFontFamily() {
        return launcherFontFamily.get();
    }

    /// Sets the font family used by launcher chrome.
    public void setLauncherFontFamily(String launcherFontFamily) {
        this.launcherFontFamily.set(launcherFontFamily);
    }

    /// Whether UI animations are disabled.
    @SerializedName("animationDisabled")
    private final BooleanProperty animationDisabled = new SimpleBooleanProperty(
            FXUtils.REDUCED_MOTION == Boolean.TRUE
                    || !JavaRuntime.CURRENT_JIT_ENABLED
                    || !FXUtils.GPU_ACCELERATION_ENABLED
    );

    /// Returns the UI animation disable property.
    public BooleanProperty animationDisabledProperty() {
        return animationDisabled;
    }

    /// Returns whether UI animations are disabled.
    public boolean isAnimationDisabled() {
        return animationDisabled.get();
    }

    /// Sets whether UI animations are disabled.
    public void setAnimationDisabled(boolean animationDisabled) {
        this.animationDisabled.set(animationDisabled);
    }

    /// Whether the launcher title area is transparent.
    @SerializedName("titleTransparent")
    private final BooleanProperty titleTransparent = new SimpleBooleanProperty(false);

    /// Returns the transparent title area property.
    public BooleanProperty titleTransparentProperty() {
        return titleTransparent;
    }

    /// Returns whether the launcher title area is transparent.
    public boolean isTitleTransparent() {
        return titleTransparent.get();
    }

    /// Sets whether the launcher title area is transparent.
    public void setTitleTransparent(boolean titleTransparent) {
        this.titleTransparent.set(titleTransparent);
    }

    /// The launcher background image source type.
    @SerializedName("backgroundType")
    private final ObjectProperty<EnumBackgroundImage> backgroundImageType = new RawPreservingObjectProperty<>(EnumBackgroundImage.DEFAULT);

    /// Returns the launcher background image source type property.
    public ObjectProperty<EnumBackgroundImage> backgroundImageTypeProperty() {
        return backgroundImageType;
    }

    /// Returns the launcher background image source type.
    public EnumBackgroundImage getBackgroundImageType() {
        return backgroundImageType.get();
    }

    /// Sets the launcher background image source type.
    public void setBackgroundImageType(EnumBackgroundImage backgroundImageType) {
        this.backgroundImageType.set(backgroundImageType);
    }

    /// The local launcher background image path.
    @SerializedName("bgpath")
    private final StringProperty backgroundImage = new SimpleStringProperty();

    /// Returns the local launcher background image path property.
    public StringProperty backgroundImageProperty() {
        return backgroundImage;
    }

    /// Returns the local launcher background image path.
    public String getBackgroundImage() {
        return backgroundImage.get();
    }

    /// Sets the local launcher background image path.
    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    /// The remote launcher background image URL.
    @SerializedName("bgurl")
    private final StringProperty backgroundImageUrl = new SimpleStringProperty();

    /// Returns the remote launcher background image URL property.
    public StringProperty backgroundImageUrlProperty() {
        return backgroundImageUrl;
    }

    /// Returns the remote launcher background image URL.
    public String getBackgroundImageUrl() {
        return backgroundImageUrl.get();
    }

    /// Sets the remote launcher background image URL.
    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl.set(backgroundImageUrl);
    }

    /// The launcher background paint.
    @SerializedName("bgpaint")
    private final ObjectProperty<Paint> backgroundPaint = new SimpleObjectProperty<>();

    /// Returns the launcher background paint.
    public Paint getBackgroundPaint() {
        return backgroundPaint.get();
    }

    /// Returns the launcher background paint property.
    public ObjectProperty<Paint> backgroundPaintProperty() {
        return backgroundPaint;
    }

    /// Sets the launcher background paint.
    public void setBackgroundPaint(Paint backgroundPaint) {
        this.backgroundPaint.set(backgroundPaint);
    }

    /// The launcher background image opacity percentage.
    @SerializedName("bgImageOpacity")
    private final IntegerProperty backgroundImageOpacity = new SimpleIntegerProperty(100);

    /// Returns the launcher background image opacity property.
    public IntegerProperty backgroundImageOpacityProperty() {
        return backgroundImageOpacity;
    }

    /// Returns the launcher background image opacity percentage.
    public int getBackgroundImageOpacity() {
        return backgroundImageOpacity.get();
    }

    /// Sets the launcher background image opacity percentage.
    public void setBackgroundImageOpacity(int backgroundImageOpacity) {
        this.backgroundImageOpacity.set(backgroundImageOpacity);
    }

    // Networks

    /// Whether HMCL automatically selects the number of download threads.
    @SerializedName("autoDownloadThreads")
    private final BooleanProperty autoDownloadThreads = new SimpleBooleanProperty(true);

    /// Returns the automatic download thread count property.
    public BooleanProperty autoDownloadThreadsProperty() {
        return autoDownloadThreads;
    }

    /// Returns whether HMCL automatically selects the number of download threads.
    public boolean getAutoDownloadThreads() {
        return autoDownloadThreads.get();
    }

    /// Sets whether HMCL automatically selects the number of download threads.
    public void setAutoDownloadThreads(boolean autoDownloadThreads) {
        this.autoDownloadThreads.set(autoDownloadThreads);
    }

    /// The configured number of download threads.
    @SerializedName("downloadThreads")
    private final IntegerProperty downloadThreads = new SimpleIntegerProperty(64);

    /// Returns the download thread count property.
    public IntegerProperty downloadThreadsProperty() {
        return downloadThreads;
    }

    /// Returns the configured number of download threads.
    public int getDownloadThreads() {
        return downloadThreads.get();
    }

    /// Sets the configured number of download threads.
    public void setDownloadThreads(int downloadThreads) {
        this.downloadThreads.set(downloadThreads);
    }

    /// The selected download provider ID.
    @SerializedName("downloadType")
    private final StringProperty downloadType = new SimpleStringProperty(DownloadProviders.DEFAULT_DIRECT_PROVIDER_ID);

    /// Returns the selected download provider ID property.
    public StringProperty downloadTypeProperty() {
        return downloadType;
    }

    /// Returns the selected download provider ID.
    public String getDownloadType() {
        return downloadType.get();
    }

    /// Sets the selected download provider ID.
    public void setDownloadType(String downloadType) {
        this.downloadType.set(downloadType);
    }

    /// Whether HMCL automatically chooses a download provider.
    @SerializedName("autoChooseDownloadType")
    private final BooleanProperty autoChooseDownloadType = new SimpleBooleanProperty(true);

    /// Returns the automatic download provider selection property.
    public BooleanProperty autoChooseDownloadTypeProperty() {
        return autoChooseDownloadType;
    }

    /// Returns whether HMCL automatically chooses a download provider.
    public boolean isAutoChooseDownloadType() {
        return autoChooseDownloadType.get();
    }

    /// Sets whether HMCL automatically chooses a download provider.
    public void setAutoChooseDownloadType(boolean autoChooseDownloadType) {
        this.autoChooseDownloadType.set(autoChooseDownloadType);
    }

    /// The selected game version list source ID.
    @SerializedName("versionListSource")
    private final StringProperty versionListSource = new SimpleStringProperty(DownloadProviders.DEFAULT_AUTO_PROVIDER_ID);

    /// Returns the selected game version list source ID property.
    public StringProperty versionListSourceProperty() {
        return versionListSource;
    }

    /// Returns the selected game version list source ID.
    public String getVersionListSource() {
        return versionListSource.get();
    }

    /// Sets the selected game version list source ID.
    public void setVersionListSource(String versionListSource) {
        this.versionListSource.set(versionListSource);
    }

    /// The selected default add-on source ID.
    @SerializedName("defaultAddonSource")
    private final StringProperty defaultAddonSource = new SimpleStringProperty("modrinth");

    /// Returns the selected default add-on source ID property.
    public StringProperty defaultAddonSourceProperty() {
        return defaultAddonSource;
    }

    /// Returns the selected default add-on source ID.
    public String getDefaultAddonSource() {
        return defaultAddonSource.get();
    }

    /// Sets the selected default add-on source ID.
    public void setDefaultAddonSource(String defaultAddonSource) {
        this.defaultAddonSource.set(defaultAddonSource);
    }

    /// Whether a network proxy is enabled.
    @SerializedName("hasProxy")
    private final BooleanProperty hasProxy = new SimpleBooleanProperty();

    /// Returns the network proxy enable property.
    public BooleanProperty hasProxyProperty() {
        return hasProxy;
    }

    /// Returns whether a network proxy is enabled.
    public boolean hasProxy() {
        return hasProxy.get();
    }

    /// Sets whether a network proxy is enabled.
    public void setHasProxy(boolean hasProxy) {
        this.hasProxy.set(hasProxy);
    }

    /// Whether proxy authentication is enabled.
    @SerializedName("hasProxyAuth")
    private final BooleanProperty hasProxyAuth = new SimpleBooleanProperty();

    /// Returns the proxy authentication enable property.
    public BooleanProperty hasProxyAuthProperty() {
        return hasProxyAuth;
    }

    /// Returns whether proxy authentication is enabled.
    public boolean hasProxyAuth() {
        return hasProxyAuth.get();
    }

    /// Sets whether proxy authentication is enabled.
    public void setHasProxyAuth(boolean hasProxyAuth) {
        this.hasProxyAuth.set(hasProxyAuth);
    }

    /// The configured network proxy type.
    @SerializedName("proxyType")
    private final ObjectProperty<Proxy.Type> proxyType = new SimpleObjectProperty<>(Proxy.Type.HTTP);

    /// Returns the network proxy type property.
    public ObjectProperty<Proxy.Type> proxyTypeProperty() {
        return proxyType;
    }

    /// Returns the configured network proxy type.
    public Proxy.Type getProxyType() {
        return proxyType.get();
    }

    /// Sets the configured network proxy type.
    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType.set(proxyType);
    }

    /// The configured network proxy host.
    @SerializedName("proxyHost")
    private final StringProperty proxyHost = new SimpleStringProperty();

    /// Returns the network proxy host property.
    public StringProperty proxyHostProperty() {
        return proxyHost;
    }

    /// Returns the configured network proxy host.
    public String getProxyHost() {
        return proxyHost.get();
    }

    /// Sets the configured network proxy host.
    public void setProxyHost(String proxyHost) {
        this.proxyHost.set(proxyHost);
    }

    /// The configured network proxy port.
    @SerializedName("proxyPort")
    private final IntegerProperty proxyPort = new SimpleIntegerProperty();

    /// Returns the network proxy port property.
    public IntegerProperty proxyPortProperty() {
        return proxyPort;
    }

    /// Returns the configured network proxy port.
    public int getProxyPort() {
        return proxyPort.get();
    }

    /// Sets the configured network proxy port.
    public void setProxyPort(int proxyPort) {
        this.proxyPort.set(proxyPort);
    }

    /// The configured proxy authentication username.
    @SerializedName("proxyUserName")
    private final StringProperty proxyUser = new SimpleStringProperty();

    /// Returns the proxy authentication username property.
    public StringProperty proxyUserProperty() {
        return proxyUser;
    }

    /// Returns the configured proxy authentication username.
    public String getProxyUser() {
        return proxyUser.get();
    }

    /// Sets the configured proxy authentication username.
    public void setProxyUser(String proxyUser) {
        this.proxyUser.set(proxyUser);
    }

    /// The configured proxy authentication password.
    @SerializedName("proxyPassword")
    private final StringProperty proxyPass = new SimpleStringProperty();

    /// Returns the proxy authentication password property.
    public StringProperty proxyPassProperty() {
        return proxyPass;
    }

    /// Returns the configured proxy authentication password.
    public String getProxyPass() {
        return proxyPass.get();
    }

    /// Sets the configured proxy authentication password.
    public void setProxyPass(String proxyPass) {
        this.proxyPass.set(proxyPass);
    }

    /// The selected game directory ID.
    @SerializedName(SELECTED_GAME_DIRECTORY_MEMBER_NAME)
    private final ObjectProperty<@Nullable GUID> selectedGameDirectory =
            new SimpleObjectProperty<>(this, SELECTED_GAME_DIRECTORY_MEMBER_NAME);

    /// Returns the selected game directory ID property.
    public ObjectProperty<@Nullable GUID> selectedGameDirectoryProperty() {
        return selectedGameDirectory;
    }

    /// Returns the selected game directory ID.
    public @Nullable GUID getSelectedGameDirectory() {
        return selectedGameDirectory.get();
    }

    /// Sets the selected game directory ID.
    public void setSelectedGameDirectory(@Nullable GUID selectedGameDirectory) {
        this.selectedGameDirectory.set(selectedGameDirectory);
    }

    /// The default game setting preset ID.
    @SerializedName(DEFAULT_GAME_SETTINGS_PRESET_MEMBER_NAME)
    private final ObjectProperty<@Nullable GUID> defaultGameSettingsPreset =
            new SimpleObjectProperty<>(this, DEFAULT_GAME_SETTINGS_PRESET_MEMBER_NAME);

    /// Returns the default game setting preset ID property.
    public ObjectProperty<@Nullable GUID> defaultGameSettingsPresetProperty() {
        return defaultGameSettingsPreset;
    }

    /// Returns the default game setting preset ID.
    public @Nullable GUID getDefaultGameSettingsPreset() {
        return defaultGameSettingsPreset.get();
    }

    /// Sets the default game setting preset ID.
    public void setDefaultGameSettingsPreset(@Nullable GUID defaultGameSettingsPreset) {
        this.defaultGameSettingsPreset.set(defaultGameSettingsPreset);
    }

    /// Selected instance IDs keyed by game directory ID.
    @SerializedName(SELECTED_INSTANCE_MEMBER_NAME)
    private final ObservableMap<GUID, String> selectedInstance = FXCollections.observableHashMap();

    /// Returns selected instance IDs keyed by game directory ID.
    public ObservableMap<GUID, String> getSelectedInstance() {
        return selectedInstance;
    }

    /// Returns the selected instance ID for the given game directory ID.
    public @Nullable String getSelectedInstance(@Nullable GUID gameDirectoryId) {
        return gameDirectoryId != null ? selectedInstance.get(gameDirectoryId) : null;
    }

    /// Sets the selected instance ID for the given game directory ID.
    public void setSelectedInstance(@Nullable GUID gameDirectoryId, @Nullable String selectedInstance) {
        if (gameDirectoryId == null) {
            return;
        }

        if (StringUtils.isBlank(selectedInstance)) {
            this.selectedInstance.remove(gameDirectoryId);
        } else {
            this.selectedInstance.put(gameDirectoryId, selectedInstance);
        }
    }

    // Accounts

    /// The preferred login type to use when the user wants to add an account.
    @SerializedName("preferredLoginType")
    private final StringProperty preferredLoginType = new SimpleStringProperty();

    /// Returns the preferred login type property.
    public StringProperty preferredLoginTypeProperty() {
        return preferredLoginType;
    }

    /// Returns the preferred login type.
    public String getPreferredLoginType() {
        return preferredLoginType.get();
    }

    /// Sets the preferred login type.
    public void setPreferredLoginType(String preferredLoginType) {
        this.preferredLoginType.set(preferredLoginType);
    }

    /// The selected account identifier.
    @SerializedName("selectedAccount")
    private final StringProperty selectedAccount = new SimpleStringProperty();

    /// Returns the selected account identifier property.
    public StringProperty selectedAccountProperty() {
        return selectedAccount;
    }

    /// Returns the selected account identifier.
    public String getSelectedAccount() {
        return selectedAccount.get();
    }

    /// Sets the selected account identifier.
    public void setSelectedAccount(String selectedAccount) {
        this.selectedAccount.set(selectedAccount);
    }

    /// JSON adapter for [Config].
    public static final class Adapter extends ObservableSetting.Adapter<Config> {
        /// Creates an empty config for deserialization.
        @Override
        protected Config createInstance() {
            return new Config();
        }

        /// Serializes the main config with its stored schema.
        @Override
        public JsonElement serialize(Config src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }

            JsonObject result = super.serialize(src, typeOfSrc, context).getAsJsonObject();
            result.add(JsonSchema.DEFAULT_MEMBER_NAME, context.serialize(src.getSchema(), JsonSchema.class));
            return result;
        }

    }
}
