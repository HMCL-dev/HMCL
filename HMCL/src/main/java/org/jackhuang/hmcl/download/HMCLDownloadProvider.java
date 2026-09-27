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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.neoforge.NeoForgeRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// @author Glavo
@NotNullByDefault
public final class HMCLDownloadProvider extends DownloadProvider {
    private static final String BMCLAPI_ROOT = System.getProperty("hmcl.bmclapi.override", "https://bmclapi2.bangbang93.com");

    private volatile DownloadSource versionListSource = DownloadSource.DEFAULT;
    private volatile DownloadSource fileSource = DownloadSource.DEFAULT;

    private static DownloadCandidates getCandidates(
            DownloadSource source,
            String defaultUrl, String bmclapiUrl
    ) {
        if (LocaleUtils.IS_CHINA_MAINLAND) {
            return switch (source) {
                case DEFAULT, OFFICIAL -> DownloadCandidates.of(defaultUrl, bmclapiUrl);
                case MIRROR -> DownloadCandidates.of(bmclapiUrl, defaultUrl);
            };
        } else {
            return switch (source) {
                case DEFAULT, OFFICIAL -> DownloadCandidates.of(defaultUrl);
                case MIRROR -> DownloadCandidates.of(bmclapiUrl, defaultUrl);
            };
        }
    }

    private final List<MirrorRule> rules = List.of(
            new MirrorRule("https://bmclapi2.bangbang93.com", BMCLAPI_ROOT),
            new MirrorRule("https://launchermeta.mojang.com", BMCLAPI_ROOT),
            new MirrorRule("https://piston-meta.mojang.com", BMCLAPI_ROOT),
            new MirrorRule("https://piston-data.mojang.com", BMCLAPI_ROOT),
            new MirrorRule("https://launcher.mojang.com", BMCLAPI_ROOT),
            new MirrorRule("https://libraries.minecraft.net", BMCLAPI_ROOT + "/libraries"),
            new MirrorRule("http://files.minecraftforge.net/maven", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://files.minecraftforge.net/maven", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://maven.minecraftforge.net", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://maven.neoforged.net/releases/", BMCLAPI_ROOT + "/maven/"),
            new MirrorRule("http://dl.liteloader.com/versions", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://dl.liteloader.com/versions", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://meta.fabricmc.net", BMCLAPI_ROOT + "/fabric-meta"),
            new MirrorRule("https://maven.fabricmc.net", BMCLAPI_ROOT + "/maven"),
            new MirrorRule("https://authlib-injector.yushi.moe", BMCLAPI_ROOT + "/mirrors/authlib-injector"),
            new MirrorRule("https://repo1.maven.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
            new MirrorRule("https://repo.maven.apache.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
            new MirrorRule("https://hmcl.glavo.site/metadata/cleanroom", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom"),
            new MirrorRule("https://hmcl.glavo.site/metadata/fmllibs", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/fmllibs"),
            new MirrorRule("https://zkitefly.github.io/unlisted-versions-of-minecraft", "https://alist.8mi.tech/d/mirror/unlisted-versions-of-minecraft/Auto"),

            // https://github.com/mcmod-info-mirror/mcim-rust-api
            new MirrorRule("https://api.modrinth.com", "https://mod.mcimirror.top/modrinth", true),
            new MirrorRule("https://cdn.modrinth.com", "https://mod.mcimirror.top", true),
            new MirrorRule("https://api.curseforge.com", "https://mod.mcimirror.top/curseforge", true),
            new MirrorRule("https://edge.forgecdn.net", "https://mod.mcimirror.top", true)
    );

    @Override
    protected Task<? extends ComponentRemoteVersionList<?>> fetchVersionsAsync(GameComponentType type, @Nullable GameVersionNumber gameVersion) {
        assert (gameVersion == null) == (type == GameComponentType.GAME);
        switch (type) {
            case GAME -> {
                return GameRemoteVersion.fetchAsync(getCandidates(
                        versionListSource,
                        GameRemoteVersion.VERSION_MANIFEST_URL,
                        BMCLAPI_ROOT + "/mc/game/version_manifest.json"
                ));
            }
            case FORGE -> {
                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchOfficial = ForgeRemoteVersion.fetchAsync(DownloadCandidates.of(ForgeRemoteVersion.FORGE_LIST), gameVersion);

                DownloadSource source = versionListSource;
                if (!LocaleUtils.IS_CHINA_MAINLAND && (
                        source == DownloadSource.DEFAULT || source == DownloadSource.OFFICIAL
                )) {
                    return fetchOfficial;
                }

                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchBMCL = ForgeRemoteVersion.fetchBMCLAsync(BMCLAPI_ROOT, gameVersion);
                if (source == DownloadSource.MIRROR) {
                    return new FallbackTask<>(fetchBMCL, fetchOfficial);
                } else {
                    return new FallbackTask<>(fetchOfficial, fetchBMCL);
                }
            }
            case NEO_FORGE -> {
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchOfficial = NeoForgeRemoteVersion.fetchAsync(
                        DownloadCandidates.of(NeoForgeRemoteVersion.META_URL),
                        DownloadCandidates.of(NeoForgeRemoteVersion.OLD_URL),
                        gameVersion
                );

                DownloadSource source = versionListSource;
                if (!LocaleUtils.IS_CHINA_MAINLAND && (
                        source == DownloadSource.DEFAULT || source == DownloadSource.OFFICIAL
                )) {
                    return fetchOfficial;
                }
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchBMCL = NeoForgeRemoteVersion.fetchBMCLAsync(BMCLAPI_ROOT, gameVersion);
                if (source == DownloadSource.MIRROR || source == DownloadSource.DEFAULT) {
                    return new FallbackTask<>(fetchBMCL, fetchOfficial);
                } else {
                    return new FallbackTask<>(fetchOfficial, fetchBMCL);
                }
            }
            case OPTIFINE -> {
                return OptiFineRemoteVersion.fetchBMCLAsync(BMCLAPI_ROOT, gameVersion);
            }
        }

        return super.fetchVersionsAsync(type, gameVersion);
    }

    public void setFileSource(DownloadSource fileSource) {
        this.fileSource = Objects.requireNonNull(fileSource);
    }

    public void setVersionListSource(DownloadSource versionListSource) {
        this.versionListSource = Objects.requireNonNull(versionListSource);
    }

    @Override
    public DownloadCandidates getDownloadCandidates(List<String> urls) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("urls cannot be empty");
        }

        DownloadSource source = fileSource;

        // true means prefer mirror, false means prefer official, null means do not use mirror
        @Nullable Boolean mirrorFirst;
        if (LocaleUtils.IS_CHINA_MAINLAND) {
            mirrorFirst = switch (source) {
                case DEFAULT, MIRROR -> true;
                case OFFICIAL -> false;
            };
        } else {
            mirrorFirst = switch (source) {
                case DEFAULT, OFFICIAL -> null;
                case MIRROR -> true;
            };
        }

        if (mirrorFirst == null) {
            return super.getDownloadCandidates(urls);
        }

        var candidates = new ArrayList<DownloadCandidate>(urls.size() * 2);
        for (String url : urls) {
            DownloadCandidate candidate = DownloadCandidate.of(url);
            if (candidate.url() == null) {
                // Invalid URL, just add it to the list and let the download task handle it.
                candidates.add(candidate);
                continue;
            }

            @Nullable DownloadCandidate mirrorCandidate = null;
            boolean fallback = false;
            for (MirrorRule rule : rules) {
                if (url.startsWith(rule.source)) {
                    mirrorCandidate = DownloadCandidate.of(rule.target + url.substring(rule.source.length()));
                    fallback = rule.fallback;
                    break;
                }
            }

            if (mirrorCandidate == null) {
                candidates.add(candidate);
            } else if (mirrorFirst && !fallback) {
                candidates.add(mirrorCandidate);
                candidates.add(candidate);
            } else {
                candidates.add(candidate);
                candidates.add(mirrorCandidate);
            }
        }

        return DownloadCandidates.of(List.copyOf(candidates));
    }

    @Override
    public DownloadCandidates getAssetObjectCandidates(AssetObject assetObject) {
        return getCandidates(
                fileSource,
                "https://resources.download.minecraft.net/" + assetObject.getLocation(),
                BMCLAPI_ROOT + "/mc/assets/" + assetObject.getLocation()
        );
    }

    @NotNullByDefault
    private static final class FallbackTask<T> extends Task<T> {

        private final Task<? extends T> task;
        private final Task<? extends T> fallback;

        private List<Task<?>> dependencies = List.of();

        public FallbackTask(Task<? extends T> task, Task<? extends T> fallback) {
            this.task = task;
            this.fallback = fallback;
        }

        @Override
        public Collection<? extends Task<?>> getDependents() {
            return List.of(task);
        }

        @Override
        public boolean isRelyingOnDependents() {
            return false;
        }

        @Override
        public void execute() throws Exception {
            if (isDependentsSucceeded()) {
                setResult(task.getResult());
            } else {
                dependencies = List.of(fallback);
                fallback.storeTo(this::setResult);
            }
        }

        @Override
        public List<Task<?>> getDependencies() {
            return dependencies;
        }
    }

    private record MirrorRule(String source, String target, boolean fallback) {
        public MirrorRule(String source, String target) {
            this(source, target, false);
        }
    }
}

