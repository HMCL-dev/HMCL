// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Wulian233
 */
@NotNullByDefault
public record ModLocalization(@Unmodifiable Set<String> languages, String name, String description) {
    public static final ModLocalization EMPTY = new ModLocalization(Set.of(), "", "");

    public ModLocalization {
        languages = Collections.unmodifiableSet(new LinkedHashSet<>(languages));
    }

    public static ModLocalization read(ZipFileTree tree, String modId, String language) {
        try {
            @Nullable var assets = tree.getDirectory("assets");
            if (assets == null) return EMPTY;

            Set<String> languages = new LinkedHashSet<>();
            String name = "", description = "";
            String filename = language.toLowerCase(Locale.ROOT) + ".json";
            for (var namespace : assets.getSubDirs().values()) {
                @Nullable var lang = namespace.getSubDirs().get("lang");
                if (lang == null) continue;

                for (String languageFile : lang.getFiles().keySet()) {
                    if (languageFile.endsWith(".json"))
                        languages.add(languageFile.substring(0, languageFile.length() - 5).toLowerCase(Locale.ROOT));
                }

                if (!name.isEmpty() && !description.isEmpty()) continue;
                @Nullable var entry = lang.getFiles().get(filename);
                if (entry == null) continue;

                JsonObject translations = JsonUtils.fromNonNullJsonFully(tree.getInputStream(entry), JsonObject.class);
                if (name.isEmpty())
                    name = translation(translations, modId, "fml.menu.mods.info.displayname.", "modmenu.nameTranslation.");
                if (description.isEmpty())
                    description = translation(translations, modId, "fml.menu.mods.info.description.", "modmenu.descriptionTranslation.");
            }
            return new ModLocalization(languages, name, description);
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to scan mod language files", e);
            return EMPTY;
        }
    }

    private static String translation(JsonObject json, String modId, String firstPrefix, String secondPrefix) {
        String value = string(json, firstPrefix + modId);
        return value.isEmpty() ? string(json, secondPrefix + modId) : value;
    }

    private static String string(JsonObject json, String key) {
        @Nullable var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return "";
        String text = value.getAsString();
        return text.isBlank() ? "" : text;
    }
}
