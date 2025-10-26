/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.*;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import org.jackhuang.hmcl.util.javafx.DirtyTracker;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.*;

/// @author Glavo
/// @see ObservableField
public abstract class ObservableSetting implements Observable {

    protected static <S extends ObservableSetting> List<ObservableField<S>> findFields(Class<S> clazz) {
        try {
            var allFields = new ArrayList<ObservableField<S>>();

            for (Class<?> current = clazz;
                 current != ObservableSetting.class;
                 current = current.getSuperclass()) {
                final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());

                Field[] fields = current.getDeclaredFields();
                for (Field field : fields) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers))
                        continue;

                    allFields.add(ObservableField.of(lookup, field));
                }
            }

            return List.copyOf(allFields);
        } catch (IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected transient final ObservableHelper helper = new ObservableHelper(this);
    protected transient final Map<String, JsonElement> unknownFields = new HashMap<>();
    protected transient final DirtyTracker tracker = new DirtyTracker();

    protected abstract List<? extends ObservableField<?>> getFields();

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    public static abstract class Adapter<T extends ObservableSetting>
            implements JsonSerializer<T>, JsonDeserializer<T> {

        protected abstract T createInstance();

        @Override
        public JsonElement serialize(T setting, Type typeOfSrc, JsonSerializationContext context) {
            if (setting == null)
                return JsonNull.INSTANCE;

            @SuppressWarnings("unchecked")
            List<ObservableField<T>> fields = (List<ObservableField<T>>) setting.getFields();

            JsonObject result = new JsonObject();
            for (var field : fields) {
                Observable observable = field.get(setting);
                if (setting.tracker.isDirty(observable)) {
                    field.serialize(result, setting, context);
                }
            }
            setting.unknownFields.forEach(result::add);
            return result;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull())
                return null;

            if (!json.isJsonObject())
                throw new JsonParseException("Config is not an object: " + json);

            T setting = createInstance();
            @SuppressWarnings("unchecked")
            List<ObservableField<T>> fields = (List<ObservableField<T>>) setting.getFields();

            var values = new LinkedHashMap<>(json.getAsJsonObject().asMap());
            for (ObservableField<T> field : fields) {
                JsonElement value = values.remove(field.getSerializedName());
                if (value == null) {
                    for (String alternateName : field.getAlternateNames()) {
                        value = values.remove(alternateName);
                        if (value != null)
                            break;
                    }
                }

                if (value != null) {
                    setting.tracker.markDirty(field.get(setting));
                    field.deserialize(setting, value, context);
                }
            }

            setting.unknownFields.putAll(values);
            return setting;
        }
    }
}
