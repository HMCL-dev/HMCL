/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ForgeBMCLVersionList extends VersionList<ForgeRemoteVersion> {

    public static final ForgeBMCLVersionList INSTANCE = new ForgeBMCLVersionList();

    private ForgeBMCLVersionList() {
    }

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task<?> loadAsync(DownloadProvider downloadProvider) {
        throw new UnsupportedOperationException("ForgeBMCLVersionList does not support loading the entire Forge remote version list.");
    }

    @Override
    public Task<?> refreshAsync(DownloadProvider downloadProvider) {
        throw new UnsupportedOperationException("ForgeBMCLVersionList does not support loading the entire Forge remote version list.");
    }

    @Override
    public Task<?> refreshAsync(String gameVersion, DownloadProvider downloadProvider) {
        final GetTask task = new GetTask(NetworkUtils.toURL("https://bmclapi2.bangbang93.com/forge/minecraft/" + gameVersion));
        return new Task<Void>() {
            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() {
                lock.writeLock().lock();

                try {
                    List<ForgeVersion> forgeVersions = JsonUtils.GSON.fromJson(task.getResult(), new TypeToken<List<ForgeVersion>>() {
                    }.getType());
                    versions.clear(gameVersion);
                    if (forgeVersions == null) return;
                    for (ForgeVersion version : forgeVersions) {
                        if (version == null)
                            continue;
                        String jar = null;
                        for (ForgeVersion.File file : version.getFiles())
                            if ("installer".equals(file.getCategory())) {
                                String classifier = gameVersion + "-" + version.getVersion()
                                        + (StringUtils.isNotBlank(version.getBranch()) ? "-" + version.getBranch() : "");
                                String fileName = "forge-" + classifier + "-" + file.getCategory() + "." + file.getFormat();
                                jar = "https://bmclapi2.bangbang93.com/maven/net/minecraftforge/forge/" + classifier + "/" + fileName;
                            }

                        if (jar == null)
                            continue;
                        versions.put(gameVersion, new ForgeRemoteVersion(
                                version.getGameVersion(), version.getVersion(), jar
                        ));
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        };
    }

    @Immutable
    public static final class ForgeVersion implements Validation {

        private final String branch;
        private final String mcversion;
        private final String version;
        private final List<File> files;

        /**
         * No-arg constructor for Gson.
         */
        @SuppressWarnings("unused")
        public ForgeVersion() {
            this(null, null, null, null);
        }

        public ForgeVersion(String branch, String mcversion, String version, List<File> files) {
            this.branch = branch;
            this.mcversion = mcversion;
            this.version = version;
            this.files = files;
        }

        public String getBranch() {
            return branch;
        }

        public String getGameVersion() {
            return mcversion;
        }

        public String getVersion() {
            return version;
        }

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
