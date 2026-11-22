// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.cleanroom.CleanroomRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.neoforge.NeoForgeRemoteVersion;
import org.jackhuang.hmcl.download.optifine.OptiFineRemoteVersion;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.setting.DownloadSource;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.RegionUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// @author Glavo
@NotNullByDefault
public final class HMCLDownloadProvider extends DownloadProvider {
    private volatile DownloadSource versionListSource = DownloadSource.DEFAULT;
    private volatile DownloadSource fileSource = DownloadSource.DEFAULT;

    private static DownloadCandidates getCandidates(
            DownloadSource source,
            WebURL defaultUrl, WebURL bmclapiUrl
    ) {
        if ("CN".equals(RegionUtils.MACHINE_REGION)) {
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

    private static DownloadCandidates getCandidates(DownloadSource source, List<MirrorRule> rules, List<String> urls) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("urls cannot be empty");
        }

        // true means prefer mirror, false means prefer official, null means do not use mirror
        @Nullable Boolean mirrorFirst;
        if ("CN".equals(RegionUtils.MACHINE_REGION)) {
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
            return DownloadCandidates.of(urls.stream().map(DownloadCandidate::of).toArray(DownloadCandidate[]::new));
        }

        var candidates = new ArrayList<DownloadCandidate>(urls.size() * 2);
        for (String url : urls) {
            DownloadCandidate candidate = DownloadCandidate.of(url);
            if (candidate.url() == null) {
                // Invalid URL, just add it to the list and let the download task handle it.
                candidates.add(candidate);
                continue;
            }

            boolean added = false;
            for (MirrorRule rule : rules) {
                @Nullable List<DownloadCandidate> list = rule.getCandidates(candidate, mirrorFirst);
                if (list != null) {
                    candidates.addAll(list);
                    added = true;
                    break;
                }
            }

            if (!added)
                candidates.add(candidate);
        }

        return DownloadCandidates.of(List.copyOf(candidates));
    }

    private final String bmclRoot;
    private final List<MirrorRule> rules;

    public HMCLDownloadProvider(String bmclRoot) {
        this.bmclRoot = bmclRoot;
        this.rules = List.of(
                new SimpleRule("https://bmclapi2.bangbang93.com", bmclRoot),
                new SimpleRule("https://launchermeta.mojang.com", bmclRoot),
                new SimpleRule("https://piston-meta.mojang.com", bmclRoot),
                new SimpleRule("https://piston-data.mojang.com", bmclRoot),
                new SimpleRule("https://launcher.mojang.com", bmclRoot),
                new SimpleRule("https://libraries.minecraft.net", bmclRoot + "/libraries"),
                new SimpleRule("http://files.minecraftforge.net/maven", bmclRoot + "/maven"),
                new SimpleRule("https://files.minecraftforge.net/maven", bmclRoot + "/maven"),
                new SimpleRule("https://maven.minecraftforge.net", bmclRoot + "/maven"),
                (source, mirrorFirst) -> {
                    String neoforgedMaven = "https://maven.neoforged.net/";
                    String neoforgedMavenRelease = "https://maven.neoforged.net/releases/";

                    if (source.url() == null || !source.url().href().startsWith(neoforgedMaven))
                        return null;

                    var candidates = new ArrayList<DownloadCandidate>();

                    if (source.url().href().startsWith(neoforgedMavenRelease)) {
                        DownloadCandidate bmclapiMirror = DownloadCandidate.of(bmclRoot + "/maven/"
                                + source.url().href().substring(neoforgedMavenRelease.length()));

                        if (mirrorFirst) {
                            candidates.add(bmclapiMirror);
                            candidates.add(source);
                        } else {
                            candidates.add(source);
                            candidates.add(bmclapiMirror);
                        }
                    } else {
                        candidates.add(source);
                    }

                    // https://neoforged.forgecdn.net
                    candidates.add(DownloadCandidate.of("https://neoforged.forgecdn.net/" + source.url().href().substring(neoforgedMaven.length())));
                    return candidates;
                },
                new SimpleRule("http://dl.liteloader.com/versions", bmclRoot + "/maven"),
                new SimpleRule("https://dl.liteloader.com/versions", bmclRoot + "/maven"),
                new SimpleRule("https://meta.fabricmc.net", bmclRoot + "/fabric-meta"),
                new SimpleRule("https://maven.fabricmc.net", bmclRoot + "/maven"),
                new SimpleRule("https://authlib-injector.yushi.moe", bmclRoot + "/mirrors/authlib-injector"),
                new SimpleRule("https://repo1.maven.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
                new SimpleRule("https://repo.maven.apache.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
                new SimpleRule("https://hmcl.glavo.site/metadata/cleanroom", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom"),
                new SimpleRule("https://hmcl.glavo.site/metadata/fmllibs", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/fmllibs"),
                new SimpleRule("https://zkitefly.github.io/unlisted-versions-of-minecraft", "https://alist.8mi.tech/d/mirror/unlisted-versions-of-minecraft/Auto"),

                // https://github.com/mcmod-info-mirror/mcim-rust-api
                new SimpleRule("https://api.modrinth.com", null, "https://mod.mcimirror.top/modrinth"),
                new SimpleRule("https://cdn.modrinth.com", null, "https://mod.mcimirror.top"),
                new SimpleRule("https://api.curseforge.com", null, "https://mod.mcimirror.top/curseforge"),
                new SimpleRule("https://edge.forgecdn.net", null, "https://mod.mcimirror.top")
        );
    }

    @Override
    protected Task<? extends ComponentRemoteVersionList<?>> fetchVersionsAsync(GameComponentType type, @Nullable GameVersionNumber gameVersion) {
        assert (gameVersion == null) == (type == GameComponentType.GAME);
        switch (type) {
            case FORGE -> {
                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchOfficial = ForgeRemoteVersion.fetchAsync(this, gameVersion);

                DownloadSource source = versionListSource;
                if (source == DownloadSource.DEFAULT || source == DownloadSource.OFFICIAL) {
                    return fetchOfficial;
                }

                Task<ComponentRemoteVersionList<ForgeRemoteVersion>> fetchBMCL = ForgeRemoteVersion.fetchBMCLAsync(this, bmclRoot, gameVersion);
                if (source == DownloadSource.MIRROR) {
                    return new FallbackTask<>(fetchBMCL, fetchOfficial);
                } else {
                    return new FallbackTask<>(fetchOfficial, fetchBMCL);
                }
            }
            case NEO_FORGE -> {
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchOfficial = NeoForgeRemoteVersion.fetchAsync(this, gameVersion);

                DownloadSource source = versionListSource;
                if (source == DownloadSource.DEFAULT || source == DownloadSource.OFFICIAL) {
                    return fetchOfficial;
                }
                Task<ComponentRemoteVersionList<NeoForgeRemoteVersion>> fetchBMCL = NeoForgeRemoteVersion.fetchBMCLAsync(bmclRoot, gameVersion);
                if (source == DownloadSource.MIRROR || source == DownloadSource.DEFAULT) {
                    return new FallbackTask<>(fetchBMCL, fetchOfficial);
                } else {
                    return new FallbackTask<>(fetchOfficial, fetchBMCL);
                }
            }
            case OPTIFINE -> {
                return OptiFineRemoteVersion.fetchBMCLAsync(this, bmclRoot, gameVersion);
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
    public HMCLCacheRepository getCacheRepository() {
        return HMCLCacheRepository.REPOSITORY;
    }

    @Override
    public DownloadCandidates getGameVersionListCandidates() {
        return getCandidates(
                versionListSource,
                GameRemoteVersion.VERSION_MANIFEST_URL,
                WebURL.parse(bmclRoot + "/mc/game/version_manifest.json")
        );
    }

    @Override
    public FabricLikeVersionListCandidates getFabricVersionListCandidates() {
        DownloadSource source = versionListSource;
        return new FabricLikeVersionListCandidates(
                getCandidates(source, FabricRemoteVersion.LOADER_META_URL, WebURL.parse(bmclRoot + "/fabric-meta/v2/versions/loader")),
                getCandidates(source, FabricRemoteVersion.GAME_META_URL, WebURL.parse(bmclRoot + "/fabric-meta/v2/versions/game"))
        );
    }

    @Override
    public DownloadCandidates getCleanroomVersionListCandidates() {
        return "CN".equals(RegionUtils.MACHINE_REGION)
                ? DownloadCandidates.of(CleanroomRemoteVersion.LOADER_LIST_URL, WebURL.parse("https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom/index.json"))
                : DownloadCandidates.of(CleanroomRemoteVersion.LOADER_LIST_URL);
    }

    @Override
    public DownloadCandidates getDownloadCandidates(List<String> urls) {
        return getCandidates(fileSource, rules, urls);
    }

    @Override
    public DownloadCandidates getVersionListCandidates(List<String> urls) {
        return getCandidates(versionListSource, rules, urls);
    }

    @Override
    public DownloadCandidates getAssetObjectCandidates(AssetObject assetObject) {
        return getCandidates(
                fileSource,
                WebURL.parse("https://resources.download.minecraft.net/" + assetObject.getLocation()),
                WebURL.parse(bmclRoot + "/assets/" + assetObject.getLocation())
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

    @NotNullByDefault
    @FunctionalInterface
    private interface MirrorRule {
        @Nullable List<DownloadCandidate> getCandidates(DownloadCandidate source, boolean mirrorFirst);
    }

    @NotNullByDefault
    private record SimpleRule(String source, @Nullable String first, @Nullable String fallback) implements MirrorRule {
        public SimpleRule(String source, String target) {
            this(source, target, null);
        }

        @Override
        public @Nullable List<DownloadCandidate> getCandidates(DownloadCandidate source, boolean mirrorFirst) {
            if (source.url() == null || !source.url().href().startsWith(this.source))
                return null;

            boolean addSourceFirst = !mirrorFirst || first == null;
            String urlWithoutPrefix = source.url().href().substring(this.source.length());

            var candidates = new ArrayList<DownloadCandidate>();

            if (addSourceFirst)
                candidates.add(source);

            if (first != null)
                candidates.add(DownloadCandidate.of(first + urlWithoutPrefix));

            if (!addSourceFirst)
                candidates.add(source);

            if (fallback != null)
                candidates.add(DownloadCandidate.of(fallback + urlWithoutPrefix));

            return candidates;
        }
    }
}

