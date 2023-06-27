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
package org.jackhuang.hmcl.mod.curse;

import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.Immutable;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Immutable
public class CurseAddon implements RemoteMod.IMod {
    private final int id;
    private final int gameId;
    private final String name;
    private final String slug;
    private final Links links;
    private final String summary;
    private final int status;
    private final int downloadCount;
    private final boolean isFeatured;
    private final int primaryCategoryId;
    private final List<Category> categories;
    private final int classId;
    private final List<Author> authors;
    private final Logo logo;
    private final int mainFileId;
    private final List<LatestFile> latestFiles;
    private final List<LatestFileIndex> latestFileIndices;
    private final Date dateCreated;
    private final Date dateModified;
    private final Date dateReleased;
    private final boolean allowModDistribution;
    private final int gamePopularityRank;
    private final boolean isAvailable;
    private final int thumbsUpCount;

    public CurseAddon(int id, int gameId, String name, String slug, Links links, String summary, int status, int downloadCount, boolean isFeatured, int primaryCategoryId, List<Category> categories, int classId, List<Author> authors, Logo logo, int mainFileId, List<LatestFile> latestFiles, List<LatestFileIndex> latestFileIndices, Date dateCreated, Date dateModified, Date dateReleased, boolean allowModDistribution, int gamePopularityRank, boolean isAvailable, int thumbsUpCount) {
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.slug = slug;
        this.links = links;
        this.summary = summary;
        this.status = status;
        this.downloadCount = downloadCount;
        this.isFeatured = isFeatured;
        this.primaryCategoryId = primaryCategoryId;
        this.categories = categories;
        this.classId = classId;
        this.authors = authors;
        this.logo = logo;
        this.mainFileId = mainFileId;
        this.latestFiles = latestFiles;
        this.latestFileIndices = latestFileIndices;
        this.dateCreated = dateCreated;
        this.dateModified = dateModified;
        this.dateReleased = dateReleased;
        this.allowModDistribution = allowModDistribution;
        this.gamePopularityRank = gamePopularityRank;
        this.isAvailable = isAvailable;
        this.thumbsUpCount = thumbsUpCount;
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

    public Links getLinks() {
        return links;
    }

    public String getSummary() {
        return summary;
    }

    public int getStatus() {
        return status;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public boolean isFeatured() {
        return isFeatured;
    }

    public int getPrimaryCategoryId() {
        return primaryCategoryId;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public int getClassId() {
        return classId;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    public Logo getLogo() {
        return logo;
    }

    public int getMainFileId() {
        return mainFileId;
    }

    public List<LatestFile> getLatestFiles() {
        return latestFiles;
    }

    public List<LatestFileIndex> getLatestFileIndices() {
        return latestFileIndices;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public Date getDateModified() {
        return dateModified;
    }

    public Date getDateReleased() {
        return dateReleased;
    }

    public boolean isAllowModDistribution() {
        return allowModDistribution;
    }

    public int getGamePopularityRank() {
        return gamePopularityRank;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public int getThumbsUpCount() {
        return thumbsUpCount;
    }

    @Override
    public List<RemoteMod> loadDependencies(RemoteModRepository modRepository) throws IOException {
        Set<Integer> dependencies = latestFiles.stream()
                .flatMap(latestFile -> latestFile.getDependencies().stream())
                .filter(dep -> dep.getRelationType() == 3)
                .map(Dependency::getModId)
                .collect(Collectors.toSet());
        List<RemoteMod> mods = new ArrayList<>();
        for (int dependencyId : dependencies) {
            mods.add(modRepository.getModById(Integer.toString(dependencyId)));
        }
        return mods;
    }

    @Override
    public Stream<RemoteMod.Version> loadVersions(RemoteModRepository modRepository) throws IOException {
        return modRepository.getRemoteVersionsById(Integer.toString(id));
    }

    public RemoteMod toMod() {
        String iconUrl = Optional.ofNullable(logo).map(Logo::getThumbnailUrl).orElse("");

        return new RemoteMod(
                slug,
                "",
                name,
                summary,
                categories.stream().map(category -> Integer.toString(category.getId())).collect(Collectors.toList()),
                links.websiteUrl,
                iconUrl,
                this
        );
    }

    @Immutable
    public static class Links {
        private final String websiteUrl;
        private final String wikiUrl;
        private final String issuesUrl;
        private final String sourceUrl;

        public Links(String websiteUrl, String wikiUrl, String issuesUrl, String sourceUrl) {
            this.websiteUrl = websiteUrl;
            this.wikiUrl = wikiUrl;
            this.issuesUrl = issuesUrl;
            this.sourceUrl = sourceUrl;
        }

        public String getWebsiteUrl() {
            return websiteUrl;
        }

        public String getWikiUrl() {
            return wikiUrl;
        }

        @Nullable
        public String getIssuesUrl() {
            return issuesUrl;
        }

        @Nullable
        public String getSourceUrl() {
            return sourceUrl;
        }
    }

    @Immutable
    public static class Author {
        private final int id;
        private final String name;
        private final String url;

        public Author(int id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }
    }

    @Immutable
    public static class Logo {
        private final int id;
        private final int modId;
        private final String title;
        private final String description;
        private final String thumbnailUrl;
        private final String url;

        public Logo(int id, int modId, String title, String description, String thumbnailUrl, String url) {
            this.id = id;
            this.modId = modId;
            this.title = title;
            this.description = description;
            this.thumbnailUrl = thumbnailUrl;
            this.url = url;
        }

        public int getId() {
            return id;
        }

        public int getModId() {
            return modId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public String getUrl() {
            return url;
        }
    }

    @Immutable
    public static class Attachment {
        private final int id;
        private final int projectId;
        private final String description;
        private final boolean isDefault;
        private final String thumbnailUrl;
        private final String title;
        private final String url;
        private final int status;

        public Attachment(int id, int projectId, String description, boolean isDefault, String thumbnailUrl, String title, String url, int status) {
            this.id = id;
            this.projectId = projectId;
            this.description = description;
            this.isDefault = isDefault;
            this.thumbnailUrl = thumbnailUrl;
            this.title = title;
            this.url = url;
            this.status = status;
        }

        public int getId() {
            return id;
        }

        public int getProjectId() {
            return projectId;
        }

        public String getDescription() {
            return description;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public String getThumbnailUrl() {
            return thumbnailUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public int getStatus() {
            return status;
        }
    }

    @Immutable
    public static class Dependency {
        private final int modId;
        private final int relationType;

        public Dependency() {
            this(0, 1);
        }

        public Dependency(int modId, int relationType) {
            this.modId = modId;
            this.relationType = relationType;
        }

        public int getModId() {
            return modId;
        }

        public int getRelationType() {
            return relationType;
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#schemafilehash">Schema</a>
     */
    @Immutable
    public static class LatestFileHash {
        private final String value;
        private final int algo;

        public LatestFileHash(String value, int algo) {
            this.value = value;
            this.algo = algo;
        }

        public String getValue() {
            return value;
        }

        public int getAlgo() {
            return algo;
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_File">Schema</a>
     */
    @Immutable
    public static class LatestFile implements RemoteMod.IVersion {
        private final int id;
        private final int gameId;
        private final int modId;
        private final boolean isAvailable;
        private final String displayName;
        private final String fileName;
        private final int releaseType;
        private final int fileStatus;
        private final List<LatestFileHash> hashes;
        private final Date fileDate;
        private final int fileLength;
        private final int downloadCount;
        private final String downloadUrl;
        private final List<String> gameVersions;
        private final List<Dependency> dependencies;
        private final int alternateFileId;
        private final boolean isServerPack;
        private final long fileFingerprint;

        public LatestFile(int id, int gameId, int modId, boolean isAvailable, String displayName, String fileName, int releaseType, int fileStatus, List<LatestFileHash> hashes, Date fileDate, int fileLength, int downloadCount, String downloadUrl, List<String> gameVersions, List<Dependency> dependencies, int alternateFileId, boolean isServerPack, long fileFingerprint) {
            this.id = id;
            this.gameId = gameId;
            this.modId = modId;
            this.isAvailable = isAvailable;
            this.displayName = displayName;
            this.fileName = fileName;
            this.releaseType = releaseType;
            this.fileStatus = fileStatus;
            this.hashes = hashes;
            this.fileDate = fileDate;
            this.fileLength = fileLength;
            this.downloadCount = downloadCount;
            this.downloadUrl = downloadUrl;
            this.gameVersions = gameVersions;
            this.dependencies = dependencies;
            this.alternateFileId = alternateFileId;
            this.isServerPack = isServerPack;
            this.fileFingerprint = fileFingerprint;
        }

        public int getId() {
            return id;
        }

        public int getGameId() {
            return gameId;
        }

        public int getModId() {
            return modId;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getFileName() {
            return fileName;
        }

        public int getReleaseType() {
            return releaseType;
        }

        public int getFileStatus() {
            return fileStatus;
        }

        public List<LatestFileHash> getHashes() {
            return hashes;
        }

        public Date getFileDate() {
            return fileDate;
        }

        public int getFileLength() {
            return fileLength;
        }

        public int getDownloadCount() {
            return downloadCount;
        }

        public String getDownloadUrl() {
            if (downloadUrl == null) {
                // This addon is not allowed for distribution, and downloadUrl will be null.
                // We try to find its download url.
                return String.format("https://edge.forgecdn.net/files/%d/%d/%s", id / 1000, id % 1000, fileName);
            }
            return downloadUrl;
        }

        public List<String> getGameVersions() {
            return gameVersions;
        }

        public List<Dependency> getDependencies() {
            return dependencies;
        }

        public int getAlternateFileId() {
            return alternateFileId;
        }

        public boolean isServerPack() {
            return isServerPack;
        }

        public long getFileFingerprint() {
            return fileFingerprint;
        }

        @Override
        public RemoteMod.Type getType() {
            return RemoteMod.Type.CURSEFORGE;
        }

        public RemoteMod.Version toVersion() {
            RemoteMod.VersionType versionType;
            switch (getReleaseType()) {
                case 1:
                    versionType = RemoteMod.VersionType.Release;
                    break;
                case 2:
                    versionType = RemoteMod.VersionType.Beta;
                    break;
                case 3:
                    versionType = RemoteMod.VersionType.Alpha;
                    break;
                default:
                    versionType = RemoteMod.VersionType.Release;
                    break;
            }

            return new RemoteMod.Version(
                    this,
                    Integer.toString(modId),
                    getDisplayName(),
                    getFileName(),
                    null,
                    getFileDate(),
                    versionType,
                    new RemoteMod.File(Collections.emptyMap(), getDownloadUrl(), getFileName()),
                    Collections.emptyList(),
                    gameVersions.stream().filter(ver -> ver.startsWith("1.") || ver.contains("w")).collect(Collectors.toList()),
                    gameVersions.stream().flatMap(version -> {
                        if ("fabric".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.FABRIC);
                        else if ("forge".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.FORGE);
                        else if ("quilt".equalsIgnoreCase(version)) return Stream.of(ModLoaderType.QUILT);
                        else return Stream.empty();
                    }).collect(Collectors.toList())
            );
        }
    }

    /**
     * @see <a href="https://docs.curseforge.com/#tocS_FileIndex">Schema</a>
     */
    @Immutable
    public static class LatestFileIndex {
        private final String gameVersion;
        private final int fileId;
        private final String filename;
        private final int releaseType;
        private final int gameVersionTypeId;
        private final int modLoader;

        public LatestFileIndex(String gameVersion, int fileId, String filename, int releaseType, int gameVersionTypeId, int modLoader) {
            this.gameVersion = gameVersion;
            this.fileId = fileId;
            this.filename = filename;
            this.releaseType = releaseType;
            this.gameVersionTypeId = gameVersionTypeId;
            this.modLoader = modLoader;
        }

        public String getGameVersion() {
            return gameVersion;
        }

        public int getFileId() {
            return fileId;
        }

        public String getFilename() {
            return filename;
        }

        public int getReleaseType() {
            return releaseType;
        }

        @Nullable
        public int getGameVersionTypeId() {
            return gameVersionTypeId;
        }

        public int getModLoader() {
            return modLoader;
        }
    }

    @Immutable
    public static class Category {
        private final int id;
        private final int gameId;
        private final String name;
        private final String slug;
        private final String url;
        private final String iconUrl;
        private final Date dateModified;
        private final boolean isClass;
        private final int classId;
        private final int parentCategoryId;

        private transient final List<Category> subcategories;

        public Category() {
            this(0, 0, "", "", "", "", new Date(), false, 0, 0);
        }

        public Category(int id, int gameId, String name, String slug, String url, String iconUrl, Date dateModified, boolean isClass, int classId, int parentCategoryId) {
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

        public Date getDateModified() {
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

        public RemoteModRepository.Category toCategory() {
            return new RemoteModRepository.Category(
                    this,
                    Integer.toString(id),
                    getSubcategories().stream().map(Category::toCategory).collect(Collectors.toList()));
        }
    }
}
