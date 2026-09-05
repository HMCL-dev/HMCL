/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.cleanroom.CleanroomVersionList;
import org.jackhuang.hmcl.download.fabric.FabricVersionList;
import org.jackhuang.hmcl.download.forge.ForgeVersionList;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricVersionList;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderVersionList;
import org.jackhuang.hmcl.download.modrinth.ModrinthComponentVersionList;
import org.jackhuang.hmcl.download.neoforge.NeoForgeOfficialVersionList;
import org.jackhuang.hmcl.download.optifine.OptiFineBMCLVersionList;
import org.jackhuang.hmcl.download.quilt.QuiltVersionList;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.net.URI;
import java.util.List;

/**
 * @author huangyuhui
 * @see <a href="http://wiki.vg">http://wiki.vg</a>
 */
public class MojangDownloadProvider implements DownloadProvider {
    private final GameVersionList game;
    private final FabricVersionList fabric;
    private final ModrinthComponentVersionList fabricApi;
    private final ForgeVersionList forge;
    private final NeoForgeOfficialVersionList neoforge;
    private final CleanroomVersionList cleanroom;
    private final LiteLoaderVersionList liteLoader;
    private final OptiFineBMCLVersionList optifine;
    private final QuiltVersionList quilt;
    private final ModrinthComponentVersionList quiltApi;
    private final LegacyFabricVersionList legacyFabric;
    private final ModrinthComponentVersionList legacyFabricApi;

    public MojangDownloadProvider() {
        // If there is no official download channel available, fallback to BMCLAPI.
        String apiRoot = "https://bmclapi2.bangbang93.com";

        this.game = new GameVersionList(this);
        this.fabric = new FabricVersionList(this);
        this.fabricApi = new ModrinthComponentVersionList(this, GameComponentType.FABRIC_API, "P7dR8mSH");
        this.forge = new ForgeVersionList(this);
        this.neoforge = new NeoForgeOfficialVersionList(this);
        this.cleanroom = new CleanroomVersionList(this);
        this.liteLoader = new LiteLoaderVersionList(this);
        this.optifine = new OptiFineBMCLVersionList(apiRoot);
        this.quilt = new QuiltVersionList(this);
        this.quiltApi = new ModrinthComponentVersionList(this, GameComponentType.QUILT_API, "qvIfYCYJ");
        this.legacyFabric = new LegacyFabricVersionList(this);
        this.legacyFabricApi = new ModrinthComponentVersionList(this, GameComponentType.LEGACY_FABRIC_API, "9CJED7xi");
        ;
    }

    @Override
    public List<URI> getVersionListURLs() {
        return List.of(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest.json"));
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return List.of(NetworkUtils.toURI("https://resources.download.minecraft.net/" + assetObjectLocation));
    }

    @Override
    public ComponentVersionList<?> getVersionList(GameComponentType componentType) {
        return switch (componentType) {
            case GAME -> game;
            case FABRIC -> fabric;
            case FABRIC_API -> fabricApi;
            case FORGE -> forge;
            case CLEANROOM -> cleanroom;
            case NEO_FORGE -> neoforge;
            case LITELOADER -> liteLoader;
            case OPTIFINE -> optifine;
            case QUILT -> quilt;
            case QUILT_API -> quiltApi;
            case LEGACY_FABRIC -> legacyFabric;
            case LEGACY_FABRIC_API -> legacyFabricApi;
        };
    }

    @Override
    public String injectURL(String baseURL) {
        return baseURL;
    }

    @Override
    public int getConcurrency() {
        return 6;
    }
}
