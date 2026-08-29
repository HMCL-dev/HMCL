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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
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

    public static @Nullable ShaderPackZipFile load(Path file) throws IOException {
        if (!Files.isRegularFile(file) || !file.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) return null;

        try (var zipSystem = CompressingUtils.createReadOnlyZipFileSystem(file)) {
            Path root = zipSystem.getRootDirectories().iterator().next();
            try (Stream<Path> stream = Files.walk(root)) {
                Path shadersPath = stream
                        .filter(path -> path.endsWith("shaders"))
                        .filter(Files::isDirectory)
                        .findFirst().orElse(null);
                if (shadersPath == null) return null;

                ShaderPackMeta meta = null;
                try {
                    meta = JsonUtils.fromJsonFile(JsonUtils.LENIENT_GSON, shadersPath.resolve("pack.json"), ShaderPackMeta.class);
                } catch (IOException e) {
                    LOG.warning("Failed to load aperture shader metadata", e);
                }

                String shadersEntry = shadersPath.toAbsolutePath().normalize().toString().substring(1) + '/';
                if (Files.isRegularFile(shadersPath.resolve("pack.ts"))) { // Aperture
                    return new ShaderPackZipFile(file, shadersEntry, ShaderLoaderType.APERTURE, meta);
                }
                return new ShaderPackZipFile(file, shadersEntry, ShaderLoaderType.OPTIFINE_IRIS, meta);
            }
        }
    }

    private final String shadersEntry;

    private ShaderPackZipFile(Path file, String shadersEntry, ShaderLoaderType loaderType, @Nullable ShaderPackMeta shaderPackMeta) {
        super(file, loaderType, shaderPackMeta);
        this.shadersEntry = shadersEntry;
    }

    @Override
    public @Nullable Image loadIcon() {
        try (var tree = CompressingUtils.openZipTree(getFile())) {
            var entry = tree.getEntry(shadersEntry + "pack.png");
            if (entry == null) return null;
            try (var inputStream = new ByteArrayInputStream(tree.readBinaryEntry(entry))) {
                return new Image(inputStream, 64, 64, true, true);
            }
        } catch (Exception e) {
            LOG.warning("Failed to load shader pack icon in file %s!/%s".formatted(getFile(), shadersEntry), e);
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
                .filter(version -> version.datePublished().compareTo(currentVersion.get().datePublished()) > 0)
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(source, RemoteAddon.Type.SHADER_PACK, this, currentVersion.get(), remoteVersions.get(0));
    }
}
