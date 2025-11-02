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
import com.google.gson.annotations.SerializedName;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SetProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import org.jackhuang.hmcl.util.TypeUtils;
import org.jackhuang.hmcl.util.javafx.DirtyTracker;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/// Represents a settings object with multiple [Observable] fields.
///
/// All instance fields in this object, unless marked as `transient`, are considered observable fields.
/// The field types should be subclasses of one of [javafx.beans.property.Property], [javafx.collections.ObservableList], [javafx.collections.ObservableSet], or [javafx.collections.ObservableMap].
///
/// This class implements the [Observable] interface.
/// If any field in a settings object changes, all listeners installed via [#addListener(InvalidationListener)] will be triggered.
///
/// For each observable field, this object tracks whether it has been modified. When serializing, fields that have never been modified will not be serialized by default.
///
/// All subclasses of this class must call [#register()] once in their constructor.
///
/// @author Glavo
public abstract class ObservableSetting implements Observable {

    private static final ClassValue<List<? extends ObservableField<?>>> FIELDS = new ClassValue<>() {
        @Override
        protected List<? extends ObservableField<?>> computeValue(@NotNull Class<?> type) {
            if (!ObservableSetting.class.isAssignableFrom(type))
                throw new AssertionError("Type: " + type);

            try {
                var allFields = new ArrayList<ObservableField<?>>();

                for (Class<?> current = type;
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

                return allFields;
            } catch (IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    };

    protected transient final ObservableHelper helper = new ObservableHelper(this);
    protected transient final Map<String, JsonElement> unknownFields = new HashMap<>();
    protected transient final DirtyTracker tracker = new DirtyTracker();

    private boolean registered = false;

    protected final void register() {
        if (registered)
            return;

        registered = true;

        @SuppressWarnings("unchecked")
        var fields = (List<ObservableField<ObservableSetting>>) FIELDS.get(this.getClass());
        for (var field : fields) {
            Observable observable = field.get(this);
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

    private static sealed abstract class ObservableField<T> {

        public static <T> ObservableField<T> of(MethodHandles.Lookup lookup, Field field) {
            String name;
            List<String> alternateNames;

            SerializedName serializedName = field.getAnnotation(SerializedName.class);
            if (serializedName == null) {
                name = field.getName();
                alternateNames = List.of();
            } else {
                name = serializedName.value();
                alternateNames = List.of(serializedName.alternate());
            }

            VarHandle varHandle;
            try {
                varHandle = lookup.unreflectVarHandle(field);
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }

            if (ObservableList.class.isAssignableFrom(field.getType())) {
                Type listType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), List.class);
                if (!(listType instanceof ParameterizedType))
                    throw new IllegalArgumentException("Cannot resolve the list type of " + field.getName());
                return new CollectionField<>(name, alternateNames, varHandle, listType, listType);
            } else if (ObservableSet.class.isAssignableFrom(field.getType())) {
                Type setType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Set.class);
                if (!(setType instanceof ParameterizedType))
                    throw new IllegalArgumentException("Cannot resolve the set type of " + field.getName());

                ParameterizedType listType = TypeUtils.newParameterizedTypeWithOwner(
                        null,
                        List.class,
                        ((ParameterizedType) setType).getActualTypeArguments()[0]
                );
                return new CollectionField<>(name, alternateNames, varHandle, setType, listType);
            } else if (ObservableMap.class.isAssignableFrom(field.getType())) {
                Type mapType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Map.class);
                if (!(mapType instanceof ParameterizedType))
                    throw new IllegalArgumentException("Cannot resolve the map type of " + field.getName());
                return new MapField<>(name, alternateNames, varHandle, mapType);
            } else if (Property.class.isAssignableFrom(field.getType())) {
                Type propertyType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Property.class);
                if (!(propertyType instanceof ParameterizedType))
                    throw new IllegalArgumentException("Cannot resolve the element type of " + field.getName());
                Type elementType = ((ParameterizedType) propertyType).getActualTypeArguments()[0];
                return new PropertyField<>(name, alternateNames, varHandle, elementType);
            } else {
                throw new IllegalArgumentException("Field " + field.getName() + " is not a property or observable collection");
            }
        }

        protected final String serializedName;
        protected final List<String> alternateNames;
        protected final VarHandle varHandle;

        private ObservableField(String serializedName, List<String> alternateNames, VarHandle varHandle) {
            this.serializedName = serializedName;
            this.alternateNames = alternateNames;
            this.varHandle = varHandle;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public List<String> getAlternateNames() {
            return alternateNames;
        }

        public Observable get(T value) {
            return (Observable) varHandle.get(value);
        }

        public abstract void serialize(JsonObject result, T value, JsonSerializationContext context);

        public abstract void deserialize(T value, JsonElement element, JsonDeserializationContext context);

        private static final class PropertyField<T> extends ObservableField<T> {
            private final Type elementType;

            PropertyField(String serializedName, List<String> alternate, VarHandle varHandle, Type elementType) {
                super(serializedName, alternate, varHandle);
                this.elementType = elementType;
            }

            @Override
            public void serialize(JsonObject result, T value, JsonSerializationContext context) {
                Property<?> property = (Property<?>) get(value);

                if (property instanceof RawPreservingProperty<?> rawPreserving) {
                    JsonElement rawJson = rawPreserving.getRawJson();
                    if (rawJson != null) {
                        result.add(getSerializedName(), rawJson);
                        return;
                    }
                }

                JsonElement serialized = context.serialize(property.getValue(), elementType);
                if (serialized != null && !serialized.isJsonNull())
                    result.add(getSerializedName(), serialized);
            }

            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public void deserialize(T value, JsonElement element, JsonDeserializationContext context) {
                Property property = (Property) get(value);

                try {
                    property.setValue(context.deserialize(element, elementType));
                } catch (Throwable e) {
                    if (property instanceof RawPreservingProperty<?>) {
                        ((RawPreservingProperty<?>) property).setRawJson(element);
                    } else {
                        throw e;
                    }
                }
            }
        }

        private static final class CollectionField<T> extends ObservableField<T> {
            private final Type collectionType;

            /// When deserializing a Set, we first deserialize it into a `List`, then put the elements into the Set.
            private final Type listType;

            CollectionField(String serializedName, List<String> alternate, VarHandle varHandle,
                            Type collectionType, Type listType) {
                super(serializedName, alternate, varHandle);
                this.collectionType = collectionType;
                this.listType = listType;
            }

            @Override
            public void serialize(JsonObject result, T value, JsonSerializationContext context) {
                result.add(getSerializedName(), context.serialize(get(value), collectionType));
            }

            @SuppressWarnings({"unchecked"})
            @Override
            public void deserialize(T value, JsonElement element, JsonDeserializationContext context) {
                List<?> deserialized = context.deserialize(element, listType);
                Object fieldValue = get(value);

                if (fieldValue instanceof ListProperty) {
                    ((ListProperty<Object>) fieldValue).set(FXCollections.observableList((List<Object>) deserialized));
                } else if (fieldValue instanceof ObservableList) {
                    ((ObservableList<Object>) fieldValue).setAll(deserialized);
                } else if (fieldValue instanceof SetProperty) {
                    ((SetProperty<Object>) fieldValue).set(FXCollections.observableSet(new HashSet<>(deserialized)));
                } else if (fieldValue instanceof ObservableSet) {
                    ObservableSet<Object> set = (ObservableSet<Object>) fieldValue;
                    set.clear();
                    set.addAll(deserialized);
                } else {
                    throw new JsonParseException("Unsupported field type: " + fieldValue.getClass());
                }
            }
        }

        private static final class MapField<T> extends ObservableField<T> {
            private final Type mapType;

            MapField(String serializedName, List<String> alternate, VarHandle varHandle, Type mapType) {
                super(serializedName, alternate, varHandle);
                this.mapType = mapType;
            }

            @Override
            public void serialize(JsonObject result, T value, JsonSerializationContext context) {
                result.add(getSerializedName(), context.serialize(get(value), mapType));
            }

            @SuppressWarnings({"unchecked"})
            @Override
            public void deserialize(T config, JsonElement element, JsonDeserializationContext context) {
                Map<Object, Object> deserialized = context.deserialize(element, mapType);
                ObservableMap<Object, Object> map = (ObservableMap<Object, Object>) varHandle.get(config);
                if (map instanceof MapProperty<?, ?>)
                    ((MapProperty<Object, Object>) map).set(FXCollections.observableMap(deserialized));
                else {
                    map.clear();
                    map.putAll(deserialized);
                }
            }
        }
    }

    public static abstract class Adapter<T extends ObservableSetting>
            implements JsonSerializer<T>, JsonDeserializer<T> {

        protected abstract T createInstance();

        @Override
        public JsonElement serialize(T setting, Type typeOfSrc, JsonSerializationContext context) {
            if (setting == null)
                return JsonNull.INSTANCE;

            @SuppressWarnings("unchecked")
            var fields = (List<ObservableField<T>>) FIELDS.get(setting.getClass());

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
            var fields = (List<ObservableField<T>>) FIELDS.get(setting.getClass());

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
