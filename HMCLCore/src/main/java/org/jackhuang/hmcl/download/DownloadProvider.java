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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.cleanroom.CleanroomRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.fabric.FabricRemoteVersion;
import org.jackhuang.hmcl.download.forge.ForgeRemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricAPIRemoteVersion;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricRemoteVersion;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderRemoteVersion;
import org.jackhuang.hmcl.download.neoforge.NeoForgeRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltAPIRemoteVersion;
import org.jackhuang.hmcl.download.quilt.QuiltRemoteVersion;
import org.jackhuang.hmcl.game.AssetObject;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;

@NotNullByDefault
public class DownloadProvider {

    private static final VarHandle VERSION_LIST_STATES_HANDLE = MethodHandles.arrayElementVarHandle(VersionListState[].class);
    private final @Nullable VersionListState[] versionListStates = new VersionListState[GameComponentType.ALL.size()];

    public boolean hasType(GameComponentType type) {
        return switch (type) {
            case GAME, OPTIFINE, NEO_FORGE, LITELOADER -> true;
            case FABRIC, FABRIC_API, FORGE, CLEANROOM, LEGACY_FABRIC, LEGACY_FABRIC_API, QUILT, QUILT_API -> false;
        };
    }

    public @Unmodifiable Task<ComponentRemoteVersionList<?>> getVersionsAsync(
            GameComponentType type,
            @Nullable GameVersionNumber gameVersion,
            boolean refresh) {
        assert (type == GameComponentType.GAME) == (gameVersion == null);

        final VersionListState state = getState(type);

        if (!refresh) {
            state.lock.readLock().lock();
            try {
                @Unmodifiable ComponentRemoteVersionList<?> result = state.tryGet(gameVersion);
                if (result != null) {
                    return Task.completed(result);
                }
            } finally {
                state.lock.readLock().unlock();
            }
        }

        var task = fetchVersionsAsync(type, gameVersion);

        return task.thenApplyAsync(result -> {
            state.lock.writeLock().lockInterruptibly();
            try {
                state.put(gameVersion, result);
            } finally {
                state.lock.writeLock().unlock();
            }
            return result;
        });
    }

    private VersionListState getState(GameComponentType type) {
        @Nullable VersionListState currentState = versionListStates[type.ordinal()];
        if (currentState != null) {
            return currentState;
        }

        VersionListState state = new VersionListState(type);
        if (VERSION_LIST_STATES_HANDLE.compareAndSet(versionListStates, type.ordinal(), null, state)) {
            return state;
        } else {
            state = (VersionListState) VERSION_LIST_STATES_HANDLE.getVolatile(versionListStates, type.ordinal());
            Objects.requireNonNull(state, "VersionListState should not be null after compareAndSet failure");
            return state;
        }
    }

    private <V extends ComponentRemoteVersion> Task<ComponentRemoteVersionList<V>> fetchFabricVersionsAsync(
            GameComponentType type,
            GameVersionNumber gameVersion,
            FabricLikeVersionListCandidates candidates,
            BiFunction<String, String, V> function
    ) {
        return Task.combine(
                new GetTask(candidates.loaderMetaCandidates),
                new GetTask(candidates.gameMetaCandidates)
        ).thenApplyAsync(pair -> {
            @JsonSerializable
            record GameVersion(String version, String maven, boolean stable) {
            }

            TypeToken<List<GameVersion>> gameVersionsType = listTypeOf(GameVersion.class);

            List<GameVersion> gameVersions = JsonUtils.fromNonNullJson(pair.getValue(), gameVersionsType);

            Optional<GameVersion> metaGameVersion = gameVersions.stream()
                    .filter(it -> gameVersion.equals(GameVersionNumber.asGameVersion(it.version)))
                    .findFirst();
            if (metaGameVersion.isEmpty()) {
                return ComponentRemoteVersionList.of(type);
            }

            SortedSet<V> versions = new TreeSet<>();
            List<GameVersion> loaderVersions = JsonUtils.fromNonNullJson(pair.getKey(), gameVersionsType);
            for (GameVersion loaderVersion : loaderVersions) {
                versions.add(function.apply(metaGameVersion.get().version, loaderVersion.version));
            }

            return ComponentRemoteVersionList.of(type, versions);
        });
    }

    private <V extends ComponentRemoteVersion> Task<ComponentRemoteVersionList<V>> fetchModrinthVersionsAsync(
            GameComponentType type,
            String modId,
            GameVersionNumber gameVersion,
            Function<RemoteAddon.Version, V> mapper
    ) {
        return Task.supplyAsync(Schedulers.io(), () -> ComponentRemoteVersionList.of(type, ModrinthRemoteAddonRepository.getInstance().getRemoteVersionsById(this, modId)
                .filter(it -> {
                    for (String supportedGameVersion : it.gameVersions()) {
                        if (GameVersionNumber.asGameVersion(supportedGameVersion).equals(gameVersion)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(mapper)
                .collect(Collectors.toCollection(() -> (SortedSet<V>) new TreeSet<V>()))));
    }

    protected Task<? extends ComponentRemoteVersionList<?>> fetchVersionsAsync(
            GameComponentType type, @Nullable GameVersionNumber gameVersion
    ) {
        assert (type == GameComponentType.GAME) == (gameVersion == null);

        return switch (type) {
            case GAME -> GameRemoteVersion.fetchAsync(this);
            case FABRIC -> fetchFabricVersionsAsync(
                    type,
                    gameVersion,
                    getFabricVersionListCandidates(),
                    (metaGameVersion, loaderVersion) -> new FabricRemoteVersion(
                            gameVersion, loaderVersion,
                            List.of("%s/%s/%s".formatted(FabricRemoteVersion.LOADER_META_URL, metaGameVersion, loaderVersion)))
            );
            case FABRIC_API -> fetchModrinthVersionsAsync(
                    type,
                    FabricAPIRemoteVersion.MODRINTH_ID,
                    gameVersion,
                    it -> new FabricAPIRemoteVersion(
                            gameVersion,
                            it.version(),
                            it.name(),
                            it.datePublished(),
                            it,
                            List.of(it.file().url()))
            );
            case LEGACY_FABRIC -> fetchFabricVersionsAsync(
                    type,
                    gameVersion,
                    getLegacyFabricVersionListCandidates(),
                    (metaGameVersion, loaderVersion) -> new LegacyFabricRemoteVersion(
                            gameVersion, loaderVersion,
                            List.of("%s/%s/%s".formatted(LegacyFabricRemoteVersion.LOADER_META_URL, metaGameVersion, loaderVersion)))
            );
            case LEGACY_FABRIC_API -> fetchModrinthVersionsAsync(
                    type,
                    LegacyFabricAPIRemoteVersion.MODRINTH_ID,
                    gameVersion,
                    it -> new LegacyFabricAPIRemoteVersion(
                            gameVersion,
                            it.version(),
                            it.name(),
                            it.datePublished(),
                            it,
                            List.of(it.file().url()))
            );
            case QUILT -> fetchFabricVersionsAsync(
                    type,
                    gameVersion,
                    getQuiltVersionListCandidates(),
                    (metaGameVersion, loaderVersion) -> new QuiltRemoteVersion(
                            gameVersion, loaderVersion,
                            List.of("%s/%s/%s".formatted(QuiltRemoteVersion.LOADER_META_URL, metaGameVersion, loaderVersion)))
            );
            case QUILT_API -> fetchModrinthVersionsAsync(
                    type,
                    QuiltAPIRemoteVersion.MODRINTH_ID,
                    gameVersion,
                    it -> new QuiltAPIRemoteVersion(
                            gameVersion,
                            it.version(),
                            it.name(),
                            it.datePublished(),
                            it,
                            List.of(it.file().url()))
            );
            case FORGE -> ForgeRemoteVersion.fetchAsync(this, gameVersion);
            case NEO_FORGE -> NeoForgeRemoteVersion.fetchAsync(this, gameVersion);
            case CLEANROOM -> CleanroomRemoteVersion.fetchAsync(this, gameVersion);
            case LITELOADER -> LiteLoaderRemoteVersion.fetchAsync(gameVersion);
            case OPTIFINE -> Task.supplyAsync(() -> {
                throw new UnsupportedOperationException("OptiFine version list fetching is not supported in this DownloadProvider.");
            });
        };
    }

    public CacheRepository getCacheRepository() {
        return CacheRepository.getInstance();
    }

    public DownloadCandidates getGameVersionListCandidates() {
        return DownloadCandidates.of("https://piston-meta.mojang.com/mc/game/version_manifest.json");
    }

    public FabricLikeVersionListCandidates getFabricVersionListCandidates() {
        return new FabricLikeVersionListCandidates(
                DownloadCandidates.of(FabricRemoteVersion.LOADER_META_URL),
                DownloadCandidates.of(FabricRemoteVersion.GAME_META_URL)
        );
    }

    public FabricLikeVersionListCandidates getLegacyFabricVersionListCandidates() {
        return new FabricLikeVersionListCandidates(
                DownloadCandidates.of(LegacyFabricRemoteVersion.LOADER_META_URL),
                DownloadCandidates.of(LegacyFabricRemoteVersion.GAME_META_URL)
        );
    }

    public FabricLikeVersionListCandidates getQuiltVersionListCandidates() {
        return new FabricLikeVersionListCandidates(
                DownloadCandidates.of(QuiltRemoteVersion.LOADER_META_URL),
                DownloadCandidates.of(QuiltRemoteVersion.GAME_META_URL)
        );
    }

    public DownloadCandidates getForgeVersionListCandidates() {
        return DownloadCandidates.of(ForgeRemoteVersion.FORGE_LIST);
    }

    public DownloadCandidates getNeoForgeVersionListCandidates() {
        return DownloadCandidates.of(NeoForgeRemoteVersion.META_URL);
    }

    public DownloadCandidates getNeoForgeOldVersionListCandidates() {
        return DownloadCandidates.of(NeoForgeRemoteVersion.OLD_URL);
    }

    public DownloadCandidates getCleanroomVersionListCandidates() {
        return DownloadCandidates.of(CleanroomRemoteVersion.LOADER_LIST_URL);
    }

    public DownloadCandidates getDownloadCandidates(String baseURL) {
        return getDownloadCandidates(List.of(baseURL));
    }

    public DownloadCandidates getDownloadCandidates(ComponentRemoteVersion remoteVersion) {
        return getDownloadCandidates(remoteVersion.getUrls());
    }

    public DownloadCandidates getDownloadCandidates(List<String> urls) {
        return DownloadCandidates.of(urls.stream().map(DownloadCandidate::of).toArray(DownloadCandidate[]::new));
    }

    public DownloadCandidates getVersionListCandidates(String url) {
        return getVersionListCandidates(List.of(url));
    }

    public DownloadCandidates getVersionListCandidates(List<String> urls) {
        return getDownloadCandidates(urls);
    }

    /// Returns unmodifiable candidate URLs for an asset's relative object location, in attempt order.
    public DownloadCandidates getAssetObjectCandidates(AssetObject assetObject) {
        return getDownloadCandidates("https://resources.download.minecraft.net/" + assetObject.getLocation());
    }

    public record FabricLikeVersionListCandidates(
            DownloadCandidates loaderMetaCandidates, DownloadCandidates gameMetaCandidates
    ) {

    }

    private static final class VersionListState {
        private final GameComponentType type;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final Map<@Nullable GameVersionNumber, SoftReference<ComponentRemoteVersionList<?>>> versions = new HashMap<>();

        private VersionListState(GameComponentType type) {
            this.type = type;
        }

        @Unmodifiable
        @Nullable ComponentRemoteVersionList<?> tryGet(@Nullable GameVersionNumber gameVersion) {
            assert (type == GameComponentType.GAME) == (gameVersion == null);

            @Nullable SoftReference<ComponentRemoteVersionList<?>> resultRef = versions.get(gameVersion);
            return resultRef != null ? resultRef.get() : null;
        }

        void put(@Nullable GameVersionNumber gameVersion, ComponentRemoteVersionList<?> componentRemoteVersions) {
            assert (type == GameComponentType.GAME) == (gameVersion == null);
            versions.put(gameVersion, new SoftReference<>(componentRemoteVersions));
        }
    }
}
