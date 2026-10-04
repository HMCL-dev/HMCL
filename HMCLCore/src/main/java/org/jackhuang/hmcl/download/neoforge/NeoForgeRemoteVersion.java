/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.neoforge;

import com.google.gson.annotations.SerializedName;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class NeoForgeRemoteVersion extends ComponentRemoteVersion {

    private static final GameVersionNumber GAME_VERSION_1_20_1 = GameVersionNumber.asGameVersion("1.20.1");

    public static final WebURL OLD_URL = WebURL.parse("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/forge");
    public static final WebURL META_URL = WebURL.parse("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");

    private static Type getType(String version) {
        return version.contains("beta") || version.contains("alpha") ? Type.SNAPSHOT : Type.RELEASE;
    }

    private static String normalize(String version) {
        if (version.startsWith("1.20.1-")) {
            if (version.startsWith("forge-", "1.20.1-".length())) {
                return version.substring("1.20.1-forge-".length());
            } else {
                return version.substring("1.20.1-".length());
            }
        } else {
            return version;
        }
    }

    public static Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchAsync(
            DownloadProvider downloadProvider, GameVersionNumber gameVersion) {
        @JsonSerializable
        record OfficialAPIResult(boolean isSnapshot, List<String> versions) {
        }

        boolean isOld = gameVersion.equals(GAME_VERSION_1_20_1);
        return new GetTask(isOld ? downloadProvider.getNeoForgeOldVersionListCandidates() : downloadProvider.getNeoForgeVersionListCandidates())
                .thenApplyAsync(result -> {
                    OfficialAPIResult apiResult = JsonUtils.fromNonNullJson(result, OfficialAPIResult.class);

                    TreeSet<NeoForgeRemoteVersion> versions = new TreeSet<>();
                    if (isOld) {
                        for (String version : apiResult.versions) {
                            versions.add(new NeoForgeRemoteVersion(
                                    GAME_VERSION_1_20_1,
                                    NeoForgeRemoteVersion.normalize(version),
                                    version,
                                    List.of(
                                            "https://maven.neoforged.net/releases/net/neoforged/forge/" + version + "/forge-" + version + "-installer.jar"
                                    )));
                        }
                    } else {
                        for (String version : apiResult.versions) {
                            GameVersionNumber mcVersion;

                            try {
                                int si1 = version.indexOf('.');
                                int si2 = version.indexOf('.', si1 + 1);
                                if (si1 < 0 || si2 < 0) {
                                    LOG.warning("Unsupported NeoForge version: " + version);
                                    continue;
                                }

                                int majorVersion = Integer.parseInt(version.substring(0, si1));
                                if (majorVersion == 0) { // Snapshot version.
                                    mcVersion = GameVersionNumber.asGameVersion(version.substring(si1 + 1, si2));
                                } else {
                                    if (majorVersion >= 26) {
                                        int si3 = version.indexOf('.', si2 + 1);

                                        if (si3 < 0) {
                                            LOG.warning("Unsupported NeoForge version: " + version);
                                            continue;
                                        }

                                        String ver = Integer.parseInt(version.substring(si2 + 1, si3)) == 0
                                                ? version.substring(0, si2)
                                                : version.substring(0, si3);

                                        int separator = version.indexOf('+');
                                        if (separator < 0)
                                            mcVersion = GameVersionNumber.asGameVersion(ver);
                                        else
                                            mcVersion = GameVersionNumber.asGameVersion(ver + "-" + version.substring(separator + 1));
                                    } else {
                                        String ver = Integer.parseInt(version.substring(si1 + 1, si2)) == 0
                                                ? version.substring(0, si1)
                                                : version.substring(0, si2);
                                        mcVersion = GameVersionNumber.asGameVersion("1." + ver);
                                    }
                                }
                            } catch (RuntimeException e) {
                                LOG.warning("Cannot parse NeoForge version %s for cracking its mc version.".formatted(version), e);
                                continue;
                            }


                            if (gameVersion.equals(mcVersion)) {
                                versions.add(new NeoForgeRemoteVersion(
                                        mcVersion, NeoForgeRemoteVersion.normalize(version), version,
                                        List.of(
                                                "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-installer.jar"
                                        )));
                            }
                        }
                    }

                    return ComponentRemoteVersionList.of(GameComponentType.NEO_FORGE, versions);
                });

    }

    public static Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchBMCLAsync(String bmclRoot, GameVersionNumber gameVersion) {
        @JsonSerializable
        record NeoForgeBMCLVersion(String rawVersion,
                                   String version,
                                   @SerializedName("mcversion") String mcVersion) {
            public NeoForgeBMCLVersion {
                Objects.requireNonNull(rawVersion, "rawVersion");
                Objects.requireNonNull(version, "version");
                Objects.requireNonNull(mcVersion, "mcversion");
            }
        }

        return new GetTask(DownloadCandidates.of(bmclRoot + "/neoforge/list/" + gameVersion)).thenGetJsonAsync(listTypeOf(NeoForgeBMCLVersion.class))
                .thenApplyAsync(neoForgeVersions -> {
                    var versions = new TreeSet<NeoForgeRemoteVersion>();
                    for (NeoForgeBMCLVersion neoForgeVersion : neoForgeVersions) {
                        versions.add(new NeoForgeRemoteVersion(
                                GameVersionNumber.asGameVersion(neoForgeVersion.mcVersion),
                                NeoForgeRemoteVersion.normalize(neoForgeVersion.version),
                                neoForgeVersion.version,
                                List.of(bmclRoot + "/neoforge/version/" + neoForgeVersion.version + "/download/installer.jar")));
                    }
                    return ComponentRemoteVersionList.of(GameComponentType.NEO_FORGE, versions);
                });
    }

    private final String fullVersion;

    public NeoForgeRemoteVersion(GameVersionNumber gameVersion, String selfVersion, String fullVersion, List<String> urls) {
        super(GameComponentType.NEO_FORGE, gameVersion, selfVersion, null, getType(selfVersion), urls);
        this.fullVersion = fullVersion;
    }

    @Override
    public String getFullVersion() {
        return fullVersion;
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new NeoForgeInstallTask(dependencyManager, baseManifest, this);
    }

}
