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
package org.jackhuang.hmcl.mod;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class DownloadManager {
    private DownloadManager() {
    }

    public interface IMod {
        List<Mod> loadDependencies() throws IOException;
        Stream<Version> loadVersions() throws IOException;
    }

    public static class Mod {
        private final String slug;
        private final String author;
        private final String title;
        private final String description;
        private final List<String> categories;
        private final String pageUrl;
        private final String iconUrl;
        private final IMod data;

        public Mod(String slug, String author, String title, String description, List<String> categories, String pageUrl, String iconUrl, IMod data) {
            this.slug = slug;
            this.author = author;
            this.title = title;
            this.description = description;
            this.categories = categories;
            this.pageUrl = pageUrl;
            this.iconUrl = iconUrl;
            this.data = data;
        }

        public String getSlug() {
            return slug;
        }

        public String getAuthor() {
            return author;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getCategories() {
            return categories;
        }

        public String getPageUrl() {
            return pageUrl;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public IMod getData() {
            return data;
        }
    }

    public enum VersionType {
        Release,
        Beta,
        Alpha
    }

    public static class Version {
        private final Object self;
        private final String name;
        private final String version;
        private final String changelog;
        private final Instant datePublished;
        private final VersionType versionType;
        private final File file;
        private final List<String> dependencies;
        private final List<String> gameVersions;
        private final List<String> loaders;

        public Version(Object self, String name, String version, String changelog, Instant datePublished, VersionType versionType, File file, List<String> dependencies, List<String> gameVersions, List<String> loaders) {
            this.self = self;
            this.name = name;
            this.version = version;
            this.changelog = changelog;
            this.datePublished = datePublished;
            this.versionType = versionType;
            this.file = file;
            this.dependencies = dependencies;
            this.gameVersions = gameVersions;
            this.loaders = loaders;
        }

        public Object getSelf() {
            return self;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        public String getChangelog() {
            return changelog;
        }

        public Instant getDatePublished() {
            return datePublished;
        }

        public VersionType getVersionType() {
            return versionType;
        }

        public File getFile() {
            return file;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public List<String> getLoaders() {
            return loaders;
        }
    }

    public static class File {
        private final Map<String, String> hashes;
        private final String url;
        private final String filename;

        public File(Map<String, String> hashes, String url, String filename) {
            this.hashes = hashes;
            this.url = url;
            this.filename = filename;
        }

        public Map<String, String> getHashes() {
            return hashes;
        }

        public String getUrl() {
            return url;
        }

        public String getFilename() {
            return filename;
        }
    }

    public static final String[] DEFAULT_GAME_VERSIONS = new String[]{
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
            "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
            "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.3", "1.7.2",
            "1.6.4", "1.6.2", "1.6.1",
            "1.5.2", "1.5.1",
            "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
            "1.3.2", "1.3.1",
            "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
            "1.1",
            "1.0"
    };
}
