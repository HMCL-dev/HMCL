// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
