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

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.List;

/// The service provider that provides Minecraft online file downloads.
///
/// @author huangyuhui
public interface DownloadProvider {

    /// Returns unmodifiable candidate URLs for the Minecraft version manifest, in attempt order.
    @Unmodifiable List<WebURL> getVersionListURLs();

    /// Returns unmodifiable candidate URLs for an asset's relative object location, in attempt order.
    @Unmodifiable List<WebURL> getAssetObjectCandidates(String assetObjectLocation);

    /// Inject into original URL provided by Mojang and Forge.
    ///
    /// Since there are many provided URLs that are written in JSONs and are unmodifiable,
    /// this method provides a way to change them.
    ///
    /// @param baseURL original URL provided by Mojang and Forge.
    /// @return the URL that is equivalent to `baseURL`, but belongs to your own service provider.
    String injectURL(String baseURL);

    /// Returns unmodifiable download candidates for an original URL, in attempt order.
    /// The default implementation parses the result of [#injectURL(String)].
    ///
    /// @param baseURL original URL provided by Mojang and Forge.
    /// @return the candidate URLs
    default @Unmodifiable List<WebURL> injectURLWithCandidates(String baseURL) {
        return List.of(WebURL.parse(injectURL(baseURL)));
    }

    /// Returns unmodifiable candidates for all URLs, preserving first occurrence order and removing duplicates.
    default @Unmodifiable List<WebURL> injectURLsWithCandidates(List<String> urls) {
        LinkedHashSet<WebURL> result = new LinkedHashSet<>();
        for (String url : urls) {
            result.addAll(injectURLWithCandidates(url));
        }
        return List.copyOf(result);
    }

    /// the specific version list that this download provider provides. i.e. "fabric", "forge", "liteloader", "game", "optifine"
    ///
    /// @param componentType the component type of specific version list that this download provider provides. i.e. "fabric", "forge", "liteloader", "game", "optifine"
    /// @return the version list
    /// @throws IllegalArgumentException if the version list does not exist
    ComponentVersionList<?> getVersionList(GameComponentType componentType);

    /// The maximum download concurrency that this download provider supports.
    ///
    /// @return the maximum download concurrency.
    int getConcurrency();
}
