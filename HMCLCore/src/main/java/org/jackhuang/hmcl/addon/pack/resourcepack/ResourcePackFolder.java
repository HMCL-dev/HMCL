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
import org.jackhuang.hmcl.addon.pack.PackMcMeta;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackFolder extends ResourcePackFile {

    public static ResourcePackFolder load(ResourcePackManager manager, Path path) {
        PackMcMeta meta = null;
        try {
            meta = PackMcMeta.fromNonNullJsonFile(path.resolve("pack.mcmeta"));
        } catch (Exception e) {
            LOG.warning("Failed to parse resource pack meta", e);
        }

        return new ResourcePackFolder(manager, path, meta != null ? meta.pack() : null);
    }

    private ResourcePackFolder(ResourcePackManager manager, Path path, PackMcMeta.PackInfo info) {
        super(manager, path, info);
    }

    @Override
    public @Nullable Image loadIcon(double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        Path iconPath = getFile().resolve("pack.png");
        if (!Files.isRegularFile(iconPath)) return null;
        try (var inputStream = Files.newInputStream(iconPath)) {
            return new Image(inputStream, requestedWidth, requestedHeight, preserveRatio, smooth);
        } catch (Exception e) {
            LOG.warning("Failed to load resource pack icon", e);
        }
        return null;
    }
}
