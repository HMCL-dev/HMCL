// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.optifine;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

@NotNullByDefault
public final class OptiFineRemoteVersion extends ComponentRemoteVersion {

    private static String toLookupVersion(String version) {
        return switch (version) {
            case "1.8" -> "1.8.0";
            case "1.9" -> "1.9.0";
            default -> version;
        };
    }

    private static String fromLookupVersion(String version) {
        return switch (version) {
            case "1.8.0" -> "1.8";
            case "1.9.0" -> "1.9";
            default -> version;
        };
    }

    public static Task<ComponentRemoteVersionList<OptiFineRemoteVersion>> fetchBMCLAsync(DownloadProvider downloadProvider, String bmclRoot, GameVersionNumber gameVersion) {
        @JsonSerializable
        record OptiFineVersion(String dl, String ver,
                               String date, String type,
                               @Nullable String patch, String mirror,
                               String mcversion) {
        }

        return new GetTask(DownloadCandidates.of(bmclRoot + "/optifine/" + toLookupVersion(gameVersion.toNormalizedString()))).thenApplyAsync(result -> {
            var root = JsonUtils.fromNonNullJson(result, listTypeOf(OptiFineVersion.class));

            var versions = new TreeSet<OptiFineRemoteVersion>();
            Set<String> duplicates = new HashSet<>();
            for (OptiFineVersion element : root) {
                String version = element.type() + "_" + element.patch();
                String mirror = bmclRoot + "/optifine/" + toLookupVersion(element.mcversion()) + "/" + element.type() + "/" + element.patch();
                if (!duplicates.add(mirror))
                    continue;

                boolean isPre = element.patch() != null && (element.patch().startsWith("pre") || element.patch().startsWith("alpha"));

                if (StringUtils.isBlank(element.mcversion()))
                    continue;

                versions.add(new OptiFineRemoteVersion(gameVersion, version, List.of(mirror), isPre));
            }

            return ComponentRemoteVersionList.of(GameComponentType.OPTIFINE, versions);
        });

    }

    private final String fullVersion;

    public OptiFineRemoteVersion(GameVersionNumber gameVersion, String selfVersion, List<String> urls,
                                 boolean snapshot) {
        super(GameComponentType.OPTIFINE, gameVersion, selfVersion, null, snapshot ? Type.SNAPSHOT : Type.RELEASE, urls);
        this.fullVersion = getGameVersion() + "_" + getSelfVersion();
    }

    @Override
    public String getFullVersion() {
        return fullVersion;
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest
            baseManifest, Path modsDirectory) {
        return new GameDownloadTask(dependencyManager, baseManifest)
                .thenComposeAsync(minecraftJar -> new OptiFineInstallTask(
                        dependencyManager,
                        baseManifest,
                        this,
                        minecraftJar));
    }
}
