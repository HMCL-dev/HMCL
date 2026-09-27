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

import java.util.Collection;
import java.util.List;

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
}

