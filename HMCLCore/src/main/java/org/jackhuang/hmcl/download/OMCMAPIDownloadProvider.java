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

import org.jackhuang.hmcl.download.fabric.FabricVersionList;
import org.jackhuang.hmcl.download.forge.ForgeOMCMVersionList;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderBMCLVersionList;
import org.jackhuang.hmcl.download.optifine.OptiFineBMCLVersionList;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;
import java.io.File;

/**
 * @author XGHeaven
 */
public class OMCMAPIDownloadProvider implements DownloadProvider {
    public static final Map<String, String> BUILTIN_HOST = new HashMap<String, String>() {{
        // 理论上协议中是不允许出现改协议头的，但是如果不出现，URL 会解析失败，所以这里暂时添加上
        put("minecraft-meta", "https://launchermeta.mojang.com");
        put("minecraft-launcher", "https://launcher.mojang.com");
        put("minecraft-libraries", "https://libraries.minecraft.net");
        put("minecraft-resources", "https://resources.download.minecraft.net");
        put("forge", "https://files.minecraftforge.net");
        put("fabric-meta", "https://meta.fabricmc.net");
        put("fabric-maven", "https://maven.fabricmc.net");
    }};

    public static String getUrlFromMap(String url, Map<String, String> mapper) throws Exception {
        URL baseUrl = new URL(url);
        for(Map.Entry<String, String> entry : mapper.entrySet()){
            String source = entry.getKey();
            String target = entry.getValue();
            URL sourceUrl = new URL(source);
            URL targetUrl = new URL(target);

            // 匹配的过程中，不考虑协议
            if (sourceUrl.getHost().equals(baseUrl.getHost()) && baseUrl.getPath().startsWith(sourceUrl.getPath())) {
                String protocol = baseUrl.getProtocol();
                String hostname = targetUrl.getHost();
                String basePath = baseUrl.getPath();
                String sourcePath = sourceUrl.getPath();
                String targetPath = targetUrl.getPath();
                if (sourcePath.endsWith("/")) {
                    sourcePath = sourcePath.substring(0, sourcePath.length() - 1);
                }
                if (targetPath.endsWith("/")) {
                    targetPath = targetPath.substring(0, targetPath.length() - 1);
                }

                String pathname = targetPath + basePath.substring(sourcePath.length());

                if (targetUrl.getProtocol() != "") {
                    // 如果镜像地址有协议的话，就使用镜像地址的
                    protocol = targetUrl.getProtocol();
                }

                return new URL(protocol, hostname, pathname).toString();
            }
        }
        return url;
    }

    private final GameVersionList game;
    private final FabricVersionList fabric;
    private final ForgeOMCMVersionList forge;
    private final LiteLoaderBMCLVersionList liteLoader;
    private final OptiFineBMCLVersionList optifine;
    private final Map<String, String> mapper;

    public OMCMAPIDownloadProvider(Map<String, String> originalMapper) {
        String fallback = "https://bmclapi2.bangbang93.com";
        Map<String, String> newMapper = new HashMap();

        // 将预定义的 key 替换为正确的域名
        for (Map.Entry<String, String> entry : originalMapper.entrySet()) {
            String source = entry.getKey();
            String target = entry.getValue();
            newMapper.put(BUILTIN_HOST.containsKey(source) ? BUILTIN_HOST.get(source) : source, target);
        }
        System.out.println(newMapper);

        this.mapper = newMapper;
        this.game = new GameVersionList(this);
        this.fabric = new FabricVersionList(this);
        this.forge = new ForgeOMCMVersionList(this);
        // 以下两个由 BMCLAPI 承担
        this.liteLoader = new LiteLoaderBMCLVersionList(new BMCLAPIDownloadProvider(fallback));
        this.optifine = new OptiFineBMCLVersionList(fallback);
    }

    @Override
    public String getVersionListURL() {
        return injectURL("https://launchermeta.mojang.com/mc/game/version_manifest.json");
    }

    @Override
    public String getAssetBaseURL() {
        return injectURL("http://resources.download.minecraft.net") + "/";
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        switch (id) {
            case "game":
                return game;
            case "fabric":
                return fabric;
            case "forge":
                return forge;
            case "liteloader":
                return liteLoader;
            case "optifine":
                return optifine;
            default:
                throw new IllegalArgumentException("Unrecognized version list id: " + id);
        }
    }

    @Override
    public String injectURL(String url) {
        try {
            return getUrlFromMap(url, mapper);
        } catch(Exception e) {
            return url;
        }
    }

    @Override
    public int getConcurrency() {
        return Math.max(Runtime.getRuntime().availableProcessors() * 2, 6);
    }
}
