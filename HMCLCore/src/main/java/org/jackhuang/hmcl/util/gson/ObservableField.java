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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// @author Glavo
public abstract class ObservableField<T> {

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
