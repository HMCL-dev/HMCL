/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies source metadata, mapping rules, and preservation of resources on invalid input.
@NotNullByDefault
final class UpdateLanguageSubtagRegistryTest {
    /// Holds isolated output files for each test.
    @TempDir
    Path directory;

    /// Creates an updater whose resources are confined to the test directory.
    private UpdateLanguageSubtagRegistry createTask() {
        var task = ProjectBuilder.builder().build().getTasks()
                .register("updateLanguageSubtagRegistry", UpdateLanguageSubtagRegistry.class).get();
        task.getSublanguagesFile().set(directory.resolve("sublanguages.csv").toFile());
        task.getDefaultScriptFile().set(directory.resolve("default_script.csv").toFile());
        return task;
    }

    /// Preserves filtering, deduplication, variant inference, and the Classical Chinese override.
    @Test
    void updatesMappings() throws IOException {
        createTask().update("""
                File-Date: 2026-10-01
                %%
                Type: language
                Subtag: abc
                Description: First line
                  continuation
                Macrolanguage: aa
                Suppress-Script: Latn
                %%
                Type: extlang
                Subtag: abc
                Macrolanguage: aa
                %%
                Type: language
                Subtag: old
                Macrolanguage: aa
                Deprecated: 2020-01-01
                %%
                Type: variant
                Subtag: example
                Prefix: aa-Latn
                Prefix: bb-Latn
                %%
                Type: variant
                Subtag: mixed
                Prefix: aa-Latn
                Prefix: bb-Cyrl
                %%
                Type: variant
                Subtag: implicit
                Prefix: aa
                %%
                Type: grandfathered
                Tag: i-example
                """);
        String header = "# IANA registry version: 2026-10-01\n"
                + "# Generated from https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry.\n";
        assertEquals(header + "aa,abc\n", Files.readString(directory.resolve("sublanguages.csv")));
        assertEquals(header + "Hant,lzh\nLatn,abc,example\n", Files.readString(directory.resolve("default_script.csv")));
    }

    /// Rejects missing metadata, invalid dates, empty registries, and malformed records before writing.
    @ParameterizedTest
    @ValueSource(strings = {
            "", "File-Date: 2026-10-01\n",
            "Type: language\nSubtag: abc\n",
            "File-Date: 2026-02-30\n%%\nType: language\nSubtag: abc\n",
            "File-Date: 2026-10-01\n%%\ninvalid",
            "File-Date: 2026-10-01\n%%\nType: language\n"
    })
    void preservesOutputsOnInvalidInput(String registry) throws IOException {
        var task = createTask();
        Files.writeString(directory.resolve("sublanguages.csv"), "original");
        Files.writeString(directory.resolve("default_script.csv"), "original");
        assertThrows(GradleException.class, () -> task.update(registry));
        assertEquals("original", Files.readString(directory.resolve("sublanguages.csv")));
        assertEquals("original", Files.readString(directory.resolve("default_script.csv")));
    }
}
