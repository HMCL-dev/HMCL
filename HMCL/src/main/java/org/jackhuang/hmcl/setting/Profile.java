/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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

import com.google.gson.*;
import javafx.beans.InvalidationListener;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.HMCLDependencyManager;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.ImmediateObjectProperty;
import org.jackhuang.hmcl.util.ImmediateStringProperty;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 *
 * @author huangyuhui
 */
public final class Profile {

    private final HMCLGameRepository repository;
    private final ModManager modManager;

    private final ImmediateObjectProperty<File> gameDirProperty;

    public ImmediateObjectProperty<File> gameDirProperty() {
        return gameDirProperty;
    }

    public File getGameDir() {
        return gameDirProperty.get();
    }

    public void setGameDir(File gameDir) {
        gameDirProperty.set(gameDir);
    }

    private final ImmediateObjectProperty<VersionSetting> globalProperty = new ImmediateObjectProperty<>(this, "global", new VersionSetting());

    public ImmediateObjectProperty<VersionSetting> globalProperty() {
        return globalProperty;
    }

    public VersionSetting getGlobal() {
        return globalProperty.get();
    }

    public void setGlobal(VersionSetting global) {
        if (global == null)
            global = new VersionSetting();
        globalProperty.set(global);
    }

    private final ImmediateStringProperty nameProperty;

    public ImmediateStringProperty nameProperty() {
        return nameProperty;
    }

    public String getName() {
        return nameProperty.get();
    }

    public void setName(String name) {
        nameProperty.set(name);
    }

    public Profile() {
        this("Default");
    }

    public Profile(String name) {
        this(name, new File(".minecraft"));
    }

    public Profile(String name, File initialGameDir) {
        nameProperty = new ImmediateStringProperty(this, "name", name);
        gameDirProperty = new ImmediateObjectProperty<>(this, "gameDir", initialGameDir);
        repository = new HMCLGameRepository(this, initialGameDir);
        modManager = new ModManager(repository);

        gameDirProperty.addListener((a, b, newValue) -> repository.changeDirectory(newValue));
    }

    public HMCLGameRepository getRepository() {
        return repository;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public HMCLDependencyManager getDependency() {
        return new HMCLDependencyManager(this, Settings.INSTANCE.getDownloadProvider(), Settings.INSTANCE.getProxy());
    }

    public VersionSetting getVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs == null || vs.isUsesGlobal()) {
            getGlobal().setGlobal(true); // always keep global.isGlobal = true
            return getGlobal();
        } else
            return vs;
    }

    public boolean isVersionGlobal(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        return vs == null || vs.isUsesGlobal();
    }

    /**
     * Make version use self version settings instead of the global one.
     * @param id the version id.
     * @return specialized version setting, null if given version does not exist.
     */
    public VersionSetting specializeVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs == null)
            vs = repository.createVersionSetting(id);
        if (vs == null)
            return null;
        vs.setUsesGlobal(false);
        return vs;
    }

    public void globalizeVersionSetting(String id) {
        VersionSetting vs = repository.getVersionSetting(id);
        if (vs != null)
            vs.setUsesGlobal(true);
    }

    public void addPropertyChangedListener(InvalidationListener listener) {
        nameProperty.addListener(listener);
        globalProperty.addListener(listener);
        gameDirProperty.addListener(listener);
    }

    public static final class Serializer implements JsonSerializer<Profile>, JsonDeserializer<Profile> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public JsonElement serialize(Profile src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null)
                return JsonNull.INSTANCE;

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("global", context.serialize(src.getGlobal()));
            jsonObject.addProperty("gameDir", src.getGameDir().getPath());

            return jsonObject;
        }

        @Override
        public Profile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json == JsonNull.INSTANCE || !(json instanceof JsonObject)) return null;
            JsonObject obj = (JsonObject) json;
            String gameDir = Optional.ofNullable(obj.get("gameDir")).map(JsonElement::getAsString).orElse("");

            Profile profile = new Profile("Default", new File(gameDir));
            profile.setGlobal(context.deserialize(obj.get("global"), VersionSetting.class));
            return profile;
        }

    }
}
