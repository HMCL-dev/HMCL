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
package org.jackhuang.hmcl.addon.shader;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ShaderPackFolder extends ShaderPackFile {

    public static @Nullable ShaderPackFolder load(Path file) {
        Path shadersPath = file.resolve("shaders");
        if (!Files.isDirectory(shadersPath)) return null;

        ShaderPackMeta meta = null;
        try {
            meta = JsonUtils.fromJsonFile(JsonUtils.LENIENT_GSON, shadersPath.resolve("pack.json"), ShaderPackMeta.class);
        } catch (IOException e) {
            LOG.warning("Failed to load shader metadata", e);
        }

        if (Files.isRegularFile(shadersPath.resolve("pack.ts"))) { // Aperture
            return new ShaderPackFolder(file, ShaderLoaderType.APERTURE, meta);
        }
        return new ShaderPackFolder(file, ShaderLoaderType.OPTIFINE_IRIS, meta);
    }

    private ShaderPackFolder(Path file, ShaderLoaderType loaderType, @Nullable ShaderPackMeta shaderPackMeta) {
        super(file, loaderType, shaderPackMeta);
    }

    public @Nullable Image loadIcon() {
        Path iconPath = getFile().resolve("shaders").resolve("pack.png");
        try (var inputStream = new ByteArrayInputStream(Files.readAllBytes(iconPath))) {
            return new Image(inputStream, 64, 64, true, true);
        } catch (Exception e) {
            LOG.warning("Failed to load shader pack icon in " + iconPath, e);
        }
        return null;
    }
}
