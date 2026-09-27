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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class ForgeRemoteVersion extends ComponentRemoteVersion {

    public static final WebURL FORGE_LIST = WebURL.parse("https://hmcl.glavo.site/metadata/forge/");

    private static String toLookupVersion(String gameVersion) {
        return "1.7.10-pre4".equals(gameVersion) ? "1.7.10_pre4" : gameVersion;
    }

    private static String toLookupBranch(String gameVersion, String branch) {
        if ("1.7.10-pre4".equals(gameVersion)) {
            return "prerelease";
        }
        return Objects.requireNonNullElse(branch, "");
    }

    public static Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchAsync(DownloadCandidates candidates, GameVersionNumber gameVersion) {
        return new GetTask(candidates).thenApplyAsync(result -> {
            ForgeVersionRoot root = JsonUtils.GSON.fromJson(result, ForgeVersionRoot.class);

            TreeSet<ForgeRemoteVersion> versions = new TreeSet<>();

            for (Map.Entry<String, int[]> entry : root.mcversion().entrySet()) {
                if (gameVersion.equals(GameVersionNumber.asGameVersion(entry.getKey()))) {
                    for (int v : entry.getValue()) {
                        ForgeVersion version = root.number().get(v);
                        if (version == null)
                            continue;
                        String jar = null;
                        for (String[] file : version.getFiles())
                            if (file.length > 1 && "installer".equals(file[1])) {
                                String classifier = version.getGameVersion() + "-" + version.getVersion()
                                        + (StringUtils.isNotBlank(version.getBranch()) ? "-" + version.getBranch() : "");
                                String fileName = root.artifact() + "-" + classifier + "-" + file[1] + "." + file[0];
                                jar = root.webpath() + classifier + "/" + fileName;
                            }

                        if (jar == null)
                            continue;

                        versions.add(new ForgeRemoteVersion(
                                GameVersionNumber.asGameVersion(toLookupVersion(version.getGameVersion())),
                                version.getVersion(),
                                version.getModified() > 0 ? Instant.ofEpochSecond(version.getModified()) : null,
                                Collections.singletonList(jar)
                        ));
                    }
                    break;
                }
            }

            return ComponentRemoteVersionList.of(GameComponentType.FORGE, versions);
        });
    }

    public static Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchBMCLAsync(String bmclRoot, GameVersionNumber gameVersion) {
        String lookupVersion = toLookupVersion(gameVersion.toString());

        return new GetTask(DownloadCandidates.of(bmclRoot + "/forge/minecraft/" + lookupVersion)).thenApplyAsync(result -> {
            List<ForgeBMCLVersion> forgeVersions = JsonUtils.fromNonNullJson(result, listTypeOf(ForgeBMCLVersion.class));

            TreeSet<ForgeRemoteVersion> versions = new TreeSet<>();
            for (ForgeBMCLVersion version : forgeVersions) {
                if (version == null)
                    continue;

                List<String> urls = new ArrayList<>();
                for (ForgeBMCLVersion.File file : version.files())
                    if ("installer".equals(file.category()) && "jar".equals(file.format())) {
                        String branch = toLookupBranch(gameVersion.toString(), version.branch());

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

                versions.add(new ForgeRemoteVersion(GameVersionNumber.asGameVersion(version.mcversion()), version.version(), releaseDate, urls));
            }

            return ComponentRemoteVersionList.of(GameComponentType.FORGE, versions);
        });
    }

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param url         the installer or universal jar original URL.
     */
    public ForgeRemoteVersion(GameVersionNumber gameVersion, String selfVersion, Instant releaseDate, List<String> url) {
        super(GameComponentType.FORGE, gameVersion, selfVersion, releaseDate, Type.UNCATEGORIZED, url);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new ForgeInstallTask(dependencyManager, baseManifest, this);
    }
}
