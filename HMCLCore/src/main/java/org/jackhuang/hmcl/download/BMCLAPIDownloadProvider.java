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
import org.jackhuang.hmcl.download.fabric.FabricAPIVersionList;
import org.jackhuang.hmcl.download.fabric.FabricVersionList;
import org.jackhuang.hmcl.download.forge.ForgeBMCLVersionList;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricAPIVersionList;
import org.jackhuang.hmcl.download.legacyfabric.LegacyFabricVersionList;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderBMCLVersionList;
import org.jackhuang.hmcl.download.neoforge.NeoForgeBMCLVersionList;
import org.jackhuang.hmcl.download.optifine.OptiFineBMCLVersionList;
import org.jackhuang.hmcl.download.quilt.QuiltAPIVersionList;
import org.jackhuang.hmcl.download.quilt.QuiltVersionList;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.jackhuang.hmcl.util.Pair.pair;

/**
 *
 * @author huang
 */
public final class BMCLAPIDownloadProvider implements DownloadProvider {
    private final String apiRoot;
    private final GameVersionList game;
    private final FabricVersionList fabric;
    private final FabricAPIVersionList fabricApi;
    private final ForgeBMCLVersionList forge;
    private final CleanroomVersionList cleanroom;
    private final LegacyFabricVersionList legacyFabric;
    private final LegacyFabricAPIVersionList legacyFabricApi;
    private final NeoForgeBMCLVersionList neoforge;
    private final LiteLoaderBMCLVersionList liteLoader;
    private final OptiFineBMCLVersionList optifine;
    private final QuiltVersionList quilt;
    private final QuiltAPIVersionList quiltApi;
    private final List<Pair<String, String>> replacement;

    public BMCLAPIDownloadProvider(String apiRoot) {
        this.apiRoot = apiRoot;
        this.game = new GameVersionList(this);
        this.fabric = new FabricVersionList(this);
        this.fabricApi = new FabricAPIVersionList(this);
        this.forge = new ForgeBMCLVersionList(apiRoot);
        this.cleanroom = new CleanroomVersionList(this);
        this.neoforge = new NeoForgeBMCLVersionList(apiRoot);
        this.liteLoader = new LiteLoaderBMCLVersionList(this);
        this.optifine = new OptiFineBMCLVersionList(apiRoot);
        this.quilt = new QuiltVersionList(this);
        this.quiltApi = new QuiltAPIVersionList(this);
        this.legacyFabric = new LegacyFabricVersionList(this);
        this.legacyFabricApi = new LegacyFabricAPIVersionList(this);

        this.replacement = Arrays.asList(
                pair("https://bmclapi2.bangbang93.com", apiRoot),
                pair("https://launchermeta.mojang.com", apiRoot),
                pair("https://piston-meta.mojang.com", apiRoot),
                pair("https://piston-data.mojang.com", apiRoot),
                pair("https://launcher.mojang.com", apiRoot),
                pair("https://libraries.minecraft.net", apiRoot + "/libraries"),
                pair("http://files.minecraftforge.net/maven", apiRoot + "/maven"),
                pair("https://files.minecraftforge.net/maven", apiRoot + "/maven"),
                pair("https://maven.minecraftforge.net", apiRoot + "/maven"),
                pair("https://maven.neoforged.net/releases/", apiRoot + "/maven/"),
                pair("http://dl.liteloader.com/versions/versions.json", apiRoot + "/maven/com/mumfrey/liteloader/versions.json"),
                pair("http://dl.liteloader.com/versions", apiRoot + "/maven"),
                pair("https://meta.fabricmc.net", apiRoot + "/fabric-meta"),
                pair("https://maven.fabricmc.net", apiRoot + "/maven"),
                pair("https://authlib-injector.yushi.moe", apiRoot + "/mirrors/authlib-injector"),
                pair("https://repo1.maven.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
                pair("https://repo.maven.apache.org/maven2", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"),
                pair("https://hmcl.glavo.site/metadata/cleanroom", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/cleanroom"),
                pair("https://hmcl.glavo.site/metadata/fmllibs", "https://alist.8mi.tech/d/mirror/HMCL-Metadata/Auto/fmllibs"),
                pair("https://zkitefly.github.io/unlisted-versions-of-minecraft", "https://alist.8mi.tech/d/mirror/unlisted-versions-of-minecraft/Auto")
//                // https://github.com/mcmod-info-mirror/mcim-rust-api
//                pair("https://api.modrinth.com", "https://mod.mcimirror.top/modrinth"),
//                pair("https://cdn.modrinth.com", "https://mod.mcimirror.top"),
//                pair("https://api.curseforge.com", "https://mod.mcimirror.top/curseforge"),
//                pair("https://edge.forgecdn.net", "https://mod.mcimirror.top"),
//                pair("https://mediafilez.forgecdn.net", "https://mod.mcimirror.top")
        );
    }

    public String getApiRoot() {
        return apiRoot;
    }

    @Override
    public List<URI> getVersionListURLs() {
        return List.of(URI.create(apiRoot + "/mc/game/version_manifest.json"));
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return List.of(NetworkUtils.toURI(apiRoot + "/assets/" + assetObjectLocation));
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return switch (id) {
            case "game" -> game;
            case "fabric" -> fabric;
            case "fabric-api" -> fabricApi;
            case "forge" -> forge;
            case "cleanroom" -> cleanroom;
            case "neoforge" -> neoforge;
            case "liteloader" -> liteLoader;
            case "optifine" -> optifine;
            case "quilt" -> quilt;
            case "quilt-api" -> quiltApi;
            case "legacyfabric" -> legacyFabric;
            case "legacyfabric-api" -> legacyFabricApi;
            default -> throw new IllegalArgumentException("Unrecognized version list id: " + id);
        };
    }

    @Override
    public String injectURL(String baseURL) {
        for (Pair<String, String> pair : replacement) {
            if (baseURL.startsWith(pair.getKey())) {
                return pair.getValue() + baseURL.substring(pair.getKey().length());
            }
        }

        return baseURL;
    }

    @Override
    public int getConcurrency() {
        return Math.max(Runtime.getRuntime().availableProcessors() * 2, 6);
    }
}
