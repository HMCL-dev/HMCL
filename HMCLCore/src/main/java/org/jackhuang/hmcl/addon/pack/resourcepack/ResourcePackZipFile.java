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
package org.jackhuang.hmcl.addon.pack.resourcepack;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.pack.PackMcMeta;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {
    public static @Nullable ResourcePackZipFile load(ResourcePackManager manager, Path path) throws IOException {
        PackMcMeta meta = null;
        byte[] iconData = null;

        try (var zipFileTree = CompressingUtils.openZipTree(path)) {
            try {
                meta = PackMcMeta.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"));
            } catch (Exception e) {
                LOG.warning("Failed to parse resource pack meta", e);
            }
            if (meta == null) return null;

            var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry != null) {
                try {
                    iconData = zipFileTree.readBinaryEntry(iconEntry);
                } catch (Exception e) {
                    LOG.warning("Failed to load resource pack icon", e);
                }
            }
        }

        Image icon = null;
        if (iconData != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(iconData)) {
                icon = new Image(inputStream, 64, 64, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to load resource pack icon", e);
            }
        }
        return new ResourcePackZipFile(manager, path, meta, icon);
    }

    private ResourcePackZipFile(ResourcePackManager manager, Path path, PackMcMeta meta, @Nullable Image icon) throws IOException {
        super(manager, path, meta, icon);
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    public AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        RemoteAddonRepository repository = source.getRepoForType(RemoteAddonRepository.Type.RESOURCE_PACK);
        if (repository == null) return null;
        Optional<RemoteAddon.Version> currentVersion = repository.getRemoteVersionByLocalFile(file);
        if (currentVersion.isEmpty()) return null;
        List<RemoteAddon.Version> remoteVersions = repository.getRemoteVersionsById(downloadProvider, currentVersion.get().modid())
                .filter(version -> version.gameVersions().contains(gameVersion))
                .filter(version -> version.datePublished().compareTo(currentVersion.get().datePublished()) > 0)
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(this, currentVersion.get(), remoteVersions.get(0), false);
    }
}

