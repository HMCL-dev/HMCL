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
package org.jackhuang.hmcl.addon.curse;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Immutable
public record CurseAddon(int id, int gameId, String name, String slug, Links links, String summary, int status,
                         int downloadCount, boolean isFeatured, int primaryCategoryId, List<Category> categories,
                         int classId, List<Author> authors, Logo logo, int mainFileId, List<LatestFile> latestFiles,
                         List<LatestFileIndex> latestFileIndices, Instant dateCreated, Instant dateModified,
                         Instant dateReleased, boolean allowModDistribution, int gamePopularityRank,
                         boolean isAvailable, int thumbsUpCount) implements RemoteAddon.IMod {
    public static final Map<Integer, RemoteAddon.DependencyType> RELATION_TYPE = Lang.mapOf(
            Pair.pair(1, RemoteAddon.DependencyType.EMBEDDED),
            Pair.pair(2, RemoteAddon.DependencyType.OPTIONAL),
            Pair.pair(3, RemoteAddon.DependencyType.REQUIRED),
            Pair.pair(4, RemoteAddon.DependencyType.TOOL),
            Pair.pair(5, RemoteAddon.DependencyType.INCOMPATIBLE),
            Pair.pair(6, RemoteAddon.DependencyType.INCLUDE)
    );

    @Override
    public List<RemoteAddon> loadDependencies(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException {
        Set<Integer> dependencies = latestFiles.stream()
                .flatMap(latestFile -> latestFile.dependencies().stream())
                .filter(dep -> dep.relationType() == 3)
                .map(Dependency::modId)
                .collect(Collectors.toSet());
        List<RemoteAddon> mods = new ArrayList<>();
        for (int dependencyId : dependencies) {
            mods.add(modRepository.getModById(downloadProvider, Integer.toString(dependencyId)));
        }
        return mods;
    }

    @Override
    public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository modRepository, DownloadProvider downloadProvider) throws IOException {
        return modRepository.getRemoteVersionsById(downloadProvider, Integer.toString(id));
    }

    public RemoteAddon toMod(RemoteAddonRepository.Type type) {
        String iconUrl = "";
        if (logo != null) {
            if (StringUtils.isNotBlank(logo.thumbnailUrl()))
                iconUrl = logo.thumbnailUrl();
            else if (StringUtils.isNotBlank(logo.url()))
                iconUrl = logo.url();
        }

        return new RemoteAddon(
                slug,
                "",
                name,
                summary,
                categories.stream().map(category -> Integer.toString(category.getId())).collect(Collectors.toList()),
                links.websiteUrl,
                iconUrl,
                this,
                type
        );
    }

    @Immutable
    public record Links(String websiteUrl, String wikiUrl, @Nullable String issuesUrl, @Nullable String sourceUrl) {
    }

    @Immutable
    public record Author(int id, String name, String url) {
    }

    @Immutable
    public record Logo(int id, int modId, String title, String description, String thumbnailUrl, String url) {
    }

    @Immutable
    public record Attachment(int id, int projectId, String description, boolean isDefault, String thumbnailUrl,
                             String title, String url, int status) {
    }

    @Immutable
    public record Dependency(int modId, int relationType) {
        public Dependency() {
            this(0, 1);
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#schemafilehash">Schema</a>
     */
    @Immutable
    public record LatestFileHash(String value, int algo) {
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_File">Schema</a>
     */
    @Immutable
    public record LatestFile(int id, int gameId, int modId, boolean isAvailable, String displayName, String fileName,
                             int releaseType, int fileStatus, List<LatestFileHash> hashes, Instant fileDate,
                             int fileLength, int downloadCount, String downloadUrl, List<String> gameVersions,
                             List<Dependency> dependencies, int alternateFileId, boolean isServerPack,
                             long fileFingerprint) implements RemoteAddon.IVersion {

        @Override
        public String downloadUrl() {
            if (downloadUrl == null) {
                // This addon is not allowed for distribution, and downloadUrl will be null.
                // We try to find its download url.
                return String.format("https://edge.forgecdn.net/files/%d/%d/%s", id / 1000, id % 1000, fileName);
            }
            return downloadUrl;
        }

        @Override
        public RemoteAddon.Type getType() {
            return RemoteAddon.Type.CURSEFORGE;
        }

        public RemoteAddon.Version toVersion() {
            RemoteAddon.VersionType versionType = switch (releaseType()) {
                case 1 -> RemoteAddon.VersionType.Release;
                case 2 -> RemoteAddon.VersionType.Beta;
                case 3 -> RemoteAddon.VersionType.Alpha;
                default -> RemoteAddon.VersionType.Release;
            };

            return new RemoteAddon.Version(
                    this,
                    Integer.toString(modId),
                    displayName(),
                    fileName(),
                    null,
                    fileDate(),
                    versionType,
                    new RemoteAddon.File(Collections.emptyMap(), downloadUrl(), fileName()),
                    dependencies.stream().map(dependency -> {
                        if (!RELATION_TYPE.containsKey(dependency.relationType())) {
                            throw new IllegalStateException("Broken datas.");
                        }
                        return RemoteAddon.Dependency.ofGeneral(RELATION_TYPE.get(dependency.relationType()), CurseForgeRemoteAddonRepository.MODS, Integer.toString(dependency.modId()));
                    }).distinct().filter(Objects::nonNull).collect(Collectors.toList()),
                    gameVersions.stream().filter(GameVersionNumber::isKnown).toList(),
                    gameVersions.stream().flatMap(version -> {
                        if ("fabric".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.FABRIC);
                        else if ("forge".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.FORGE);
                        else if ("quilt".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.QUILT);
                        else if ("neoforge".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.NEO_FORGE);
                        else return Stream.empty();
                    }).collect(Collectors.toList())
            );
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_FileIndex">Schema</a>
     */
    @Immutable
    public record LatestFileIndex(String gameVersion, int fileId, String filename, int releaseType,
                                  int gameVersionTypeId, int modLoader) {
    }

    @Immutable
    public static class Category {
        private final int id;
        private final int gameId;
        private final String name;
        private final String slug;
        private final String url;
        private final String iconUrl;
        private final Instant dateModified;
        private final boolean isClass;
        private final int classId;
        private final int parentCategoryId;

        private transient final List<Category> subcategories;

        public Category() {
            this(0, 0, "", "", "", "", Instant.now(), false, 0, 0);
        }

        public Category(int id, int gameId, String name, String slug, String url, String iconUrl, Instant dateModified, boolean isClass, int classId, int parentCategoryId) {
            this.id = id;
            this.gameId = gameId;
            this.name = name;
            this.slug = slug;
            this.url = url;
            this.iconUrl = iconUrl;
            this.dateModified = dateModified;
            this.isClass = isClass;
            this.classId = classId;
            this.parentCategoryId = parentCategoryId;

            this.subcategories = new ArrayList<>();
        }

        public int getId() {
            return id;
        }

        public int getGameId() {
            return gameId;
        }

        public String getName() {
            return name;
        }

        public String getSlug() {
            return slug;
        }

        public String getUrl() {
            return url;
        }

        public String getIconUrl() {
            return iconUrl;
        }

        public Instant getDateModified() {
            return dateModified;
        }

        public boolean isClass() {
            return isClass;
        }

        public int getClassId() {
            return classId;
        }

        public int getParentCategoryId() {
            return parentCategoryId;
        }

        public List<Category> getSubcategories() {
            return subcategories;
        }

        public RemoteAddonRepository.Category toCategory() {
            return new RemoteAddonRepository.Category(
                    this,
                    Integer.toString(id),
                    getSubcategories().stream().map(Category::toCategory).collect(Collectors.toList()));
        }
    }
}
