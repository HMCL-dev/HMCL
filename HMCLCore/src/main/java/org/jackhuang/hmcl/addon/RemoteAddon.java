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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public record RemoteAddon(String slug, String author, String title, String description, List<String> categories,
                          String pageUrl, String iconUrl, IAddon data, @Nullable Type type) {

    public static final RemoteAddon BROKEN = new RemoteAddon("", "", "RemoteAddon.BROKEN", "", Collections.emptyList(), "", "", new IAddon() {
        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            throw new IOException();
        }

        @Override
        public Stream<Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException {
            throw new IOException();
        }
    }, Type.MOD);

    public enum VersionType {
        Release,
        Beta,
        Alpha
    }

    public enum DependencyType {
        REQUIRED,
        OPTIONAL,
        TOOL,
        INCLUDE,
        EMBEDDED,
        INCOMPATIBLE,
        BROKEN
    }

    public static final class Dependency {
        private static @Nullable Dependency BROKEN_DEPENDENCY = null;

        private final DependencyType type;

        private final @Nullable Source source;

        private final @Nullable String id;

        /// Stable remote version ID required by the dependency, or `null` for any project version.
        private final @Nullable String versionId;

        /// Project ID discovered while resolving a version-only dependency.
        private transient @Nullable String resolvedProjectId;

        private transient @Nullable RemoteAddon remoteAddon = null;

        private Dependency(DependencyType type, @Nullable Source source, @Nullable String id,
                           @Nullable String versionId) {
            this.type = type;
            this.source = source;
            this.id = id;
            this.versionId = versionId;
            this.resolvedProjectId = id;
        }

        public static Dependency ofGeneral(DependencyType type, Source source, String id) {
            if (type == DependencyType.BROKEN) {
                return ofBroken();
            } else {
                return new Dependency(type, source, id, null);
            }
        }

        /// Creates a dependency that may require one exact remote version identity.
        public static Dependency ofVersion(
                DependencyType type,
                Source source,
                @Nullable String id,
                @Nullable String versionId) {
            if (type == DependencyType.BROKEN) {
                return ofBroken();
            }
            return new Dependency(type, source, id, versionId);
        }

        public static Dependency ofBroken() {
            if (BROKEN_DEPENDENCY == null) {
                BROKEN_DEPENDENCY = new Dependency(DependencyType.BROKEN, null, null, null);
            }
            return BROKEN_DEPENDENCY;
        }

        public DependencyType getType() {
            return this.type;
        }

        @Nullable
        public Source getSource() {
            return this.source;
        }

        @Nullable
        public String getId() {
            return this.id;
        }

        /// Returns the exact remote version ID when the platform dependency names one.
        public @Nullable String getVersionId() {
            return versionId;
        }

        /// Returns the declared or lazily discovered stable project ID.
        public @Nullable String getResolvedProjectId() {
            return resolvedProjectId;
        }

        public RemoteAddon load(DownloadProvider downloadProvider) throws IOException {
            if (this.remoteAddon == null) {
                if (this.type == DependencyType.BROKEN) {
                    this.remoteAddon = RemoteAddon.BROKEN;
                } else if (this.id == null && this.versionId != null) {
                    @Nullable RemoteAddonRepository repository = this.source.getRepoForType(Type.MOD);
                    Optional<Version> version = repository == null
                            ? Optional.empty()
                            : repository.getRemoteVersionById(downloadProvider, this.versionId);
                    if (version.isPresent()) {
                        this.resolvedProjectId = version.get().projectId();
                        this.remoteAddon = repository.getAddonById(
                                downloadProvider, version.get().projectId());
                    } else {
                        this.remoteAddon = RemoteAddon.BROKEN;
                    }
                } else if (this.id == null) {
                    this.remoteAddon = RemoteAddon.BROKEN;
                } else {
                    this.remoteAddon = this.source.getRepository().resolveDependency(downloadProvider, this.id);
                }
            }
            return this.remoteAddon;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Dependency that = (Dependency) o;

            if (type != that.type) return false;
            if (source != that.source) return false;
            if (!Objects.equals(id, that.id)) return false;
            return Objects.equals(versionId, that.versionId);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + Objects.hashCode(source);
            result = 31 * result + Objects.hashCode(id);
            result = 31 * result + Objects.hashCode(versionId);
            return result;
        }
    }

    public enum Source {
        CURSEFORGE(CurseForgeRemoteAddonRepository.getInstance()),
        MODRINTH(ModrinthRemoteAddonRepository.getInstance());

        private final RemoteAddonRepository repo;

        public RemoteAddonRepository getRepository() {
            return repo;
        }

        Source(RemoteAddonRepository repo) {
            this.repo = repo;
        }
    }

    public enum Type {
        MOD,
        MODPACK,
        RESOURCE_PACK,
        SHADER_PACK,
        WORLD,
        CUSTOMIZATION
    }

    public interface IAddon {
        List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException;

        Stream<Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) throws IOException;
    }

    public interface IVersion {
        Source getSource();
    }

    public record Version(IVersion self, String versionId, String projectId, String name, String version,
                          Instant datePublished, VersionType versionType, File file, List<Dependency> dependencies,
                          List<String> gameVersions, List<AddonLoader> loaders) {
    }

    public record File(Map<String, String> hashes, String url, String filename) {

        public FileDownloadTask.IntegrityCheck getIntegrityCheck() {
            if (hashes.containsKey("md5")) {
                return new FileDownloadTask.IntegrityCheck("MD5", hashes.get("md5"));
            } else if (hashes.containsKey("sha1")) {
                return new FileDownloadTask.IntegrityCheck("SHA-1", hashes.get("sha1"));
            } else if (hashes.containsKey("sha256")) {
                return new FileDownloadTask.IntegrityCheck("SHA-256", hashes.get("sha256"));
            } else if (hashes.containsKey("sha512")) {
                return new FileDownloadTask.IntegrityCheck("SHA-512", hashes.get("sha512"));
            } else {
                return null;
            }
        }
    }
}
