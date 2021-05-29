package org.jackhuang.hmcl.util.gson;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JsonTypeAdapterFactory implements TypeAdapterFactory {

    public static final JsonTypeAdapterFactory INSTANCE = new JsonTypeAdapterFactory();

    private <T> TypeAdapter<T> createForJsonType(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();
        JsonType jsonType = rawType.getDeclaredAnnotation(JsonType.class);
        if (jsonType == null)
            return null;
        JsonSubtype[] subtypes = jsonType.subtypes();
        Map<String, TypeAdapter<?>> labelTypeAdapterMap = new HashMap<>();
        Map<Class<?>, TypeAdapter<?>> classTypeAdapterMap = new HashMap<>();
        Map<Class<?>, JsonSubtype> classJsonSubtypeMap = new HashMap<>();
        for (JsonSubtype subtype : subtypes) {
            TypeAdapter<?> typeAdapter = gson.getDelegateAdapter(this, TypeToken.get(subtype.clazz()));
            labelTypeAdapterMap.put(subtype.name(), typeAdapter);
            classTypeAdapterMap.put(subtype.clazz(), typeAdapter);
            classJsonSubtypeMap.put(subtype.clazz(), subtype);
        }

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                Class<?> type = value.getClass();
                @SuppressWarnings("unchecked")
                TypeAdapter<T> delegate = (TypeAdapter<T>) classTypeAdapterMap.get(type);
                if (delegate == null) {
                    throw new JsonParseException("Cannot serialize " + type.getName() + ". Please check your @JsonType configuration");
                }
                JsonSubtype subtype = classJsonSubtypeMap.get(type);
                JsonObject jsonObject = delegate.toJsonTree(value).getAsJsonObject();
                if (jsonObject.has(jsonType.property())) {
                    throw new JsonParseException("Cannot serialize " + type.getName() + ". Because it has already defined a field named '" + jsonType.property() + "'");
                }
                jsonObject.add(jsonType.property(), new JsonPrimitive(subtype.name()));
                Streams.write(jsonObject, out);
            }

            @Override
            public T read(JsonReader in) {
                JsonElement jsonElement = Streams.parse(in);
                JsonElement typeLabelElement = jsonElement.getAsJsonObject().get(jsonType.property());
                if (typeLabelElement == null) {
                    throw new JsonParseException("Cannot deserialize " + type + ". Because it does not define a field named '" + jsonType.property() + "'");
                }
                String typeLabel = typeLabelElement.getAsString();
                @SuppressWarnings("unchecked")
                TypeAdapter<T> delegate = (TypeAdapter<T>) labelTypeAdapterMap.get(typeLabel);
                if (delegate == null) {
                    throw new JsonParseException("Cannot deserialize " + type + " with subtype '" + typeLabel + "'");
                }

                return delegate.fromJsonTree(jsonElement);
            }
        };
    }

    private <T> TypeAdapter<T> createForJsonSubtype(Gson gson, TypeToken<T> type) {
        Class<? super T> rawType = type.getRawType();
        if (rawType.getSuperclass() == null) return null;
        JsonType jsonType = rawType.getSuperclass().getDeclaredAnnotation(JsonType.class);
        if (jsonType == null)
            return null;
        JsonSubtype jsonSubtype = null;
        for (JsonSubtype subtype : jsonType.subtypes()) {
            if (subtype.clazz() == rawType) {
                jsonSubtype = subtype;
            }
        }
        if (jsonSubtype == null)
            return null;
        final JsonSubtype subtype = jsonSubtype;

        TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                Class<?> type = value.getClass();
                JsonObject jsonObject = delegate.toJsonTree(value).getAsJsonObject();
                if (jsonObject.has(jsonType.property())) {
                    throw new JsonParseException("Cannot serialize " + type.getName() + ". Because it has already defined a field named '" + jsonType.property() + "'");
                }
                jsonObject.add(jsonType.property(), new JsonPrimitive(subtype.name()));
                Streams.write(jsonObject, out);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                return delegate.read(in);
            }
        };
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        TypeAdapter<T> typeAdapter = createForJsonType(gson, type);
        if (typeAdapter == null)
            typeAdapter = createForJsonSubtype(gson, type);
        return typeAdapter;
    }
}
