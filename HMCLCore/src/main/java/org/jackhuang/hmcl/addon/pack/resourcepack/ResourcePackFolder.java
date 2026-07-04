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
import org.jackhuang.hmcl.addon.pack.PackMcMeta;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackFolder extends ResourcePackFile {

    public static @Nullable ResourcePackFolder load(ResourcePackManager manager, Path path) {
        PackMcMeta meta = null;
        try {
            meta = PackMcMeta.fromNonNullJsonFile(path.resolve("pack.mcmeta"));
        } catch (Exception e) {
            LOG.warning("Failed to parse resource pack meta", e);
        }
        if (meta == null) return null;

        byte[] iconData = null;
        Image icon = null;
        try {
            iconData = Files.readAllBytes(path.resolve("pack.png"));
        } catch (IOException e) {
            LOG.warning("Failed to read resource pack icon", e);
        }
        if (iconData != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(iconData)) {
                icon = new Image(inputStream, 64, 64, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to load resource pack icon", e);
            }
        }
        return new ResourcePackFolder(manager, path, meta, icon);
    }

    public ResourcePackFolder(ResourcePackManager manager, Path path, PackMcMeta meta, @Nullable Image icon) {
        super(manager, path, meta, icon);
    }

    @Override
    public void delete() throws IOException {
        FileUtils.deleteDirectory(file);
    }

    @Override
    public AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) {
        return null;
    }
}
