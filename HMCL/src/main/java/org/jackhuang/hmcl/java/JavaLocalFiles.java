// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.java;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;

/**
 * @author Glavo
 */
public final class JavaLocalFiles {
    @JsonAdapter(Serializer.class)
    public abstract static class Local {
        private final String type;

        Local(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    public static final class LocalFile extends Local {
        private final String sha1;
        private final long size;

        public LocalFile(String sha1, long size) {
            super("file");
            this.sha1 = sha1;
            this.size = size;
        }

        public String getSha1() {
            return sha1;
        }

        public long getSize() {
            return size;
        }
    }

    public static final class LocalDirectory extends Local {
        public LocalDirectory() {
            super("directory");
        }
    }

    public static final class LocalLink extends Local {
        private final String target;

        public LocalLink(String target) {
            super("link");
            this.target = target;
        }

        public String getTarget() {
            return target;
        }
    }

    public static class Serializer implements JsonSerializer<Local>, JsonDeserializer<Local> {

        @Override
        public JsonElement serialize(Local src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", src.getType());
            if (src instanceof LocalFile) {
                obj.addProperty("sha1", ((LocalFile) src).getSha1());
                obj.addProperty("size", ((LocalFile) src).getSize());
            } else if (src instanceof LocalLink) {
                obj.addProperty("target", ((LocalLink) src).getTarget());
            }
            return obj;
        }

        @Override
        public Local deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject())
                throw new JsonParseException(json.toString());

            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("type"))
                throw new JsonParseException(json.toString());

            String type = obj.getAsJsonPrimitive("type").getAsString();

            try {
                switch (type) {
                    case "file": {
                        String sha1 = obj.getAsJsonPrimitive("sha1").getAsString();
                        long size = obj.getAsJsonPrimitive("size").getAsLong();
                        return new LocalFile(sha1, size);
                    }
                    case "directory": {
                        return new LocalDirectory();
                    }
                    case "link": {
                        String target = obj.getAsJsonPrimitive("target").getAsString();
                        return new LocalLink(target);
                    }
                    default:
                        throw new AssertionError("unknown type: " + type);
                }
            } catch (Throwable e) {
                throw new JsonParseException(json.toString());
            }
        }
    }
}
