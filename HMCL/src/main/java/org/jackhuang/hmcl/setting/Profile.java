/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;

/**
 *
 * @author huangyuhui
 */
@JsonAdapter(Profile.Serializer.class)
@NotNullByDefault
public final class Profile implements Observable {
    private final HMCLGameRepository repository;

    /// The stable profile ID.
    private final GUID id;

    /// Returns the stable profile ID.
    public GUID getId() {
        return id;
    }

    /// The game directory path.
    private final ObjectProperty<PortablePath> path;

    /// Returns the game directory path property.
    public ObjectProperty<PortablePath> pathProperty() {
        return path;
    }

    /// Returns the game directory path.
    public PortablePath getPath() {
        return path.get();
    }

    /// Sets the game directory path.
    public void setPath(PortablePath path) {
        this.path.set(Objects.requireNonNull(path));
    }

    /// The custom profile name, or `null` for profiles without a stored name.
    private final SimpleStringProperty name;

    /// Returns the custom profile name property.
    public StringProperty nameProperty() {
        return name;
    }

    /// Returns the custom profile name, or `null` when no name is stored.
    public @Nullable String getName() {
        return name.get();
    }

    /// Sets the custom profile name.
    public void setName(@Nullable String name) {
        this.name.set(name);
    }

    /// The migrated legacy game settings preset ID, or `null` when this profile uses the default preset.
    private final ObjectProperty<@Nullable GUID> legacyGameSettings;

    /// Returns the migrated legacy game settings preset ID property.
    public ObjectProperty<@Nullable GUID> legacyGameSettingsProperty() {
        return legacyGameSettings;
    }

    /// Returns the migrated legacy game settings preset ID, or `null` when this profile uses the default preset.
    public @Nullable GUID getLegacyGameSettings() {
        return legacyGameSettings.get();
    }

    /// Sets the migrated legacy game settings preset ID.
    public void setLegacyGameSettings(@Nullable GUID legacyGameSettings) {
        this.legacyGameSettings.set(legacyGameSettings);
    }

    /// Creates a profile.
    public Profile(GUID id, @Nullable String name, PortablePath path) {
        this(id, name, path, null);
    }

    /// Creates a profile.
    public Profile(GUID id, @Nullable String name, PortablePath path, @Nullable GUID legacyGameSettings) {
        this.id = Objects.requireNonNull(id);
        this.name = new SimpleStringProperty(this, "name", name);
        this.path = new SimpleObjectProperty<>(this, "path", Objects.requireNonNull(path));
        this.legacyGameSettings = new SimpleObjectProperty<>(this, "legacyGameSettings", legacyGameSettings);
        repository = new HMCLGameRepository(this, path.toPath());

        this.path.addListener((a, b, newValue) -> repository.changeDirectory(newValue.toPath()));

        addPropertyChangedListener(onInvalidating(this::invalidate));
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public DefaultDependencyManager getDependency() {
        return getDependency(DownloadProviders.getDownloadProvider());
    }

    public DefaultDependencyManager getDependency(DownloadProvider downloadProvider) {
        return new DefaultDependencyManager(repository, downloadProvider, HMCLCacheRepository.REPOSITORY);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("path", getPath())
                .append("name", getName())
                .toString();
    }

    private void addPropertyChangedListener(InvalidationListener listener) {
        name.addListener(listener);
        path.addListener(listener);
        legacyGameSettings.addListener(listener);
    }

    private final ObservableHelper observableHelper = new ObservableHelper(this);

    @Override
    public void addListener(InvalidationListener listener) {
        observableHelper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        observableHelper.removeListener(listener);
    }

    private void invalidate() {
        Platform.runLater(observableHelper::invalidate);
    }

    public record ProfileVersion(Profile profile, @Nullable String version) {
    }

    public static final class Serializer implements JsonSerializer<Profile>, JsonDeserializer<Profile> {
        @Override
        public JsonElement serialize(@Nullable Profile src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null)
                return JsonNull.INSTANCE;

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("id", context.serialize(src.getId(), GUID.class));
            if (src.getName() != null) {
                jsonObject.addProperty("name", src.getName());
            }
            jsonObject.add("path", context.serialize(src.getPath(), PortablePath.class));
            if (src.getLegacyGameSettings() != null) {
                jsonObject.add("legacyGameSettings", context.serialize(src.getLegacyGameSettings(), GUID.class));
            }

            return jsonObject;
        }

        @Override
        public @Nullable Profile deserialize(@Nullable JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject obj)) return null;
            GUID id = context.deserialize(obj.get("id"), GUID.class);
            if (id == null) {
                throw new JsonParseException("Profile ID cannot be null");
            } else if (GUID.NIL.equals(id)) {
                throw new JsonParseException("Profile ID cannot be nil");
            }
            PortablePath path = context.deserialize(obj.get("path"), PortablePath.class);
            if (path == null) {
                String gameDir = Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse("");
                path = PortablePath.of(gameDir);
            }

            return new Profile(id,
                    Optional.ofNullable(obj.get("name")).map(JsonElement::getAsString).orElse(null),
                    path,
                    context.deserialize(obj.get("legacyGameSettings"), GUID.class));
        }

    }
}
