// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.mojang;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.util.gson.JsonSerializable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;

@JsonSerializable
@JsonAdapter(MojangJavaDownloads.Adapter.class)
public record MojangJavaDownloads(Map<String, Map<String, List<JavaDownload>>> downloads) {
    public static class Adapter implements JsonSerializer<MojangJavaDownloads>, JsonDeserializer<MojangJavaDownloads> {
        @Override
        public JsonElement serialize(MojangJavaDownloads src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.downloads);
        }

        @Override
        public MojangJavaDownloads deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new MojangJavaDownloads(context.deserialize(json, mapTypeOf(String.class, mapTypeOf(String.class, listTypeOf(JavaDownload.class))).getType()));
        }
    }

    @JsonSerializable
    public record JavaDownload(Availability availability, DownloadInfo manifest, Version version) {
    }

    @JsonSerializable
    public record Availability(int group, int progress) {
    }

    @JsonSerializable
    public record Version(String name, String released) {
    }
}
