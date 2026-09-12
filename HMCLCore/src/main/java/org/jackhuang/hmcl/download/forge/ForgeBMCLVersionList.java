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
import org.jackhuang.hmcl.download.ComponentVersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import static org.jackhuang.hmcl.download.forge.ForgeInstallation.fromLookupVersion;
import static org.jackhuang.hmcl.download.forge.ForgeInstallation.toLookupVersion;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ForgeBMCLVersionList extends ComponentVersionList<ForgeRemoteVersion> {
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

    private static String toLookupBranch(String gameVersion, String branch) {
        if ("1.7.10-pre4".equals(gameVersion)) {
            return "prerelease";
        }
        return Objects.requireNonNullElse(branch, "");
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
                            for (ForgeVersion.File file : version.files())
                                if (("installer".equals(file.category()) && "jar".equals(file.format())) || (("client".equals(file.category()) || "universal".equals(file.category())) && "zip".equals(file.format()))) {
                                    String branch = toLookupBranch(gameVersion, version.branch());

                                    String classifier = lookupVersion + "-" + version.version() + (branch.isEmpty() ? "" : '-' + branch);
                                    String fileName1 = "forge-" + classifier + "-" + file.category() + "." + file.format();
                                    String fileName2 = "forge-" + classifier + "-" + lookupVersion + "-" + file.category() + "." + file.format();
                                    urls.add("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + classifier + "/" + fileName1);
                                    urls.add("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + classifier + "-" + lookupVersion + "/" + fileName2);
                                    urls.add(NetworkUtils.withQuery("https://bmclapi2.bangbang93.com/forge/download", mapOf(
                                            pair("mcversion", version.mcversion()),
                                            pair("version", version.version()),
                                            pair("branch", branch),
                                            pair("category", file.category()),
                                            pair("format", file.format())
                                    )));
                                }

                            if (urls.isEmpty())
                                continue;

                            Instant releaseDate = null;
                            if (version.modified() != null) {
                                try {
                                    releaseDate = Instant.parse(version.modified());
                                } catch (DateTimeParseException e) {
                                    LOG.warning("Failed to parse instant " + version.modified(), e);
                                }
                            }

                            versions.put(gameVersion, new ForgeRemoteVersion(
                                    fromLookupVersion(version.mcversion()), version.version(), releaseDate, urls));
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
    @JsonSerializable
    public record ForgeVersion(String branch, int build, String mcversion, String modified, String version,
                               List<File> files) implements Validation {

        /**
         * No-arg constructor for Gson.
         */
        @SuppressWarnings("unused")
        public ForgeVersion() {
            this(null, 0, "", null, "", Collections.emptyList());
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
        @JsonSerializable
        public record File(String format, String category, String hash) {
            public File() {
                this("", "", "");
            }
        }
    }
}
