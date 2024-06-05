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
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jackhuang.hmcl.util.javafx.PropertyUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

@JsonAdapter(GlobalConfig.Serializer.class)
public final class GlobalConfig implements Cloneable, Observable {

    @Nullable
    public static GlobalConfig fromJson(String json) throws JsonParseException {
        GlobalConfig loaded = Config.CONFIG_GSON.fromJson(json, GlobalConfig.class);
        if (loaded == null) {
            return null;
        }
        GlobalConfig instance = new GlobalConfig();
        PropertyUtils.copyProperties(loaded, instance);
        instance.unknownFields.putAll(loaded.unknownFields);
        return instance;
    }

    private final IntegerProperty agreementVersion = new SimpleIntegerProperty();

    private final IntegerProperty platformPromptVersion = new SimpleIntegerProperty();

    private final IntegerProperty logRetention = new SimpleIntegerProperty();

    private final Map<String, Object> unknownFields = new HashMap<>();

    private final transient ObservableHelper helper = new ObservableHelper(this);

    public GlobalConfig() {
        PropertyUtils.attachListener(this, helper);
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
        return Config.CONFIG_GSON.toJson(this);
    }

    @Override
    public GlobalConfig clone() {
        return fromJson(this.toJson());
    }

    public int getAgreementVersion() {
        return agreementVersion.get();
    }

    public IntegerProperty agreementVersionProperty() {
        return agreementVersion;
    }

    public void setAgreementVersion(int agreementVersion) {
        this.agreementVersion.set(agreementVersion);
    }

    public int getPlatformPromptVersion() {
        return platformPromptVersion.get();
    }

    public IntegerProperty platformPromptVersionProperty() {
        return platformPromptVersion;
    }

    public void setPlatformPromptVersion(int platformPromptVersion) {
        this.platformPromptVersion.set(platformPromptVersion);
    }

    public int getLogRetention() {
        return logRetention.get();
    }

    public IntegerProperty logRetentionProperty() {
        return logRetention;
    }

    public void setLogRetention(int logRetention) {
        this.logRetention.set(logRetention);
    }

    public static final class Serializer implements JsonSerializer<GlobalConfig>, JsonDeserializer<GlobalConfig> {
        private static final Set<String> knownFields = new HashSet<>(Arrays.asList(
                "agreementVersion",
                "platformPromptVersion",
                "logRetention"
        ));

        @Override
        public JsonElement serialize(GlobalConfig src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return JsonNull.INSTANCE;
            }

            JsonObject jsonObject = new JsonObject();
            jsonObject.add("agreementVersion", context.serialize(src.getAgreementVersion()));
            jsonObject.add("platformPromptVersion", context.serialize(src.getPlatformPromptVersion()));
            jsonObject.add("logRetention", context.serialize(src.getLogRetention()));
            for (Map.Entry<String, Object> entry : src.unknownFields.entrySet()) {
                jsonObject.add(entry.getKey(), context.serialize(entry.getValue()));
            }

            return jsonObject;
        }

        @Override
        public GlobalConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject)) return null;

            JsonObject obj = (JsonObject) json;

            GlobalConfig config = new GlobalConfig();
            config.setAgreementVersion(Optional.ofNullable(obj.get("agreementVersion")).map(JsonElement::getAsInt).orElse(0));
            config.setPlatformPromptVersion(Optional.ofNullable(obj.get("platformPromptVersion")).map(JsonElement::getAsInt).orElse(0));
            config.setLogRetention(Optional.ofNullable(obj.get("logRetention")).map(JsonElement::getAsInt).orElse(20));

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (!knownFields.contains(entry.getKey())) {
                    config.unknownFields.put(entry.getKey(), context.deserialize(entry.getValue(), Object.class));
                }
            }

            return config;
        }
    }
}
