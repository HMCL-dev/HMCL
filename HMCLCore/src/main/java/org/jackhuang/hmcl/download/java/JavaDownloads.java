/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.util.Immutable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

@JsonAdapter(JavaDownloads.Adapter.class)
public class JavaDownloads {

    private final Map<String, Map<String, List<JavaDownload>>> downloads;

    public JavaDownloads(Map<String, Map<String, List<JavaDownload>>> downloads) {
        this.downloads = downloads;
    }

    public Map<String, Map<String, List<JavaDownload>>> getDownloads() {
        return downloads;
    }

    public static class Adapter implements JsonSerializer<JavaDownloads>, JsonDeserializer<JavaDownloads> {

        @Override
        public JsonElement serialize(JavaDownloads src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.downloads);
        }

        @Override
        public JavaDownloads deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new JavaDownloads(context.deserialize(json, new TypeToken<Map<String, Map<String, List<JavaDownload>>>>() {
            }.getType()));
        }
    }

    @Immutable
    public static class JavaDownload {
        private final Availability availability;
        private final DownloadInfo manifest;
        private final Version version;

        public JavaDownload() {
            this(new Availability(), new DownloadInfo(), new Version());
        }

        public JavaDownload(Availability availability, DownloadInfo manifest, Version version) {
            this.availability = availability;
            this.manifest = manifest;
            this.version = version;
        }

        public Availability getAvailability() {
            return availability;
        }

        public DownloadInfo getManifest() {
            return manifest;
        }

        public Version getVersion() {
            return version;
        }
    }

    @Immutable
    public static class Availability {
        private final int group;
        private final int progress;

        public Availability() {
            this(0, 0);
        }

        public Availability(int group, int progress) {
            this.group = group;
            this.progress = progress;
        }

        public int getGroup() {
            return group;
        }

        public int getProgress() {
            return progress;
        }
    }

    @Immutable
    public static class Version {
        private final String name;
        private final String released;

        public Version() {
            this("", "");
        }

        public Version(String name, String released) {
            this.name = name;
            this.released = released;
        }

        public String getName() {
            return name;
        }

        public String getReleased() {
            return released;
        }
    }
}
