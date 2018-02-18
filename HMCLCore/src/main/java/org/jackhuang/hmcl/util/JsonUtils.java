package org.jackhuang.hmcl.util;

import com.google.gson.JsonParseException;

public final class JsonUtils {

    private JsonUtils() {}

    public static <T> T fromNonNullJson(String json, Class<T> classOfT) throws JsonParseException {
        T parsed = Constants.GSON.fromJson(json, classOfT);
        if (parsed == null)
            throw new JsonParseException("Json object cannot be null.");
        return parsed;
    }
}
