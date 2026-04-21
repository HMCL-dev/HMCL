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
package org.jackhuang.hmcl.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcepackDescriptionResolver {
    private ResourcepackDescriptionResolver() {
    }

    static @Nullable LocalModFile.Description resolveFromFolder(Path root, Locale locale) throws IOException {
        Path mcmeta = root.resolve("pack.mcmeta");
        String mcmetaText = Files.readString(mcmeta);
        return resolve(mcmetaText, locale, new FolderTranslationLookup(root));
    }

    static @Nullable LocalModFile.Description resolveFromZip(ZipFileTree tree, Locale locale) throws IOException {
        String mcmetaText = tree.readTextEntry("/pack.mcmeta");
        return resolve(mcmetaText, locale, new ZipTranslationLookup(tree));
    }

    static @Nullable LocalModFile.Description resolve(String mcmetaText, Locale locale, TranslationLookup translationLookup) {
        JsonObject json = JsonUtils.fromMaybeMalformedJson(mcmetaText, JsonObject.class);
        if (json == null) {
            return null;
        }

        JsonObject pack = getJsonObject(json, "pack");
        JsonObject descriptionObject = pack != null ? getJsonObject(pack, "description") : null;
        if (descriptionObject != null && descriptionObject.has("translate")) {
            return resolveTranslatedDescription(descriptionObject, locale, translationLookup);
        }

        PackMcMeta packMcMeta = JsonUtils.fromMaybeMalformedJson(mcmetaText, PackMcMeta.class);
        return packMcMeta != null ? packMcMeta.pack().description() : null;
    }

    private static @Nullable LocalModFile.Description resolveTranslatedDescription(JsonObject descriptionObject, Locale locale, TranslationLookup translationLookup) {
        String translate = getJsonString(descriptionObject, "translate");
        if (StringUtils.isNotBlank(translate)) {
            try {
                for (String namespace : translationLookup.listNamespaces()) {
                    for (String langFileName : LocaleUtils.getMinecraftLanguageFileNames(locale)) {
                        String translated = translationLookup.findTranslation(namespace, langFileName, translate);
                        if (translated != null) {
                            return PackMcMeta.parseDescription(translated);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warning("Failed to resolve translated resourcepack description", e);
            } catch (JsonParseException e) {
                LOG.warning("Failed to parse resourcepack language file", e);
            }
        }

        String fallback = getJsonString(descriptionObject, "fallback");
        return StringUtils.isNotBlank(fallback) ? PackMcMeta.parseDescription(fallback) : null;
    }

    private static @Nullable JsonObject getJsonObject(JsonObject object, String memberName) {
        JsonElement element = object.get(memberName);
        return element instanceof JsonObject jsonObject ? jsonObject : null;
    }

    private static @Nullable String getJsonString(JsonObject object, String memberName) {
        JsonElement element = object.get(memberName);
        return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }

    interface TranslationLookup {
        List<String> listNamespaces() throws IOException;

        @Nullable String findTranslation(String namespace, String languageFileName, String key) throws IOException, JsonParseException;
    }

    private static final class FolderTranslationLookup implements TranslationLookup {
        private final Path root;

        private FolderTranslationLookup(Path root) {
            this.root = root;
        }

        @Override
        public List<String> listNamespaces() throws IOException {
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

            Map<String, String> translations = JsonUtils.fromJsonFile(langFile, JsonUtils.mapTypeOf(String.class, String.class));
            return translations != null ? translations.get(key) : null;
        }
    }

    private static final class ZipTranslationLookup implements TranslationLookup {
        private final ZipFileTree tree;

        private ZipTranslationLookup(ZipFileTree tree) {
            this.tree = tree;
        }

        @Override
        public List<String> listNamespaces() {
            ArchiveFileTree.Dir<ZipArchiveEntry> assets = tree.getDirectory("assets");
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
            ZipArchiveEntry entry = tree.getEntry(path);
            if (entry == null) {
                return null;
            }

            Map<String, String> translations = JsonUtils.fromNonNullJson(tree.readTextEntry(entry), JsonUtils.mapTypeOf(String.class, String.class));
            return translations.get(key);
        }
    }
}
