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

import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.task.FileDownloadTask;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.io.NetworkUtils.encodeLocation;

public class RemoteMod {
    private static RemoteMod EMPTY = null;

    public static void registerEmptyRemoteMod(RemoteMod empty) {
        EMPTY = empty;
    }

    public static RemoteMod getEmptyRemoteMod() {
        if (EMPTY == null) {
            throw new NullPointerException();
        }
        return EMPTY;
    }

    private final String slug;
    private final String author;
    private final String title;
    private final String description;
    private final List<String> categories;
    private final String pageUrl;
    private final String iconUrl;
    private final IMod data;

    public RemoteMod(String slug, String author, String title, String description, List<String> categories, String pageUrl, String iconUrl, IMod data) {
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

    public enum VersionType {
        Release,
        Beta,
        Alpha
    }

    public enum DependencyType {
        EMBEDDED,
        OPTIONAL,
        REQUIRED,
        TOOL,
        INCLUDE,
        INCOMPATIBLE,
        BROKEN
    }

    public static final class Dependency {
        private static Dependency BROKEN_DEPENDENCY = null;

        private final DependencyType type;

        private final RemoteModRepository remoteModRepository;

        private final String id;

        private transient RemoteMod remoteMod = null;

        private Dependency(DependencyType type, RemoteModRepository remoteModRepository, String modid) {
            this.type = type;
            this.remoteModRepository = remoteModRepository;
            this.id = modid;
        }

        public static Dependency ofGeneral(DependencyType type, RemoteModRepository remoteModRepository, String modid) {
            if (type == DependencyType.BROKEN) {
                return ofBroken();
            } else {
                return new Dependency(type, remoteModRepository, modid);
            }
        }

        public static Dependency ofBroken() {
            if (BROKEN_DEPENDENCY == null) {
                BROKEN_DEPENDENCY = new Dependency(DependencyType.BROKEN, null, null);
            }
            return BROKEN_DEPENDENCY;
        }

        public DependencyType getType() {
            return this.type;
        }

        public RemoteModRepository getRemoteModRepository() {
            return this.remoteModRepository;
        }

        public String getId() {
            return this.id;
        }

        public RemoteMod load() throws IOException {
            if (this.remoteMod == null) {
                if (this.type == DependencyType.BROKEN) {
                    this.remoteMod = RemoteMod.getEmptyRemoteMod();
                } else {
                    this.remoteMod = this.remoteModRepository.getModById(this.id);
                }
            }
            return this.remoteMod;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Dependency that = (Dependency) o;

            if (type != that.type) return false;
            if (!remoteModRepository.equals(that.remoteModRepository)) return false;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + remoteModRepository.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    public enum Type {
        CURSEFORGE(CurseForgeRemoteModRepository.MODS),
        MODRINTH(ModrinthRemoteModRepository.MODS);

        private final RemoteModRepository remoteModRepository;

        public RemoteModRepository getRemoteModRepository() {
            return this.remoteModRepository;
        }

        Type(RemoteModRepository remoteModRepository) {
            this.remoteModRepository = remoteModRepository;
        }
    }

    public interface IMod {
        List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException;

        Stream<Version> loadVersions(RemoteModRepository modRepository) throws IOException;
    }

    public interface IVersion {
        Type getType();
    }

    public static class Version {
        private final IVersion self;
        private final String modid;
        private final String name;
        private final String version;
        private final String changelog;
        private final Date datePublished;
        private final VersionType versionType;
        private final File file;
        private final List<Dependency> dependencies;
        private final List<String> gameVersions;
        private final List<ModLoaderType> loaders;

        public Version(IVersion self, String modid, String name, String version, String changelog, Date datePublished, VersionType versionType, File file, List<Dependency> dependencies, List<String> gameVersions, List<ModLoaderType> loaders) {
            this.self = self;
            this.modid = modid;
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

        public IVersion getSelf() {
            return self;
        }

        public String getModid() {
            return modid;
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

        public Date getDatePublished() {
            return datePublished;
        }

        public VersionType getVersionType() {
            return versionType;
        }

        public File getFile() {
            return file;
        }

        public List<Dependency> getDependencies() {
            return dependencies;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public List<ModLoaderType> getLoaders() {
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

        public FileDownloadTask.IntegrityCheck getIntegrityCheck() {
            if (hashes.containsKey("md5")) {
                return new FileDownloadTask.IntegrityCheck("MD5", hashes.get("sha1"));
            } else if (hashes.containsKey("sha1")) {
                return new FileDownloadTask.IntegrityCheck("SHA-1", hashes.get("sha1"));
            } else if (hashes.containsKey("sha512")) {
                return new FileDownloadTask.IntegrityCheck("SHA-256", hashes.get("sha1"));
            } else {
                return null;
            }
        }

        public String getUrl() {
            return encodeLocation(url);
        }

        public String getFilename() {
            return filename;
        }
    }
}
