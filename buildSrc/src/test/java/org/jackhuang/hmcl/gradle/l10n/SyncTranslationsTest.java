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
package org.jackhuang.hmcl.gradle.l10n;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies layout synchronization and preservation of properties parsing semantics.
@NotNullByDefault
class SyncTranslationsTest {

    /// Checks ordering, consecutive blank lines, obsolete keys, and untranslated entries.
    @Test
    void synchronizesKeysAndBlankLines() throws IOException {
        String source = "first=First\n\n\nsecond=Second\nthird=Third\n\n";
        String translation = "third=Three\nobsolete=Old\n\nfirst=One\n";
        assertEquals("first=One\n\n\n# second=\nthird=Three\n\n", sync(source, translation));
    }

    /// Retains the translator's header and moves comments with the following key.
    @Test
    void preservesTranslationComments() throws IOException {
        String source = "# Reference author\n\nfirst=First\n\n# Reference note\nsecond=Second\n";
        String translation = "# Translation author\n\nsecond=Two\n# First note\nfirst=One\n! Final note\n";
        assertEquals("# Translation author\n\n# First note\nfirst=One\n\nsecond=Two\n! Final note\n",
                sync(source, translation));
    }

    /// Preserves continuation lines, escaped keys, separators, Unicode, and empty values.
    @Test
    void preservesPropertySyntaxAndValues() throws IOException {
        String source = "escaped\\ key=Source\npath=Path\nempty=Empty\nunicode=Unicode\n";
        String translation = "unicode=\\u00e9\nempty=\npath:C:\\\\temp\\\\\n"
                + "escaped\\ key : caf\u00e9\\n\\\n  # continued text\\\n  and more\n";
        String result = sync(source, translation);
        assertEquals(load(translation), load(result));
        assertTrue(result.startsWith("escaped\\ key : caf\u00e9\\n\\\n  # continued text\\\n  and more\n"));
        assertTrue(result.contains("path:C:\\\\temp\\\\\nempty=\nunicode=\\u00e9\n"));
    }

    /// Keeps leading placeholders out of the header and removes obsolete placeholders.
    @Test
    void synchronizesPlaceholdersIdempotently() throws IOException {
        String source = "first=First\n\nsecond=Second\n";
        String translation = "# Author\n\n# obsolete=\n# second=  \t\n";
        String result = sync(source, translation);
        assertEquals("# Author\n\n# first=\n\n# second=\n", result);
        assertEquals(result, sync(source, result));
        assertTrue(load(result).isEmpty());
    }

    /// Generates placeholders whose uncommented keys decode to the reference keys.
    @Test
    void escapesPlaceholderKeys() throws IOException {
        String source = "escaped\\ key\\:\\=\\\\\\#\\!\\t\\n=value\n=Empty key\n";
        String result = sync(source, "");
        assertEquals(load(source).stringPropertyNames(), load(result.replace("# ", "")).stringPropertyNames());
        assertEquals(result, sync(source, result));
    }

    /// Uses the last active duplicate value even when a placeholder follows it.
    @Test
    void handlesDuplicateKeys() throws IOException {
        assertEquals("key=Last\n", sync("key=Reference\n", "key=First\nkey=Last\n# key=\n"));
        assertThrows(org.gradle.api.GradleException.class, () -> sync("key=A\nkey=B\n", "key=C\n"));
    }

    /// Terminates a continuation at EOF so that a reordered entry cannot consume the next key.
    @Test
    void terminatesContinuationAtEndOfFile() throws IOException {
        String source = "first=First\nsecond=Second\n";
        String translation = "second=Two\nfirst=One\\";
        String result = sync(source, translation);
        assertEquals(load(translation), load(result));
        assertEquals(result, sync(source, result));
    }

    /// Normalizes CRLF and handles files with no terminating newline or no entries.
    @Test
    void handlesLineEndingsAndEmptyFiles() throws IOException {
        assertEquals("first=One\n\n# second=\n", sync("first=A\r\n\r\nsecond=B", "first=One"));
        assertEquals("", sync("", "obsolete=Old\n"));
        assertEquals("# first=\n", sync("first=First\n", ""));
    }

    /// Synchronizes two in-memory files through the production parser.
    private static String sync(String source, String translation) throws IOException {
        return SyncTranslations.synchronize(SyncTranslations.parse(source), SyncTranslations.parse(translation));
    }

    /// Loads the effective properties for comparison independently of layout.
    private static Properties load(String content) throws IOException {
        Properties result = new Properties();
        result.load(new StringReader(content));
        return result;
    }
}
