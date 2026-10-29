// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.shader;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.update.AddonUpdateConditions;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ShaderPackZipFile extends ShaderPackFile {

    public static @Nullable ShaderPackZipFile load(ShaderPackManager manager, Path file) throws IOException {
        if (!Files.isRegularFile(file) || !file.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) return null;

        try (var zipFileSystem = CompressingUtils.createReadOnlyZipFileSystem(file)) {
            Path root = zipFileSystem.getRootDirectories().iterator().next();

            aperture:
            {
                Path metaPath = root.resolve("pack.json");
                if (!Files.isRegularFile(metaPath) || !Files.isDirectory(root.resolve("src"))) {
                    break aperture;
                }
                ShaderPackMeta meta = null;
                try {
                    meta = JsonUtils.fromJsonFile(JsonUtils.LENIENT_GSON, metaPath, ShaderPackMeta.class);
                } catch (IOException e) {
                    LOG.warning("Failed to load shader metadata", e);
                }
                if (meta == null || StringUtils.isBlank(meta.apertureApiVersion())) break aperture;

                String iconPath = root.resolve("pack.png").toAbsolutePath().normalize().toString();
                return new ShaderPackZipFile(manager, file, iconPath, ShaderLoaderType.APERTURE, meta);
            }

            {
                // Optifine & Iris
                Path shadersPath;
                try (Stream<Path> stream = Files.walk(root)) {
                    shadersPath = stream
                            .filter(path -> path.endsWith("shaders"))
                            .filter(Files::isDirectory)
                            .findFirst().orElse(null);
                    if (shadersPath == null) return null;
                }

                Path metaPath = shadersPath.resolve("pack.json");
                ShaderPackMeta meta = null;
                if (Files.isRegularFile(metaPath)) {
                    try {
                        meta = JsonUtils.fromJsonFile(JsonUtils.LENIENT_GSON, metaPath, ShaderPackMeta.class);
                    } catch (IOException e) {
                        LOG.warning("Failed to load shader metadata", e);
                    }
                }

                String iconPath = shadersPath.resolve("pack.png").toAbsolutePath().normalize().toString();
                return new ShaderPackZipFile(manager, file, iconPath, ShaderLoaderType.OPTIFINE_IRIS, meta);
            }
        }
    }

    private final String iconPath;

    private ShaderPackZipFile(ShaderPackManager manager, Path file, String iconPath, ShaderLoaderType loaderType, @Nullable ShaderPackMeta shaderPackMeta) {
        super(manager, file, loaderType, shaderPackMeta);
        this.iconPath = iconPath;
    }

    @Override
    public @Nullable Image loadIcon(double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        try (var fs = CompressingUtils.createReadOnlyZipFileSystem(getFile())) {
            Path path = fs.getPath(this.iconPath);
            if (!Files.isRegularFile(path)) return null;
            try (var inputStream = Files.newInputStream(path)) {
                return new Image(inputStream, requestedWidth, requestedHeight, preserveRatio, smooth);
            }
        } catch (Exception e) {
            LOG.warning("Failed to load shader pack icon at %s!/%s".formatted(getFile(), iconPath), e);
        }
        return null;
    }

    @Override
    protected AddonUpdateConditions getTargetVersionRestrictions() {
        return new AddonUpdateConditions(version -> version.loaders().stream().anyMatch(it -> it.type() == getShaderLoaderType()));
    }
}
