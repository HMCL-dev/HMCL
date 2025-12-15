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
package org.jackhuang.hmcl.download.forge;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ForgeBMCLVersionList extends VersionList<ForgeRemoteVersion> {
    private final String apiRoot;

    /**
     * @param apiRoot API Root of BMCLAPI implementations
     */
    public ForgeBMCLVersionList(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task<?> refreshAsync() {
        throw new UnsupportedOperationException("ForgeBMCLVersionList does not support loading the entire Forge remote version list.");
    }

    private static String toLookupVersion(String gameVersion) {
        return "1.7.10-pre4".equals(gameVersion) ? "1.7.10_pre4" : gameVersion;
    }

    private static String fromLookupVersion(String lookupVersion) {
        return "1.7.10_pre4".equals(lookupVersion) ? "1.7.10-pre4" : lookupVersion;
    }

    private static String toLookupBranch(String gameVersion, String branch) {
        if ("1.7.10-pre4".equals(gameVersion)) {
            return "prerelease";
        }
        return Lang.requireNonNullElse(branch, "");
    }

    @Override
    public Task<?> refreshAsync(String gameVersion) {
        String lookupVersion = toLookupVersion(gameVersion);

        return new GetTask(apiRoot + "/forge/minecraft/" + lookupVersion).thenGetJsonAsync(listTypeOf(ForgeVersion.class))
                .thenAcceptAsync(forgeVersions -> {
                    lock.writeLock().lock();
                    try {
                        versions.clear(gameVersion);
                        if (forgeVersions == null) return;
                        for (ForgeVersion version : forgeVersions) {
                            if (version == null)
                                continue;
                            List<String> urls = new ArrayList<>();
                            for (ForgeVersion.File file : version.getFiles())
                                if ("installer".equals(file.getCategory()) && "jar".equals(file.getFormat())) {
                                    String branch = toLookupBranch(gameVersion, version.getBranch());

                                    String classifier = lookupVersion + "-" + version.getVersion() + (branch.isEmpty() ? "" : '-' + branch);
                                    String fileName1 = "forge-" + classifier + "-" + file.getCategory() + "." + file.getFormat();
                                    String fileName2 = "forge-" + classifier + "-" + lookupVersion + "-" + file.getCategory() + "." + file.getFormat();
                                    urls.add("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + classifier + "/" + fileName1);
                                    urls.add("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + classifier + "-" + lookupVersion + "/" + fileName2);
                                    urls.add(NetworkUtils.withQuery("https://bmclapi2.bangbang93.com/forge/download", mapOf(
                                            pair("mcversion", version.getGameVersion()),
                                            pair("version", version.getVersion()),
                                            pair("branch", branch),
                                            pair("category", file.getCategory()),
                                            pair("format", file.getFormat())
                                    )));
                                }

                            if (urls.isEmpty())
                                continue;

                            Instant releaseDate = null;
                            if (version.getModified() != null) {
                                try {
                                    releaseDate = Instant.parse(version.getModified());
                                } catch (DateTimeParseException e) {
                                    LOG.warning("Failed to parse instant " + version.getModified(), e);
                                }
                            }

                            versions.put(gameVersion, new ForgeRemoteVersion(
                                    fromLookupVersion(version.getGameVersion()), version.getVersion(), releaseDate, urls));
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Override
    public Optional<ForgeRemoteVersion> getVersion(String gameVersion, String remoteVersion) {
        remoteVersion = StringUtils.substringAfter(remoteVersion, "-", remoteVersion);
        return super.getVersion(gameVersion, remoteVersion);
    }

    @Immutable
    public static final class ForgeVersion implements Validation {

        private final String branch;
        private final int build;
        private final String mcversion;
        private final String modified;
        private final String version;
        private final List<File> files;

        /**
         * No-arg constructor for Gson.
         */
        @SuppressWarnings("unused")
        public ForgeVersion() {
            this(null, 0, "", null, "", Collections.emptyList());
        }

        public ForgeVersion(String branch, int build, String mcversion, String modified, String version, List<File> files) {
            this.branch = branch;
            this.build = build;
            this.mcversion = mcversion;
            this.modified = modified;
            this.version = version;
            this.files = files;
        }

        @Nullable
        public String getBranch() {
            return branch;
        }

        public int getBuild() {
            return build;
        }

        @NotNull
        public String getGameVersion() {
            return mcversion;
        }

        @Nullable
        public String getModified() {
            return modified;
        }

        @NotNull
        public String getVersion() {
            return version;
        }

        @NotNull
        public List<File> getFiles() {
            return files;
        }

        @Override
        public void validate() throws JsonParseException {
            if (files == null)
                throw new JsonParseException("ForgeVersion files cannot be null");
            if (version == null)
                throw new JsonParseException("ForgeVersion version cannot be null");
            if (mcversion == null)
                throw new JsonParseException("ForgeVersion mcversion cannot be null");
        }

        @Immutable
        public static final class File {
            private final String format;
            private final String category;
            private final String hash;

            public File() {
                this("", "", "");
            }

            public File(String format, String category, String hash) {
                this.format = format;
                this.category = category;
                this.hash = hash;
            }

            public String getFormat() {
                return format;
            }

            public String getCategory() {
                return category;
            }

            public String getHash() {
                return hash;
            }
        }
    }
}
