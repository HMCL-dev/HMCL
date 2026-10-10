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
package org.jackhuang.hmcl.addon.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.MinecraftTranslatedTextResolver;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/// Resolves resource-pack descriptions using translations stored in the pack itself.
@NotNullByDefault
final class ResourcePackDescriptionResolver {
    /// Prevents instantiation.
    private ResourcePackDescriptionResolver() {
    }

    /// Reads `pack.mcmeta` and resolves its description using language files beneath `root`.
    ///
    /// @throws IOException if the metadata file cannot be read
    static @Nullable LocalAddonFile.Description resolveFromFolder(Path root, Locale locale) throws IOException {
        Path mcmeta = root.resolve("pack.mcmeta");
        String mcmetaText = Files.readString(mcmeta);
        return resolve(mcmetaText, locale, new FolderTranslationLookup(root));
    }

    /// Reads `pack.mcmeta` and resolves its description without closing the supplied archive.
    ///
    /// @throws IOException if the metadata entry cannot be read
    static @Nullable LocalAddonFile.Description resolveFromZip(ZipFileTree tree, Locale locale) throws IOException {
        String mcmetaText = tree.readTextEntry("/pack.mcmeta");
        return resolve(mcmetaText, locale, new ZipTranslationLookup(tree));
    }

    /// Parses a description, resolving a top-level translation key with the supplied lookup.
    ///
    /// Returns null if the pack object is absent or a translated description has neither a
    /// matching translation nor a non-blank fallback. Other descriptions retain their formatting.
    static @Nullable LocalAddonFile.Description resolve(String mcmetaText, Locale locale, MinecraftTranslatedTextResolver.TranslationLookup translationLookup) {
        @Nullable JsonObject json = JsonUtils.fromMaybeMalformedJson(mcmetaText, JsonObject.class);
        if (json == null) {
            return null;
        }

        @Nullable JsonObject pack = getJsonObject(json, "pack");
        if (pack == null) {
            return null;
        }

        @Nullable JsonElement description = pack.get("description");
        if (description instanceof JsonObject descriptionObject && descriptionObject.has("translate")) {
            @Nullable String translated = MinecraftTranslatedTextResolver.resolve(descriptionObject, locale, translationLookup);
            return translated != null ? PackMcMeta.parseDescription(translated) : null;
        }

        return PackMcMeta.parseDescription(description);
    }

    /// Returns an object member, or null if the member is absent or has a different JSON type.
    private static @Nullable JsonObject getJsonObject(JsonObject object, String memberName) {
        @Nullable JsonElement element = object.get(memberName);
        return element instanceof JsonObject jsonObject ? jsonObject : null;
    }

    /// Looks up translations in a resource-pack directory.
    @NotNullByDefault
    private static final class FolderTranslationLookup implements MinecraftTranslatedTextResolver.TranslationLookup {
        /// The resource-pack root directory.
        private final Path root;

        /// Creates a lookup rooted at the given resource-pack directory.
        private FolderTranslationLookup(Path root) {
            this.root = root;
        }

        @Override
        public @Unmodifiable List<String> listNamespaces() throws IOException {
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) {
                return List.of();
            }

            try (var stream = Files.list(assets)) {
                return stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        }

        @Override
        public @Nullable String findTranslation(String namespace, String languageFileName, String key) throws IOException {
            Path langFile = root.resolve("assets").resolve(namespace).resolve("lang").resolve(languageFileName);
            if (!Files.isRegularFile(langFile)) {
                return null;
            }

            @Nullable Map<String, String> translations = JsonUtils.fromJsonFile(langFile, JsonUtils.mapTypeOf(String.class, String.class));
            return translations != null ? translations.get(key) : null;
        }
    }

    /// Looks up translations in an archive owned by the caller.
    @NotNullByDefault
    private static final class ZipTranslationLookup implements MinecraftTranslatedTextResolver.TranslationLookup {
        /// The archive, which must remain open during lookups.
        private final ZipFileTree tree;

        /// Creates a lookup without taking ownership of the supplied archive.
        private ZipTranslationLookup(ZipFileTree tree) {
            this.tree = tree;
        }

        @Override
        public @Unmodifiable List<String> listNamespaces() {
            ArchiveFileTree.@Nullable Dir<ZipArchiveEntry> assets = tree.getDirectory("assets");
            if (assets == null) {
                return List.of();
            }

            return assets.getSubDirs().keySet().stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        @Override
        public @Nullable String findTranslation(String namespace, String languageFileName, String key) throws IOException {
            String path = "assets/" + namespace + "/lang/" + languageFileName;
            @Nullable ZipArchiveEntry entry = tree.getEntry(path);
            if (entry == null) {
                return null;
            }

            Map<String, String> translations = JsonUtils.fromNonNullJson(tree.readTextEntry(entry), JsonUtils.mapTypeOf(String.class, String.class));
            return translations.get(key);
        }
    }
}
