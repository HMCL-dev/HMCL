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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.annotations.SerializedName;
import javafx.beans.Observable;
import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SetProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.jackhuang.hmcl.util.TypeUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// @author Glavo
public abstract class ObservableField<T> {

    public static <T> ObservableField<T> of(MethodHandles.Lookup lookup, Field field) {
        SerializedName serializedName = field.getAnnotation(SerializedName.class);
        if (serializedName == null)
            throw new IllegalArgumentException("Field " + field.getName() + " is missing @SerializedName annotation");

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
            return new CollectionField<>(serializedName.value(), varHandle, listType);
        } else if (SetProperty.class.isAssignableFrom(field.getType())) {
            Type setType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Set.class);
            if (!(setType instanceof ParameterizedType))
                throw new IllegalArgumentException("Cannot resolve the set type of " + field.getName());
            return new CollectionField<>(serializedName.value(), varHandle, setType);
        } else if (ObservableMap.class.isAssignableFrom(field.getType())) {
            Type mapType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Map.class);
            if (!(mapType instanceof ParameterizedType))
                throw new IllegalArgumentException("Cannot resolve the list map of " + field.getName());
            return new MapField<>(serializedName.value(), varHandle, mapType);
        } else if (Property.class.isAssignableFrom(field.getType())) {
            Type propertyType = TypeUtils.getSupertype(field.getGenericType(), field.getType(), Property.class);
            if (!(propertyType instanceof ParameterizedType))
                throw new IllegalArgumentException("Cannot resolve the element type of " + field.getName());
            Type elementType = ((ParameterizedType) propertyType).getActualTypeArguments()[0];
            return new PropertyField<>(serializedName.value(), varHandle, elementType);
        } else {
            throw new IllegalArgumentException("Field " + field.getName() + " is not a property or observable collection");
        }
    }

    protected final String serializedName;
    protected final VarHandle varHandle;

    private ObservableField(String serializedName, VarHandle varHandle) {
        this.serializedName = serializedName;
        this.varHandle = varHandle;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public Observable get(T value) {
        return (Observable) varHandle.get(value);
    }

    public abstract JsonElement serialize(T value, JsonSerializationContext context);

    public abstract void deserialize(T value, JsonElement element, JsonDeserializationContext context);

    private static final class PropertyField<T> extends ObservableField<T> {
        private final Type elementType;

        PropertyField(String serializedName, VarHandle varHandle, Type elementType) {
            super(serializedName, varHandle);
            this.elementType = elementType;
        }

        @Override
        public JsonElement serialize(T value, JsonSerializationContext context) {
            return context.serialize(((Property<?>) get(value)).getValue());
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void deserialize(T value, JsonElement element, JsonDeserializationContext context) {
            ((Property) get(value)).setValue(context.deserialize(element, elementType));
        }
    }

    private static final class CollectionField<T> extends ObservableField<T> {
        private final Type collectionType;

        CollectionField(String serializedName, VarHandle varHandle, Type collectionType) {
            super(serializedName, varHandle);
            this.collectionType = collectionType;
        }

        @Override
        public JsonElement serialize(T value, JsonSerializationContext context) {
            return context.serialize(get(value), collectionType);
        }

        @SuppressWarnings({"unchecked"})
        @Override
        public void deserialize(T value, JsonElement element, JsonDeserializationContext context) {
            Object deserialized = context.deserialize(element, collectionType);
            Object fieldValue = get(value);

            if (fieldValue instanceof ListProperty) {
                ((ListProperty<Object>) fieldValue).set(FXCollections.observableList((List<Object>) deserialized));
            } else if (fieldValue instanceof ObservableList) {
                ((ObservableList<Object>) fieldValue).setAll((List<Object>) deserialized);
            } else if (fieldValue instanceof SetProperty) {
                ((SetProperty<Object>) fieldValue).set(FXCollections.observableSet((Set<Object>) deserialized));
            } else {
                throw new JsonParseException("Unsupported field type: " + fieldValue.getClass());
            }
        }
    }

    private static final class MapField<T> extends ObservableField<T> {
        private final Type mapType;

        MapField(String serializedName, VarHandle varHandle, Type mapType) {
            super(serializedName, varHandle);
            this.mapType = mapType;
        }

        @Override
        public JsonElement serialize(T value, JsonSerializationContext context) {
            return context.serialize(get(value), mapType);
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
