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
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
                if (!Files.isRegularFile(metaPath)
                        || !Files.isDirectory(root.resolve("src"))
                        || !Files.isDirectory(root.resolve("slang"))) {
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
                ShaderPackMeta meta = null;
                try {
                    meta = JsonUtils.fromJsonFile(JsonUtils.LENIENT_GSON, shadersPath.resolve("pack.json"), ShaderPackMeta.class);
                } catch (IOException e) {
                    LOG.warning("Failed to load shader metadata", e);
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
    public @Nullable Image loadIcon() {
        try (var zipFileSystem = CompressingUtils.createReadOnlyZipFileSystem(getFile())) {
            var path = zipFileSystem.getPath(iconPath);
            if (!Files.isRegularFile(path)) return null;
            try (var inputStream = Files.newInputStream(zipFileSystem.getPath(iconPath))) {
                return new Image(inputStream, 64, 64, true, true);
            }
        } catch (Exception e) {
            LOG.warning("Failed to load shader pack icon at %s!/%s".formatted(getFile(), iconPath), e);
        }
        return null;
    }

    @Override
    public @Nullable AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        RemoteAddonRepository repository = source.getRepoForType(RemoteAddon.Type.SHADER_PACK);
        if (repository == null) return null;
        Optional<RemoteAddon.Version> currentVersion = repository.getRemoteVersionByLocalFile(file);
        if (currentVersion.isEmpty()) return null;
        List<RemoteAddon.Version> remoteVersions = repository.getRemoteVersionsById(downloadProvider, currentVersion.get().projectId())
                .filter(version -> version.gameVersions().contains(gameVersion)) //TODO loader
                .filter(version -> version.loaders().stream().anyMatch(it -> it.type() == getShaderLoaderType()))
                .filter(version -> version.datePublished().compareTo(currentVersion.get().datePublished()) > 0)
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(source, RemoteAddon.Type.SHADER_PACK, this, currentVersion.get(), remoteVersions.get(0));
    }
}
