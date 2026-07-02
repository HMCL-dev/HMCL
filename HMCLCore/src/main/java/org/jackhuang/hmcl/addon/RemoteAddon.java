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

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.Either;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record RemoteAddon(String slug, String author, String title, String description, List<String> categories,
                          String pageUrl, String iconUrl, IMod data, RemoteAddonRepository.Type repoType) {

    public static final RemoteAddon BROKEN = new RemoteAddon("", "", "RemoteAddon.BROKEN", "", Collections.emptyList(), "", "", new IMod() {
        @Override
        public List<RemoteAddon> loadDependencies(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException {
            throw new IOException();
        }

        @Override
        public Stream<Version> loadVersions(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException {
            throw new IOException();
        }
    }, RemoteAddonRepository.Type.MOD);

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
        private static Dependency BROKEN_DEPENDENCY = null;

        private final DependencyType type;

        private final RemoteAddonRepository remoteAddonRepository;

        private final String id;

        private transient RemoteAddon remoteAddon = null;

        private Dependency(DependencyType type, RemoteAddonRepository remoteAddonRepository, String modid) {
            this.type = type;
            this.remoteAddonRepository = remoteAddonRepository;
            this.id = modid;
        }

        public static Dependency ofGeneral(DependencyType type, RemoteAddonRepository remoteAddonRepository, String modid) {
            if (type == DependencyType.BROKEN) {
                return ofBroken();
            } else {
                return new Dependency(type, remoteAddonRepository, modid);
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

        public RemoteAddonRepository getRemoteModRepository() {
            return this.remoteAddonRepository;
        }

        public String getId() {
            return this.id;
        }

        public RemoteAddon load(DownloadProvider downloadProvider) throws IOException {
            if (this.remoteAddon == null) {
                if (this.type == DependencyType.BROKEN) {
                    this.remoteAddon = RemoteAddon.BROKEN;
                } else {
                    this.remoteAddon = this.remoteAddonRepository.resolveDependency(downloadProvider, this.id);
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
            if (!remoteAddonRepository.equals(that.remoteAddonRepository)) return false;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + remoteAddonRepository.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    public enum Source {
        CURSEFORGE(
                CurseForgeRemoteAddonRepository.MODS,
                CurseForgeRemoteAddonRepository.RESOURCE_PACKS,
                CurseForgeRemoteAddonRepository.SHADERS,
                CurseForgeRemoteAddonRepository.WORLDS,
                CurseForgeRemoteAddonRepository.MODPACKS,
                CurseForgeRemoteAddonRepository.CUSTOMIZATIONS
        ),
        MODRINTH(
                ModrinthRemoteAddonRepository.MODS,
                ModrinthRemoteAddonRepository.RESOURCE_PACKS,
                ModrinthRemoteAddonRepository.SHADER_PACKS,
                null,
                ModrinthRemoteAddonRepository.MODPACKS,
                null
        );

        public final RemoteAddonRepository modRepo;
        public final RemoteAddonRepository resourcePackRepo;
        public final RemoteAddonRepository shaderPackRepo;
        public final RemoteAddonRepository worldRepo;
        public final RemoteAddonRepository modpackRepo;
        public final RemoteAddonRepository customizationRepo;

        @Nullable
        public RemoteAddonRepository getRepoForType(RemoteAddonRepository.Type type) {
            return switch (type) {
                case MOD -> modRepo;
                case RESOURCE_PACK -> resourcePackRepo;
                case SHADER_PACK -> shaderPackRepo;
                case WORLD -> worldRepo;
                case MODPACK -> modpackRepo;
                case CUSTOMIZATION -> customizationRepo;
            };
        }

        Source(
                RemoteAddonRepository modRepo,
                RemoteAddonRepository resourcePackRepo,
                RemoteAddonRepository shaderPackRepo,
                RemoteAddonRepository worldRepo,
                RemoteAddonRepository modpackRepo,
                RemoteAddonRepository customizationRepo
        ) {
            this.modRepo = modRepo;
            this.resourcePackRepo = resourcePackRepo;
            this.shaderPackRepo = shaderPackRepo;
            this.worldRepo = worldRepo;
            this.modpackRepo = modpackRepo;
            this.customizationRepo = customizationRepo;
        }
    }

    public interface IMod {
        List<RemoteAddon> loadDependencies(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException;

        Stream<Version> loadVersions(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException;
    }

    public interface IVersion {
        Source getType();
    }

    public record Version(IVersion self, String modid, String name, String version, String changelog,
                          Instant datePublished, VersionType versionType, File file, List<Dependency> dependencies,
                          List<String> gameVersions, List<Either<ModLoaderType, String>> loaders) {
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
