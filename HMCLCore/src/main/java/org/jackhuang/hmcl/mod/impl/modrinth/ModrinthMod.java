/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.impl.modrinth;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModrinthMod implements RemoteMod.IMod {
    private final String slug;

    private final String title;

    private final String description;

    private final List<String> categories;

    /**
     * A long body describing project in detail.
     */
    private final String body;

    @SerializedName("project_type")
    private final String projectType;

    private final int downloads;

    @SerializedName("icon_url")
    private final String iconUrl;

    private final String id;

    private final String team;

    private final Date published;

    private final Date updated;

    private final List<String> versions;

    public ModrinthMod(String slug, String title, String description, List<String> categories, String body, String projectType, int downloads, String iconUrl, String id, String team, Date published, Date updated, List<String> versions) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.categories = categories;
        this.body = body;
        this.projectType = projectType;
        this.downloads = downloads;
        this.iconUrl = iconUrl;
        this.id = id;
        this.team = team;
        this.published = published;
        this.updated = updated;
        this.versions = versions;
    }

    public String getSlug() {
        return slug;
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

    public String getBody() {
        return body;
    }

    public String getProjectType() {
        return projectType;
    }

    public int getDownloads() {
        return downloads;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getId() {
        return id;
    }

    public String getTeam() {
        return team;
    }

    public Date getPublished() {
        return published;
    }

    public Date getUpdated() {
        return updated;
    }

    public List<String> getVersions() {
        return versions;
    }

    @Override
    public List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException {
        Set<String> dependencies = modRepository.getRemoteVersionsById(getId())
                .flatMap(version -> version.getDependencies().stream())
                .collect(Collectors.toSet());
        List<RemoteMod> mods = new ArrayList<>();
        for (String dependencyId : dependencies) {
            if (StringUtils.isNotBlank(dependencyId)) {
                mods.add(modRepository.getModById(dependencyId));
            }
        }
        return mods;
    }

    @Override
    public Stream<RemoteMod.Version> loadVersions(RemoteModRepository modRepository) throws IOException {
        return modRepository.getRemoteVersionsById(getId());
    }

    public RemoteMod toMod() {
        return new RemoteMod(
                slug,
                "",
                title,
                description,
                categories,
                null,
                iconUrl,
                (RemoteMod.IMod) this
        );
    }

    public static class ModrinthVersionImpl implements RemoteMod.IVersion {
        private final String name;

        @SerializedName("version_number")
        private final String versionNumber;

        private final String changelog;

        private final List<ModrinthRemoteModRepository.Dependency> dependencies;

        @SerializedName("game_versions")
        private final List<String> gameVersions;

        @SerializedName("version_type")
        private final String versionType;

        private final List<String> loaders;

        private final boolean featured;

        private final String id;

        @SerializedName("project_id")
        private final String projectId;

        @SerializedName("author_id")
        private final String authorId;

        @SerializedName("date_published")
        private final Date datePublished;

        private final int downloads;

        @SerializedName("changelog_url")
        private final String changelogUrl;

        private final List<ModrinthRemoteModRepository.ProjectVersionFile> files;

        public ModrinthVersionImpl(String name, String versionNumber, String changelog, List<ModrinthRemoteModRepository.Dependency> dependencies, List<String> gameVersions, String versionType, List<String> loaders, boolean featured, String id, String projectId, String authorId, Date datePublished, int downloads, String changelogUrl, List<ModrinthRemoteModRepository.ProjectVersionFile> files) {
            this.name = name;
            this.versionNumber = versionNumber;
            this.changelog = changelog;
            this.dependencies = dependencies;
            this.gameVersions = gameVersions;
            this.versionType = versionType;
            this.loaders = loaders;
            this.featured = featured;
            this.id = id;
            this.projectId = projectId;
            this.authorId = authorId;
            this.datePublished = datePublished;
            this.downloads = downloads;
            this.changelogUrl = changelogUrl;
            this.files = files;
        }

        public String getName() {
            return name;
        }

        public String getVersionNumber() {
            return versionNumber;
        }

        public String getChangelog() {
            return changelog;
        }

        public List<ModrinthRemoteModRepository.Dependency> getDependencies() {
            return dependencies;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public String getVersionType() {
            return versionType;
        }

        public List<String> getLoaders() {
            return loaders;
        }

        public boolean isFeatured() {
            return featured;
        }

        public String getId() {
            return id;
        }

        public String getProjectId() {
            return projectId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public Date getDatePublished() {
            return datePublished;
        }

        public int getDownloads() {
            return downloads;
        }

        public String getChangelogUrl() {
            return changelogUrl;
        }

        public List<ModrinthRemoteModRepository.ProjectVersionFile> getFiles() {
            return files;
        }

        @Override
        public RemoteMod.ModType getModType() {
            return RemoteMod.ModType.MODRINTH;
        }

        @Override
        public RemoteMod getRemoteMod() throws IOException {
            return ModrinthRemoteModRepository.MODS.getModById(this.getProjectId());
        }

        public Optional<RemoteMod.Version> toVersion() {
            RemoteMod.VersionType type;
            if ("release".equals(versionType)) {
                type = RemoteMod.VersionType.Release;
            } else if ("beta".equals(versionType)) {
                type = RemoteMod.VersionType.Beta;
            } else if ("alpha".equals(versionType)) {
                type = RemoteMod.VersionType.Alpha;
            } else {
                type = RemoteMod.VersionType.Release;
            }

            if (files.size() == 0) {
                return Optional.empty();
            }

            return Optional.of(new RemoteMod.Version(
                    this,
                    projectId,
                    name,
                    versionNumber,
                    changelog,
                    datePublished,
                    type,
                    files.get(0).toFile(),
                    dependencies.stream().map(ModrinthRemoteModRepository.Dependency::getProjectId).filter(Objects::nonNull).collect(Collectors.toList()),
                    gameVersions,
                    loaders.stream().flatMap(loader -> {
                        if ("fabric".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FABRIC);
                        else if ("forge".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.FORGE);
                        else if ("quilt".equalsIgnoreCase(loader)) return Stream.of(ModLoaderType.QUILT);
                        else return Stream.empty();
                    }).collect(Collectors.toList())
            ));
        }
    }
}
