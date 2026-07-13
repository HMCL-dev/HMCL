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
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.glavo.monetfx.ColorStyle;
import org.hildan.fxgson.creators.ObservableListCreator;
import org.hildan.fxgson.creators.ObservableMapCreator;
import org.hildan.fxgson.creators.ObservableSetCreator;
import org.hildan.fxgson.factories.JavaFxPropertyTypeAdapterFactory;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.theme.BackgroundLoadPolicy;
import org.jackhuang.hmcl.theme.BuiltinBackground;
import org.jackhuang.hmcl.theme.NetworkBackgroundImageCachePolicy;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemeReference;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.*;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.*;

/// Stores the current workspace's main launcher settings.
///
/// This file keeps launcher-level choices such as UI preferences, network settings, selected game directory,
/// selected instances, and account selection. Larger domain-specific stores, such as game directories,
/// game settings presets, accounts, launcher state, and authlib-injector servers, are persisted in detached
/// JSON files managed by [SettingsManager].
@JsonAdapter(value = LauncherSettings.Adapter.class)
@NotNullByDefault
public final class LauncherSettings extends ObservableSetting implements JsonSchemaSetting {

    /// The JSON schema supported by this launcher settings class.
    public static final JsonSchema CURRENT_SCHEMA = new JsonSchema("launcher-settings", new JsonSchema.Version(1, 0, 0));

    /// The JSON property name for the default game setting preset ID.
    static final String PROPERTY_DEFAULT_GAME_SETTINGS_PRESET = "defaultGameSettingsPreset";

    /// The JSON property name for the selected game directory ID.
    static final String PROPERTY_SELECTED_GAME_DIRECTORY = "selectedGameDirectory";

    /// The JSON property name for selected instance IDs keyed by game directory ID.
    static final String PROPERTY_SELECTED_INSTANCE = "selectedInstance";

    /// The JSON property name for instance group names keyed by game directory and group ID.
    static final String PROPERTY_INSTANCE_GROUP_NAMES = "instanceGroupNames";

    /// The JSON property name for instance group membership keyed by game directory and instance ID.
    static final String PROPERTY_INSTANCE_GROUP_MEMBERSHIP = "instanceGroupMembership";

    /// Default launcher theme used when no stored theme reference is available.
    public static final ThemeReference DEFAULT_THEME_REFERENCE = new ThemeReference("hmcl.default", null);

    /// Theme appearance override key for theme brightness mode.
    public static final String THEME_APPEARANCE_BRIGHTNESS_MODE = "themeBrightnessMode";

    /// Theme appearance override key for theme color seed.
    public static final String THEME_APPEARANCE_COLOR = "themeColor";

    /// Theme appearance override key for theme color style.
    public static final String THEME_APPEARANCE_COLOR_STYLE = "themeColorStyle";

    /// Theme appearance override key for title-bar transparency.
    public static final String THEME_APPEARANCE_TITLE_BAR_TRANSPARENT = "titleBarTransparent";

    /// Theme appearance override key for window transparency.
    public static final String THEME_APPEARANCE_WINDOW_TRANSPARENT = "windowTransparent";

    /// Theme appearance override key for the primary background source.
    public static final String THEME_APPEARANCE_BACKGROUND = "background";

    /// Theme appearance override key for background opacity.
    public static final String THEME_APPEARANCE_BACKGROUND_OPACITY = "backgroundOpacity";

    /// Gson instance used for launcher settings and related settings objects that depend on JavaFX properties.
    public static final Gson SETTINGS_GSON = new GsonBuilder()
            .registerTypeAdapter(Path.class, PathTypeAdapter.INSTANCE)
            .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
            .registerTypeAdapter(ObservableList.class, new ObservableListCreator())
            .registerTypeAdapter(ObservableSet.class, new ObservableSetCreator())
            .registerTypeAdapter(ObservableMap.class, new ObservableMapCreator())
            .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
            .registerTypeAdapter(Paint.class, new PaintAdapter())
            .setPrettyPrinting()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    /// Deserializes launcher settings from JSON.
    ///
    /// @param json the JSON object to read
    /// @return the deserialized launcher settings
    /// @throws JsonParseException if the JSON cannot be deserialized as launcher settings
    public static LauncherSettings fromJson(JsonObject json) throws JsonParseException {
        return SETTINGS_GSON.fromJson(json, LauncherSettings.class);
    }

    /// Creates empty launcher settings using current defaults.
    public LauncherSettings() {
        tracker.markDirty(schema);
        register();
    }

    /// Serializes these launcher settings to formatted JSON.
    public String toJson() {
        return SETTINGS_GSON.toJson(this);
    }

    // Properties

    /// The schema used by this launcher settings file.
    @SerializedName(JsonSchema.PROPERTY_SCHEMA)
    private final ObjectProperty<JsonSchema> schema = new SimpleObjectProperty<>(CURRENT_SCHEMA);

    /// Returns the schema property.
    public ObjectProperty<JsonSchema> schemaProperty() {
        return schema;
    }

    /// Returns the schema used by this launcher settings file.
    @Override
    public JsonSchema getSchema() {
        return schema.get();
    }

    /// Sets the schema used by this launcher settings file.
    @Override
    public void setSchema(JsonSchema schema) {
        this.schema.set(Objects.requireNonNull(schema));
    }

    /// Whether this launcher settings object may be saved back to `config/launcher-settings.json`.
    private transient boolean savable = true;

    /// Whether the next successful save should back up the current `config/launcher-settings.json` first.
    private transient boolean backupOnNextSave = false;

    /// Returns whether this launcher settings object may be saved back to `config/launcher-settings.json`.
    @Override
    public boolean isSavable() {
        return savable;
    }

    /// Sets whether this launcher settings object may be saved back to `config/launcher-settings.json`.
    @Override
    public void setSavable(boolean savable) {
        this.savable = savable;
    }

    /// Returns whether the next successful save should back up the current `config/launcher-settings.json` first.
    @Override
    public boolean isBackupOnNextSave() {
        return backupOnNextSave;
    }

    /// Sets whether the next successful save should back up the current `config/launcher-settings.json` first.
    @Override
    public void setBackupOnNextSave(boolean backupOnNextSave) {
        this.backupOnNextSave = backupOnNextSave;
    }

    /// The launcher UI language.
    @SerializedName("language")
    private final ObjectProperty<SupportedLocale> language = new SimpleObjectProperty<>(SupportedLocale.DEFAULT);

    /// Returns the launcher UI language property.
    public ObjectProperty<SupportedLocale> languageProperty() {
        return language;
    }

    /// Whether preview builds are accepted by update checks.
    @SerializedName("acceptPreviewUpdate")
    private final BooleanProperty acceptPreviewUpdate = new SimpleBooleanProperty(false);

    /// Returns the preview update opt-in property.
    public BooleanProperty acceptPreviewUpdateProperty() {
        return acceptPreviewUpdate;
    }

    /// Whether automatic update dialogs are disabled.
    @SerializedName("disableAutoShowUpdateDialog")
    private final BooleanProperty disableAutoShowUpdateDialog = new SimpleBooleanProperty(false);

    /// Returns the automatic update dialog disable property.
    public BooleanProperty disableAutoShowUpdateDialogProperty() {
        return disableAutoShowUpdateDialog;
    }

    /// Whether April Fools features are disabled.
    @SerializedName("disableAprilFools")
    private final BooleanProperty disableAprilFools = new SimpleBooleanProperty(false);

    /// Returns the April Fools disable property.
    public BooleanProperty disableAprilFoolsProperty() {
        return disableAprilFools;
    }

    /// The common Minecraft directory selection mode.
    @SerializedName("commonDirectoryType")
    private final ObjectProperty<EnumCommonDirectory> commonDirectoryType = new RawPreservingObjectProperty<>(EnumCommonDirectory.DEFAULT);

    /// Returns the common Minecraft directory selection mode property.
    public ObjectProperty<EnumCommonDirectory> commonDirectoryTypeProperty() {
        return commonDirectoryType;
    }

    /// The custom common Minecraft directory path.
    @SerializedName("commonDirectory")
    private final StringProperty commonDirectory = new SimpleStringProperty();

    /// Returns the custom common Minecraft directory property.
    public StringProperty commonDirectoryProperty() {
        return commonDirectory;
    }

    /// Returns the default common Minecraft directory path.
    public static String getDefaultCommonDirectory() {
        return Metadata.MINECRAFT_DIRECTORY.toString();
    }

    /// Resolves the effective common Minecraft directory from the current directory settings.
    ///
    /// @return the effective directory path, or `null` when the configured mode is not recognized
    public String getResolvedCommonDirectory() {
        EnumCommonDirectory type = commonDirectoryType.get();
        String customPath = commonDirectory.get();

        return type == EnumCommonDirectory.CUSTOM && StringUtils.isNotBlank(customPath)
                ? customPath
                : getDefaultCommonDirectory();
    }

    /// The maximum number of log lines kept in log views.
    @SerializedName("logLines")
    private final ObjectProperty<@Nullable Integer> logLines = new SimpleObjectProperty<>();

    /// Returns the log line limit property.
    public ObjectProperty<@Nullable Integer> logLinesProperty() {
        return logLines;
    }

    // UI

    // Theme selection

    /// The installed theme selected by the launcher, or `null` when older settings do not contain a theme reference.
    @SerializedName("selectedTheme")
    private final ObjectProperty<@Nullable ThemeReference> selectedTheme =
            new SimpleObjectProperty<>(DEFAULT_THEME_REFERENCE);

    /// Returns the selected installed theme property.
    public ObjectProperty<@Nullable ThemeReference> selectedThemeProperty() {
        return selectedTheme;
    }

    /// Returns the selected installed theme, falling back to the built-in default theme.
    public ThemeReference getSelectedThemeOrDefault() {
        return Objects.requireNonNullElse(selectedTheme.get(), DEFAULT_THEME_REFERENCE);
    }

    // Theme appearance overrides

    /// Theme appearance setting keys overridden by the launcher.
    @SerializedName("themeAppearanceOverrides")
    private final ObservableSet<String> themeAppearanceOverrides = FXCollections.observableSet();

    /// Returns the theme appearance setting keys overridden by the launcher.
    public ObservableSet<String> getThemeAppearanceOverrides() {
        return themeAppearanceOverrides;
    }

    // Theme appearance values

    /// The configured theme brightness mode identifier.
    @SerializedName("themeBrightnessMode")
    private final StringProperty themeBrightnessMode = new SimpleStringProperty("auto");

    /// Returns the theme brightness mode property.
    public StringProperty themeBrightnessModeProperty() {
        return themeBrightnessMode;
    }

    /// The custom launcher theme color preserved when dynamic color extraction is used.
    @SerializedName("customThemeColor")
    private final ObjectProperty<ThemeColor> customThemeColor = new SimpleObjectProperty<>(ThemeColor.DEFAULT);

    /// Returns the custom launcher theme color property.
    public ObjectProperty<ThemeColor> customThemeColorProperty() {
        return customThemeColor;
    }

    /// The source used to choose the launcher Monet theme color seed.
    @SerializedName("themeColorType")
    private final ObjectProperty<ThemeColorType> themeColorType = new RawPreservingObjectProperty<>(ThemeColorType.DEFAULT);

    /// Returns the launcher theme color source type property.
    public ObjectProperty<ThemeColorType> themeColorTypeProperty() {
        return themeColorType;
    }

    /// The MonetFX color style used to generate the launcher color scheme.
    @SerializedName("themeColorStyle")
    private final ObjectProperty<ColorStyle> themeColorStyle = new RawPreservingObjectProperty<>(ColorStyle.FIDELITY);

    /// Returns the launcher theme color style property.
    public ObjectProperty<ColorStyle> themeColorStyleProperty() {
        return themeColorStyle;
    }

    /// Whether the launcher title bar is transparent.
    @SerializedName("titleBarTransparent")
    private final BooleanProperty titleBarTransparent = new SimpleBooleanProperty(false);

    /// Returns the transparent title-bar property.
    public BooleanProperty titleBarTransparentProperty() {
        return titleBarTransparent;
    }

    /// Whether translucent launcher backgrounds reveal content behind the window.
    @SerializedName("windowTransparent")
    private final BooleanProperty windowTransparent = new SimpleBooleanProperty(false);

    /// Returns the window transparency property.
    public BooleanProperty windowTransparentProperty() {
        return windowTransparent;
    }

    // Background source

    /// The launcher background source type.
    @SerializedName("backgroundType")
    private final ObjectProperty<BackgroundType> backgroundType = new RawPreservingObjectProperty<>(BackgroundType.DEFAULT);

    /// Returns the launcher background source type property.
    public ObjectProperty<BackgroundType> backgroundTypeProperty() {
        return backgroundType;
    }

    /// The selected built-in launcher wallpaper ID.
    @SerializedName("builtinBackgroundId")
    private final StringProperty builtinBackgroundId = new SimpleStringProperty(BuiltinBackground.FALLBACK.id());

    /// Returns the selected built-in launcher wallpaper ID property.
    public StringProperty builtinBackgroundIdProperty() {
        return builtinBackgroundId;
    }

    /// The local custom launcher background image path.
    @SerializedName("customBackgroundImagePath")
    private final StringProperty customBackgroundImagePath = new SimpleStringProperty();

    /// Returns the local custom launcher background image path property.
    public StringProperty customBackgroundImagePathProperty() {
        return customBackgroundImagePath;
    }

    /// The remote network launcher background image URL.
    @SerializedName("networkBackgroundImageUrl")
    private final StringProperty networkBackgroundImageUrl = new SimpleStringProperty();

    /// Returns the remote network launcher background image URL property.
    public StringProperty networkBackgroundImageUrlProperty() {
        return networkBackgroundImageUrl;
    }

    /// The custom launcher background paint.
    @SerializedName("customBackgroundPaint")
    private final ObjectProperty<@Nullable Paint> customBackgroundPaint = new SimpleObjectProperty<>();

    /// Returns the custom launcher background paint property.
    public ObjectProperty<@Nullable Paint> customBackgroundPaintProperty() {
        return customBackgroundPaint;
    }

    // Background appearance

    /// The launcher background opacity value.
    @SerializedName("backgroundOpacity")
    private final DoubleProperty backgroundOpacity = new SimpleDoubleProperty(1.0);

    /// Returns the custom launcher background opacity value property.
    public DoubleProperty backgroundOpacityProperty() {
        return backgroundOpacity;
    }

    // Background loading

    /// The URL image cache policy for network launcher backgrounds.
    @SerializedName("networkBackgroundImageCachePolicy")
    private final ObjectProperty<NetworkBackgroundImageCachePolicy> networkBackgroundImageCachePolicy =
            new RawPreservingObjectProperty<>(NetworkBackgroundImageCachePolicy.ENABLED);

    /// Returns the URL image cache policy for network launcher backgrounds.
    public ObjectProperty<NetworkBackgroundImageCachePolicy> networkBackgroundImageCachePolicyProperty() {
        return networkBackgroundImageCachePolicy;
    }

    /// The fallback source used when the selected launcher background cannot be loaded.
    @SerializedName("backgroundFallbackType")
    private final ObjectProperty<BackgroundType> backgroundFallbackType =
            new RawPreservingObjectProperty<>(BackgroundType.BUILTIN);

    /// Returns the launcher background fallback source type property.
    public ObjectProperty<BackgroundType> backgroundFallbackTypeProperty() {
        return backgroundFallbackType;
    }

    /// The fallback paint used when the selected launcher background cannot be loaded.
    @SerializedName("backgroundFallbackPaint")
    private final ObjectProperty<Paint> backgroundFallbackPaint = new SimpleObjectProperty<>(Color.WHITE);

    /// Returns the launcher background fallback paint property.
    public ObjectProperty<Paint> backgroundFallbackPaintProperty() {
        return backgroundFallbackPaint;
    }

    /// How the launcher displays its window while the selected background is loading.
    @SerializedName("backgroundLoadPolicy")
    private final ObjectProperty<BackgroundLoadPolicy> backgroundLoadPolicy =
            new RawPreservingObjectProperty<>(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND);

    /// Returns the launcher background loading policy property.
    public ObjectProperty<BackgroundLoadPolicy> backgroundLoadPolicyProperty() {
        return backgroundLoadPolicy;
    }

    // Fonts

    /// The font family used by launcher log views.
    @SerializedName("logFontFamily")
    private final StringProperty logFontFamily = new SimpleStringProperty();

    /// Returns the launcher log font family property.
    public StringProperty logFontFamilyProperty() {
        return logFontFamily;
    }

    /// The launcher log font size.
    @SerializedName("logFontSize")
    private final DoubleProperty logFontSize = new SimpleDoubleProperty(12);

    /// Returns the launcher log font size property.
    public DoubleProperty logFontSizeProperty() {
        return logFontSize;
    }

    /// The font family used by launcher chrome.
    @SerializedName("launcherFontFamily")
    private final StringProperty launcherFontFamily = new SimpleStringProperty();

    /// Returns the launcher chrome font family property.
    public StringProperty launcherFontFamilyProperty() {
        return launcherFontFamily;
    }

    // General UI

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

    // Networks

    /// Whether HMCL automatically selects the number of download threads.
    @SerializedName("autoDownloadThreads")
    private final BooleanProperty autoDownloadThreads = new SimpleBooleanProperty(true);

    /// Returns the automatic download thread count property.
    public BooleanProperty autoDownloadThreadsProperty() {
        return autoDownloadThreads;
    }

    /// The configured number of download threads.
    @SerializedName("downloadThreads")
    private final IntegerProperty downloadThreads = new SimpleIntegerProperty(64);

    /// Returns the download thread count property.
    public IntegerProperty downloadThreadsProperty() {
        return downloadThreads;
    }

    /// The selected game version list download source.
    @SerializedName("versionListSource")
    private final ObjectProperty<DownloadSource> versionListSource = new RawPreservingObjectProperty<>(DownloadSource.DEFAULT);

    /// Returns the selected game version list download source property.
    public ObjectProperty<DownloadSource> versionListSourceProperty() {
        return versionListSource;
    }

    /// The selected file download source.
    @SerializedName("fileDownloadSource")
    private final ObjectProperty<DownloadSource> fileDownloadSource = new RawPreservingObjectProperty<>(DownloadSource.DEFAULT);

    /// Returns the selected file download source property.
    public ObjectProperty<DownloadSource> fileDownloadSourceProperty() {
        return fileDownloadSource;
    }

    /// The selected default add-on source ID.
    @SerializedName("defaultAddonSource")
    private final StringProperty defaultAddonSource = new SimpleStringProperty("modrinth");

    /// Returns the selected default add-on source ID property.
    public StringProperty defaultAddonSourceProperty() {
        return defaultAddonSource;
    }

    /// Whether proxy authentication is enabled.
    @SerializedName("hasProxyAuth")
    private final BooleanProperty hasProxyAuth = new SimpleBooleanProperty();

    /// Returns the proxy authentication enable property.
    public BooleanProperty hasProxyAuthProperty() {
        return hasProxyAuth;
    }

    /// The configured network proxy selection mode.
    @SerializedName("proxyType")
    private final ObjectProperty<ProxyType> proxyType = new SimpleObjectProperty<>(ProxyType.SYSTEM);

    /// Returns the network proxy selection mode property.
    public ObjectProperty<ProxyType> proxyTypeProperty() {
        return proxyType;
    }

    /// The configured network proxy host.
    @SerializedName("proxyHost")
    private final StringProperty proxyHost = new SimpleStringProperty();

    /// Returns the network proxy host property.
    public StringProperty proxyHostProperty() {
        return proxyHost;
    }

    /// The configured network proxy port.
    @SerializedName("proxyPort")
    private final IntegerProperty proxyPort = new SimpleIntegerProperty();

    /// Returns the network proxy port property.
    public IntegerProperty proxyPortProperty() {
        return proxyPort;
    }

    /// The configured proxy authentication username.
    @SerializedName("proxyUser")
    private final StringProperty proxyUser = new SimpleStringProperty();

    /// Returns the proxy authentication username property.
    public StringProperty proxyUserProperty() {
        return proxyUser;
    }

    /// The configured proxy authentication password.
    @SerializedName("proxyPassword")
    private final StringProperty proxyPassword = new SimpleStringProperty();

    /// Returns the proxy authentication password property.
    public StringProperty proxyPasswordProperty() {
        return proxyPassword;
    }

    /// The selected game directory ID.
    ///
    /// This field is owned by [GameDirectoryManager]. Code outside [GameDirectoryManager] should not modify it directly.
    @SerializedName(PROPERTY_SELECTED_GAME_DIRECTORY)
    private final ObjectProperty<@Nullable GameDirectoryID> selectedGameDirectory =
            new SimpleObjectProperty<>(this, PROPERTY_SELECTED_GAME_DIRECTORY);

    /// Returns the selected game directory ID property.
    ///
    /// This property is exposed for persistence and [GameDirectoryManager] integration. Code outside [GameDirectoryManager]
    /// should use `GameDirectoryManager.setSelectedGameDirectory` instead of modifying this property directly.
    public ObjectProperty<@Nullable GameDirectoryID> selectedGameDirectoryProperty() {
        return selectedGameDirectory;
    }

    /// The default game setting preset ID.
    @SerializedName(PROPERTY_DEFAULT_GAME_SETTINGS_PRESET)
    private final ObjectProperty<@Nullable GameSettingsPresetID> defaultGameSettingsPreset =
            new SimpleObjectProperty<>(this, PROPERTY_DEFAULT_GAME_SETTINGS_PRESET);

    /// Returns the default game setting preset ID property.
    public ObjectProperty<@Nullable GameSettingsPresetID> defaultGameSettingsPresetProperty() {
        return defaultGameSettingsPreset;
    }

    /// Selected instance IDs keyed by game directory ID.
    ///
    /// This field is owned by [GameDirectoryManager]. Code outside [GameDirectoryManager] should not modify it directly.
    @SerializedName(PROPERTY_SELECTED_INSTANCE)
    private final ObservableMap<GameDirectoryID, String> selectedInstance = FXCollections.observableHashMap();

    /// Returns selected instance IDs keyed by game directory ID.
    ///
    /// The map stores persisted selected instance values by game directory ID.
    public ObservableMap<GameDirectoryID, String> getSelectedInstance() {
        return selectedInstance;
    }

    /// Returns the selected instance ID for the given game directory ID.
    ///
    /// The value is loaded by the game repository for the matching game directory.
    public @Nullable String getSelectedInstance(@Nullable GameDirectoryID gameDirectoryId) {
        return gameDirectoryId != null ? selectedInstance.get(gameDirectoryId) : null;
    }

    /// Sets the selected instance ID for the given game directory ID.
    ///
    /// Blank values remove the persisted selected instance entry.
    public void setSelectedInstance(@Nullable GameDirectoryID gameDirectoryId, @Nullable String selectedInstance) {
        if (gameDirectoryId == null) {
            return;
        }

        if (StringUtils.isBlank(selectedInstance)) {
            this.selectedInstance.remove(gameDirectoryId);
        } else {
            this.selectedInstance.put(gameDirectoryId, selectedInstance);
        }
    }

    /// Instance group names keyed by a composite game-directory and group identifier.
    @SerializedName(PROPERTY_INSTANCE_GROUP_NAMES)
    private final ObservableMap<String, String> instanceGroupNames = FXCollections.observableHashMap();

    /// Instance group membership keyed by a composite game-directory and instance identifier.
    @SerializedName(PROPERTY_INSTANCE_GROUP_MEMBERSHIP)
    private final ObservableMap<String, String> instanceGroupMembership = FXCollections.observableHashMap();

    /// Returns the observable instance group name store.
    public ObservableMap<String, String> getInstanceGroupNames() {
        return instanceGroupNames;
    }

    /// Returns the observable instance group membership store.
    public ObservableMap<String, String> getInstanceGroupMembership() {
        return instanceGroupMembership;
    }

    /// Returns the groups configured for a game directory, ordered by name.
    public @Unmodifiable List<InstanceGroup> getInstanceGroups(GameDirectoryID gameDirectoryId) {
        String prefix = instanceGroupKeyPrefix(gameDirectoryId);
        return instanceGroupNames.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> new InstanceGroup(entry.getKey().substring(prefix.length()), entry.getValue()))
                .sorted(Comparator.comparing(InstanceGroup::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InstanceGroup::name)
                        .thenComparing(InstanceGroup::id))
                .toList();
    }

    /// Creates an empty group and returns its stable identifier.
    public String createInstanceGroup(GameDirectoryID gameDirectoryId, String name) {
        String normalizedName = validateInstanceGroupName(gameDirectoryId, null, name);
        String groupId = UUID.randomUUID().toString();
        instanceGroupNames.put(instanceGroupKey(gameDirectoryId, groupId), normalizedName);
        return groupId;
    }

    /// Renames an existing group.
    public void renameInstanceGroup(GameDirectoryID gameDirectoryId, String groupId, String name) {
        String key = instanceGroupKey(gameDirectoryId, groupId);
        if (!instanceGroupNames.containsKey(key)) {
            throw new IllegalArgumentException("Unknown instance group: " + groupId);
        }
        instanceGroupNames.put(key, validateInstanceGroupName(gameDirectoryId, groupId, name));
    }

    /// Deletes a group and leaves its former instances ungrouped.
    public void deleteInstanceGroup(GameDirectoryID gameDirectoryId, String groupId) {
        instanceGroupNames.remove(instanceGroupKey(gameDirectoryId, groupId));
        String prefix = instanceMembershipKeyPrefix(gameDirectoryId);
        instanceGroupMembership.entrySet().removeIf(entry ->
                entry.getKey().startsWith(prefix) && groupId.equals(entry.getValue()));
    }

    /// Returns the group identifier assigned to an instance, or `null` when it is ungrouped.
    public @Nullable String getInstanceGroup(GameDirectoryID gameDirectoryId, String instanceId) {
        @Nullable String groupId = instanceGroupMembership.get(instanceMembershipKey(gameDirectoryId, instanceId));
        return groupId != null && instanceGroupNames.containsKey(instanceGroupKey(gameDirectoryId, groupId))
                ? groupId
                : null;
    }

    /// Assigns an instance to a group, or removes its assignment when `groupId` is `null`.
    public void setInstanceGroup(GameDirectoryID gameDirectoryId, String instanceId, @Nullable String groupId) {
        String membershipKey = instanceMembershipKey(gameDirectoryId, instanceId);
        if (groupId == null) {
            instanceGroupMembership.remove(membershipKey);
        } else {
            if (!instanceGroupNames.containsKey(instanceGroupKey(gameDirectoryId, groupId))) {
                throw new IllegalArgumentException("Unknown instance group: " + groupId);
            }
            instanceGroupMembership.put(membershipKey, groupId);
        }
    }

    /// Moves a stored group assignment after an instance is renamed.
    public void renameInstanceGroupMember(GameDirectoryID gameDirectoryId, String oldInstanceId, String newInstanceId) {
        @Nullable String groupId = instanceGroupMembership.remove(instanceMembershipKey(gameDirectoryId, oldInstanceId));
        if (groupId != null) {
            instanceGroupMembership.put(instanceMembershipKey(gameDirectoryId, newInstanceId), groupId);
        }
    }

    /// Removes any stored group assignment for an instance.
    public void removeInstanceGroupMember(GameDirectoryID gameDirectoryId, String instanceId) {
        instanceGroupMembership.remove(instanceMembershipKey(gameDirectoryId, instanceId));
    }

    /// Validates and normalizes a group name for a game directory.
    private String validateInstanceGroupName(GameDirectoryID gameDirectoryId, @Nullable String currentGroupId, String name) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Instance group name cannot be blank");
        }
        boolean duplicate = getInstanceGroups(gameDirectoryId).stream()
                .anyMatch(group -> !group.id().equals(currentGroupId) && group.name().equalsIgnoreCase(normalizedName));
        if (duplicate) {
            throw new IllegalArgumentException("Duplicate instance group name: " + normalizedName);
        }
        return normalizedName;
    }

    /// Returns the composite key prefix for groups in a game directory.
    private static String instanceGroupKeyPrefix(GameDirectoryID gameDirectoryId) {
        return gameDirectoryId + "\n";
    }

    /// Returns the composite key for a group in a game directory.
    private static String instanceGroupKey(GameDirectoryID gameDirectoryId, String groupId) {
        return instanceGroupKeyPrefix(gameDirectoryId) + groupId;
    }

    /// Returns the composite key prefix for instance memberships in a game directory.
    private static String instanceMembershipKeyPrefix(GameDirectoryID gameDirectoryId) {
        return gameDirectoryId + "\n";
    }

    /// Returns the composite key for an instance membership in a game directory.
    private static String instanceMembershipKey(GameDirectoryID gameDirectoryId, String instanceId) {
        return instanceMembershipKeyPrefix(gameDirectoryId) + instanceId;
    }

    /// Identifies a persisted instance group and its display name.
    @NotNullByDefault
    public record InstanceGroup(String id, String name) {
        /// Creates an immutable instance group descriptor.
        public InstanceGroup {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
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

    /// The selected account reference.
    @SerializedName("selectedAccount")
    private final ObjectProperty<@Nullable AccountID> selectedAccount = new SimpleObjectProperty<>();

    /// Returns the selected account reference property.
    public ObjectProperty<@Nullable AccountID> selectedAccountProperty() {
        return selectedAccount;
    }

    /// JSON adapter for [LauncherSettings].
    public static final class Adapter extends ObservableSetting.Adapter<LauncherSettings> {
        /// Creates empty launcher settings for deserialization.
        @Override
        protected LauncherSettings createInstance() {
            return new LauncherSettings();
        }
    }
}
