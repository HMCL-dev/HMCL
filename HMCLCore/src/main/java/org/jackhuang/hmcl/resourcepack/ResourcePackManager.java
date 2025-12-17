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
package org.jackhuang.hmcl.resourcepack;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcePackManager {

    @NotNull
    public static PackMcMeta.PackVersion getPackVersion(Path gameJar) {
        try (var zipFileTree = new ZipFileTree(CompressingUtils.openZipFile(gameJar))) {
            return JsonUtils.fromNonNullJson(zipFileTree.readTextEntry("/version.json"), GameVersionInfo.class)
                    .packVersionInfo().resource();
        } catch (Exception e) {
            LOG.error("Failed to load Minecraft resource pack version", e);
            return PackMcMeta.PackVersion.UNSPECIFIED;
        }
    }

    @NotNull
    @Contract(pure = true)
    public static VersionRange<PackMcMeta.PackVersion> getResourcePackVersionRangeOld(PackMcMeta.PackInfo packInfo) {
        if (packInfo == null) {
            return VersionRange.empty();
        }
        boolean supportedFormatsUnspecified = packInfo.supportedFormats().isUnspecified();
        if (supportedFormatsUnspecified && packInfo.packFormat() <= 0) {
            return VersionRange.empty();
        }
        if (supportedFormatsUnspecified) {
            return VersionRange.only(new PackMcMeta.PackVersion(packInfo.packFormat(), 0));
        }
        return VersionRange.between(packInfo.supportedFormats().getMin(), packInfo.supportedFormats().getMax());
    }

    @NotNull
    @Contract(pure = true)
    public static VersionRange<PackMcMeta.PackVersion> getResourcePackVersionRangeNew(PackMcMeta.PackInfo packInfo) {
        if (packInfo == null) {
            return VersionRange.empty();
        }
        boolean packFormatUnspecified = packInfo.packFormat() <= 0;
        boolean supportedFormatsUnspecified = packInfo.supportedFormats().isUnspecified();

        // See https://zh.minecraft.wiki/w/Pack.mcmeta
        // Also referring to Minecraft's source code
        if (!(packInfo.minPackVersion().isUnspecified() || packInfo.maxPackVersion().isUnspecified())) {
            int minMajor = packInfo.minPackVersion().majorVersion();
            int maxMajor = packInfo.maxPackVersion().majorVersion();
            if (packInfo.minPackVersion().compareTo(packInfo.maxPackVersion()) > 0) {
                return VersionRange.empty();
            }
            if (minMajor > 64) {
                if (!supportedFormatsUnspecified) {
                    return VersionRange.empty();
                }

                if (!packFormatUnspecified && isPackFormatInvalidate(minMajor, maxMajor, packInfo.packFormat())) {
                    return VersionRange.empty();
                }
            } else {
                if (supportedFormatsUnspecified) {
                    return VersionRange.empty();
                }
                PackMcMeta.SupportedFormats supportedFormats = packInfo.supportedFormats();
                if (supportedFormats.min() != minMajor) {
                    return VersionRange.empty();
                }
                if (supportedFormats.max() != maxMajor && supportedFormats.max() != 64) {
                    return VersionRange.empty();
                }
                if (packFormatUnspecified) return VersionRange.empty();
                if (isPackFormatInvalidate(minMajor, maxMajor, packInfo.packFormat())) return VersionRange.empty();
            }

            return VersionRange.between(packInfo.minPackVersion(), packInfo.maxPackVersion());
        } else if (!supportedFormatsUnspecified) {
            PackMcMeta.SupportedFormats supportedFormats = packInfo.supportedFormats();
            int min = supportedFormats.min();
            int max = supportedFormats.max();
            if (max > 64) {
                return VersionRange.empty();
            } else {
                if (packFormatUnspecified) return VersionRange.empty();
                if (isPackFormatInvalidate(min, max, packInfo.packFormat())) return VersionRange.empty();
            }

            return VersionRange.between(supportedFormats.getMin(), supportedFormats.getMax());
        } else if (!packFormatUnspecified) {
            int packFormat = packInfo.packFormat();
            PackMcMeta.PackVersion packVersion = new PackMcMeta.PackVersion(packFormat, 0);
            return packFormat > 64 ? VersionRange.empty() : VersionRange.between(packVersion, packVersion);
        }
        return VersionRange.empty();
    }

    @Contract(pure = true)
    private static boolean isPackFormatInvalidate(int i, int j, int k) {
        if (k >= i && k <= j) {
            return k < 15;
        } else {
            return true;
        }
    }

    private final GameRepository repository;
    private final String id;

    private final Path resourcePackDirectory;
    private final TreeSet<ResourcePackFile> resourcePackFiles = new TreeSet<>();

    private final Path optionsFile;
    private final @NotNull PackMcMeta.PackVersion requiredVersion;

    private boolean loaded = false;

    public ResourcePackManager(GameRepository repository, String id) {
        this.repository = repository;
        this.id = id;
        this.resourcePackDirectory = this.repository.getResourcePackDirectory(this.id);
        this.optionsFile = repository.getRunDirectory(id).resolve("options.txt");
        this.requiredVersion = getPackVersion(repository.getVersionJar(id));
    }

    @NotNull
    private Map<String, String> loadOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        if (!Files.isRegularFile(optionsFile)) return options;
        try (var stream = Files.lines(optionsFile)) {
            stream.forEach(s -> {
                if (StringUtils.isNotBlank(s)) {
                    var entry = s.split(":", 2);
                    if (entry.length == 2) {
                        options.put(entry[0], entry[1]);
                    }
                }
            });
        } catch (IOException e) {
            LOG.warning("Failed to read instance options file", e);
        }
        return options;
    }

    private void saveOptions(@NotNull Map<String, String> options) {
        try {
            if (!Files.isRegularFile(optionsFile)) {
                Files.createFile(optionsFile);
            }
            StringBuilder sb = new StringBuilder();
            for (var entry : options.entrySet()) {
                sb.append(entry.getKey()).append(":").append(entry.getValue()).append(System.lineSeparator());
            }
            Files.writeString(optionsFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Failed to save instance options file", e);
        }
    }

    public GameRepository getRepository() {
        return repository;
    }

    public String getInstanceId() {
        return id;
    }

    public Path getResourcePackDirectory() {
        return resourcePackDirectory;
    }

    private void addResourcePackInfo(Path file) throws IOException {
        ResourcePackFile resourcePack = ResourcePackFile.parse(this, file);
        if (resourcePack != null) resourcePackFiles.add(resourcePack);
    }

    public void refreshResourcePacks() throws IOException {
        resourcePackFiles.clear();

        if (Files.isDirectory(resourcePackDirectory)) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(resourcePackDirectory)) {
                for (Path subitem : directoryStream) {
                    addResourcePackInfo(subitem);
                }
            }
        }
        loaded = true;
    }

    public @Unmodifiable List<ResourcePackFile> getResourcePacks() throws IOException {
        if (!loaded)
            refreshResourcePacks();
        return List.copyOf(resourcePackFiles);
    }

    public void importResourcePack(Path file) throws IOException {
        if (!loaded)
            refreshResourcePacks();

        Files.createDirectories(resourcePackDirectory);

        Path newFile = resourcePackDirectory.resolve(file.getFileName());
        FileUtils.copyFile(file, newFile);

        addResourcePackInfo(newFile);
    }

    public void removeResourcePacks(ResourcePackFile... resourcePacks) throws IOException {
        for (ResourcePackFile resourcePack : resourcePacks) {
            if (resourcePack != null && resourcePack.manager == this) {
                resourcePack.delete();
                resourcePackFiles.remove(resourcePack);
            }
        }
    }

    public void enableResourcePack(ResourcePackFile resourcePack) {
        if (resourcePack.manager != this) return;
        Map<String, String> options = loadOptions();
        String packId = "file/" + resourcePack.getFileName();
        boolean b = false;
        List<String> resourcePacks = new LinkedList<>(StringUtils.deserializeStringList(options.get("resourcePacks")));
        if (!resourcePacks.contains(packId)) {
            resourcePacks.add(packId);
            options.put("resourcePacks", StringUtils.serializeStringList(resourcePacks));
            b = true;
        }
        List<String> incompatibleResourcePacks = new LinkedList<>(StringUtils.deserializeStringList(options.get("incompatibleResourcePacks")));
        if (!incompatibleResourcePacks.contains(packId) && isIncompatible(resourcePack)) {
            incompatibleResourcePacks.add(packId);
            options.put("incompatibleResourcePacks", StringUtils.serializeStringList(incompatibleResourcePacks));
            b = true;
        }
        if (b) saveOptions(options);
    }

    public void disableResourcePack(ResourcePackFile resourcePack) {
        if (resourcePack.manager != this) return;
        Map<String, String> options = loadOptions();
        String packId = "file/" + resourcePack.getFileName();
        boolean b = false;
        List<String> resourcePacks = new LinkedList<>(StringUtils.deserializeStringList(options.get("resourcePacks")));
        if (resourcePacks.contains(packId)) {
            resourcePacks.remove(packId);
            options.put("resourcePacks", StringUtils.serializeStringList(resourcePacks));
            b = true;
        }
        List<String> incompatibleResourcePacks = new LinkedList<>(StringUtils.deserializeStringList(options.get("incompatibleResourcePacks")));
        if (incompatibleResourcePacks.contains(packId)) {
            incompatibleResourcePacks.remove(packId);
            options.put("incompatibleResourcePacks", StringUtils.serializeStringList(incompatibleResourcePacks));
            b = true;
        }
        if (b) saveOptions(options);
    }

    public boolean isEnabled(ResourcePackFile resourcePack) {
        if (resourcePack.manager != this) return false;
        Map<String, String> options = loadOptions();
        String packId = "file/" + resourcePack.getFileName();
        List<String> resourcePacks = StringUtils.deserializeStringList(options.get("resourcePacks"));
        if (!resourcePacks.contains(packId)) return false;
        List<String> incompatibleResourcePacks = StringUtils.deserializeStringList(options.get("incompatibleResourcePacks"));
        return isIncompatible(resourcePack) == incompatibleResourcePacks.contains(packId);
    }

    public ResourcePackFile.Compatibility getCompatibility(@NotNull ResourcePackFile resourcePack) {
        if (resourcePack.getMeta() == null || resourcePack.getMeta().pack() == null) return ResourcePackFile.Compatibility.MISSING_PACK_META;
        if (this.requiredVersion.isUnspecified()) return ResourcePackFile.Compatibility.MISSING_GAME_META;
        var versionRange = requiredVersion.majorVersion() > 64
                ? getResourcePackVersionRangeNew(resourcePack.getMeta().pack())
                : getResourcePackVersionRangeOld(resourcePack.getMeta().pack());
        if (versionRange.isEmpty())
            return ResourcePackFile.Compatibility.INVALID;
        if (versionRange.getMaximum().compareTo(this.requiredVersion) < 0)
            return ResourcePackFile.Compatibility.TOO_OLD;
        if (versionRange.getMinimum().compareTo(this.requiredVersion) > 0)
            return ResourcePackFile.Compatibility.TOO_NEW;
        return ResourcePackFile.Compatibility.COMPATIBLE;
    }

    public boolean isIncompatible(@NotNull ResourcePackFile resourcePack) {
        return getCompatibility(resourcePack) != ResourcePackFile.Compatibility.COMPATIBLE;
    }

    @JsonSerializable
    private record GameVersionInfo(@SerializedName("pack_version") PackVersionInfo packVersionInfo) {
    }

    @JsonSerializable
    @JsonAdapter(PackVersionInfoDeserializer.class)
    private record PackVersionInfo(PackMcMeta.PackVersion resource) {
    }

    private static final class PackVersionInfoDeserializer implements JsonDeserializer<PackVersionInfo> {
        @Override
        public PackVersionInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new PackVersionInfo(PackMcMeta.PackVersion.fromJson(json.getAsJsonObject().get("resource")));
        }
    }
}
