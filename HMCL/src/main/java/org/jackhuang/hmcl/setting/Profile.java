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

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.EventPriority;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.util.GUID;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;

/**
 *
 * @author huangyuhui
 */
@JsonAdapter(Profile.Serializer.class)
public final class Profile implements Observable {
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private final HMCLGameRepository repository;

    /// The stable profile ID.
    private final ObjectProperty<GUID> id = new SimpleObjectProperty<>(this, "id", GUID.random());

    /// Returns the stable profile ID property.
    public ObjectProperty<GUID> idProperty() {
        return id;
    }

    /// Returns the stable profile ID.
    public GUID getId() {
        return id.get();
    }

    /// Sets the stable profile ID.
    public void setId(GUID id) {
        this.id.set(Objects.requireNonNull(id));
    }

    private final StringProperty selectedVersion = new SimpleStringProperty();

    public StringProperty selectedVersionProperty() {
        return selectedVersion;
    }

    public String getSelectedVersion() {
        return selectedVersion.get();
    }

    public void setSelectedVersion(String selectedVersion) {
        this.selectedVersion.set(selectedVersion);
    }

    private final ObjectProperty<Path> gameDir;

    public ObjectProperty<Path> gameDirProperty() {
        return gameDir;
    }

    public Path getGameDir() {
        return gameDir.get();
    }

    public void setGameDir(Path gameDir) {
        this.gameDir.set(gameDir);
    }

    private final SimpleStringProperty name;

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    private final BooleanProperty useRelativePath = new SimpleBooleanProperty(this, "useRelativePath", false);

    public BooleanProperty useRelativePathProperty() {
        return useRelativePath;
    }

    public boolean isUseRelativePath() {
        return useRelativePath.get();
    }

    public void setUseRelativePath(boolean useRelativePath) {
        this.useRelativePath.set(useRelativePath);
    }

    public Profile(String name, Path initialGameDir) {
        this(name, initialGameDir, null, false);
    }

    public Profile(String name, Path initialGameDir, @Nullable String selectedVersion, boolean useRelativePath) {
        this(GUID.random(), name, initialGameDir, selectedVersion, useRelativePath);
    }

    /// Creates a profile with an explicit stable ID.
    Profile(GUID id, String name, Path initialGameDir, @Nullable String selectedVersion, boolean useRelativePath) {
        this.id.set(Objects.requireNonNull(id));
        this.name = new SimpleStringProperty(this, "name", name);
        gameDir = new SimpleObjectProperty<>(this, "gameDir", initialGameDir);
        repository = new HMCLGameRepository(this, initialGameDir);
        this.selectedVersion.set(selectedVersion);
        this.useRelativePath.set(useRelativePath);

        gameDir.addListener((a, b, newValue) -> repository.changeDirectory(newValue));
        this.selectedVersion.addListener(o -> checkSelectedVersion());
        listenerHolder.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> checkSelectedVersion(), EventPriority.HIGHEST));

        addPropertyChangedListener(onInvalidating(this::invalidate));
    }

    private void checkSelectedVersion() {
        runInFX(() -> {
            if (!repository.isLoaded()) return;
            String newValue = selectedVersion.get();
            if (!repository.hasVersion(newValue)) {
                Optional<String> version = repository.getVersions().stream().findFirst().map(Version::getId);
                if (version.isPresent())
                    selectedVersion.setValue(version.get());
                else if (newValue != null)
                    selectedVersion.setValue(null);
            }
        });
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
                .append("gameDir", getGameDir())
                .append("name", getName())
                .append("useRelativePath", isUseRelativePath())
                .toString();
    }

    private void addPropertyChangedListener(InvalidationListener listener) {
        id.addListener(listener);
        name.addListener(listener);
        gameDir.addListener(listener);
        useRelativePath.addListener(listener);
        selectedVersion.addListener(listener);
    }

    private ObservableHelper observableHelper = new ObservableHelper(this);

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

    public record ProfileVersion(Profile profile, String version) {
    }

    public static final class Serializer implements JsonSerializer<Profile>, JsonDeserializer<Profile> {
        @Override
        public JsonElement serialize(Profile src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null)
                return JsonNull.INSTANCE;

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("id", context.serialize(src.getId(), GUID.class));
            jsonObject.addProperty("name", src.getName());
            jsonObject.addProperty("gameDir", src.getGameDir().toString());
            jsonObject.addProperty("useRelativePath", src.isUseRelativePath());
            jsonObject.addProperty("selectedMinecraftVersion", src.getSelectedVersion());

            return jsonObject;
        }

        @Override
        public Profile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject obj)) return null;
            GUID id = context.deserialize(obj.get("id"), GUID.class);
            if (id == null) {
                throw new JsonParseException("Profile ID cannot be null");
            }
            String gameDir = Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse("");

            return new Profile(id,
                    Optional.ofNullable(obj.get("name")).map(JsonElement::getAsString).orElse("Default"),
                    Path.of(gameDir),
                    Optional.ofNullable(obj.get("selectedMinecraftVersion")).map(JsonElement::getAsString).orElse(""),
                    Optional.ofNullable(obj.get("useRelativePath")).map(JsonElement::getAsBoolean).orElse(false));
        }

    }
}
