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
package org.jackhuang.hmcl.util.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Resolves Minecraft JSON text components that use a top-level `translate` key and optional `fallback`.
@NotNullByDefault
public final class MinecraftTranslatedTextResolver {
    /// Prevents instantiation.
    private MinecraftTranslatedTextResolver() {
    }

    /// Resolves a Minecraft translation key component to a localized plain text value.
    ///
    /// If a translation key is present, language files are searched using Minecraft's locale fallback order.
    /// If no translation is found, this method returns the component's non-blank `fallback` string when present.
    public static @Nullable String resolve(JsonObject component, Locale locale, TranslationLookup translationLookup) {
        String translate = getJsonString(component, "translate");
        if (StringUtils.isNotBlank(translate)) {
            try {
                List<String> langFileNames = LocaleUtils.getMinecraftLanguageFileNames(locale);
                List<String> namespaces = translationLookup.listNamespaces();
                for (String langFileName : langFileNames) {
                    for (String namespace : namespaces) {
                        String translated = translationLookup.findTranslation(namespace, langFileName, translate);
                        if (translated != null) {
                            return translated;
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warning("Failed to resolve translated Minecraft text component", e);
            } catch (JsonParseException e) {
                LOG.warning("Failed to parse Minecraft language file", e);
            }
        }

        String fallback = getJsonString(component, "fallback");
        return StringUtils.isNotBlank(fallback) ? fallback : null;
    }

    /// Returns a string member from a JSON object.
    private static @Nullable String getJsonString(JsonObject object, String memberName) {
        return object.get(memberName) instanceof JsonPrimitive primitive && primitive.isString()
                ? primitive.getAsString()
                : null;
    }

    /// Looks up Minecraft language entries from a caller-provided language source.
    public interface TranslationLookup {

        /// Lists namespaces that may contain Minecraft language files, in lookup precedence order.
        @Unmodifiable List<String> listNamespaces() throws IOException;

        /// Finds a translation value for the namespace, language file, and translation key.
        @Nullable String findTranslation(String namespace, String languageFileName, String key) throws IOException, JsonParseException;
    }
}
